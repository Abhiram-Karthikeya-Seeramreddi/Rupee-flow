package com.rupeeflow.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Inclusive date range stored as epoch days so it is time-zone independent once recorded. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "Range end $end is before start $start" } }
    val startDay: Long get() = start.toEpochDay()
    val endDay: Long get() = end.toEpochDay()
    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    fun contains(epochDay: Long) = epochDay in startDay..endDay

    /** Days from [today] to the end, including today. 0 if the range is over. */
    fun daysLeft(today: LocalDate): Int = when {
        today.isAfter(end) -> 0
        today.isBefore(start) -> days
        else -> (ChronoUnit.DAYS.between(today, end) + 1).toInt()
    }

    /** Days elapsed including today. */
    fun daysElapsed(today: LocalDate): Int = when {
        today.isBefore(start) -> 0
        today.isAfter(end) -> days
        else -> (ChronoUnit.DAYS.between(start, today) + 1).toInt()
    }

    /** The range of the same length immediately before this one. */
    fun previousSameLength(): DateRange {
        val newEnd = start.minusDays(1)
        return DateRange(newEnd.minusDays(days.toLong() - 1), newEnd)
    }

    fun label(): String {
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        val fy = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        return if (start == end) fy.format(start)
        else if (start.year == end.year) "${f.format(start)} – ${fy.format(end)}"
        else "${fy.format(start)} – ${fy.format(end)}"
    }

    companion object {
        fun ofDays(startDay: Long, endDay: Long) = DateRange(LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay))
        fun single(d: LocalDate) = DateRange(d, d)
    }
}

/**
 * Salary-cycle engine. With salary day 28 the cycle is 28th -> 27th of next month.
 * Short months clamp: salary day 31 in February becomes 28/29 Feb.
 *
 * When [followActualSalary] is on and a salary credit was actually recorded within
 * ±[WINDOW_DAYS] days of the scheduled date, the cycle starts on the real credit date
 * (handles salary arriving early or late). Missing salaries fall back to the schedule.
 */
class SalaryCycle(
    val salaryDay: Int,
    private val actualSalaryDates: Set<LocalDate> = emptySet(),
    private val followActualSalary: Boolean = true,
) {
    init { require(salaryDay in 1..31) { "Salary day must be 1..31" } }

    fun scheduledStart(ym: YearMonth): LocalDate = ym.atDay(minOf(salaryDay, ym.lengthOfMonth()))

    fun effectiveStart(ym: YearMonth): LocalDate {
        val scheduled = scheduledStart(ym)
        if (!followActualSalary || actualSalaryDates.isEmpty()) return scheduled
        val lo = scheduled.minusDays(WINDOW_DAYS)
        val hi = scheduled.plusDays(WINDOW_DAYS)
        return actualSalaryDates.filter { !it.isBefore(lo) && !it.isAfter(hi) }.minOrNull() ?: scheduled
    }

    fun cycleContaining(date: LocalDate): DateRange {
        val ym = YearMonth.from(date)
        val candidates = listOf(ym.minusMonths(2), ym.minusMonths(1), ym, ym.plusMonths(1))
        val startYm = candidates.filter { !effectiveStart(it).isAfter(date) }.maxOrNull() ?: ym.minusMonths(2)
        val start = effectiveStart(startYm)
        val end = effectiveStart(startYm.plusMonths(1)).minusDays(1)
        return DateRange(start, end)
    }

    fun previous(range: DateRange): DateRange = cycleContaining(range.start.minusDays(1))
    fun next(range: DateRange): DateRange = cycleContaining(range.end.plusDays(1))

    /** Next date salary is expected, strictly after [today]'s cycle start. */
    fun nextSalaryDate(today: LocalDate): LocalDate {
        val current = cycleContaining(today)
        return scheduledStart(YearMonth.from(current.end.plusDays(1)))
    }

    companion object { const val WINDOW_DAYS = 7L }
}

enum class PeriodKind(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This week"),
    LAST_WEEK("Last week"),
    SALARY_CYCLE("This salary cycle"),
    LAST_CYCLE("Last salary cycle"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    LAST_6_MONTHS("Last 6 months"),
    LAST_12_MONTHS("Last 12 months"),
    CUSTOM("Custom range"),
}

object Periods {

    fun weekContaining(date: LocalDate, firstDay: DayOfWeek): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
        return DateRange(start, start.plusDays(6))
    }

    fun monthContaining(date: LocalDate): DateRange {
        val ym = YearMonth.from(date)
        return DateRange(ym.atDay(1), ym.atEndOfMonth())
    }

    fun quarterContaining(date: LocalDate): DateRange {
        val firstMonth = ((date.monthValue - 1) / 3) * 3 + 1
        val start = LocalDate.of(date.year, firstMonth, 1)
        return DateRange(start, start.plusMonths(3).minusDays(1))
    }

    fun yearContaining(date: LocalDate) = DateRange(LocalDate.of(date.year, 1, 1), LocalDate.of(date.year, 12, 31))

    fun resolve(
        kind: PeriodKind,
        today: LocalDate,
        cycle: SalaryCycle,
        firstDay: DayOfWeek,
        custom: DateRange? = null,
    ): DateRange = when (kind) {
        PeriodKind.TODAY -> DateRange.single(today)
        PeriodKind.YESTERDAY -> DateRange.single(today.minusDays(1))
        PeriodKind.THIS_WEEK -> weekContaining(today, firstDay)
        PeriodKind.LAST_WEEK -> weekContaining(today.minusWeeks(1), firstDay)
        PeriodKind.SALARY_CYCLE -> cycle.cycleContaining(today)
        PeriodKind.LAST_CYCLE -> cycle.previous(cycle.cycleContaining(today))
        PeriodKind.THIS_MONTH -> monthContaining(today)
        PeriodKind.LAST_MONTH -> monthContaining(today.minusMonths(1))
        PeriodKind.LAST_3_MONTHS -> DateRange(today.minusMonths(3).plusDays(1), today)
        PeriodKind.LAST_6_MONTHS -> DateRange(today.minusMonths(6).plusDays(1), today)
        PeriodKind.LAST_12_MONTHS -> DateRange(today.minusMonths(12).plusDays(1), today)
        PeriodKind.CUSTOM -> custom ?: DateRange.single(today)
    }

    /** The comparable previous period, used for "vs last time" insights. */
    fun previousOf(kind: PeriodKind, current: DateRange, cycle: SalaryCycle, firstDay: DayOfWeek): DateRange = when (kind) {
        PeriodKind.SALARY_CYCLE, PeriodKind.LAST_CYCLE -> cycle.previous(current)
        PeriodKind.THIS_MONTH, PeriodKind.LAST_MONTH -> monthContaining(current.start.minusDays(1))
        PeriodKind.THIS_WEEK, PeriodKind.LAST_WEEK -> weekContaining(current.start.minusDays(1), firstDay)
        else -> current.previousSameLength()
    }

    fun budgetRange(
        period: BudgetPeriod,
        date: LocalDate,
        cycle: SalaryCycle,
        firstDay: DayOfWeek,
        customStart: LocalDate? = null,
        customEnd: LocalDate? = null,
    ): DateRange = when (period) {
        BudgetPeriod.DAILY -> DateRange.single(date)
        BudgetPeriod.WEEKLY -> weekContaining(date, firstDay)
        BudgetPeriod.MONTHLY -> monthContaining(date)
        BudgetPeriod.SALARY_CYCLE -> cycle.cycleContaining(date)
        BudgetPeriod.QUARTERLY -> quarterContaining(date)
        BudgetPeriod.YEARLY -> yearContaining(date)
        BudgetPeriod.CUSTOM -> {
            val s = customStart ?: date
            val e = customEnd ?: s
            DateRange(s, if (e.isBefore(s)) s else e)
        }
    }

    fun previousBudgetRange(
        period: BudgetPeriod,
        current: DateRange,
        cycle: SalaryCycle,
        firstDay: DayOfWeek,
    ): DateRange? = when (period) {
        BudgetPeriod.CUSTOM -> null
        else -> budgetRange(period, current.start.minusDays(1), cycle, firstDay)
    }
}
