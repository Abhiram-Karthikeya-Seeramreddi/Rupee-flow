package com.rupeeflow.app.data

import android.util.Base64
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(message: String) : Exception(message)

data class BackupPreview(
    val createdAt: String,
    val appVersion: String,
    val counts: Map<String, Int>,
    val newTransactions: Int,
    val duplicateTransactions: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val warnings: List<String>,
)

enum class RestoreMode { MERGE, REPLACE }

/**
 * JSON backup / restore.
 * Format: { app, schemaVersion, dbVersion, createdAt, encrypted, data | (kdf, iter, salt, iv, cipher) }
 * Encrypted backups use PBKDF2-HMAC-SHA256 (210k iterations) + AES-256-GCM with a password
 * the user chooses. The password is never stored.
 */
class BackupManager(private val repo: FinanceRepository, private val filesDir: File, private val appVersion: String) {

    private val db get() = repo.db

    /* ------------ export ------------ */

    suspend fun exportJson(password: String?): String {
        val raw = repo.loadRaw()
        val s = repo.settings.current()
        val data = JSONObject().apply {
            put("accounts", JSONArray(raw.accounts.map { it.toJson() }))
            put("categories", JSONArray(raw.categories.map { it.toJson() }))
            put("transactions", JSONArray(raw.transactions.map { it.toJson() }))
            put("budgets", JSONArray(raw.budgets.map { it.toJson() }))
            put("subscriptions", JSONArray(raw.subscriptions.map { it.toJson() }))
            put("subscriptionPayments", JSONArray(raw.payments.map { it.toJson() }))
            put("goals", JSONArray(raw.goals.map { it.toJson() }))
            put("rules", JSONArray(raw.rules.map { JSONObject().put("keyword", it.keyword).put("categoryId", it.categoryId).put("createdAt", it.createdAt) }))
            put("templates", JSONArray(raw.templates.map { it.toJson() }))
            put("dayChecks", JSONArray(raw.dayChecks.map { JSONObject().put("epochDay", it.epochDay).put("noSpend", it.noSpend).put("createdAt", it.createdAt) }))
            put("challenges", JSONArray(raw.challenges.map { it.toJson() }))
            put("settings", s.toJson())
        }
        val root = JSONObject()
            .put("app", APP_ID)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("dbVersion", AppDatabase.VERSION)
            .put("appVersion", appVersion)
            .put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        if (password.isNullOrEmpty()) {
            root.put("encrypted", false).put("data", data)
        } else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
            val enc = cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
            root.put("encrypted", true).put("kdf", "PBKDF2WithHmacSHA256").put("iter", ITERATIONS)
                .put("salt", b64(salt)).put("iv", b64(iv)).put("cipher", b64(enc))
        }
        return root.toString()
    }

    /** Writes a safety copy inside the app before any destructive operation. */
    suspend fun autoSnapshot(reason: String): File {
        val dir = File(filesDir, "auto-backups").apply { mkdirs() }
        val f = File(dir, "before-$reason-${System.currentTimeMillis()}.json")
        f.writeText(exportJson(null))
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
        return f
    }

    /* ------------ import ------------ */

    fun isEncrypted(text: String): Boolean = runCatching { JSONObject(text).optBoolean("encrypted", false) }.getOrDefault(false)

    private fun open(text: String, password: String?): Pair<JSONObject, JSONObject> {
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupException("This file isn't a valid backup (not JSON).") }
        if (root.optString("app") != APP_ID) throw BackupException("This file wasn't created by RupeeFlow.")
        val schema = root.optInt("schemaVersion", -1)
        if (schema < 1) throw BackupException("Backup schema is missing or invalid.")
        if (schema > SCHEMA_VERSION) throw BackupException("This backup is from a newer app version. Update RupeeFlow first.")
        val data = if (root.optBoolean("encrypted", false)) {
            if (password.isNullOrEmpty()) throw BackupException("This backup is encrypted. Enter its password.")
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, unb64(root.getString("salt")), root.optInt("iter", ITERATIONS)),
                    GCMParameterSpec(128, unb64(root.getString("iv"))))
                JSONObject(String(cipher.doFinal(unb64(root.getString("cipher"))), Charsets.UTF_8))
            } catch (e: Exception) {
                throw BackupException("Wrong password, or the backup file is damaged.")
            }
        } else root.optJSONObject("data") ?: throw BackupException("Backup has no data section.")
        return root to data
    }

    private data class Parsed(
        val accounts: List<AccountEntity>, val categories: List<CategoryEntity>, val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity>, val subscriptions: List<SubscriptionEntity>, val payments: List<SubscriptionPaymentEntity>,
        val goals: List<GoalEntity>, val rules: List<CategoryRuleEntity>, val templates: List<TemplateEntity>,
        val dayChecks: List<DayCheckEntity>, val challenges: List<ChallengeEntity>, val settings: JSONObject?,
    )

    private fun parse(data: JSONObject): Parsed {
        fun arr(name: String): List<JSONObject> = data.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
        try {
            return Parsed(
                accounts = arr("accounts").map { accountFrom(it) },
                categories = arr("categories").map { categoryFrom(it) },
                transactions = arr("transactions").map { txFrom(it) },
                budgets = arr("budgets").map { budgetFrom(it) },
                subscriptions = arr("subscriptions").map { subFrom(it) },
                payments = arr("subscriptionPayments").map { paymentFrom(it) },
                goals = arr("goals").map { goalFrom(it) },
                rules = arr("rules").map { CategoryRuleEntity(it.getString("keyword"), it.getString("categoryId"), it.optLong("createdAt")) },
                templates = arr("templates").map { templateFrom(it) },
                dayChecks = arr("dayChecks").map { DayCheckEntity(it.getLong("epochDay"), it.optBoolean("noSpend", true), it.optLong("createdAt")) },
                challenges = arr("challenges").map { challengeFrom(it) },
                settings = data.optJSONObject("settings"),
            )
        } catch (e: Exception) {
            throw BackupException("The backup is damaged or incomplete: ${e.message}")
        }
    }

    private fun validate(p: Parsed): List<String> {
        val warnings = ArrayList<String>()
        val accIds = p.accounts.map { it.id }.toSet()
        val catIds = p.categories.map { it.id }.toSet()
        if (accIds.size != p.accounts.size) throw BackupException("Backup contains duplicate account IDs.")
        if (p.transactions.map { it.id }.toSet().size != p.transactions.size) throw BackupException("Backup contains duplicate transaction IDs.")
        for (t in p.transactions) {
            if (t.accountId !in accIds) throw BackupException("Transaction ${t.id.take(8)} refers to a missing account.")
            if (t.toAccountId != null && t.toAccountId !in accIds) throw BackupException("Transfer ${t.id.take(8)} refers to a missing account.")
            val tt = runCatching { com.rupeeflow.core.TxType.valueOf(t.type) }.getOrNull() ?: throw BackupException("Unknown transaction type '${t.type}'.")
            if (tt.isMovement && (t.toAccountId == null || t.toAccountId == t.accountId)) throw BackupException("Transfer ${t.id.take(8)} is missing its destination account.")
            if (t.categoryId != null && t.categoryId !in catIds) warnings += "Some transactions reference categories that aren't in the backup; they'll show as uncategorised."
        }
        return warnings.distinct()
    }

    suspend fun preview(text: String, password: String?): BackupPreview {
        val (root, data) = open(text, password)
        val p = parse(data)
        val warnings = validate(p).toMutableList()
        if (root.optInt("dbVersion", 1) != AppDatabase.VERSION) warnings += "Backup came from database version ${root.optInt("dbVersion")}; it will be converted."
        val existing = repo.loadRaw().transactions.map { it.id }.toSet()
        val dup = p.transactions.count { it.id in existing }
        return BackupPreview(
            createdAt = root.optString("createdAt"),
            appVersion = root.optString("appVersion"),
            counts = mapOf(
                "Accounts" to p.accounts.size, "Categories" to p.categories.size, "Transactions" to p.transactions.size,
                "Budgets" to p.budgets.size, "Bills" to p.subscriptions.size, "Goals" to p.goals.size,
            ),
            newTransactions = p.transactions.size - dup,
            duplicateTransactions = dup,
            firstDate = p.transactions.minOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it) },
            lastDate = p.transactions.maxOfOrNull { it.epochDay }?.let { LocalDate.ofEpochDay(it) },
            warnings = warnings,
        )
    }

    /**
     * Restores a backup. REPLACE first saves an automatic safety copy of current data.
     * MERGE only adds records whose IDs don't exist yet — nothing existing is overwritten.
     * Everything runs in one database transaction: it either fully succeeds or changes nothing.
     */
    suspend fun restore(text: String, password: String?, mode: RestoreMode): Int {
        val (_, data) = open(text, password)
        val p = parse(data)
        validate(p)
        if (mode == RestoreMode.REPLACE) autoSnapshot("restore")
        val catIds = p.categories.map { it.id }.toSet()
        var added = 0
        db.withTransaction {
            val d = db
            if (mode == RestoreMode.REPLACE) {
                d.subscriptions().deleteAllPayments(); d.transactions().deleteAll(); d.budgets().deleteAll()
                d.subscriptions().deleteAll(); d.goals().deleteAll(); d.misc().deleteAllRules(); d.misc().deleteAllTemplates()
                d.misc().deleteAllDayChecks(); d.misc().deleteAllChallenges(); d.accounts().deleteAll(); d.categories().deleteAll()
            }
            val cur = repo.loadRaw()
            val haveAcc = cur.accounts.map { it.id }.toSet()
            val haveCat = cur.categories.map { it.id }.toSet()
            val haveTx = cur.transactions.map { it.id }.toSet()
            val haveSub = cur.subscriptions.map { it.id }.toSet()
            val haveGoal = cur.goals.map { it.id }.toSet()
            val haveBud = cur.budgets.map { it.id }.toSet()
            val havePay = cur.payments.map { it.subscriptionId to it.dueEpochDay }.toSet()
            val haveFp = cur.transactions.mapNotNull { it.importFingerprint }.toSet()

            // Parents before children so foreign keys hold.
            val cats = p.categories.filter { it.id !in haveCat }.sortedBy { if (it.parentId == null) 0 else 1 }
            d.categories().insertAll(cats)
            d.accounts().insertAll(p.accounts.filter { it.id !in haveAcc })
            val allCats = haveCat + catIds
            val allAcc = haveAcc + p.accounts.map { it.id }
            d.goals().insertAll(p.goals.filter { it.id !in haveGoal }.map { it.copy(accountId = it.accountId?.takeIf { a -> a in allAcc }) })
            d.subscriptions().insertAll(p.subscriptions.filter { it.id !in haveSub }.map {
                it.copy(categoryId = it.categoryId?.takeIf { c -> c in allCats }, accountId = it.accountId?.takeIf { a -> a in allAcc })
            })
            val allSubs = haveSub + p.subscriptions.map { it.id }
            val allGoals = haveGoal + p.goals.map { it.id }
            val txs = p.transactions.filter { it.id !in haveTx && (it.importFingerprint == null || it.importFingerprint !in haveFp) }.map {
                it.copy(
                    categoryId = it.categoryId?.takeIf { c -> c in allCats },
                    subscriptionId = it.subscriptionId?.takeIf { s -> s in allSubs },
                    goalId = it.goalId?.takeIf { g -> g in allGoals },
                )
            }
            d.transactions().insertAll(txs)
            added = txs.size
            val txIds = haveTx + txs.map { it.id }
            d.subscriptions().insertPayments(p.payments.filter { (it.subscriptionId to it.dueEpochDay) !in havePay && it.subscriptionId in allSubs }
                .map { it.copy(transactionId = it.transactionId?.takeIf { t -> t in txIds }) })
            d.budgets().insertAll(p.budgets.filter { it.id !in haveBud && (it.categoryId == null || it.categoryId in allCats) })
            d.misc().insertRules(p.rules.filter { it.categoryId in allCats })
            d.misc().insertTemplates(p.templates)
            d.misc().insertDayChecks(p.dayChecks)
            d.misc().insertChallenges(p.challenges)
        }
        if (mode == RestoreMode.REPLACE) p.settings?.let { js -> repo.settings.update { applySettings(it, js) } }
        repo.ensureDefaultCategories()
        repo.onLedgerChanged?.invoke()
        return added
    }

    companion object {
        const val APP_ID = "RupeeFlow"
        const val SCHEMA_VERSION = 1
        const val ITERATIONS = 210_000

        private fun deriveKey(password: String, salt: ByteArray, iter: Int): SecretKeySpec {
            val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            return SecretKeySpec(bytes, "AES")
        }
        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

        private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
        private fun JSONObject.lng(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
        private fun Any?.orNull(): Any = this ?: JSONObject.NULL

        fun AccountEntity.toJson() = JSONObject().put("id", id).put("name", name).put("type", type).put("openingBalance", openingBalance)
            .put("includeInSpendable", includeInSpendable).put("emoji", emoji).put("archived", archived).put("sortOrder", sortOrder).put("createdAt", createdAt)
        fun accountFrom(j: JSONObject) = AccountEntity(j.getString("id"), j.getString("name"), j.getString("type"), j.getLong("openingBalance"),
            j.optBoolean("includeInSpendable", true), j.optString("emoji", "🏦"), j.optBoolean("archived"), j.optInt("sortOrder"), j.optLong("createdAt"))

        fun CategoryEntity.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("color", color).put("kind", kind)
            .put("parentId", parentId.orNull()).put("essential", essential).put("fixed", fixed).put("favorite", favorite).put("archived", archived).put("sortOrder", sortOrder)
        fun categoryFrom(j: JSONObject) = CategoryEntity(j.getString("id"), j.getString("name"), j.optString("emoji", "📦"), j.optLong("color", 0xFF90A4AE),
            j.optString("kind", "EXPENSE"), j.str("parentId"), j.optBoolean("essential"), j.optBoolean("fixed"), j.optBoolean("favorite"), j.optBoolean("archived"), j.optInt("sortOrder"))

        fun TransactionEntity.toJson() = JSONObject().put("id", id).put("type", type).put("amount", amount).put("accountId", accountId)
            .put("toAccountId", toAccountId.orNull()).put("categoryId", categoryId.orNull()).put("epochDay", epochDay).put("minuteOfDay", minuteOfDay)
            .put("payment", payment.orNull()).put("merchant", merchant.orNull()).put("note", note.orNull()).put("tags", tags)
            .put("subscriptionId", subscriptionId.orNull()).put("goalId", goalId.orNull()).put("isRecurring", isRecurring)
            .put("importFingerprint", importFingerprint.orNull()).put("createdAt", createdAt).put("updatedAt", updatedAt)
        fun txFrom(j: JSONObject) = TransactionEntity(j.getString("id"), j.getString("type"), j.getLong("amount"), j.getString("accountId"),
            j.str("toAccountId"), j.str("categoryId"), j.getLong("epochDay"), j.optInt("minuteOfDay", 720), j.str("payment"), j.str("merchant"),
            j.str("note"), j.optString("tags", ""), null, j.str("subscriptionId"), j.str("goalId"), j.optBoolean("isRecurring"),
            j.str("importFingerprint"), j.optLong("createdAt"), j.optLong("updatedAt"))

        fun BudgetEntity.toJson() = JSONObject().put("id", id).put("categoryId", categoryId.orNull()).put("amount", amount).put("period", period)
            .put("customStart", customStart.orNull()).put("customEnd", customEnd.orNull()).put("rollover", rollover).put("thresholds", thresholds)
            .put("active", active).put("createdAt", createdAt)
        fun budgetFrom(j: JSONObject) = BudgetEntity(j.getString("id"), j.str("categoryId"), j.getLong("amount"), j.getString("period"),
            j.lng("customStart"), j.lng("customEnd"), j.optBoolean("rollover"), j.optString("thresholds", "50,75,90,100"), j.optBoolean("active", true), j.optLong("createdAt"))

        fun SubscriptionEntity.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("amount", amount)
            .put("categoryId", categoryId.orNull()).put("accountId", accountId.orNull()).put("frequency", frequency).put("intervalDays", intervalDays)
            .put("anchorDay", anchorDay).put("nextDue", nextDue).put("autopay", autopay).put("status", status).put("remind7", remind7)
            .put("remind3", remind3).put("remind1", remind1).put("notes", notes.orNull()).put("createdAt", createdAt)
        fun subFrom(j: JSONObject) = SubscriptionEntity(j.getString("id"), j.getString("name"), j.optString("emoji", "🔁"), j.getLong("amount"),
            j.str("categoryId"), j.str("accountId"), j.getString("frequency"), j.optInt("intervalDays", 30), j.getInt("anchorDay"), j.getLong("nextDue"),
            j.optBoolean("autopay"), j.optString("status", "ACTIVE"), j.optBoolean("remind7"), j.optBoolean("remind3", true), j.optBoolean("remind1", true),
            j.str("notes"), j.optLong("createdAt"))

        fun SubscriptionPaymentEntity.toJson() = JSONObject().put("id", id).put("subscriptionId", subscriptionId).put("dueEpochDay", dueEpochDay)
            .put("status", status).put("transactionId", transactionId.orNull()).put("amount", amount).put("recordedAt", recordedAt)
        fun paymentFrom(j: JSONObject) = SubscriptionPaymentEntity(j.getString("id"), j.getString("subscriptionId"), j.getLong("dueEpochDay"),
            j.getString("status"), j.str("transactionId"), j.optLong("amount"), j.optLong("recordedAt"))

        fun GoalEntity.toJson() = JSONObject().put("id", id).put("name", name).put("emoji", emoji).put("target", target)
            .put("targetEpochDay", targetEpochDay.orNull()).put("accountId", accountId.orNull()).put("initialSaved", initialSaved)
            .put("archived", archived).put("createdAt", createdAt)
        fun goalFrom(j: JSONObject) = GoalEntity(j.getString("id"), j.getString("name"), j.optString("emoji", "🎯"), j.getLong("target"),
            j.lng("targetEpochDay"), j.str("accountId"), j.optLong("initialSaved"), j.optBoolean("archived"), j.optLong("createdAt"))

        fun TemplateEntity.toJson() = JSONObject().put("id", id).put("label", label).put("type", type).put("amount", amount.orNull())
            .put("categoryId", categoryId.orNull()).put("accountId", accountId.orNull()).put("payment", payment.orNull())
            .put("merchant", merchant.orNull()).put("sortOrder", sortOrder)
        fun templateFrom(j: JSONObject) = TemplateEntity(j.getString("id"), j.getString("label"), j.getString("type"), j.lng("amount"),
            j.str("categoryId"), j.str("accountId"), j.str("payment"), j.str("merchant"), j.optInt("sortOrder"))

        fun ChallengeEntity.toJson() = JSONObject().put("id", id).put("kind", kind).put("title", title).put("categoryId", categoryId.orNull())
            .put("amount", amount.orNull()).put("target", target).put("startEpochDay", startEpochDay).put("endEpochDay", endEpochDay).put("active", active)
        fun challengeFrom(j: JSONObject) = ChallengeEntity(j.getString("id"), j.getString("kind"), j.getString("title"), j.str("categoryId"),
            j.lng("amount"), j.optInt("target"), j.getLong("startEpochDay"), j.getLong("endEpochDay"), j.optBoolean("active", true))

        fun Settings.toJson() = JSONObject().put("userName", userName).put("salaryDay", salaryDay).put("expectedSalary", expectedSalary)
            .put("followActualSalary", followActualSalary).put("firstDayOfWeek", firstDayOfWeek.value).put("theme", theme.name)
            .put("dynamicColor", dynamicColor).put("indianGrouping", indianGrouping).put("dailyReminder", dailyReminder)
            .put("dailyReminderMinute", dailyReminderMinute).put("budgetAlerts", budgetAlerts).put("billReminders", billReminders)
            .put("cycleReminders", cycleReminders).put("savingsReminders", savingsReminders).put("defaultAccountId", defaultAccountId.orNull())
            .put("defaultPayment", defaultPayment).put("plannedSavingsPerCycle", plannedSavingsPerCycle)

        fun applySettings(s: Settings, j: JSONObject) = s.copy(
            onboarded = true,
            userName = j.optString("userName", s.userName),
            salaryDay = j.optInt("salaryDay", s.salaryDay).coerceIn(1, 31),
            expectedSalary = j.optLong("expectedSalary", s.expectedSalary),
            followActualSalary = j.optBoolean("followActualSalary", s.followActualSalary),
            firstDayOfWeek = java.time.DayOfWeek.of(j.optInt("firstDayOfWeek", s.firstDayOfWeek.value).coerceIn(1, 7)),
            theme = runCatching { ThemeMode.valueOf(j.optString("theme", s.theme.name)) }.getOrDefault(s.theme),
            dynamicColor = j.optBoolean("dynamicColor", s.dynamicColor),
            indianGrouping = j.optBoolean("indianGrouping", s.indianGrouping),
            dailyReminder = j.optBoolean("dailyReminder", s.dailyReminder),
            dailyReminderMinute = j.optInt("dailyReminderMinute", s.dailyReminderMinute),
            budgetAlerts = j.optBoolean("budgetAlerts", s.budgetAlerts),
            billReminders = j.optBoolean("billReminders", s.billReminders),
            cycleReminders = j.optBoolean("cycleReminders", s.cycleReminders),
            savingsReminders = j.optBoolean("savingsReminders", s.savingsReminders),
            defaultAccountId = if (j.isNull("defaultAccountId")) s.defaultAccountId else j.optString("defaultAccountId"),
            defaultPayment = j.optString("defaultPayment", s.defaultPayment),
            plannedSavingsPerCycle = j.optLong("plannedSavingsPerCycle", s.plannedSavingsPerCycle),
        )
    }
}
