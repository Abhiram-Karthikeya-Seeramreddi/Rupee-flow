package com.rupeeflow.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rupeeflow.app.container
import com.rupeeflow.app.data.Settings
import com.rupeeflow.app.data.subStatus
import com.rupeeflow.app.widget.WidgetUpdater
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.SubStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background work. Android may delay work under battery optimisation, so reminders are
 * "around" the chosen time rather than guaranteed to the minute.
 */
object Scheduling {
    private const val CHECK = "rupeeflow-check"
    private const val DAILY = "rupeeflow-daily"

    fun scheduleAll(context: Context, settings: Settings) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            CHECK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CheckWorker>(4, TimeUnit.HOURS).build(),
        )
        scheduleDaily(context, settings, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleDaily(context: Context, settings: Settings, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val wm = WorkManager.getInstance(context)
        if (!settings.dailyReminder) { wm.cancelUniqueWork(DAILY); return }
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atStartOfDay().plusMinutes(settings.dailyReminderMinute.toLong())
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(60_000)
        wm.enqueueUniqueWork(DAILY, policy,
            OneTimeWorkRequestBuilder<DailyReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
    }

    fun runCheckNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<CheckWorker>().build())
    }
}

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val c = applicationContext.container
        val snap = c.snapshot()
        c.alerts.evaluate(snap)
        WidgetUpdater.update(applicationContext, snap)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

class DailyReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        try {
            val snap = c.snapshot()
            c.alerts.dailyReminder(snap)
            c.alerts.evaluate(snap)
        } catch (_: Exception) {
        }
        // Chain the next day's reminder. APPEND_OR_REPLACE avoids cancelling this running work.
        Scheduling.scheduleDaily(applicationContext, c.settings.current(), ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

/** Handles notification buttons without opening the app. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_NO_SPEND = "com.rupeeflow.app.action.NO_SPEND"
        const val ACTION_PAY_BILL = "com.rupeeflow.app.action.PAY_BILL"
        const val ACTION_SALARY = "com.rupeeflow.app.action.SALARY"
        const val EXTRA_SUB = "sub_id"
        const val EXTRA_DUE = "due_day"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_NO_SPEND -> {
                        c.repo.setNoSpendToday(true)
                        c.notifier.cancel(201)
                    }
                    ACTION_PAY_BILL -> {
                        val subId = intent.getStringExtra(EXTRA_SUB) ?: return@launch
                        val due = intent.getStringExtra(EXTRA_DUE)?.toLongOrNull() ?: return@launch
                        val sub = c.db.subscriptions().get(subId) ?: return@launch
                        if (sub.subStatus() != SubStatus.ACTIVE) return@launch
                        val acc = resolveAccount(c, sub.accountId)
                        if (acc != null && c.db.subscriptions().paymentFor(subId, due) == null) {
                            c.repo.confirmBill(subId, due, sub.amount, acc, LocalDate.now(), if (sub.autopay) "AUTOPAY" else null, null)
                        }
                        c.notifier.cancel(("bill$subId").hashCode())
                    }
                    ACTION_SALARY -> {
                        val s = c.settings.current()
                        val acc = resolveAccount(c, s.defaultAccountId)
                        val snap = c.snapshot()
                        if (acc != null && s.expectedSalary > 0 && snap.salaryPrompt) c.repo.recordSalary(s.expectedSalary, acc, LocalDate.now())
                        c.notifier.cancel(303)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun resolveAccount(c: com.rupeeflow.app.AppContainer, preferred: String?): String? {
        val all = c.db.accounts().getAll().filter { !it.archived }
        return all.firstOrNull { it.id == preferred }?.id
            ?: c.settings.current().defaultAccountId?.let { d -> all.firstOrNull { it.id == d }?.id }
            ?: all.firstOrNull { it.type == AccountType.BANK.name }?.id
            ?: all.firstOrNull()?.id
    }
}

/** Re-plans reminders after reboot, time-zone or clock changes. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = context.container
                Scheduling.scheduleAll(context, c.settings.current())
                Scheduling.runCheckNow(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

