package com.rupeeflow.app.data

import androidx.room.withTransaction
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.DefaultCategories
import com.rupeeflow.core.ImportedRow
import com.rupeeflow.core.Ledger
import com.rupeeflow.core.LedgerTx
import com.rupeeflow.core.QuickParse
import com.rupeeflow.core.Schedule
import com.rupeeflow.core.SubStatus
import com.rupeeflow.core.TxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class ValidationException(message: String) : Exception(message)

data class ImportCandidate(
    val row: ImportedRow,
    val selected: Boolean,
    val categoryId: String?,
    val accountId: String,
    val type: TxType,
    val duplicate: Boolean,
)

/** Everything the UI observes, combined into one consistent stream. */
data class RawData(
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val subscriptions: List<SubscriptionEntity>,
    val payments: List<SubscriptionPaymentEntity>,
    val goals: List<GoalEntity>,
    val rules: List<CategoryRuleEntity>,
    val templates: List<TemplateEntity>,
    val dayChecks: List<DayCheckEntity>,
    val challenges: List<ChallengeEntity>,
)

class FinanceRepository(
    val db: AppDatabase,
    val settings: SettingsStore,
    private val filesDir: File,
) {
    /** Called after every successful write so alerts and widgets can refresh. */
    var onLedgerChanged: (suspend () -> Unit)? = null

    private val accounts = db.accounts()
    private val categories = db.categories()
    private val txDao = db.transactions()
    private val budgets = db.budgets()
    private val subs = db.subscriptions()
    private val goals = db.goals()
    private val misc = db.misc()

    fun observeRaw(): Flow<RawData> {
        val a = combine(accounts.observeAll(), categories.observeAll(), txDao.observeAll(), budgets.observeAll()) { ac, ca, tx, bu ->
            listOf<Any>(ac, ca, tx, bu)
        }
        val b = combine(subs.observeAll(), subs.observePayments(), goals.observeAll(), misc.observeRules()) { s, p, g, r ->
            listOf<Any>(s, p, g, r)
        }
        val c = combine(misc.observeTemplates(), misc.observeDayChecks(), misc.observeChallenges()) { t, d, ch ->
            listOf<Any>(t, d, ch)
        }
        @Suppress("UNCHECKED_CAST")
        return combine(a, b, c) { x, y, z ->
            RawData(
                accounts = x[0] as List<AccountEntity>,
                categories = x[1] as List<CategoryEntity>,
                transactions = x[2] as List<TransactionEntity>,
                budgets = x[3] as List<BudgetEntity>,
                subscriptions = y[0] as List<SubscriptionEntity>,
                payments = y[1] as List<SubscriptionPaymentEntity>,
                goals = y[2] as List<GoalEntity>,
                rules = y[3] as List<CategoryRuleEntity>,
                templates = z[0] as List<TemplateEntity>,
                dayChecks = z[1] as List<DayCheckEntity>,
                challenges = z[2] as List<ChallengeEntity>,
            )
        }
    }

    suspend fun loadRaw(): RawData = RawData(
        accounts.getAll(), categories.getAll(), txDao.getAll(), budgets.getAll(), subs.getAll(), subs.getPayments(),
        goals.getAll(), misc.getRules(), misc.getTemplates(), misc.getDayChecks(), misc.getChallenges(),
    )

    private suspend fun changed() { runCatching { onLedgerChanged?.invoke() } }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
    }

    /* ---------------- Setup ---------------- */

    /** Inserts built-in categories that don't exist yet. Never overwrites user edits. */
    suspend fun ensureDefaultCategories() {
        val favorites = setOf("food", "restaurants", "transport", "fuel", "badminton", "shopping", "entertainment", "salary")
        val list = ArrayList<CategoryEntity>()
        DefaultCategories.all.forEachIndexed { i, d ->
            list += CategoryEntity(d.key, d.name, d.emoji, d.color, d.kind.name, null, d.essential, d.fixed, d.key in favorites, false, i * 10)
            d.subcategories.forEachIndexed { j, sub ->
                val id = d.key + "__" + sub.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                list += CategoryEntity(id, sub, d.emoji, d.color, d.kind.name, d.key, d.essential, d.fixed, false, false, i * 10 + j + 1)
            }
        }
        categories.insertAllIgnore(list)
    }

    /* ---------------- Transactions ---------------- */

    private suspend fun validate(t: TransactionEntity) {
        val type = t.txType()
        if (type == TxType.ADJUSTMENT) {
            if (t.amount == 0L) throw ValidationException("Adjustment amount can't be zero.")
        } else if (t.amount <= 0) throw ValidationException("Enter an amount greater than zero.")
        if (t.amount > 1_000_000_000_00L || t.amount < -1_000_000_000_00L) throw ValidationException("That amount looks too large.")
        accounts.get(t.accountId) ?: throw ValidationException("Choose an account.")
        if (type.isMovement) {
            val to = t.toAccountId ?: throw ValidationException("Choose where the money goes.")
            if (to == t.accountId) throw ValidationException("From and to accounts must be different.")
            accounts.get(to) ?: throw ValidationException("Destination account not found.")
        }
        t.categoryId?.let { categories.get(it) ?: throw ValidationException("Category not found.") }
    }

    /** Normalises a draft so movements never carry a category and ledger rules always hold. */
    private fun normalize(t: TransactionEntity): TransactionEntity {
        val type = t.txType()
        return t.copy(
            categoryId = if (type.isMovement || type == TxType.ADJUSTMENT) null else t.categoryId,
            toAccountId = if (type.isMovement) t.toAccountId else null,
            merchant = t.merchant?.trim()?.takeIf { it.isNotEmpty() },
            note = t.note?.trim()?.takeIf { it.isNotEmpty() },
            tags = t.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(","),
        )
    }

    /** Possible accidental double entry: same type, amount, account and day saved in the last 10 minutes. */
    suspend fun findLikelyDuplicate(t: TransactionEntity): TransactionEntity? =
        txDao.findRecentDuplicate(t.type, t.amount, t.accountId, t.epochDay, t.id, System.currentTimeMillis() - 10 * 60_000)

    suspend fun saveTransaction(draft: TransactionEntity, learnRule: Boolean = true) {
        val t = normalize(draft).copy(updatedAt = System.currentTimeMillis())
        validate(t)
        db.withTransaction {
            val existing = txDao.get(t.id)
            if (existing == null) txDao.insert(t) else txDao.update(t.copy(createdAt = existing.createdAt))
            // Keep a linked bill payment's amount in sync if the user edits it.
            subs.paymentForTransaction(t.id)?.let { p ->
                if (p.amount != t.amount) {
                    subs.deletePayment(p.id)
                    subs.insertPayment(p.copy(amount = t.amount))
                }
            }
        }
        if (learnRule && t.txType() == TxType.EXPENSE && t.categoryId != null) learn(t)
        changed()
    }

    private suspend fun learn(t: TransactionEntity) {
        val text = t.merchant ?: t.note ?: return
        val key = QuickParse.learnableKeyword(text) ?: return
        val existing = misc.getRules().firstOrNull { it.keyword == key }
        val builtIn = DefaultCategories.all.any { d -> d.keywords.any { it == key } && d.key == t.categoryId }
        if (!builtIn && existing?.categoryId != t.categoryId) misc.upsertRule(CategoryRuleEntity(key, t.categoryId!!))
    }

    data class Deleted(val tx: TransactionEntity, val payment: SubscriptionPaymentEntity?, val previousNextDue: Long?)

    suspend fun deleteTransaction(id: String): Deleted? {
        var result: Deleted? = null
        db.withTransaction {
            val t = txDao.get(id) ?: return@withTransaction
            val p = subs.paymentForTransaction(id)
            var prevNext: Long? = null
            if (p != null) {
                subs.deletePayment(p.id)
                val s = subs.get(p.subscriptionId)
                // If this was the latest settled occurrence, put the bill back to "due".
                if (s != null && Schedule.next(LocalDate.ofEpochDay(p.dueEpochDay), s.freq(), s.intervalDays, s.anchorDay).toEpochDay() == s.nextDue) {
                    prevNext = s.nextDue
                    subs.upsert(s.copy(nextDue = p.dueEpochDay))
                }
            }
            txDao.delete(id)
            result = Deleted(t, p, prevNext)
        }
        changed()
        return result
    }

    suspend fun undoDelete(d: Deleted) {
        db.withTransaction {
            if (txDao.get(d.tx.id) == null) txDao.insert(d.tx)
            if (d.payment != null && subs.paymentFor(d.payment.subscriptionId, d.payment.dueEpochDay) == null) {
                subs.insertPayment(d.payment)
                if (d.previousNextDue != null) subs.get(d.payment.subscriptionId)?.let { subs.upsert(it.copy(nextDue = d.previousNextDue)) }
            }
        }
        changed()
    }

    suspend fun recordSalary(amount: Long, accountId: String, day: LocalDate) {
        saveTransaction(
            TransactionEntity(newId(), TxType.INCOME.name, amount, accountId, categoryId = "salary", epochDay = day.toEpochDay(),
                minuteOfDay = nowMinute(), payment = "NET_BANKING", merchant = "Salary"),
            learnRule = false,
        )
    }

    /* ---------------- Bills / subscriptions ---------------- */

    suspend fun saveSubscription(s: SubscriptionEntity) {
        if (s.name.isBlank()) throw ValidationException("Give this bill a name.")
        if (s.amount <= 0) throw ValidationException("Enter the bill amount.")
        subs.upsert(s.copy(name = s.name.trim()))
        changed()
    }

    suspend fun deleteSubscription(id: String) { subs.delete(id); changed() }

    suspend fun setSubscriptionStatus(id: String, status: SubStatus) {
        subs.get(id)?.let { subs.upsert(it.copy(status = status.name)) }
        changed()
    }

    /** Candidate existing transactions that could be this bill's payment (avoids double counting). */
    suspend fun findBillMatch(sub: SubscriptionEntity, due: LocalDate): TransactionEntity? {
        val window = txDao.between(due.toEpochDay() - 5, due.toEpochDay() + 5)
        val linked = subs.getPayments().mapNotNull { it.transactionId }.toSet()
        val lite = window.filter { it.id !in linked }.map {
            LedgerTx(it.id, it.txType(), it.amount, it.accountId, it.toAccountId, it.categoryId, it.epochDay, subscriptionId = it.subscriptionId)
        }
        val m = Schedule.findMatch(lite, sub.amount, due) ?: return null
        return window.firstOrNull { it.id == m.id }
    }

    /**
     * Confirms one bill occurrence. Either links an existing transaction or records a new expense.
     * The UNIQUE(subscriptionId, dueEpochDay) index guarantees an occurrence is settled once.
     */
    suspend fun confirmBill(subId: String, dueDay: Long, amount: Long, accountId: String, paidOn: LocalDate, payment: String?, linkTxId: String?) {
        if (amount <= 0) throw ValidationException("Enter the amount you actually paid.")
        db.withTransaction {
            val s = subs.get(subId) ?: throw ValidationException("Bill not found.")
            if (subs.paymentFor(subId, dueDay) != null) throw ValidationException("This payment is already recorded.")
            val txId: String
            if (linkTxId != null) {
                val t = txDao.get(linkTxId) ?: throw ValidationException("Linked transaction not found.")
                if (subs.paymentForTransaction(linkTxId) != null) throw ValidationException("That transaction is already linked to a bill.")
                txDao.update(t.copy(subscriptionId = subId, categoryId = t.categoryId ?: s.categoryId, updatedAt = System.currentTimeMillis()))
                txId = linkTxId
            } else {
                accounts.get(accountId) ?: throw ValidationException("Choose the account you paid from.")
                txId = newId()
                txDao.insert(
                    TransactionEntity(txId, TxType.EXPENSE.name, amount, accountId, categoryId = s.categoryId ?: "subscriptions",
                        epochDay = paidOn.toEpochDay(), minuteOfDay = nowMinute(), payment = payment ?: if (s.autopay) "AUTOPAY" else "UPI",
                        merchant = s.name, subscriptionId = subId, isRecurring = true),
                )
            }
            subs.insertPayment(SubscriptionPaymentEntity(newId(), subId, dueDay, "PAID", txId, amount))
            if (dueDay == s.nextDue) {
                val next = Schedule.next(LocalDate.ofEpochDay(dueDay), s.freq(), s.intervalDays, s.anchorDay)
                subs.upsert(s.copy(nextDue = next.toEpochDay()))
            }
        }
        changed()
    }

    suspend fun skipBill(subId: String, dueDay: Long) {
        db.withTransaction {
            val s = subs.get(subId) ?: return@withTransaction
            if (subs.paymentFor(subId, dueDay) == null) subs.insertPayment(SubscriptionPaymentEntity(newId(), subId, dueDay, "SKIPPED", null, 0))
            if (dueDay == s.nextDue) subs.upsert(s.copy(nextDue = Schedule.next(LocalDate.ofEpochDay(dueDay), s.freq(), s.intervalDays, s.anchorDay).toEpochDay()))
        }
        changed()
    }

    /* ---------------- Budgets, goals, challenges ---------------- */

    suspend fun saveBudget(b: BudgetEntity) {
        if (b.amount <= 0) throw ValidationException("Budget amount must be more than zero.")
        if (b.period == "CUSTOM" && (b.customStart == null || b.customEnd == null || b.customEnd < b.customStart))
            throw ValidationException("Pick a valid start and end date.")
        budgets.upsert(b); changed()
    }
    suspend fun deleteBudget(id: String) { budgets.delete(id); changed() }

    suspend fun saveGoal(g: GoalEntity) {
        if (g.name.isBlank()) throw ValidationException("Name your goal.")
        if (g.target <= 0) throw ValidationException("Set a target amount.")
        goals.upsert(g); changed()
    }
    suspend fun deleteGoal(id: String) { goals.delete(id); changed() }

    /** Moves real money for a goal. Progress is never just "allocated" — it's a recorded transfer. */
    suspend fun moveGoalMoney(goalId: String, amount: Long, fromAccountId: String, toAccountId: String, withdraw: Boolean) {
        val g = goals.getAll().firstOrNull { it.id == goalId } ?: throw ValidationException("Goal not found.")
        saveTransaction(
            TransactionEntity(newId(), TxType.SAVINGS.name, amount, fromAccountId, toAccountId,
                epochDay = LocalDate.now().toEpochDay(), minuteOfDay = nowMinute(),
                note = (if (withdraw) "Withdrawn from " else "Saved for ") + g.name, goalId = goalId),
            learnRule = false,
        )
    }

    suspend fun saveChallenge(c: ChallengeEntity) { misc.upsertChallenge(c); changed() }
    suspend fun deleteChallenge(id: String) { misc.deleteChallenge(id); changed() }

    suspend fun setNoSpendToday(on: Boolean, day: LocalDate = LocalDate.now()) {
        if (on) misc.upsertDayCheck(DayCheckEntity(day.toEpochDay())) else misc.deleteDayCheck(day.toEpochDay())
        changed()
    }

    /* ---------------- Accounts ---------------- */

    suspend fun saveAccount(a: AccountEntity) {
        if (a.name.isBlank()) throw ValidationException("Name the account.")
        accounts.upsert(a.copy(name = a.name.trim())); changed()
    }

    suspend fun deleteOrArchiveAccount(id: String): String {
        val used = txDao.countForAccount(id)
        val a = accounts.get(id) ?: return "Not found"
        return if (used > 0) {
            accounts.upsert(a.copy(archived = true)); changed()
            "Archived — it has $used transactions, so history is kept."
        } else {
            accounts.delete(a); changed(); "Deleted"
        }
    }

    /** Records the difference between the app and your real balance as an adjustment; history untouched. */
    suspend fun reconcile(accountId: String, actualBalance: Long, recorded: Long) {
        val delta = Ledger.reconciliationDelta(recorded, actualBalance)
        if (delta == 0L) return
        saveTransaction(
            TransactionEntity(newId(), TxType.ADJUSTMENT.name, delta, accountId, epochDay = LocalDate.now().toEpochDay(),
                minuteOfDay = nowMinute(), note = "Balance reconciled"),
            learnRule = false,
        )
    }

    /* ---------------- Categories & rules ---------------- */

    suspend fun saveCategory(c: CategoryEntity) {
        if (c.name.isBlank()) throw ValidationException("Name the category.")
        categories.upsert(c.copy(name = c.name.trim())); changed()
    }

    /** Deletes a category safely: its transactions move to [reassignTo] first, sub-categories are kept. */
    suspend fun deleteCategory(id: String, reassignTo: String?) {
        db.withTransaction {
            val c = categories.get(id) ?: return@withTransaction
            val used = categories.usageCount(id)
            if (used > 0) {
                val target = reassignTo ?: (if (c.categoryKind() == CategoryKind.INCOME) "other_income" else "misc")
                if (target == id) throw ValidationException("Pick a different category to move transactions to.")
                categories.reassign(id, target)
            }
            categories.detachChildren(id)
            categories.delete(c)
        }
        changed()
    }

    suspend fun deleteRule(keyword: String) { misc.deleteRule(keyword); changed() }
    suspend fun saveRule(keyword: String, categoryId: String) {
        val k = keyword.trim().lowercase()
        if (k.length < 2) throw ValidationException("Keyword is too short.")
        misc.upsertRule(CategoryRuleEntity(k, categoryId)); changed()
    }

    suspend fun saveTemplate(t: TemplateEntity) { misc.upsertTemplate(t); changed() }
    suspend fun deleteTemplate(id: String) { misc.deleteTemplate(id); changed() }

    /* ---------------- Import ---------------- */

    suspend fun existingFingerprints(): Set<String> = txDao.fingerprints().toSet()

    /** Imports reviewed rows. Rows whose fingerprint already exists are skipped, never duplicated. */
    suspend fun importRows(rows: List<ImportCandidate>): Int {
        var n = 0
        db.withTransaction {
            val known = txDao.fingerprints().toHashSet()
            for (r in rows) {
                if (!r.selected) continue
                // Imports are single-sided; a transfer needs both accounts, so it's never created here.
                if (r.type.isMovement || r.type == TxType.ADJUSTMENT) continue
                if (accounts.get(r.accountId) == null) throw ValidationException("Choose an account to import into.")
                val fp = r.row.fingerprint
                if (fp in known) continue
                val type = r.type
                txDao.insert(
                    TransactionEntity(newId(), type.name, r.row.amount, r.accountId,
                        categoryId = r.categoryId?.takeIf { categories.get(it) != null },
                        epochDay = r.row.date.toEpochDay(), minuteOfDay = 12 * 60,
                        payment = if (r.row.description.contains("upi", true)) "UPI" else null,
                        merchant = r.row.description.take(60), importFingerprint = fp),
                )
                known += fp
                n++
            }
        }
        changed()
        return n
    }

    /* ---------------- Reset ---------------- */

    suspend fun resetEverything() {
        db.withTransaction {
            subs.deleteAllPayments()
            txDao.deleteAll()
            budgets.deleteAll()
            subs.deleteAll()
            goals.deleteAll()
            misc.deleteAllRules(); misc.deleteAllTemplates(); misc.deleteAllDayChecks()
            misc.deleteAllChallenges(); misc.deleteAllAlerts()
            accounts.deleteAll()
            categories.deleteAll()
        }
        File(filesDir, "receipts").deleteRecursively()
        settings.clear()
        ensureDefaultCategories()
        changed()
    }

    fun defaultAccountTypes() = AccountType.entries
}
