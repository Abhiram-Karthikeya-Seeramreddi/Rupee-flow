package com.rupeeflow.core

import java.time.LocalDate

enum class BudgetState(val label: String) {
    NOT_STARTED("Not started"),
    ON_TRACK("On track"),
    FAST("Spending fast"),
    NEAR_LIMIT("Near limit"),
    OVER("Over budget"),
}

data class BudgetStatus(
    val limit: Long,
    val spent: Long,
    /** Can be negative when overspent. This is the category budget, NOT your bank balance. */
    val remaining: Long,
    val percentUsed: Int,
    val daysLeft: Int,
    val daysTotal: Int,
    val state: BudgetState,
    /** How much you can spend per remaining day to stay within this budget. */
    val perDayLeft: Long,
    /** Estimate only: spent so far extrapolated at the current pace. */
    val projectedSpend: Long,
    val thresholdsCrossed: List<Int>,
)

object BudgetMath {

    fun status(
        limit: Long,
        spent: Long,
        range: DateRange,
        today: LocalDate,
        thresholds: List<Int> = listOf(50, 75, 90, 100),
        carryOver: Long = 0,
    ): BudgetStatus {
        val effectiveLimit = maxOf(0L, limit + carryOver)
        val remaining = effectiveLimit - spent
        val pct = Money.percent(spent, effectiveLimit)
        val daysLeft = range.daysLeft(today)
        val elapsed = range.daysElapsed(today)
        val projected = if (elapsed in 1 until range.days) spent * range.days / elapsed else spent
        val elapsedPct = if (range.days == 0) 100 else elapsed * 100 / range.days
        val state = when {
            today.isBefore(range.start) -> BudgetState.NOT_STARTED
            spent > effectiveLimit -> BudgetState.OVER
            pct >= 90 -> BudgetState.NEAR_LIMIT
            // "Unusually fast": used noticeably more of the budget than of the period.
            pct >= 30 && pct > elapsedPct + 15 && projected > effectiveLimit -> BudgetState.FAST
            else -> BudgetState.ON_TRACK
        }
        val perDay = if (daysLeft > 0) maxOf(0L, remaining) / daysLeft else 0L
        return BudgetStatus(
            limit = effectiveLimit,
            spent = spent,
            remaining = remaining,
            percentUsed = pct,
            daysLeft = daysLeft,
            daysTotal = range.days,
            state = state,
            perDayLeft = perDay,
            projectedSpend = projected,
            thresholdsCrossed = thresholds.sorted().filter { pct >= it },
        )
    }

    /** Rollover: unused (or overspent) amount from the previous period, one period deep. */
    fun carryOver(previousLimit: Long, previousSpent: Long): Long = previousLimit - previousSpent

    fun parseThresholds(csv: String): List<Int> =
        csv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..200 }.distinct().sorted()
}

/**
 * "Safe to spend": how much you can spend per day until the next salary while still
 * paying known bills and planned savings. Every input is real data; nothing is guessed.
 */
data class SafeToSpend(
    val spendable: Long,
    val upcomingBills: Long,
    val plannedSavingsLeft: Long,
    val free: Long,
    val daysLeft: Int,
    val perDay: Long,
    val spentToday: Long,
    val leftToday: Long,
)

object SafeToSpendCalc {
    fun compute(
        spendableBalance: Long,
        upcomingBills: Long,
        plannedSavingsLeft: Long,
        daysLeftInCycle: Int,
        spentToday: Long,
    ): SafeToSpend {
        val free = spendableBalance - upcomingBills - plannedSavingsLeft
        val days = maxOf(1, daysLeftInCycle)
        // Today's allowance is computed as if today's spending hadn't happened yet,
        // so the number doesn't shrink twice for the same purchase.
        val perDay = maxOf(0L, free + spentToday) / days
        return SafeToSpend(
            spendable = spendableBalance,
            upcomingBills = upcomingBills,
            plannedSavingsLeft = plannedSavingsLeft,
            free = free,
            daysLeft = days,
            perDay = perDay,
            spentToday = spentToday,
            leftToday = perDay - spentToday,
        )
    }
}
