package com.rupeeflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BudgetEntity::class,
        SubscriptionEntity::class, SubscriptionPaymentEntity::class, GoalEntity::class,
        CategoryRuleEntity::class, TemplateEntity::class, DayCheckEntity::class,
        ChallengeEntity::class, AlertLogEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun budgets(): BudgetDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun goals(): GoalDao
    abstract fun misc(): MiscDao

    companion object {
        const val VERSION = 1
        const val NAME = "rupeeflow.db"

        /**
         * Migration policy: every schema change bumps VERSION and adds an explicit
         * Migration here (plus a MigrationTestHelper test against the exported JSON
         * schema in app/schemas). There is deliberately NO fallbackToDestructiveMigration:
         * a missing migration must crash in testing rather than wipe someone's money data.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
