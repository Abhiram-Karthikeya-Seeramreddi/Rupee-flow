package com.rupeeflow.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnginesTest {

    private val keywords = DefaultCategories.all.flatMap { d ->
        listOf(CategoryKeywords(d.key, d.keywords, d.kind)) +
            d.subcategories.map { sub -> DefaultCategories.subId(d.key, sub).let { id -> CategoryKeywords(id, DefaultCategories.keywordsFor(id), d.kind) } }
    }

    @Test fun budgetStatusAndThresholds() {
        val range = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27))
        val s = BudgetMath.status(rupees(5000), rupees(3800), range, LocalDate.of(2026, 11, 20))
        assertEquals(rupees(1200), s.remaining)
        assertEquals(76, s.percentUsed)
        assertEquals(listOf(50, 75), s.thresholdsCrossed)
        assertEquals(8, s.daysLeft)
        assertEquals(rupees(150), s.perDayLeft)

        val over = BudgetMath.status(rupees(3000), rupees(3400), range, LocalDate.of(2026, 11, 20))
        assertEquals(BudgetState.OVER, over.state)
        assertEquals(rupees(-400), over.remaining)
        assertEquals(0, over.perDayLeft)
        assertTrue(100 in over.thresholdsCrossed)

        // 60% used after 5 of 31 days -> spending fast.
        val fast = BudgetMath.status(rupees(5000), rupees(3000), range, LocalDate.of(2026, 11, 1))
        assertEquals(BudgetState.FAST, fast.state)
        assertTrue(fast.projectedSpend > rupees(5000))
    }

    @Test fun budgetRollover() {
        assertEquals(rupees(800), BudgetMath.carryOver(rupees(5000), rupees(4200)))
        val range = DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        val s = BudgetMath.status(rupees(5000), rupees(5500), range, LocalDate.of(2026, 11, 10), carryOver = rupees(800))
        assertEquals(rupees(5800), s.limit)
        assertEquals(rupees(300), s.remaining)
        assertEquals(listOf(50, 75, 90), BudgetMath.parseThresholds("90, 50,75,abc"))
    }

    @Test fun scheduleKeepsMonthEndAnchor() {
        val jan31 = LocalDate.of(2027, 1, 31)
        val feb = Schedule.next(jan31, Frequency.MONTHLY, anchorDay = 31)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), Schedule.next(feb, Frequency.MONTHLY, anchorDay = 31))
        assertEquals(LocalDate.of(2028, 2, 29), Schedule.next(LocalDate.of(2027, 2, 28), Frequency.YEARLY, anchorDay = 29))
        assertEquals(LocalDate.of(2026, 11, 7), Schedule.next(LocalDate.of(2026, 10, 31), Frequency.WEEKLY))
        assertEquals(LocalDate.of(2027, 1, 5), Schedule.next(LocalDate.of(2026, 10, 5), Frequency.QUARTERLY))
        assertEquals(LocalDate.of(2026, 11, 14), Schedule.next(LocalDate.of(2026, 10, 31), Frequency.CUSTOM_DAYS, intervalDays = 14))
    }

    @Test fun subscriptionCosts() {
        assertEquals(rupees(18000), Schedule.yearlyCost(rupees(1500), Frequency.MONTHLY))
        assertEquals(rupees(1500), Schedule.monthlyEquivalent(rupees(1500), Frequency.MONTHLY))
        assertEquals(rupees(125), Schedule.monthlyEquivalent(rupees(1500), Frequency.YEARLY))
        assertEquals(rupees(1300), Schedule.monthlyEquivalent(rupees(300), Frequency.WEEKLY))
        assertEquals(rupees(500), Schedule.monthlyEquivalent(rupees(3000), Frequency.HALF_YEARLY))
    }

    @Test fun billsDueUntilCycleEnd() {
        val today = LocalDate.of(2026, 10, 28)
        val bills = listOf(
            BillLite("gym", rupees(1500), LocalDate.of(2026, 11, 5), Frequency.MONTHLY, 30, 5, true),
            BillLite("net", rupees(799), LocalDate.of(2026, 10, 20), Frequency.MONTHLY, 30, 20, true), // overdue + next on 20 Nov
            BillLite("old", rupees(999), LocalDate.of(2026, 11, 1), Frequency.MONTHLY, 30, 1, false),
            BillLite("ott", rupees(1499), LocalDate.of(2027, 3, 1), Frequency.YEARLY, 30, 1, true),
        )
        assertEquals(rupees(1500) + rupees(799) * 2, Bills.dueUntil(bills, today, LocalDate.of(2026, 11, 27)))
    }

    @Test fun quickParseExamples() {
        val p = QuickParse.parse("Spent 250 rupees on chicken biryani using UPI", keywords)
        assertEquals(rupees(250), p.amount)
        assertEquals("restaurants", p.categoryId)
        assertEquals(PaymentMethod.UPI, p.payment)
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals("Chicken biryani", p.note)
        assertEquals(0, p.dayOffset)

        val a = QuickParse.parse("120 auto yesterday", keywords)
        assertEquals(rupees(120), a.amount); assertEquals("transport", a.categoryId); assertEquals(-1, a.dayOffset)

        val s = QuickParse.parse("got salary 45,000", keywords)
        assertEquals(TxType.INCOME, s.type); assertEquals("salary", s.categoryId); assertEquals(rupees(45000), s.amount)

        val c = QuickParse.parse("petrol 1.5k credit card", keywords)
        assertEquals(rupees(1500), c.amount); assertEquals("fuel", c.categoryId); assertEquals(PaymentMethod.CREDIT_CARD, c.payment)

        val b = QuickParse.parse("₹400 badminton court gpay", keywords)
        assertEquals("sports__badminton", b.categoryId); assertEquals(PaymentMethod.UPI, b.payment); assertEquals(rupees(400), b.amount)
        assertEquals("sports", QuickParse.parse("500 turf booking", keywords).categoryId)

        val ruled = QuickParse.parse("350 at Ramu's mess", keywords, mapOf("ramu's mess" to "restaurants"))
        assertEquals("restaurants", ruled.categoryId)

        val noAmount = QuickParse.parse("coffee", keywords)
        assertNull(noAmount.amount)
        assertEquals("restaurants", noAmount.categoryId)

        val decimal = QuickParse.parse("milk 32.50", keywords)
        assertEquals(3250L, decimal.amount); assertEquals("food", decimal.categoryId)
    }

    @Test fun sportsCategoryStructure() {
        val sports = DefaultCategories.byKey.getValue("sports")
        assertEquals("Sports", sports.name)
        assertTrue("Badminton" in sports.subcategories)
        assertEquals("sports__badminton", DefaultCategories.legacySportsTarget)
        assertTrue("shuttle" in DefaultCategories.keywordsFor("sports__badminton"))
        assertNull(DefaultCategories.byKey["badminton"])
    }

    @Test fun csvBankStatement() {
        val csv = """
            Account No: XXXX1234,,,,
            Statement period,01/10/2026 - 24/10/2026,,,
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            02/10/26,UPI-SWIGGY-swiggy@ybl-REF1,000412345678,02/10/26,"1,250.00",,"43,750.00"
            28/09/2026,SALARY OCT ACME LTD,000498765432,28/09/26,,"45,000.00","45,000.00"
            not a date,garbage,,,,,
        """.trimIndent()
        val res = CsvImport.parse(csv)
        assertNull(res.error)
        assertEquals(2, res.rows.size)
        val sw = res.rows[0]
        assertEquals(LocalDate.of(2026, 10, 2), sw.date)
        assertEquals(rupees(1250), sw.amount)
        assertTrue(sw.isDebit)
        val sal = res.rows[1]
        assertTrue(!sal.isDebit); assertEquals(rupees(45000), sal.amount)
        assertEquals(1, res.skippedLines)
        // Same row twice -> same fingerprint (duplicate detection).
        assertEquals(CsvImport.parse(csv).rows[0].fingerprint, sw.fingerprint)
    }

    @Test fun csvSignedAmountColumn() {
        val csv = "date;description;amount\n2026-10-05;Zomato;-349.00\n2026-10-06;Refund Amazon;199\n"
        val rows = CsvImport.parse(csv).rows
        assertEquals(2, rows.size)
        assertTrue(rows[0].isDebit); assertEquals(34900L, rows[0].amount)
        assertTrue(!rows[1].isDebit)
    }

    @Test fun smsParsing() {
        val d = LocalDate.of(2026, 10, 24)
        val upi = SmsParser.parse("Rs.250.00 debited from A/c XX1234 on 24-10-26 to VPA swiggy.food@icici (UPI Ref No 629812345678). Not you? Call 1800", d)
        assertNotNull(upi)
        assertEquals(rupees(250), upi.amount); assertTrue(upi.isDebit); assertEquals("Swiggy Food", upi.description)
        assertEquals("629812345678", upi.reference); assertEquals("1234", upi.accountHint)

        val credit = SmsParser.parse("INR 45,000.00 credited to your A/c XX1234 on 28-Oct-26. Info: SALARY ACME. Avl Bal INR 45,120.00", d)
        assertNotNull(credit); assertTrue(!credit.isDebit); assertEquals(rupees(45000), credit.amount)

        val card = SmsParser.parse("You've spent Rs 1,999 on your HDFC Bank Credit Card xx9876 at AMAZON on 2026-10-24.", d)
        assertNotNull(card); assertTrue(card.isDebit); assertEquals("Amazon", card.description)

        assertNull(SmsParser.parse("Your OTP for txn of Rs 500 is 123456", d))
        assertNull(SmsParser.parse("Rs 799 will be debited on 25-10 for your Netflix mandate", d))
        assertNull(SmsParser.parse("Minimum amount due Rs 2,000 on your card. Due date 05-11", d))
    }

    @Test fun safeToSpend() {
        val s = SafeToSpendCalc.compute(rupees(30000), rupees(2300), rupees(5000), daysLeftInCycle = 20, spentToday = rupees(150))
        assertEquals(rupees(22700), s.free)
        assertEquals((rupees(22700) + rupees(150)) / 20, s.perDay)
        assertEquals(s.perDay - rupees(150), s.leftToday)
        val broke = SafeToSpendCalc.compute(rupees(1000), rupees(2000), 0, 10, 0)
        assertEquals(0, broke.perDay)
        assertTrue(broke.free < 0)
    }

    @Test fun goalsAndStreaks() {
        val c = SalaryCycle(28)
        val g = GoalMath.progress(rupees(60000), rupees(10000), LocalDate.of(2026, 10, 28), LocalDate.of(2027, 3, 31), c)
        assertEquals(rupees(50000), g.remaining)
        assertEquals(17, g.percent)
        assertEquals(6, g.cyclesLeft) // Oct28, Nov28, Dec28, Jan28, Feb28, Mar28
        assertEquals(rupees(50000) / 6 + 1, g.perCycle)

        val today = LocalDate.of(2026, 10, 24).toEpochDay()
        assertEquals(3, Analytics.streak(setOf(today, today - 1, today - 2, today - 4), LocalDate.ofEpochDay(today)))
        assertEquals(2, Analytics.streak(setOf(today - 1, today - 2), LocalDate.ofEpochDay(today)))
        assertEquals(0, Analytics.streak(emptySet(), LocalDate.ofEpochDay(today)))

        val h = Analytics.healthScore(100, 25, 10, 10)
        assertEquals(100, h.total)
        assertEquals(20 + 15 + 15, Analytics.healthScore(50, 10, 5, 10).total)
    }

    @Test fun insightsComeFromData() {
        val cats = DefaultCategories.all.associate { it.key to CategoryMeta(it.key, it.name, it.emoji, it.essential, it.fixed) }
        val cur = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27))
        val prev = DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 27))
        val txs = listOf(
            LedgerTx("1", TxType.EXPENSE, rupees(3000), "bank", categoryId = "restaurants", epochDay = LocalDate.of(2026, 11, 2).toEpochDay()),
            LedgerTx("2", TxType.EXPENSE, rupees(1800), "bank", categoryId = "restaurants", epochDay = LocalDate.of(2026, 10, 2).toEpochDay()),
        )
        val ins = Analytics.insights(txs, cur, prev, cats, emptyList(), LocalDate.of(2026, 11, 27), rupees(4000), "this cycle")
        assertTrue(ins.any { it.text.contains("₹1,200 more on restaurants") }, ins.joinToString { it.text })
        assertTrue(ins.any { it.text.contains("₹4,000 free") })
        // Empty data -> no fabricated insights apart from the safe-to-spend statement.
        val empty = Analytics.insights(emptyList(), cur, prev, cats, emptyList(), LocalDate.of(2026, 11, 1), null, "this cycle")
        assertTrue(empty.isEmpty())
    }

    @Test fun noSpendDaysStartFromFirstEntry() {
        val cats = DefaultCategories.all.associate { it.key to CategoryMeta(it.key, it.name, it.emoji, it.essential, it.fixed) }
        val cur = DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27))
        val prev = DateRange(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 27))
        // New user: first entry on 20 Nov, so 28 Oct-19 Nov must not count as "no-spend days".
        val txs = listOf(LedgerTx("1", TxType.EXPENSE, rupees(250), "bank", categoryId = "restaurants", epochDay = LocalDate.of(2026, 11, 20).toEpochDay()))
        val ins = Analytics.insights(txs, cur, prev, cats, emptyList(), LocalDate.of(2026, 11, 27), null, "this cycle")
        assertTrue(ins.any { it.text.startsWith("7 no-spend days") }, ins.joinToString { it.text })
        // Too new (first entry yesterday): say nothing rather than a meaningless count.
        val fresh = listOf(LedgerTx("2", TxType.EXPENSE, rupees(250), "bank", categoryId = "restaurants", epochDay = LocalDate.of(2026, 11, 26).toEpochDay()))
        assertTrue(Analytics.insights(fresh, cur, prev, cats, emptyList(), LocalDate.of(2026, 11, 27), null, "this cycle").none { it.text.contains("no-spend") })
    }

    @Test fun unusualSpending() {
        val base = LocalDate.of(2026, 9, 1)
        val hist = (0 until 10).map { LedgerTx("h$it", TxType.EXPENSE, rupees(200), "bank", categoryId = "restaurants", epochDay = base.plusDays(it.toLong()).toEpochDay()) } +
            LedgerTx("big", TxType.EXPENSE, rupees(2400), "bank", categoryId = "restaurants", epochDay = LocalDate.of(2026, 10, 30).toEpochDay())
        val u = Analytics.unusual(hist, DateRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 27)))
        assertEquals(listOf("big"), u.map { it.id })
    }
}
