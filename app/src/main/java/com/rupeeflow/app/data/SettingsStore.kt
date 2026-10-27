package com.rupeeflow.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val userName = stringPreferencesKey("user_name")
        val salaryDay = intPreferencesKey("salary_day")
        val expectedSalary = longPreferencesKey("expected_salary")
        val followActual = booleanPreferencesKey("follow_actual_salary")
        val firstDay = intPreferencesKey("first_day_of_week")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val indian = booleanPreferencesKey("indian_grouping")
        val appLock = booleanPreferencesKey("app_lock")
        val hideRecents = booleanPreferencesKey("hide_in_recents")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val daily = booleanPreferencesKey("daily_reminder")
        val dailyMinute = intPreferencesKey("daily_reminder_minute")
        val budgetAlerts = booleanPreferencesKey("budget_alerts")
        val billReminders = booleanPreferencesKey("bill_reminders")
        val cycleReminders = booleanPreferencesKey("cycle_reminders")
        val savingsReminders = booleanPreferencesKey("savings_reminders")
        val defaultAccount = stringPreferencesKey("default_account")
        val defaultPayment = stringPreferencesKey("default_payment")
        val plannedSavings = longPreferencesKey("planned_savings")
        val lastSmsScan = longPreferencesKey("last_sms_scan")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { read(it) }

    private fun read(p: Preferences): Settings =
        Settings(
            onboarded = p[K.onboarded] ?: false,
            userName = p[K.userName] ?: "",
            salaryDay = (p[K.salaryDay] ?: 28).coerceIn(1, 31),
            expectedSalary = p[K.expectedSalary] ?: 0,
            followActualSalary = p[K.followActual] ?: true,
            firstDayOfWeek = DayOfWeek.of((p[K.firstDay] ?: 1).coerceIn(1, 7)),
            theme = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[K.dynamic] ?: false,
            indianGrouping = p[K.indian] ?: true,
            appLock = p[K.appLock] ?: false,
            hideInRecents = p[K.hideRecents] ?: false,
            hideAmounts = p[K.hideAmounts] ?: false,
            dailyReminder = p[K.daily] ?: true,
            dailyReminderMinute = p[K.dailyMinute] ?: (21 * 60),
            budgetAlerts = p[K.budgetAlerts] ?: true,
            billReminders = p[K.billReminders] ?: true,
            cycleReminders = p[K.cycleReminders] ?: true,
            savingsReminders = p[K.savingsReminders] ?: true,
            defaultAccountId = p[K.defaultAccount],
            defaultPayment = p[K.defaultPayment] ?: "UPI",
            plannedSavingsPerCycle = p[K.plannedSavings] ?: 0,
            lastSmsScan = p[K.lastSmsScan] ?: 0,
        )

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = read(p)
            val n = transform(cur)
            p[K.onboarded] = n.onboarded
            p[K.userName] = n.userName
            p[K.salaryDay] = n.salaryDay.coerceIn(1, 31)
            p[K.expectedSalary] = n.expectedSalary
            p[K.followActual] = n.followActualSalary
            p[K.firstDay] = n.firstDayOfWeek.value
            p[K.theme] = n.theme.name
            p[K.dynamic] = n.dynamicColor
            p[K.indian] = n.indianGrouping
            p[K.appLock] = n.appLock
            p[K.hideRecents] = n.hideInRecents
            p[K.hideAmounts] = n.hideAmounts
            p[K.daily] = n.dailyReminder
            p[K.dailyMinute] = n.dailyReminderMinute.coerceIn(0, 24 * 60 - 1)
            p[K.budgetAlerts] = n.budgetAlerts
            p[K.billReminders] = n.billReminders
            p[K.cycleReminders] = n.cycleReminders
            p[K.savingsReminders] = n.savingsReminders
            if (n.defaultAccountId != null) p[K.defaultAccount] = n.defaultAccountId else p.remove(K.defaultAccount)
            p[K.defaultPayment] = n.defaultPayment
            p[K.plannedSavings] = n.plannedSavingsPerCycle
            p[K.lastSmsScan] = n.lastSmsScan
        }
    }

    suspend fun clear() { context.dataStore.edit { it.clear() } }
}
