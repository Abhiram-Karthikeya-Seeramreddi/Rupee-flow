package com.rupeeflow.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Recurring-payment schedule maths. A schedule is only an expectation: nothing here
 * creates an expense. Payments are confirmed explicitly (or linked to an existing
 * transaction), and each due date can be settled at most once.
 */
object Schedule {

    /** The due date after [current]. [anchorDay] keeps month-end bills stable (31 → 28 → 31). */
    fun next(current: LocalDate, freq: Frequency, intervalDays: Int = 30, anchorDay: Int = current.dayOfMonth): LocalDate =
        when (freq) {
            Frequency.DAILY -> current.plusDays(1)
            Frequency.WEEKLY -> current.plusWeeks(1)
            Frequency.MONTHLY -> addMonths(current, 1, anchorDay)
            Frequency.QUARTERLY -> addMonths(current, 3, anchorDay)
            Frequency.HALF_YEARLY -> addMonths(current, 6, anchorDay)
            Frequency.YEARLY -> addMonths(current, 12, anchorDay)
            Frequency.CUSTOM_DAYS -> current.plusDays(maxOf(1, intervalDays).toLong())
        }

    private fun addMonths(d: LocalDate, months: Long, anchorDay: Int): LocalDate {
        val ym = YearMonth.from(d).plusMonths(months)
        return ym.atDay(minOf(anchorDay.coerceIn(1, 31), ym.lengthOfMonth()))
    }

    /** All due dates from [firstDue] that fall within [range]. */
    fun occurrences(firstDue: LocalDate, freq: Frequency, intervalDays: Int, anchorDay: Int, range: DateRange, max: Int = 400): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = firstDue
        var guard = 0
        while (!d.isAfter(range.end) && guard++ < 5000) {
            if (!d.isBefore(range.start)) out.add(d)
            if (out.size >= max) break
            d = next(d, freq, intervalDays, anchorDay)
        }
        return out
    }

    /** Cost per year in paise (exact for fixed frequencies, nearest paisa for custom). */
    fun yearlyCost(amount: Long, freq: Frequency, intervalDays: Int = 30): Long = when (freq) {
        Frequency.DAILY -> amount * 365
        Frequency.WEEKLY -> amount * 52
        Frequency.MONTHLY -> amount * 12
        Frequency.QUARTERLY -> amount * 4
        Frequency.HALF_YEARLY -> amount * 2
        Frequency.YEARLY -> amount
        Frequency.CUSTOM_DAYS -> (amount * 365 * 2 + maxOf(1, intervalDays)) / (2L * maxOf(1, intervalDays))
    }

    fun monthlyEquivalent(amount: Long, freq: Frequency, intervalDays: Int = 30): Long =
        (yearlyCost(amount, freq, intervalDays) * 2 + 12) / 24

    fun daysUntil(today: LocalDate, due: LocalDate): Long = ChronoUnit.DAYS.between(today, due)

    /** Which reminder (7/3/1/0 days before) should fire today, if any. */
    fun reminderDue(today: LocalDate, due: LocalDate, offsets: Set<Int>): Int? {
        val d = daysUntil(today, due).toInt()
        return if (d in offsets) d else null
    }

    /**
     * Finds an existing transaction that is very likely this bill's payment, so confirming
     * a bill can link to it instead of recording the same money twice.
     */
    fun findMatch(
        candidates: List<LedgerTx>,
        amount: Long,
        due: LocalDate,
        windowDays: Int = 5,
        tolerancePercent: Int = 2,
    ): LedgerTx? {
        val tol = maxOf(100L, amount * tolerancePercent / 100)
        return candidates
            .filter { it.type == TxType.EXPENSE && it.subscriptionId == null }
            .filter { abs(it.amount - amount) <= tol }
            .filter { abs(it.epochDay - due.toEpochDay()) <= windowDays }
            .minByOrNull { abs(it.epochDay - due.toEpochDay()) * 1000 + abs(it.amount - amount) }
    }
}
