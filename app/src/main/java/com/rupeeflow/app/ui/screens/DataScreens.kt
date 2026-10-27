@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rupeeflow.app.data.BackupException
import com.rupeeflow.app.data.BackupPreview
import com.rupeeflow.app.data.CsvExporter
import com.rupeeflow.app.data.ImportCandidate
import com.rupeeflow.app.data.RestoreMode
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.data.txType
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.DatePickerButton
import com.rupeeflow.app.ui.components.KeyValueRow
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.fullDate
import com.rupeeflow.app.ui.components.shortDate
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.CsvImport
import com.rupeeflow.core.DateRange
import com.rupeeflow.core.ImportedRow
import com.rupeeflow.core.PeriodKind
import com.rupeeflow.core.Periods
import com.rupeeflow.core.QuickParse
import com.rupeeflow.core.SmsParser
import com.rupeeflow.core.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private fun Context.readText(uri: Uri): String =
    contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IllegalStateException("Couldn't open file")

private fun Context.writeText(uri: Uri, text: String) {
    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IllegalStateException("Couldn't write file")
}

@Composable
fun DataScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = LocalMoney.current
    var backupPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Restore state
    var restoreText by remember { mutableStateOf<String?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var needsPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }

    // Export state
    var exportPeriod by remember { mutableStateOf<PeriodKind?>(PeriodKind.SALARY_CYCLE) }
    var customStart by remember { mutableStateOf(snap.cycleRange.start) }
    var customEnd by remember { mutableStateOf(snap.today) }
    val exportCats = remember { mutableStateListOf<String>() }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val json = vm.c.backup.exportJson(backupPassword.ifBlank { null })
                withContext(Dispatchers.IO) { ctx.writeText(uri, json) }
                vm.toast(if (backupPassword.isBlank()) "Backup saved" else "Encrypted backup saved — keep the password safe")
            } catch (e: Exception) { vm.toast("Backup failed: ${e.message}") } finally { busy = false }
        }
    }

    fun runPreview(text: String, password: String?) {
        scope.launch {
            try {
                preview = vm.c.backup.preview(text, password)
                needsPassword = false
            } catch (e: BackupException) {
                if (vm.c.backup.isEncrypted(text) && password.isNullOrEmpty()) needsPassword = true
                else { vm.toast(e.message ?: "Invalid backup"); restoreText = null }
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { ctx.readText(uri) }
                restoreText = text; restorePassword = ""
                if (vm.c.backup.isEncrypted(text)) needsPassword = true else runPreview(text, null)
            } catch (e: Exception) { vm.toast("Couldn't read that file") }
        }
    }

    fun exportRange(): DateRange? = when (exportPeriod) {
        null -> null
        PeriodKind.CUSTOM -> DateRange(customStart, if (customEnd.isBefore(customStart)) customStart else customEnd)
        else -> Periods.resolve(exportPeriod!!, snap.today, snap.cycle, snap.settings.firstDayOfWeek)
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val csv = CsvExporter.export(snap.transactions, snap.accounts, snap.categories, exportRange(), exportCats.toSet().ifEmpty { null })
                withContext(Dispatchers.IO) { ctx.writeText(uri, csv) }
                vm.toast("CSV exported")
            } catch (e: Exception) { vm.toast("Export failed: ${e.message}") }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Backup & export", onBack) }
        item {
            SectionLabel("Full backup")
            RfCard {
                Text("Saves everything (accounts, transactions, budgets, bills, goals, settings) to a file you choose — Downloads, Google Drive, WhatsApp to yourself…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(backupPassword, { backupPassword = it }, label = { Text("Password (optional, encrypts the file)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth())
                Text("If you forget the password, the backup can't be opened. Nobody can recover it.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { createBackup.launch("RupeeFlow-backup-${snap.today}.json") }, enabled = !busy) { Text("Create backup") }
                    OutlinedButton(onClick = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Restore…") }
                    if (busy) CircularProgressIndicator(Modifier.width(24.dp))
                }
            }
        }
        item {
            SectionLabel("Export to CSV")
            RfCard {
                Text("Opens in Excel or Google Sheets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(exportPeriod == null, { exportPeriod = null }, label = { Text("All time") })
                    listOf(PeriodKind.SALARY_CYCLE, PeriodKind.LAST_CYCLE, PeriodKind.THIS_MONTH, PeriodKind.LAST_MONTH, PeriodKind.LAST_3_MONTHS, PeriodKind.LAST_12_MONTHS, PeriodKind.CUSTOM).forEach { p ->
                        FilterChip(exportPeriod == p, { exportPeriod = p }, label = { Text(p.label) })
                    }
                }
                if (exportPeriod == PeriodKind.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    DatePickerButton("From", customStart, { customStart = it }, Modifier.weight(1f))
                    DatePickerButton("To", customEnd, { customEnd = it }, Modifier.weight(1f))
                }
                Text("Categories (none selected = all)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snap.categories.filter { it.parentId == null }.forEach { c ->
                        FilterChip(c.id in exportCats, { if (c.id in exportCats) exportCats.remove(c.id) else exportCats.add(c.id) }, label = { Text("${c.emoji} ${c.name}") })
                    }
                }
                exportRange()?.let { Text(it.label(), style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { createCsv.launch("RupeeFlow-${snap.today}.csv") }) { Text("Export CSV") }
            }
        }
    }

    if (needsPassword && restoreText != null) {
        AlertDialog(
            onDismissRequest = { needsPassword = false; restoreText = null },
            title = { Text("Encrypted backup") },
            text = {
                OutlinedTextField(restorePassword, { restorePassword = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
            },
            confirmButton = { TextButton(onClick = { runPreview(restoreText!!, restorePassword) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { needsPassword = false; restoreText = null }) { Text("Cancel") } },
        )
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { preview = null; restoreText = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Created ${p.createdAt.replace('T', ' ').take(16)}" + (p.appVersion.takeIf { it.isNotBlank() }?.let { " · v$it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                    p.counts.forEach { (k, v) -> KeyValueRow(k, "$v") }
                    if (p.firstDate != null && p.lastDate != null) Text("Entries from ${fullDate(p.firstDate)} to ${fullDate(p.lastDate)}", style = MaterialTheme.typography.labelMedium)
                    Text("${p.newTransactions} new · ${p.duplicateTransactions} already on this phone", style = MaterialTheme.typography.labelLarge)
                    p.warnings.forEach { Text("⚠️ $it", style = MaterialTheme.typography.labelSmall, color = Rf.colors.warning) }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == RestoreMode.MERGE, { mode = RestoreMode.MERGE })
                        Column { Text("Merge (safe)"); Text("Only adds what's missing; nothing here is changed.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(mode == RestoreMode.REPLACE, { mode = RestoreMode.REPLACE })
                        Column { Text("Replace everything"); Text("Current data is first saved to an automatic safety copy.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = restoreText ?: return@TextButton
                    val pw = restorePassword.ifBlank { null }
                    preview = null
                    scope.launch {
                        busy = true
                        try {
                            val n = vm.c.backup.restore(text, pw, mode)
                            vm.toast("Restored · $n transactions added")
                        } catch (e: Exception) {
                            vm.toast("Restore failed, nothing was changed: ${e.message}")
                        } finally { busy = false; restoreText = null }
                    }
                }) { Text(if (mode == RestoreMode.REPLACE) "Replace" else "Merge") }
            },
            dismissButton = { TextButton(onClick = { preview = null; restoreText = null }) { Text("Cancel") } },
        )
    }
}

/* ======================= Import (bank CSV / SMS) ======================= */

@Composable
fun ImportScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = LocalMoney.current
    val rows = remember { mutableStateListOf<ImportCandidate>() }
    var source by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val accounts = snap.activeAccounts
    var accountId by remember { mutableStateOf(snap.settings.defaultAccountId ?: accounts.firstOrNull { it.accountType() == AccountType.BANK }?.id ?: accounts.firstOrNull()?.id) }
    var smsDays by remember { mutableStateOf(45) }

    suspend fun prepare(found: List<ImportedRow>) {
        val known = vm.repo.existingFingerprints()
        val manual = snap.transactions.filter { it.importFingerprint == null }.map { Triple(it.epochDay, it.amount, it.txType() == TxType.EXPENSE) }.toSet()
        val acc = accountId ?: return
        rows.clear()
        found.sortedByDescending { it.date }.forEach { r ->
            val p = QuickParse.parse(r.description, snap.keywords, snap.rules)
            val type = if (r.isDebit) TxType.EXPENSE else TxType.INCOME
            val cat = p.categoryId?.takeIf { id -> snap.category(id)?.categoryKind() == (if (r.isDebit) CategoryKind.EXPENSE else CategoryKind.INCOME) }
                ?: if (r.isDebit) "misc" else "other_income"
            val dup = r.fingerprint in known || Triple(r.date.toEpochDay(), r.amount, r.isDebit) in manual
            rows += ImportCandidate(r, !dup, cat, acc, type, dup)
        }
    }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            try {
                val text = withContext(Dispatchers.IO) { ctx.readText(uri) }
                val res = CsvImport.parse(text)
                if (res.error != null && res.rows.isEmpty()) vm.toast(res.error!!)
                source = "Bank statement · ${res.rows.size} rows" + if (res.skippedLines > 0) " (${res.skippedLines} lines skipped)" else ""
                prepare(res.rows)
            } catch (e: Exception) { vm.toast("Couldn't read that file: ${e.message}") } finally { loading = false }
        }
    }

    fun scanSms() {
        scope.launch {
            loading = true
            try {
                val since = System.currentTimeMillis() - smsDays * 24L * 3600 * 1000
                val found = withContext(Dispatchers.IO) {
                    val out = ArrayList<ImportedRow>()
                    ctx.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
                        "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} DESC")?.use { c ->
                        val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                        val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                        while (c.moveToNext()) {
                            val body = c.getString(bi) ?: continue
                            val date = Instant.ofEpochMilli(c.getLong(di)).atZone(ZoneId.systemDefault()).toLocalDate()
                            SmsParser.parse(body, date)?.let { out += it }
                        }
                    }
                    out
                }
                source = "Bank SMS · last $smsDays days · ${found.size} found"
                prepare(found)
                vm.updateSettings { it.copy(lastSmsScan = System.currentTimeMillis()) }
            } catch (e: SecurityException) {
                vm.toast("SMS permission was not granted.")
            } catch (e: Exception) {
                vm.toast("Couldn't read SMS: ${e.message}")
            } finally { loading = false }
        }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanSms() else vm.toast("No problem — SMS scanning stays off.")
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Import", onBack) }
        if (rows.isEmpty()) {
            item {
                RfCard {
                    Text("🏦 Bank statement (CSV)", style = MaterialTheme.typography.titleSmall)
                    Text("Download your statement as CSV from net banking, then pick it here. HDFC, SBI, ICICI, Axis and most UPI exports work.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pickCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) }) { Text("Choose CSV file") }
                }
            }
            item {
                RfCard {
                    Text("💬 Bank & UPI SMS", style = MaterialTheme.typography.titleSmall)
                    Text("Reads debit/credit alerts already in your inbox, only when you tap the button. OTPs, offers and reminders are ignored. Nothing is saved until you review it, and nothing leaves your phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7, 30, 45, 90).forEach { d -> FilterChip(smsDays == d, { smsDays = d }, label = { Text("$d days") }) }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) scanSms()
                        else smsPermission.launch(Manifest.permission.READ_SMS)
                    }) { Text("Scan SMS") }
                    if (snap.settings.lastSmsScan > 0) Text("Last scanned ${shortDate(Instant.ofEpochMilli(snap.settings.lastSmsScan).atZone(ZoneId.systemDefault()).toLocalDate())}",
                        style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                }
            }
            if (loading) item { CircularProgressIndicator() }
        } else {
            item {
                RfCard {
                    Text(source, style = MaterialTheme.typography.titleSmall)
                    val sel = rows.count { it.selected }
                    Text("$sel selected · ${rows.count { it.duplicate }} look like duplicates (unticked)", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                    Spacer(Modifier.height(8.dp))
                    Selector("Into account", accounts.map { it.id }, accountId, display = { id -> snap.account(id)?.let { "${it.emoji} ${it.name}" } ?: "" },
                        onSelect = { id -> accountId = id; for (i in rows.indices) rows[i] = rows[i].copy(accountId = id) })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = sel > 0, onClick = {
                            scope.launch {
                                try {
                                    val n = vm.repo.importRows(rows.toList())
                                    vm.toast("Imported $n entr${if (n == 1) "y" else "ies"}")
                                    rows.clear()
                                } catch (e: Exception) { vm.toast("Import failed: ${e.message}") }
                            }
                        }) { Text("Import $sel") }
                        OutlinedButton(onClick = { rows.clear() }) { Text("Cancel") }
                    }
                }
            }
            itemsIndexed(rows, key = { i, r -> r.row.fingerprint + i }) { i, r ->
                RfCard(padding = 10) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.selected, { v -> rows[i] = r.copy(selected = v) })
                        Column(Modifier.weight(1f)) {
                            Text(r.row.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(shortDate(r.row.date) + (if (r.duplicate) " · possible duplicate" else ""), style = MaterialTheme.typography.labelSmall,
                                color = if (r.duplicate) Rf.colors.warning else Rf.colors.subtle)
                        }
                        Text((if (r.row.isDebit) "−" else "+") + fmt.f(r.row.amount), style = MaterialTheme.typography.titleSmall,
                            color = if (r.row.isDebit) MaterialTheme.colorScheme.onSurface else Rf.colors.positive)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val kinds = if (r.row.isDebit) listOf(TxType.EXPENSE) else listOf(TxType.INCOME, TxType.REFUND)
                        kinds.forEach { t -> FilterChip(r.type == t, { rows[i] = r.copy(type = t) }, label = { Text(t.label) }) }
                    }
                    if (r.row.isDebit) Text("Moved money to your own account? Untick it and add it as a Transfer so both sides are recorded.",
                        style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    if (!r.type.isMovement) {
                        val kind = if (r.type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                        Selector("Category", snap.categories.filter { it.categoryKind() == kind && !it.archived }.map { it.id }, r.categoryId,
                            display = { id -> snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { id -> rows[i] = r.copy(categoryId = id) })
                    }
                }
            }
        }
    }
}
