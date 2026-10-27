package com.rupeeflow.app

import android.app.Application
import android.content.Context
import com.rupeeflow.app.data.AppDatabase
import com.rupeeflow.app.data.BackupManager
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.SettingsStore
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.SnapshotBuilder
import com.rupeeflow.app.notify.AlertEngine
import com.rupeeflow.app.notify.Notifier
import com.rupeeflow.app.notify.Scheduling
import com.rupeeflow.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Manual dependency container. A small app with one process doesn't need Hilt's annotation
 * processing; this keeps the build simpler and every dependency explicit.
 */
class AppContainer(context: Context) {
    val app: Context = context.applicationContext
    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsStore(app)
    val repo = FinanceRepository(db, settings, app.filesDir)
    val backup = BackupManager(repo, app.filesDir, BuildConfig.VERSION_NAME)
    val notifier = Notifier(app)
    val alerts = AlertEngine(this)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun snapshot(today: LocalDate = LocalDate.now()): Snapshot {
        val r = repo.loadRaw()
        return SnapshotBuilder.build(today, settings.current(), r.accounts, r.categories, r.transactions, r.budgets,
            r.subscriptions, r.payments, r.goals, r.rules, r.templates, r.dayChecks, r.challenges)
    }
}

class RupeeFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.repo.onLedgerChanged = {
            container.scope.launch {
                runCatching {
                    val snap = container.snapshot()
                    container.alerts.evaluate(snap, immediateOnly = true)
                    WidgetUpdater.update(this@RupeeFlowApp, snap)
                }
            }
        }
        container.scope.launch {
            runCatching { container.repo.ensureDefaultCategories() }
            runCatching { Scheduling.scheduleAll(this@RupeeFlowApp, container.settings.current()) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as RupeeFlowApp).container
