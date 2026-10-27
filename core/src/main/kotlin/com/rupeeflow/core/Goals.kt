package com.rupeeflow.core

import java.time.LocalDate

data class GoalProgress(
    val target: Long,
    val saved: Long,
    val remaining: Long,
    val percent: Int,
    val cyclesLeft: Int,
    val perCycle: Long,
    val done: Boolean,
)

object GoalMath {
    /**
     * [saved] must come from real money movements (transfers tagged to the goal) plus any
     * amount the user declared as already saved when creating the goal.
     */
    fun progress(target: Long, saved: Long, today: LocalDate, targetDate: LocalDate?, cycle: SalaryCycle): GoalProgress {
        val remaining = maxOf(0L, target - saved)
        val cycles = if (targetDate == null || !targetDate.isAfter(today)) 1 else cyclesBetween(today, targetDate, cycle)
        val perCycle = if (remaining == 0L) 0 else (remaining + cycles - 1) / cycles
        return GoalProgress(target, saved, remaining, Money.percent(saved, target).coerceAtMost(100), cycles, perCycle, remaining == 0L)
    }

    /** Number of salary cycles that start between today (current cycle counts) and the target date. */
    fun cyclesBetween(today: LocalDate, targetDate: LocalDate, cycle: SalaryCycle): Int {
        var n = 1
        var c = cycle.cycleContaining(today)
        while (true) {
            val nxt = cycle.next(c)
            if (nxt.start.isAfter(targetDate) || n > 600) break
            n++
            c = nxt
        }
        return n
    }
}

data class BillLite(
    val id: String,
    val amount: Long,
    val nextDue: LocalDate,
    val frequency: Frequency,
    val intervalDays: Int,
    val anchorDay: Int,
    val active: Boolean,
)

object Bills {
    /** Total of unpaid bill occurrences due from today (and any overdue) up to [until]. */
    fun dueUntil(bills: List<BillLite>, today: LocalDate, until: LocalDate): Long {
        var total = 0L
        for (b in bills) {
            if (!b.active) continue
            val start = minOf(b.nextDue, today)
            if (b.nextDue.isAfter(until)) continue
            val occ = Schedule.occurrences(b.nextDue, b.frequency, b.intervalDays, b.anchorDay, DateRange(start, maxOf(until, start)))
            total += occ.size * b.amount
        }
        return total
    }
}
