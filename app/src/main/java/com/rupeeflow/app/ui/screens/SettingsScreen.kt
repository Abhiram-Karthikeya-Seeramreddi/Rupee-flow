@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.BuildConfig
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.ThemeMode
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.AmountField
import com.rupeeflow.app.ui.components.ConfirmDialog
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.Money
import com.rupeeflow.core.PaymentMethod
import java.time.DayOfWeek

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Rf.colors.subtle)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, navigate: (String) -> Unit, onBack: () -> Unit) {
    val s = snap.settings
    val fmt = LocalMoney.current
    val ctx = LocalContext.current
    var salaryDialog by remember { mutableStateOf(false) }
    var resetStep by remember { mutableStateOf(0) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Notifications are off. You can allow them in Android settings.")
    }
    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Settings", onBack) }

        item {
            SectionLabel("Salary & cycle")
            RfCard(padding = 12) {
                SettingRow("Salary", "${if (s.expectedSalary > 0) fmt.f(s.expectedSalary) else "Not set"} on day ${s.salaryDay} of each month", onClick = { salaryDialog = true })
                SettingRow("Cycle", "${snap.cycleRange.label()} (next: ${snap.cycle.next(snap.cycleRange).label()})")
                SettingRow("Start cycle when salary actually arrives", "If it comes a few days early or late, the cycle follows the real date",
                    trailing = { Switch(s.followActualSalary, { v -> vm.updateSettings { it.copy(followActualSalary = v) } }) })
                Text("First day of week", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).forEach { d ->
                        FilterChip(s.firstDayOfWeek == d, { vm.updateSettings { it.copy(firstDayOfWeek = d) } },
                            label = { Text(d.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }

        item {
            SectionLabel("Money setup")
            RfCard(padding = 12) {
                SettingRow("Accounts & balances", "${snap.activeAccounts.size} accounts · reconcile with your bank", onClick = { navigate("accounts") })
                SettingRow("Categories", "Add, rename, archive, sub-categories, essentials", onClick = { navigate("categories") })
                SettingRow("Budgets, goals & savings plan", onClick = { navigate("budgets") })
                SettingRow("Auto-categorise rules", "${snap.rules.size} learned keyword${if (snap.rules.size == 1) "" else "s"} (e.g. swiggy → Restaurants)", onClick = { navigate("rules") })
                Text("Default payment method", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
                    listOf(PaymentMethod.UPI, PaymentMethod.CASH, PaymentMethod.DEBIT_CARD, PaymentMethod.CREDIT_CARD).forEach { p ->
                        FilterChip(s.defaultPayment == p.name, { vm.updateSettings { it.copy(defaultPayment = p.name) } }, label = { Text(p.label) })
                    }
                }
            }
        }

        item {
            SectionLabel("Reminders")
            RfCard(padding = 12) {
                SettingRow("Daily check-in", "Around ${"%02d:%02d".format(s.dailyReminderMinute / 60, s.dailyReminderMinute % 60)} — asks if you've logged today",
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> vm.updateSettings(reschedule = true) { it.copy(dailyReminderMinute = h * 60 + m, dailyReminder = true) } },
                            s.dailyReminderMinute / 60, s.dailyReminderMinute % 60, false).show()
                    },
                    trailing = { Switch(s.dailyReminder, { v -> if (v) ensureNotifPermission(); vm.updateSettings(reschedule = true) { it.copy(dailyReminder = v) } }) })
                SettingRow("Budget alerts", "At the thresholds you set per budget (50/75/90/100%)",
                    trailing = { Switch(s.budgetAlerts, { v -> if (v) ensureNotifPermission(); vm.updateSettings { it.copy(budgetAlerts = v) } }) })
                SettingRow("Bill reminders", "7/3/1 days before, set per bill",
                    trailing = { Switch(s.billReminders, { v -> if (v) ensureNotifPermission(); vm.updateSettings { it.copy(billReminders = v) } }) })
                SettingRow("Salary & new cycle", trailing = { Switch(s.cycleReminders, { v -> vm.updateSettings { it.copy(cycleReminders = v) } }) })
                SettingRow("Savings plan check", "Near the end of each cycle", trailing = { Switch(s.savingsReminders, { v -> vm.updateSettings { it.copy(savingsReminders = v) } }) })
                Text("Android may delay reminders a little to save battery; they're never sent more than once.",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(4.dp))
            }
        }

        item {
            SectionLabel("Your data")
            RfCard(padding = 12) {
                SettingRow("Backup & restore", "JSON backup (optionally password-encrypted)", onClick = { navigate("data") })
                SettingRow("Export to CSV", "By date range and category", onClick = { navigate("data") })
                SettingRow("Import bank statement / SMS", "Review before anything is saved", onClick = { navigate("import") })
            }
        }

        item {
            SectionLabel("Privacy & security")
            RfCard(padding = 12) {
                val bm = BiometricManager.from(ctx)
                val canLock = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
                SettingRow("App lock", if (canLock) "Fingerprint, face or screen lock when opening" else "Set a screen lock on your phone first",
                    trailing = { Switch(s.appLock, { v -> vm.updateSettings { it.copy(appLock = v && canLock) } }, enabled = canLock || s.appLock) })
                SettingRow("Hide in recent apps", "Blocks screenshots and blurs the app switcher",
                    trailing = { Switch(s.hideInRecents, { v -> vm.updateSettings { it.copy(hideInRecents = v) } }) })
                SettingRow("Hide amounts", "Shows ₹ •••• until you tap the eye on Home",
                    trailing = { Switch(s.hideAmounts, { v -> vm.updateSettings { it.copy(hideAmounts = v) } }) })
                Text("Everything stays on this phone. The app has no internet permission and no account.",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(4.dp))
            }
        }

        item {
            SectionLabel("Appearance")
            RfCard(padding = 12) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(4.dp)) {
                    ThemeMode.entries.forEach { t -> FilterChip(s.theme == t, { vm.updateSettings { it.copy(theme = t) } }, label = { Text(t.label) }) }
                }
                if (Build.VERSION.SDK_INT >= 31) SettingRow("Use wallpaper colours", "Material You",
                    trailing = { Switch(s.dynamicColor, { v -> vm.updateSettings { it.copy(dynamicColor = v) } }) })
                SettingRow("Indian digit grouping", if (s.indianGrouping) "₹1,25,000" else "₹125,000",
                    trailing = { Switch(s.indianGrouping, { v -> vm.updateSettings { it.copy(indianGrouping = v) } }) })
                var name by remember { mutableStateOf(s.userName) }
                LaunchedEffect(name) {
                    delay(600)
                    if (name != s.userName) vm.updateSettings { it.copy(userName = name.take(24)) }
                }
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Your name (for greetings)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(4.dp))
            }
        }

        item {
            SectionLabel("Danger zone")
            RfCard(padding = 12) {
                SettingRow("Reset all data", "Deletes everything on this phone. You'll be offered a backup first.", onClick = { resetStep = 1 })
            }
        }
        item {
            Text("RupeeFlow ${BuildConfig.VERSION_NAME} · works offline · made for the 28th-to-27th life",
                style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(8.dp))
        }
    }

    if (salaryDialog) SalaryDialog(vm, snap) { salaryDialog = false }
    when (resetStep) {
        1 -> ConfirmDialog("Back up first?", "Resetting can't be undone. Save a backup file first so you can restore later.", "Back up now",
            onConfirm = { resetStep = 0; navigate("data") }, onDismiss = { resetStep = 2 }, dismiss = "Skip backup")
        2 -> ResetConfirm(onConfirm = {
            resetStep = 0
            vm.write("Everything was reset") {
                vm.c.backup.autoSnapshot("reset")
                vm.repo.resetEverything()
            }
        }, onDismiss = { resetStep = 0 })
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
}

@Composable
private fun ResetConfirm(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all data?") },
        text = {
            Column {
                Text("All accounts, transactions, budgets, bills, goals and settings will be removed from this phone. Type RESET to confirm.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(typed, { typed = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = typed.trim() == "RESET") { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SalaryDialog(vm: MainViewModel, snap: Snapshot, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(if (snap.settings.expectedSalary > 0) Money.toInput(snap.settings.expectedSalary) else "") }
    var day by remember { mutableStateOf(snap.settings.salaryDay) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(amount, { amount = it }, "Monthly take-home (expected)")
                Text("Credited on day", style = MaterialTheme.typography.labelMedium)
                DayGrid(day) { day = it }
                Text("Expected salary is only a reminder. Your balance changes when you confirm it arrived.",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.updateSettings(reschedule = true) { it.copy(expectedSalary = Money.parse(amount) ?: 0, salaryDay = day) }
                vm.toast("Salary settings saved"); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** 1..31 day picker. Days beyond a month's length fall on its last day. */
@Composable
fun DayGrid(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = 7) {
        (1..31).forEach { d ->
            FilterChip(selected == d, { onSelect(d) }, label = { Text("$d") }, modifier = Modifier.width(44.dp))
        }
    }
    if (selected > 28) Text("In shorter months this becomes the last day of the month.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
}

