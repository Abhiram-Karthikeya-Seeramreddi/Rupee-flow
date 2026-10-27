package com.rupeeflow.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, createdAt")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, createdAt")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: String): AccountEntity?

    @Upsert suspend fun upsert(a: AccountEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<AccountEntity>)
    @Delete suspend fun delete(a: AccountEntity)
    @Query("DELETE FROM accounts") suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Upsert suspend fun upsert(c: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAllIgnore(list: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<CategoryEntity>)
    @Delete suspend fun delete(c: CategoryEntity)
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    suspend fun usageCount(id: String): Int
    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun reassign(from: String, to: String)
    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :id")
    suspend fun detachChildren(id: String)
    @Query("DELETE FROM categories") suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, minuteOfDay")
    suspend fun between(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT importFingerprint FROM transactions WHERE importFingerprint IS NOT NULL")
    suspend fun fingerprints(): List<String>

    @Query(
        "SELECT * FROM transactions WHERE type = :type AND amount = :amount AND accountId = :accountId " +
            "AND epochDay = :epochDay AND id != :excludeId AND createdAt > :since LIMIT 1",
    )
    suspend fun findRecentDuplicate(type: String, amount: Long, accountId: String, epochDay: Long, excludeId: String, since: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(t: TransactionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<TransactionEntity>)
    @Update suspend fun update(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun countForAccount(id: String): Int
    @Query("DELETE FROM transactions") suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt")
    fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun getAll(): List<BudgetEntity>
    @Upsert suspend fun upsert(b: BudgetEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<BudgetEntity>)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM budgets") suspend fun deleteAll()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY nextDue")
    fun observeAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions") suspend fun getAll(): List<SubscriptionEntity>
    @Query("SELECT * FROM subscriptions WHERE id = :id") suspend fun get(id: String): SubscriptionEntity?
    @Upsert suspend fun upsert(s: SubscriptionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<SubscriptionEntity>)
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM subscriptions") suspend fun deleteAll()

    @Query("SELECT * FROM subscription_payments ORDER BY dueEpochDay DESC")
    fun observePayments(): Flow<List<SubscriptionPaymentEntity>>
    @Query("SELECT * FROM subscription_payments") suspend fun getPayments(): List<SubscriptionPaymentEntity>
    @Query("SELECT * FROM subscription_payments WHERE subscriptionId = :subId AND dueEpochDay = :due")
    suspend fun paymentFor(subId: String, due: Long): SubscriptionPaymentEntity?
    @Query("SELECT * FROM subscription_payments WHERE transactionId = :txId")
    suspend fun paymentForTransaction(txId: String): SubscriptionPaymentEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayment(p: SubscriptionPaymentEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayments(list: List<SubscriptionPaymentEntity>)
    @Query("DELETE FROM subscription_payments WHERE id = :id") suspend fun deletePayment(id: String)
    @Query("DELETE FROM subscription_payments") suspend fun deleteAllPayments()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY archived, createdAt")
    fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals") suspend fun getAll(): List<GoalEntity>
    @Upsert suspend fun upsert(g: GoalEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(list: List<GoalEntity>)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM goals") suspend fun deleteAll()
}

@Dao
interface MiscDao {
    @Query("SELECT * FROM category_rules") fun observeRules(): Flow<List<CategoryRuleEntity>>
    @Query("SELECT * FROM category_rules") suspend fun getRules(): List<CategoryRuleEntity>
    @Upsert suspend fun upsertRule(r: CategoryRuleEntity)
    @Query("DELETE FROM category_rules WHERE keyword = :k") suspend fun deleteRule(k: String)
    @Query("DELETE FROM category_rules") suspend fun deleteAllRules()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRules(list: List<CategoryRuleEntity>)

    @Query("SELECT * FROM templates ORDER BY sortOrder, label") fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM templates") suspend fun getTemplates(): List<TemplateEntity>
    @Upsert suspend fun upsertTemplate(t: TemplateEntity)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)
    @Query("DELETE FROM templates") suspend fun deleteAllTemplates()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTemplates(list: List<TemplateEntity>)

    @Query("SELECT * FROM day_checks") fun observeDayChecks(): Flow<List<DayCheckEntity>>
    @Query("SELECT * FROM day_checks") suspend fun getDayChecks(): List<DayCheckEntity>
    @Upsert suspend fun upsertDayCheck(d: DayCheckEntity)
    @Query("DELETE FROM day_checks WHERE epochDay = :day") suspend fun deleteDayCheck(day: Long)
    @Query("DELETE FROM day_checks") suspend fun deleteAllDayChecks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDayChecks(list: List<DayCheckEntity>)

    @Query("SELECT * FROM challenges ORDER BY startEpochDay DESC") fun observeChallenges(): Flow<List<ChallengeEntity>>
    @Query("SELECT * FROM challenges") suspend fun getChallenges(): List<ChallengeEntity>
    @Upsert suspend fun upsertChallenge(c: ChallengeEntity)
    @Query("DELETE FROM challenges WHERE id = :id") suspend fun deleteChallenge(id: String)
    @Query("DELETE FROM challenges") suspend fun deleteAllChallenges()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChallenges(list: List<ChallengeEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM alert_log WHERE alertKey = :key)") suspend fun alertFired(key: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun logAlert(a: AlertLogEntity)
    @Query("DELETE FROM alert_log WHERE firedAt < :before") suspend fun pruneAlerts(before: Long)
    @Query("DELETE FROM alert_log") suspend fun deleteAllAlerts()
}
