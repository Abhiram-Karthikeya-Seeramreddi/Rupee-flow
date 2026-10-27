package com.rupeeflow.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.AppDatabase
import com.rupeeflow.app.data.BackupManager
import com.rupeeflow.app.data.BudgetEntity
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.ImportCandidate
import com.rupeeflow.app.data.RestoreMode
import com.rupeeflow.app.data.SettingsStore
import com.rupeeflow.app.data.SnapshotBuilder
import com.rupeeflow.app.data.SubscriptionEntity
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.data.ValidationException
import com.rupeeflow.core.ImportedRow
import com.rupeeflow.core.TxType
import com.rupeeflow.core.rupees
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** End-to-end ledger behaviour against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var dir: File

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        dir = File(ctx.cacheDir, "test-${System.nanoTime()}").apply { mkdirs() }
        repo = FinanceRepository(db, SettingsStore(ctx), dir)
        repo.ensureDefaultCategories()
        repo.saveAccount(AccountEntity("bank", "Bank", "BANK", 0, true, "🏦"))
        repo.saveAccount(AccountEntity("sav", "Savings", "SAVINGS", 0, false, "🐷"))
    }

    @After fun tearDown() { db.close(); dir.deleteRecursively() }

    private fun t(id: String, type: TxType, amt: Long, day: LocalDate, cat: String? = null, to: String? = null) =
        TransactionEntity(id, type.name, amt, "bank", to, cat, day.toEpochDay(), 600)

    private suspend fun snap(today: LocalDate) = repo.loadRaw().let { r ->
        SnapshotBuilder.build(today, com.rupeeflow.app.data.Settings(onboarded = true), r.accounts, r.categories, r.transactions, r.budgets,
            r.subscriptions, r.payments, r.goals, r.rules, r.templates, r.dayChecks, r.challenges)
    }

    @Test fun acceptanceScenario() = runTest {
        val d = LocalDate.of(2026, 10, 28)
        repo.saveTransaction(t("s", TxType.INCOME, rupees(45000), d, "salary"))
        repo.saveTransaction(t("f", TxType.EXPENSE, rupees(300), d, "food"))
        repo.saveTransaction(t("p", TxType.EXPENSE, rupees(500), d, "fuel"))
        repo.saveTransaction(t("v", TxType.SAVINGS, rupees(10000), d, to = "sav"))
        repo.saveBudget(BudgetEntity("bf", "food", rupees(5000), "SALARY_CYCLE"))
        repo.saveBudget(BudgetEntity("bp", "fuel", rupees(2000), "SALARY_CYCLE"))
        val due = LocalDate.of(2026, 11, 5)
        repo.saveSubscription(SubscriptionEntity("gym", "Gym", "🏋️", rupees(1500), "gym", "bank", "MONTHLY", 30, 5, due.toEpochDay()))

        repo.confirmBill("gym", due.toEpochDay(), rupees(1500), "bank", due, null, null)
        assertFailsWith<ValidationException> { repo.confirmBill("gym", due.toEpochDay(), rupees(1500), "bank", due, null, null) }

        val s = snap(due)
        assertEquals(rupees(32700), s.balances["bank"])
        assertEquals(rupees(4700), s.budgets.first { it.budget.id == "bf" }.status.remaining)
        assertEquals(rupees(1500), s.budgets.first { it.budget.id == "bp" }.status.remaining)
        assertEquals(rupees(2300), s.spentCycle)
        assertEquals(rupees(10000), s.savedCycle)
        assertEquals(1, s.transactions.count { it.subscriptionId == "gym" })
        assertEquals(LocalDate.of(2026, 12, 5), s.bills.single().nextDue)
    }

    @Test fun deletingABillPaymentPutsTheBillBackAndUndoRestores() = runTest {
        val due = LocalDate.of(2026, 11, 5)
        repo.saveSubscription(SubscriptionEntity("net", "Wi-Fi", "🌐", rupees(799), "internet", "bank", "MONTHLY", 30, 5, due.toEpochDay()))
        repo.confirmBill("net", due.toEpochDay(), rupees(799), "bank", due, null, null)
        val txId = repo.loadRaw().transactions.single().id
        val deleted = assertNotNull(repo.deleteTransaction(txId))
        assertEquals(due.toEpochDay(), db.subscriptions().get("net")!!.nextDue)
        assertNull(db.subscriptions().paymentFor("net", due.toEpochDay()))
        repo.undoDelete(deleted)
        assertEquals(LocalDate.of(2026, 12, 5).toEpochDay(), db.subscriptions().get("net")!!.nextDue)
        assertNotNull(db.subscriptions().paymentFor("net", due.toEpochDay()))
    }

    @Test fun linkingAnExistingPaymentDoesNotDoubleCount() = runTest {
        val due = LocalDate.of(2026, 11, 5)
        repo.saveSubscription(SubscriptionEntity("ott", "Netflix", "🎬", rupees(649), "subscriptions", "bank", "MONTHLY", 30, 5, due.toEpochDay()))
        repo.saveTransaction(t("typed", TxType.EXPENSE, rupees(649), due.plusDays(1), "entertainment"))
        val sub = db.subscriptions().get("ott")!!
        val match = assertNotNull(repo.findBillMatch(sub, due))
        repo.confirmBill("ott", due.toEpochDay(), match.amount, match.accountId, due, null, match.id)
        val raw = repo.loadRaw()
        assertEquals(1, raw.transactions.size)
        assertEquals("ott", raw.transactions.single().subscriptionId)
    }

    @Test fun invalidEntriesAreRejected() = runTest {
        val d = LocalDate.of(2026, 11, 1)
        assertFailsWith<ValidationException> { repo.saveTransaction(t("a", TxType.EXPENSE, 0, d, "food")) }
        assertFailsWith<ValidationException> { repo.saveTransaction(t("b", TxType.TRANSFER, 100, d)) }
        assertFailsWith<ValidationException> { repo.saveTransaction(t("c", TxType.TRANSFER, 100, d, to = "bank")) }
        assertEquals(0, repo.loadRaw().transactions.size)
    }

    @Test fun importSkipsDuplicates() = runTest {
        val row = ImportedRow(LocalDate.of(2026, 11, 2), "UPI-SWIGGY", rupees(250), true, "123456789012")
        val cand = ImportCandidate(row, true, "restaurants", "bank", TxType.EXPENSE, false)
        assertEquals(1, repo.importRows(listOf(cand)))
        assertEquals(0, repo.importRows(listOf(cand)))
        assertEquals(1, repo.loadRaw().transactions.size)
    }

    @Test fun reconcileAddsAdjustmentWithoutTouchingHistory() = runTest {
        val d = LocalDate.now()
        repo.saveTransaction(t("e", TxType.EXPENSE, rupees(100), d, "food"))
        repo.reconcile("bank", rupees(-150), rupees(-100))
        val s = snap(d)
        assertEquals(rupees(-150), s.balances["bank"])
        assertEquals(rupees(100), s.spentToday)
        assertEquals(2, s.transactions.size)
    }

    @Test fun backupRoundTripWithEncryption() = runTest {
        val d = LocalDate.of(2026, 11, 1)
        repo.saveTransaction(t("x", TxType.EXPENSE, rupees(420), d, "food"))
        val backup = BackupManager(repo, dir, "test")
        val json = backup.exportJson("secret")
        assertFailsWith<com.rupeeflow.app.data.BackupException> { backup.preview(json, "wrong") }
        val p = backup.preview(json, "secret")
        assertEquals(1, p.duplicateTransactions)
        repo.deleteTransaction("x")
        assertEquals(1, backup.restore(json, "secret", RestoreMode.MERGE))
        assertEquals(rupees(420), repo.loadRaw().transactions.single().amount)
        assertEquals(0, backup.restore(json, "secret", RestoreMode.MERGE), "merging twice adds nothing")
    }
}
