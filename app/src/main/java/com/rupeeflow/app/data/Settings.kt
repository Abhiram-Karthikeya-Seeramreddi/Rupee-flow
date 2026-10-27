package com.rupeeflow.app.data

import java.time.DayOfWeek

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class Settings(
    val onboarded: Boolean = false,
    val userName: String = "",
    val salaryDay: Int = 28,
    val expectedSalary: Long = 0,
    val followActualSalary: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val indianGrouping: Boolean = true,
    val appLock: Boolean = false,
    val hideInRecents: Boolean = false,
    val hideAmounts: Boolean = false,
    val dailyReminder: Boolean = true,
    val dailyReminderMinute: Int = 21 * 60,
    val budgetAlerts: Boolean = true,
    val billReminders: Boolean = true,
    val cycleReminders: Boolean = true,
    val savingsReminders: Boolean = true,
    val defaultAccountId: String? = null,
    val defaultPayment: String = "UPI",
    val plannedSavingsPerCycle: Long = 0,
    val lastSmsScan: Long = 0,
)
