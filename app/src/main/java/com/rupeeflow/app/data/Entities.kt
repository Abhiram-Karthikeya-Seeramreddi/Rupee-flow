package com.rupeeflow.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Database design notes
 * ---------------------
 * - Money: Long paise everywhere. No REAL columns.
 * - Dates: epochDay (local calendar day at the moment of entry) + minute-of-day. Storing the
 *   calendar day means a later time-zone change never moves a purchase to another day.
 * - TransactionEntity is the single authoritative ledger. Balances, budgets, analytics,
 *   goal progress and bill payment history are all *derived* from it; nothing stores a
 *   running total that could drift.
 * - Goal contributions are real transfers tagged with goalId (no separate shadow table).
 * - SubscriptionPaymentEntity has UNIQUE(subscriptionId, dueEpochDay): a bill occurrence
 *   can be settled only once, which makes double-recording impossible at the DB level.
 * - importFingerprint is UNIQUE so the same bank row / SMS can never be imported twice.
 */

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val openingBalance: Long,
    val includeInSpendable: Boolean,
    val emoji: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "categories",
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: String,
    val parentId: String? = null,
    val essential: Boolean = false,
    val fixed: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SubscriptionEntity::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("epochDay"), Index("accountId"), Index("toAccountId"), Index("categoryId"),
        Index("subscriptionId"), Index("goalId"), Index(value = ["importFingerprint"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val epochDay: Long,
    val minuteOfDay: Int,
    val payment: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val tags: String = "",
    val receiptPath: String? = null,
    val subscriptionId: String? = null,
    val goalId: String? = null,
    val isRecurring: Boolean = false,
    val importFingerprint: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    /** null = overall spending budget across all categories. */
    val categoryId: String?,
    val amount: Long,
    val period: String,
    val customStart: Long? = null,
    val customEnd: Long? = null,
    val rollover: Boolean = false,
    val thresholds: String = "50,75,90,100",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("accountId")],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val amount: Long,
    val categoryId: String?,
    val accountId: String?,
    val frequency: String,
    val intervalDays: Int = 30,
    val anchorDay: Int,
    val nextDue: Long,
    val autopay: Boolean = false,
    val status: String = "ACTIVE",
    val remind7: Boolean = false,
    val remind3: Boolean = true,
    val remind1: Boolean = true,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subscription_payments",
    foreignKeys = [
        ForeignKey(entity = SubscriptionEntity::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["subscriptionId", "dueEpochDay"], unique = true), Index("transactionId")],
)
data class SubscriptionPaymentEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val dueEpochDay: Long,
    /** PAID or SKIPPED */
    val status: String,
    val transactionId: String?,
    val amount: Long,
    val recordedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "goals",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("accountId")],
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val target: Long,
    val targetEpochDay: Long?,
    /** The savings/investment account where this goal's money is kept. */
    val accountId: String?,
    /** Money the user says was already saved for this goal before using the app. */
    val initialSaved: Long = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Learned "keyword → category" rules (e.g. "swiggy" → Restaurants). */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey val keyword: String,
    val categoryId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One-tap templates for common entries. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val label: String,
    val type: String,
    val amount: Long?,
    val categoryId: String?,
    val accountId: String?,
    val payment: String?,
    val merchant: String?,
    val sortOrder: Int = 0,
)

/** "No spend today" check-ins so zero-spend days still count for the logging streak. */
@Entity(tableName = "day_checks")
data class DayCheckEntity(
    @PrimaryKey val epochDay: Long,
    val noSpend: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Gentle challenges. Progress is always computed from real transactions. */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    /** NO_SPEND_DAYS, CATEGORY_CAP, LOG_DAYS */
    val kind: String,
    val title: String,
    val categoryId: String? = null,
    val amount: Long? = null,
    val target: Int = 0,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val active: Boolean = true,
)

/** Notification de-duplication: each alert key fires once. */
@Entity(tableName = "alert_log")
data class AlertLogEntity(
    @PrimaryKey val alertKey: String,
    val firedAt: Long = System.currentTimeMillis(),
)
