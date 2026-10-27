package com.rupeeflow.core

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {

    private val bank = AccountInfo("bank", "HDFC", AccountType.BANK, 0, true)
    private val savings = AccountInfo("sav", "Savings", AccountType.SAVINGS, 0, false)
    private val card = AccountInfo("card", "Credit card", AccountType.CREDIT_CARD, 0, false)
    private val cash = AccountInfo("cash", "Cash", AccountType.CASH, rupees(2000), true)
    private val accounts = listOf(bank, savings, card, cash)

    private var seq = 0
    private fun tx(type: TxType, amt: Long, acc: String = "bank", to: String? = null, cat: String? = null, day: LocalDate, sub: String? = null) =
        LedgerTx("t${seq++}", type, amt, acc, to, cat, day.toEpochDay(), subscriptionId = sub)

    /** Section 19 acceptance scenario from the brief. */
    @Test fun acceptanceScenario() {
        val d = LocalDate.of(2026, 10, 28)
        val cycle = SalaryCycle(28)
        val range = cycle.cycleContaining(d)
        val txs = mutableListOf(
            tx(TxType.INCOME, rupees(45000), cat = "salary", day = d),
            tx(TxType.EXPENSE, rupees(300), cat = "food", day = d),
            tx(TxType.EXPENSE, rupees(500), cat = "fuel", day = d.plusDays(1)),
            tx(TxType.SAVINGS, rupees(10000), to = "sav", day = d.plusDays(1)),
        )
        // Gym subscription due on the 5th: schedule only, nothing recorded yet.
        val gymDue = LocalDate.of(2026, 11, 5)
        var balances = Ledger.balances(accounts, txs)
        assertEquals(rupees(34200), balances["bank"])
        assertEquals(rupees(800), Ledger.spending(txs, range))

        // Bill reminder 3 days before.
        assertEquals(3, Schedule.reminderDue(LocalDate.of(2026, 11, 2), gymDue, setOf(7, 3, 1)))

        // Confirm actual payment: one expense linked to the subscription.
        txs += tx(TxType.EXPENSE, rupees(1500), cat = "gym", day = gymDue, sub = "gym")
        val nextDue = Schedule.next(gymDue, Frequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 12, 5), nextDue)

        balances = Ledger.balances(accounts, txs)
        assertEquals(rupees(32700), balances["bank"])
        assertEquals(rupees(10000), balances["sav"])
        val byCat = Ledger.spendingByCategory(txs, range)
        val food = BudgetMath.status(rupees(5000), byCat["food"] ?: 0, range, gymDue)
        val fuel = BudgetMath.status(rupees(2000), byCat["fuel"] ?: 0, range, gymDue)
        assertEquals(rupees(4700), food.remaining)
        assertEquals(rupees(1500), fuel.remaining)
        assertEquals(rupees(2300), Ledger.spending(txs, range))
        assertEquals(rupees(10000), Ledger.savedAndInvested(txs, range, accounts))
        assertEquals(1, txs.count { it.subscriptionId == "gym" })
        assertEquals(rupees(45000), Ledger.income(txs, range))

        // Linking instead of re-recording: an imported debit already exists -> matched.
        val imported = tx(TxType.EXPENSE, rupees(1500), day = gymDue.plusDays(1))
        val match = Schedule.findMatch(listOf(imported), rupees(1500), gymDue)
        assertEquals(imported.id, match?.id)
        // Already-linked payments are never matched again.
        assertNull(Schedule.findMatch(txs, rupees(1500), gymDue))
    }

    @Test fun transfersAreNeverIncomeOrExpense() {
        val d = LocalDate.of(2026, 11, 1)
        val r = DateRange.single(d)
        val txs = listOf(
            tx(TxType.TRANSFER, rupees(1000), "bank", "cash", day = d),
            tx(TxType.INVESTMENT, rupees(5000), "bank", "sav", day = d),
        )
        val b = Ledger.balances(accounts, txs)
        assertEquals(rupees(-6000), b["bank"])
        assertEquals(rupees(3000), b["cash"])
        assertEquals(0, Ledger.spending(txs, r))
        assertEquals(0, Ledger.income(txs, r))
        val netBefore = accounts.sumOf { it.openingBalance }
        assertEquals(netBefore, b.values.sum())
    }

    @Test fun creditCardFlowDoesNotDoubleCount() {
        val d = LocalDate.of(2026, 11, 3)
        val range = DateRange(d, d.plusDays(20))
        val txs = mutableListOf(
            tx(TxType.INCOME, rupees(45000), day = d),
            tx(TxType.EXPENSE, rupees(2500), "card", cat = "shopping", day = d),
        )
        var b = Ledger.balances(accounts, txs)
        assertEquals(rupees(45000), b["bank"], "card purchase must not touch the bank")
        assertEquals(rupees(-2500), b["card"])
        assertEquals(rupees(2500), Ledger.position(accounts, b).cardDues)
        // Pay the bill: transfer bank -> card.
        txs += tx(TxType.TRANSFER, rupees(2500), "bank", "card", day = d.plusDays(15))
        b = Ledger.balances(accounts, txs)
        assertEquals(rupees(42500), b["bank"])
        assertEquals(0, b["card"])
        assertEquals(rupees(2500), Ledger.spending(txs, range), "consumption counted exactly once")
    }

    @Test fun refundsReduceCategorySpendingAndRestoreBalance() {
        val d = LocalDate.of(2026, 11, 3)
        val r = DateRange(d, d.plusDays(5))
        val txs = listOf(
            tx(TxType.EXPENSE, rupees(1200), cat = "shopping", day = d),
            tx(TxType.REFUND, rupees(700), cat = "shopping", day = d.plusDays(2)),
        )
        assertEquals(rupees(500), Ledger.spending(txs, r))
        assertEquals(rupees(-500), Ledger.balances(accounts, txs)["bank"])
        // Refund bigger than spend in range never makes spending negative.
        val only = listOf(tx(TxType.REFUND, rupees(700), cat = "shopping", day = d))
        assertEquals(0, Ledger.spending(only, r))
    }

    @Test fun editAndDeleteRecomputeEverything() {
        val d = LocalDate.of(2026, 11, 3)
        val r = DateRange(d.minusDays(10), d)
        val orig = tx(TxType.EXPENSE, rupees(250), cat = "food", day = d)
        val txs = mutableListOf(orig)
        assertEquals(rupees(250), Ledger.spendingByCategory(txs, r)["food"])
        // Edit: change category, amount and move to an older date in another period.
        txs[0] = orig.copy(amount = rupees(300), categoryId = "restaurants", epochDay = d.minusDays(20).toEpochDay())
        assertNull(Ledger.spendingByCategory(txs, r)["food"])
        assertEquals(0, Ledger.spending(txs, r))
        assertEquals(rupees(-300), Ledger.balances(accounts, txs)["bank"])
        txs.clear()
        assertEquals(0, Ledger.balances(accounts, txs)["bank"])
    }

    @Test fun futureDatedTransactionsAreScheduledNotSpent() {
        val today = LocalDate.of(2026, 11, 3)
        val txs = listOf(tx(TxType.EXPENSE, rupees(999), day = today.plusDays(3)))
        assertEquals(0, Ledger.balances(accounts, txs, today.toEpochDay())["bank"])
        assertEquals(rupees(-999), Ledger.balances(accounts, txs)["bank"])
    }

    @Test fun reconciliationUsesAnAdjustmentNotARewrite() {
        val d = LocalDate.of(2026, 11, 3)
        val txs = mutableListOf(tx(TxType.EXPENSE, rupees(100), "cash", cat = "food", day = d))
        val recorded = Ledger.balances(accounts, txs)["cash"]!!
        assertEquals(rupees(1900), recorded)
        val delta = Ledger.reconciliationDelta(recorded, rupees(1750))
        txs += tx(TxType.ADJUSTMENT, delta, "cash", day = d)
        assertEquals(rupees(1750), Ledger.balances(accounts, txs)["cash"])
        assertEquals(rupees(100), Ledger.spending(txs, DateRange.single(d)), "adjustments are not spending")
        assertEquals(2, txs.size, "history preserved")
    }

    @Test fun invalidMovementsAreRejected() {
        val d = LocalDate.of(2026, 11, 3)
        assertFailsWith<IllegalArgumentException> { Ledger.effects(tx(TxType.TRANSFER, 100, "bank", null, day = d)) }
        assertFailsWith<IllegalArgumentException> { Ledger.effects(tx(TxType.TRANSFER, 100, "bank", "bank", day = d)) }
    }

    @Test fun positionSeparatesSpendableSavingsAndDues() {
        val b = mapOf("bank" to rupees(30000), "sav" to rupees(10000), "card" to rupees(-4000), "cash" to rupees(500))
        val p = Ledger.position(accounts, b)
        assertEquals(rupees(30500), p.spendable)
        assertEquals(rupees(10000), p.savings)
        assertEquals(rupees(4000), p.cardDues)
        assertEquals(rupees(36500), p.netWorth)
    }

    @Test fun largeHistoryIsFastAndExact() {
        val start = LocalDate.of(2020, 1, 1)
        val txs = (0 until 50_000).map { i -> tx(TxType.EXPENSE, 1999L + (i % 7), cat = "food", day = start.plusDays((i % 2000).toLong())) }
        val t0 = System.nanoTime()
        val b = Ledger.balances(accounts, txs)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(-txs.sumOf { it.amount }, b["bank"])
        assertTrue(ms < 2000, "took $ms ms")
        assertNotNull(Ledger.spendingByCategory(txs, DateRange(start, start.plusDays(2000)))["food"])
    }
}
