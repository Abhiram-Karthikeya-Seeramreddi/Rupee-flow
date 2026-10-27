package com.rupeeflow.core

import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyAndPeriodsTest {

    @Test fun indianGrouping() {
        assertEquals("₹1,25,000", Money.format(rupees(125000)))
        assertEquals("₹45,000", Money.format(rupees(45000)))
        assertEquals("₹999", Money.format(rupees(999)))
        assertEquals("₹1,00,00,000", Money.format(rupees(10000000)))
        assertEquals("₹125,000", Money.format(rupees(125000), indianGrouping = false))
        assertEquals("-₹1,200.50", Money.format(-120050))
        assertEquals("₹0", Money.format(0))
        assertEquals("+₹10", Money.format(1000, showSign = true))
    }

    @Test fun compactFormat() {
        assertEquals("₹950", Money.compact(rupees(950)))
        assertEquals("₹12.5K", Money.compact(rupees(12500)))
        assertEquals("₹1.25L", Money.compact(rupees(125000)))
    }

    @Test fun parsingIsExact() {
        assertEquals(15000L, Money.parse("150"))
        assertEquals(125050L, Money.parse("1,250.50"))
        assertEquals(9990L, Money.parse("₹ 99.9"))
        assertEquals(200000L, Money.parse("2k"))
        assertEquals(15000000L, Money.parse("1.5L"))
        assertEquals(1L, Money.parse("0.01"))
        assertEquals(30L, Money.parse("0.1 + 0.2".substringAfter("+ ")) ?.let { it + Money.parse("0.1")!! })
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("-5"))
        assertEquals("125.50", Money.toInput(12550))
        assertEquals("150", Money.toInput(15000))
    }

    @Test fun salaryCycle28th() {
        val c = SalaryCycle(28)
        val r = c.cycleContaining(LocalDate.of(2026, 10, 28))
        assertEquals(LocalDate.of(2026, 10, 28), r.start)
        assertEquals(LocalDate.of(2026, 11, 27), r.end)
        val before = c.cycleContaining(LocalDate.of(2026, 10, 27))
        assertEquals(LocalDate.of(2026, 9, 28), before.start)
        assertEquals(LocalDate.of(2026, 10, 27), before.end)
        assertEquals(LocalDate.of(2026, 11, 28), c.next(r).start)
        assertEquals(LocalDate.of(2026, 12, 27), c.next(r).end)
        assertEquals(LocalDate.of(2026, 10, 28), c.nextSalaryDate(LocalDate.of(2026, 10, 24)))
        assertEquals(LocalDate.of(2026, 11, 28), c.nextSalaryDate(LocalDate.of(2026, 10, 28)))
    }

    @Test fun salaryCycleJanuaryAndYearBoundary() {
        val c = SalaryCycle(28)
        val r = c.cycleContaining(LocalDate.of(2027, 1, 5))
        assertEquals(LocalDate.of(2026, 12, 28), r.start)
        assertEquals(LocalDate.of(2027, 1, 27), r.end)
    }

    @Test fun salaryDay31ClampsInShortMonthsAndLeapYears() {
        val c = SalaryCycle(31)
        // Non-leap Feb 2027 -> 28th
        val feb = c.cycleContaining(LocalDate.of(2027, 3, 1))
        assertEquals(LocalDate.of(2027, 2, 28), feb.start)
        assertEquals(LocalDate.of(2027, 3, 30), feb.end)
        // Leap Feb 2028 -> 29th
        val leap = c.cycleContaining(LocalDate.of(2028, 2, 29))
        assertEquals(LocalDate.of(2028, 2, 29), leap.start)
        assertEquals(LocalDate.of(2028, 3, 30), leap.end)
        val c29 = SalaryCycle(29)
        assertEquals(LocalDate.of(2027, 2, 28), c29.cycleContaining(LocalDate.of(2027, 3, 10)).start)
        assertEquals(LocalDate.of(2028, 2, 29), c29.cycleContaining(LocalDate.of(2028, 3, 10)).start)
    }

    @Test fun cyclesTileWithoutGapsForYears() {
        for (day in 1..31) {
            val c = SalaryCycle(day)
            var r = c.cycleContaining(LocalDate.of(2025, 1, 1))
            repeat(60) {
                val n = c.next(r)
                assertEquals(r.end.plusDays(1), n.start, "gap for salary day $day after $r")
                assertTrue(n.days in 28..31, "odd length ${n.days} for day $day")
                r = n
            }
        }
    }

    @Test fun earlyAndLateSalaryMoveTheCycle() {
        val early = SalaryCycle(28, setOf(LocalDate.of(2026, 10, 26)))
        val r = early.cycleContaining(LocalDate.of(2026, 10, 26))
        assertEquals(LocalDate.of(2026, 10, 26), r.start)
        assertEquals(LocalDate.of(2026, 11, 27), r.end)
        assertEquals(LocalDate.of(2026, 10, 25), early.cycleContaining(LocalDate.of(2026, 10, 25)).end)

        val late = SalaryCycle(28, setOf(LocalDate.of(2026, 10, 30)))
        assertEquals(LocalDate.of(2026, 9, 28), late.cycleContaining(LocalDate.of(2026, 10, 29)).start)
        assertEquals(LocalDate.of(2026, 10, 30), late.cycleContaining(LocalDate.of(2026, 10, 30)).start)

        // Turning the option off ignores actual dates.
        val off = SalaryCycle(28, setOf(LocalDate.of(2026, 10, 26)), followActualSalary = false)
        assertEquals(LocalDate.of(2026, 10, 28), off.cycleContaining(LocalDate.of(2026, 10, 28)).start)
        // A salary far from the schedule (bonus etc.) is ignored.
        val far = SalaryCycle(28, setOf(LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2026, 9, 28), far.cycleContaining(LocalDate.of(2026, 10, 20)).start)
    }

    @Test fun periodsResolve() {
        val today = LocalDate.of(2026, 10, 24) // Saturday
        val c = SalaryCycle(28)
        val week = Periods.resolve(PeriodKind.THIS_WEEK, today, c, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 19), week.start)
        assertEquals(LocalDate.of(2026, 10, 25), week.end)
        val sunWeek = Periods.resolve(PeriodKind.THIS_WEEK, today, c, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 18), sunWeek.start)
        assertEquals(LocalDate.of(2026, 10, 1), Periods.resolve(PeriodKind.THIS_MONTH, today, c, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.of(2026, 9, 28), Periods.resolve(PeriodKind.SALARY_CYCLE, today, c, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.of(2026, 8, 28), Periods.resolve(PeriodKind.LAST_CYCLE, today, c, DayOfWeek.MONDAY).start)
        val q = Periods.quarterContaining(today)
        assertEquals(LocalDate.of(2026, 10, 1), q.start); assertEquals(LocalDate.of(2026, 12, 31), q.end)
        val r = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27))
        assertEquals(31, r.days)
        assertEquals(4, r.daysLeft(LocalDate.of(2026, 11, 24)))
    }
}
