package com.rupeeflow.app

import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.BudgetEntity
import com.rupeeflow.app.data.CategoryEntity
import com.rupeeflow.app.data.CsvExporter
import com.rupeeflow.app.data.InsightsBuilder
import com.rupeeflow.app.data.Settings
import com.rupeeflow.app.data.SnapshotBuilder
import com.rupeeflow.app.data.SubscriptionEntity
import com.rupeeflow.app.data.SubscriptionPaymentEntity
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.core.DefaultCategories
import com.rupeeflow.core.PeriodKind
import com.rupeeflow.core.rupees
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the brief's acceptance scenario through the same SnapshotBuilder that feeds every
 * screen, proving the dashboard, budgets, bills and analytics agree with the ledger.
 */
class SnapshotBuilderTest {

    private val cats = DefaultCategories.all.map { CategoryEntity(it.key, it.name, it.emoji, it.color, it.kind.name, null, it.essential, it.fixed) } +
        CategoryEntity("food__vegetables", "Vegetables", "🥦", 0, "EXPENSE", parentId = "food")
    private val bank = AccountEntity("bank", "HDFC", "BANK", 0, true, "🏦")
    private val sav = AccountEntity("sav", "Savings", "SAVINGS", 0, false, "🐷")
    private val settings = Settings(onboarded = true, salaryDay = 28, expectedSalary = rupees(45000))

    private fun tx(id: String, type: String, amt: Long, day: LocalDate, cat: String? = null, to: String? = null, sub: String? = null) =
        TransactionEntity(id, type, amt, "bank", to, cat, day.toEpochDay(), 600, if (type == "EXPENSE") "UPI" else null, subscriptionId = sub)

    @Test fun acceptanceScenarioOnDashboard() {
        val d0 = LocalDate.of(2026, 10, 28)
        val gymDue = LocalDate.of(2026, 11, 5)
        val txs = listOf(
            tx("1", "INCOME", rupees(45000), d0, "salary"),
            tx("2", "EXPENSE", rupees(300), d0, "food__vegetables"),
            tx("3", "EXPENSE", rupees(500), d0.plusDays(1), "fuel"),
            tx("4", "SAVINGS", rupees(10000), d0.plusDays(1), to = "sav"),
            tx("5", "EXPENSE", rupees(1500), gymDue, "gym", sub = "gym"),
        )
        val budgets = listOf(
            BudgetEntity("b1", "food", rupees(5000), "SALARY_CYCLE"),
            BudgetEntity("b2", "fuel", rupees(2000), "SALARY_CYCLE"),
            BudgetEntity("b3", "entertainment", rupees(3000), "SALARY_CYCLE"),
        )
        val gym = SubscriptionEntity("gym", "Gym", "🏋️", rupees(1500), "gym", "bank", "MONTHLY", 30, 5, LocalDate.of(2026, 12, 5).toEpochDay())
        val payments = listOf(SubscriptionPaymentEntity("p1", "gym", gymDue.toEpochDay(), "PAID", "5", rupees(1500)))

        val today = LocalDate.of(2026, 11, 6)
        val s = SnapshotBuilder.build(today, settings, listOf(bank, sav), cats, txs, budgets, listOf(gym), payments,
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        assertEquals(LocalDate.of(2026, 10, 28), s.cycleRange.start)
        assertEquals(LocalDate.of(2026, 11, 27), s.cycleRange.end)
        assertEquals(rupees(32700), s.balances["bank"])
        assertEquals(rupees(10000), s.balances["sav"])
        assertEquals(rupees(32700), s.position.spendable)
        assertEquals(rupees(2300), s.spentCycle)
        assertEquals(rupees(45000), s.incomeCycle)
        assertEquals(rupees(10000), s.savedCycle)
        val food = s.budgets.first { it.budget.categoryId == "food" }
        assertEquals(rupees(4700), food.status.remaining, "sub-category spending rolls up into the parent budget")
        assertEquals(rupees(1500), s.budgets.first { it.budget.categoryId == "fuel" }.status.remaining)
        assertEquals(rupees(3000), s.budgets.first { it.budget.categoryId == "entertainment" }.status.remaining)
        assertEquals(rupees(10000), s.budgetAllocated)
        // Gym recorded once, next due moved to December -> nothing more due this cycle.
        assertEquals(1, s.transactions.count { it.subscriptionId == "gym" })
        assertEquals(0, s.billsDueThisCycle)
        assertEquals(LocalDate.of(2026, 12, 5), s.bills.single().nextDue)
        // Safe-to-spend uses exactly the same balance.
        assertEquals(rupees(32700), s.safe.spendable)
        assertEquals(s.cycleRange.daysLeft(today), s.safe.daysLeft)
        // Analytics agree with the dashboard.
        val ins = InsightsBuilder.build(s, PeriodKind.SALARY_CYCLE, null)
        assertEquals(s.spentCycle, ins.spent)
        assertEquals(s.incomeCycle, ins.income)
        assertEquals(s.savedCycle, ins.saved)
        assertEquals(rupees(300), ins.byCategory.first { it.first?.id == "food" }.second)
        assertTrue(ins.byCategory.none { it.first?.id == null && it.second == rupees(10000) }, "savings transfer is not spending")
    }

    @Test fun recordingAnExpenseUpdatesEverything() {
        val today = LocalDate.of(2026, 11, 6)
        val base = listOf(tx("1", "INCOME", rupees(45000), LocalDate.of(2026, 10, 28), "salary"))
        val budgets = listOf(BudgetEntity("b1", "food", rupees(5000), "SALARY_CYCLE", thresholds = "50,75,90,100"))
        fun snap(t: List<TransactionEntity>) = SnapshotBuilder.build(today, settings, listOf(bank), cats, t, budgets, emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val before = snap(base)
        val after = snap(base + tx("x", "EXPENSE", rupees(250), today, "food"))
        assertEquals(before.balances["bank"]!! - rupees(250), after.balances["bank"])
        assertEquals(before.spentToday + rupees(250), after.spentToday)
        assertEquals(before.spentCycle + rupees(250), after.spentCycle)
        assertEquals(before.budgets[0].status.remaining - rupees(250), after.budgets[0].status.remaining)
        assertTrue(after.loggedToday)
        assertEquals(1, after.streak)
    }

    @Test fun futureEntriesAndMalformedTransfersNeverBreakTheDashboard() {
        val today = LocalDate.of(2026, 11, 6)
        val txs = listOf(
            tx("1", "INCOME", rupees(1000), today, "salary"),
            tx("f", "EXPENSE", rupees(400), today.plusDays(2), "food"),
            tx("bad", "TRANSFER", rupees(50), today, to = null),
        )
        val s = SnapshotBuilder.build(today, settings, listOf(bank), cats, txs, emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(rupees(1000), s.balances["bank"])
        assertEquals(1, s.scheduled.size)
        assertEquals(0, s.spentCycle)
    }

    @Test fun csvExportIsSafeAndFiltered() {
        val t = listOf(
            TransactionEntity("a", "EXPENSE", 12550, "bank", null, "food", LocalDate.of(2026, 11, 1).toEpochDay(), 615, "UPI", "=HYPERLINK(evil)", "milk, bread"),
            TransactionEntity("b", "EXPENSE", 9900, "bank", null, "fuel", LocalDate.of(2026, 11, 2).toEpochDay(), 700),
        )
        val csv = CsvExporter.export(t, listOf(bank), cats, null, setOf("food"))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].startsWith("2026-11-01,10:15,Expense,-125.50,Groceries"))
        assertTrue(lines[1].contains("'=HYPERLINK(evil)"), "formula injection neutralised")
        assertTrue(lines[1].contains("\"milk, bread\""))
    }
}
