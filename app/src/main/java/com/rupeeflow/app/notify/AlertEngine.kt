package com.rupeeflow.app.notify

import com.rupeeflow.app.AppContainer
import com.rupeeflow.app.data.AlertLogEntity
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.subStatus
import com.rupeeflow.core.BudgetState
import com.rupeeflow.core.Money
import com.rupeeflow.core.SubStatus

/**
 * Decides which notifications to show. Every alert has a stable key stored in alert_log,
 * so the same alert never fires twice (non-repetitive by construction).
 */
class AlertEngine(private val c: AppContainer) {

    private suspend fun once(key: String, block: () -> Unit) {
        val misc = c.db.misc()
        if (misc.alertFired(key)) return
        misc.logAlert(AlertLogEntity(key))
        block()
    }

    private fun fmt(p: Long, s: Snapshot) = Money.format(p, s.settings.indianGrouping)

    /**
     * @param immediateOnly true right after a transaction is saved: only budget alerts
     * (time-based reminders are left to the background worker).
     */
    suspend fun evaluate(s: Snapshot, immediateOnly: Boolean = false) {
        if (!s.settings.onboarded) return
        val n = c.notifier

        if (s.settings.budgetAlerts) {
            for (b in s.budgets) {
                val st = b.status
                val start = b.range.startDay
                val crossed = st.thresholdsCrossed
                if (crossed.isNotEmpty()) {
                    val highest = crossed.last()
                    // Log lower thresholds silently so we only ever show the most relevant one.
                    val newOnes = crossed.filter { !c.db.misc().alertFired("budget:${b.budget.id}:$start:$it") }
                    if (newOnes.isNotEmpty()) {
                        newOnes.forEach { c.db.misc().logAlert(AlertLogEntity("budget:${b.budget.id}:$start:$it")) }
                        val text = if (st.remaining < 0)
                            "${b.name} is over budget by ${fmt(-st.remaining, s)}. Your account balance is separate — this is the category budget."
                        else "You've used $highest% of your ${b.name.lowercase()} budget. ${fmt(st.remaining, s)} left for ${st.daysLeft} day${if (st.daysLeft == 1) "" else "s"}."
                        n.show(("b" + b.budget.id).hashCode(), Notifier.CH_BUDGETS,
                            if (st.remaining < 0) "${b.emoji} Budget exceeded" else "${b.emoji} ${b.name}: $highest% used", text,
                            tap = n.openAppIntent(Notifier.ACTION_OPEN_BUDGETS, 11))
                    }
                }
                if (st.state == BudgetState.FAST && crossed.lastOrNull()?.let { it < 90 } != false) {
                    once("fast:${b.budget.id}:$start") {
                        n.show(("f" + b.budget.id).hashCode(), Notifier.CH_BUDGETS, "${b.emoji} Spending fast on ${b.name}",
                            "At this pace you'd reach about ${fmt(st.projectedSpend, s)} (estimate) against ${fmt(st.limit, s)}. Try to keep to ${fmt(st.perDayLeft, s)}/day.",
                            tap = n.openAppIntent(Notifier.ACTION_OPEN_BUDGETS, 12))
                    }
                }
            }
        }
        if (immediateOnly) return

        if (s.settings.billReminders) {
            for (bill in s.bills) {
                val sub = bill.sub
                if (sub.subStatus() != SubStatus.ACTIVE) continue
                val d = bill.daysUntil
                val offsets = buildSet { if (sub.remind7) add(7); if (sub.remind3) add(3); if (sub.remind1) add(1) }
                val notifId = ("bill" + sub.id).hashCode()
                if (d in offsets) {
                    once("bill:${sub.id}:${sub.nextDue}:$d") {
                        n.show(notifId, Notifier.CH_BILLS, "${sub.emoji} ${sub.name} due ${if (d == 1) "tomorrow" else "in $d days"}",
                            "${fmt(sub.amount, s)} on ${bill.nextDue.dayOfMonth} ${bill.nextDue.month.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                                if (sub.autopay) " (auto-pay)." else ".",
                            tap = n.openAppIntent(Notifier.ACTION_OPEN_BILLS, 13))
                    }
                }
                if (d <= 0) {
                    once("billdue:${sub.id}:${sub.nextDue}") {
                        val title = if (d == 0) "${sub.emoji} ${sub.name} is due today" else "${sub.emoji} ${sub.name} is overdue"
                        val text = if (sub.autopay) "Auto-pay of ${fmt(sub.amount, s)} — confirm once it has gone through so it's counted once."
                        else "${fmt(sub.amount, s)}. Tap Paid when you've paid it."
                        n.show(notifId, Notifier.CH_BILLS, title, text, tap = n.openAppIntent(Notifier.ACTION_OPEN_BILLS, 14),
                            actions = listOf(Notifier.Action("Paid ${fmt(sub.amount, s)}",
                                n.actionIntent(ActionReceiver.ACTION_PAY_BILL, notifId,
                                    mapOf(ActionReceiver.EXTRA_SUB to sub.id, ActionReceiver.EXTRA_DUE to sub.nextDue.toString())))))
                    }
                }
            }
        }

        if (s.settings.cycleReminders) {
            if (s.daysToSalary == 1) once("cycle-eve:${s.nextSalaryDate}") {
                n.show(301, Notifier.CH_CYCLE, "🗓️ New salary cycle tomorrow",
                    "Your next salary cycle begins tomorrow. ${fmt(s.safe.free.coerceAtLeast(0), s)} is still free this cycle.")
            }
            if (s.today == s.cycleRange.start) once("cycle-start:${s.cycleRange.start}") {
                n.show(302, Notifier.CH_CYCLE, "🌱 New budget cycle started",
                    "Budgets reset for ${s.cycleRange.label()}. Unspent money stays in your accounts.")
            }
            if (s.salaryPrompt && s.settings.expectedSalary > 0) once("salary:${s.cycleRange.start}:${s.nextSalaryDate}") {
                n.show(303, Notifier.CH_CYCLE, "💼 Did your salary arrive?",
                    "Expected ${fmt(s.settings.expectedSalary, s)}. Record it so your balance stays accurate.",
                    actions = listOf(Notifier.Action("Received ${fmt(s.settings.expectedSalary, s)}",
                        n.actionIntent(ActionReceiver.ACTION_SALARY, 303))))
            }
        }

        if (s.settings.savingsReminders && s.settings.plannedSavingsPerCycle > 0) {
            val left = s.cycleRange.daysLeft(s.today)
            if (left in 1..5 && s.plannedSavingsLeft > 0) once("savings:${s.cycleRange.start}") {
                n.show(304, Notifier.CH_CYCLE, "🐷 Savings plan check",
                    "You are ${fmt(s.plannedSavingsLeft, s)} short of this cycle's savings plan, with $left day${if (left == 1) "" else "s"} to go.")
            }
        }

        c.db.misc().pruneAlerts(System.currentTimeMillis() - 400L * 24 * 3600 * 1000)
    }

    suspend fun dailyReminder(s: Snapshot) {
        if (!s.settings.onboarded || !s.settings.dailyReminder) return
        once("daily:${s.today}") {
            val n = c.notifier
            if (s.loggedToday) {
                val left = s.safe.leftToday
                n.show(201, Notifier.CH_REMINDERS, "📒 Today you spent ${fmt(s.spentToday, s)}",
                    if (left >= 0) "${fmt(left, s)} of today's safe-to-spend is left. Streak: ${s.streak} day${if (s.streak == 1) "" else "s"} 🔥"
                    else "That's ${fmt(-left, s)} over today's safe-to-spend. Tomorrow's a fresh start.",
                    tap = n.openAppIntent(null, 21))
            } else {
                n.show(201, Notifier.CH_REMINDERS, "📒 Have you recorded today's expenses?",
                    "Takes 5 seconds. Keep your ${s.streak}-day streak going.",
                    tap = n.openAppIntent(Notifier.ACTION_QUICK_ADD, 22),
                    actions = listOf(
                        Notifier.Action("Add expense", n.openAppIntent(Notifier.ACTION_QUICK_ADD, 23)),
                        Notifier.Action("No spend today", n.actionIntent(ActionReceiver.ACTION_NO_SPEND, 24)),
                    ))
            }
        }
    }
}
