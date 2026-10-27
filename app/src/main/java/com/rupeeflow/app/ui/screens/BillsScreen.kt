@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.data.BillView
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.SubscriptionEntity
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.data.freq
import com.rupeeflow.app.data.subStatus
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.AmountField
import com.rupeeflow.app.ui.components.ConfirmDialog
import com.rupeeflow.app.ui.components.DatePickerButton
import com.rupeeflow.app.ui.components.EmojiBadge
import com.rupeeflow.app.ui.components.EmptyState
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.Pill
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.dueText
import com.rupeeflow.app.ui.components.fullDate
import com.rupeeflow.app.ui.components.shortDate
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Frequency
import com.rupeeflow.core.Money
import com.rupeeflow.core.PaymentMethod
import com.rupeeflow.core.SubStatus
import java.time.LocalDate

private data class BillPreset(val emoji: String, val name: String, val amount: Long, val category: String, val freq: Frequency = Frequency.MONTHLY)

private val presets = listOf(
    BillPreset("🏋️", "Gym membership", 150000, "gym"),
    BillPreset("🏸", "Badminton coaching", 200000, "sports__badminton"),
    BillPreset("🏸", "Court booking", 60000, "sports__badminton", Frequency.WEEKLY),
    BillPreset("🎬", "Netflix", 64900, "subscriptions"),
    BillPreset("📦", "Amazon Prime", 149900, "subscriptions", Frequency.YEARLY),
    BillPreset("▶️", "YouTube Premium", 14900, "subscriptions"),
    BillPreset("🎵", "Spotify", 11900, "subscriptions"),
    BillPreset("📱", "Mobile recharge", 34900, "mobile"),
    BillPreset("🌐", "Wi-Fi / broadband", 79900, "internet"),
    BillPreset("🛡️", "Insurance premium", 1200000, "insurance", Frequency.YEARLY),
    BillPreset("☁️", "Cloud storage", 13000, "software"),
    BillPreset("📚", "Online course", 99900, "education"),
    BillPreset("🏦", "Loan EMI", 500000, "emi"),
    BillPreset("🏠", "Rent", 1500000, "rent"),
)

@Composable
fun BillsScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues) {
    val fmt = LocalMoney.current
    var filter by rememberSaveable { mutableStateOf(SubStatus.ACTIVE) }
    var edit by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var pay by remember { mutableStateOf<BillView?>(null) }

    val active = snap.bills.filter { it.sub.subStatus() == SubStatus.ACTIVE }
    val shown = snap.bills.filter { it.sub.subStatus() == filter }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Bills & subscriptions", style = MaterialTheme.typography.headlineSmall) }
        item {
            RfCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Per month", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        Text(fmt.f(active.sumOf { it.monthly }), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Per year", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        Text(fmt.f(active.sumOf { it.yearly }), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Due this cycle", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        Text(fmt.f(snap.billsDueThisCycle), style = MaterialTheme.typography.titleLarge, color = Rf.colors.warning)
                    }
                }
                Text("Counted as spent only when marked paid.",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubStatus.entries.forEach { s ->
                    val n = snap.bills.count { it.sub.subStatus() == s }
                    FilterChip(filter == s, { filter = s }, label = { Text("${s.label} ($n)") })
                }
            }
        }
        val overdue = shown.filter { it.overdue }
        val soon = shown.filter { !it.overdue && it.daysUntil <= 7 }
        val later = shown.filter { !it.overdue && it.daysUntil > 7 }
        if (filter == SubStatus.ACTIVE) {
            if (overdue.isNotEmpty()) { item { Text("Overdue", style = MaterialTheme.typography.titleSmall, color = Rf.colors.negative) }
                items(overdue, key = { it.sub.id }) { b -> BillCard(vm, b, snap, { pay = b }, { edit = b.sub; isNew = false }) } }
            if (soon.isNotEmpty()) { item { Text("Next 7 days", style = MaterialTheme.typography.titleSmall) }
                items(soon, key = { it.sub.id }) { b -> BillCard(vm, b, snap, { pay = b }, { edit = b.sub; isNew = false }) } }
            if (later.isNotEmpty()) { item { Text("Later", style = MaterialTheme.typography.titleSmall) }
                items(later, key = { it.sub.id }) { b -> BillCard(vm, b, snap, { pay = b }, { edit = b.sub; isNew = false }) } }
            val candidates = active.filter { it.skippedInARow >= 2 }
            if (candidates.isNotEmpty()) item {
                RfCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("💡 Still using these?", style = MaterialTheme.typography.titleSmall)
                    Text("You skipped ${candidates.joinToString { it.sub.name }} two or more times in a row. Cancelling could save " +
                        fmt.f(candidates.sumOf { it.yearly }) + " a year.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            items(shown, key = { it.sub.id }) { b -> BillCard(vm, b, snap, { pay = b }, { edit = b.sub; isNew = false }) }
        }
        if (shown.isEmpty()) item {
            EmptyState("🔁", if (filter == SubStatus.ACTIVE) "No bills yet" else "Nothing ${filter.label.lowercase()}",
                "Add a bill to get reminders before it's due.")
        }
        item {
            Text("Quick add", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                presets.forEach { p ->
                    FilledTonalButton(onClick = {
                        val due = snap.today.plusDays(7)
                        edit = SubscriptionEntity(FinanceRepository.newId(), p.name, p.emoji, p.amount, p.category,
                            snap.settings.defaultAccountId ?: snap.activeAccounts.firstOrNull()?.id, p.freq.name, 30, due.dayOfMonth, due.toEpochDay())
                        isNew = true
                    }) { Text("${p.emoji} ${p.name}") }
                }
                Button(onClick = {
                    val due = snap.today.plusDays(7)
                    edit = SubscriptionEntity(FinanceRepository.newId(), "", "🔁", 0, "subscriptions",
                        snap.settings.defaultAccountId ?: snap.activeAccounts.firstOrNull()?.id, Frequency.MONTHLY.name, 30, due.dayOfMonth, due.toEpochDay())
                    isNew = true
                }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Custom") }
            }
        }
    }

    edit?.let { s -> BillEditor(vm, snap, s, isNew) { edit = null } }
    pay?.let { b -> PayBillDialog(vm, snap, b) { pay = null } }
}

@Composable
private fun BillCard(vm: MainViewModel, b: BillView, snap: Snapshot, onPay: () -> Unit, onEdit: () -> Unit) {
    val fmt = LocalMoney.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val s = b.sub
    val status = s.subStatus()
    RfCard(onClick = onEdit, padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(s.emoji, MaterialTheme.colorScheme.primary, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                Text("${fmt.f(s.amount)} · ${s.freq().label}" + (if (s.freq() != Frequency.MONTHLY) " (≈${fmt.f(b.monthly)}/mo)" else "") +
                    (if (s.autopay) " · auto-pay" else ""), style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                if (status == SubStatus.ACTIVE) Text(dueText(b.daysUntil) + " · " + fullDate(b.nextDue), style = MaterialTheme.typography.labelMedium,
                    color = if (b.overdue) Rf.colors.negative else if (b.daysUntil <= 3) Rf.colors.warning else Rf.colors.subtle)
                b.lastPaid?.let { Text("Last paid for ${shortDate(it)} · ${b.paidCount} payment${if (b.paidCount == 1) "" else "s"} recorded", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    if (status == SubStatus.ACTIVE) {
                        DropdownMenuItem(text = { Text("Skip this payment") }, onClick = { menu = false; vm.write("Skipped — next due date moved") { vm.repo.skipBill(s.id, s.nextDue) } })
                        DropdownMenuItem(text = { Text("Pause") }, onClick = { menu = false; vm.write("Paused") { vm.repo.setSubscriptionStatus(s.id, SubStatus.PAUSED) } })
                        DropdownMenuItem(text = { Text("Cancel subscription") }, onClick = { menu = false; vm.write("Marked cancelled") { vm.repo.setSubscriptionStatus(s.id, SubStatus.CANCELLED) } })
                    } else {
                        DropdownMenuItem(text = { Text("Resume") }, onClick = {
                            menu = false
                            vm.write("Resumed") {
                                // Move a stale due date forward so a long pause doesn't show as hugely overdue.
                                var due = LocalDate.ofEpochDay(s.nextDue)
                                while (due.isBefore(snap.today)) due = com.rupeeflow.core.Schedule.next(due, s.freq(), s.intervalDays, s.anchorDay)
                                vm.repo.saveSubscription(s.copy(status = SubStatus.ACTIVE.name, nextDue = due.toEpochDay()))
                            }
                        })
                    }
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        if (status == SubStatus.ACTIVE && b.daysUntil <= 7) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPay) { Text("Mark paid") }
                TextButton(onClick = { vm.write("Skipped") { vm.repo.skipBill(s.id, s.nextDue) } }) { Text("Skip") }
            }
        }
        when (status) {
            SubStatus.PAUSED -> Pill("Paused", Rf.colors.warning, Modifier.padding(top = 6.dp))
            SubStatus.CANCELLED -> Pill("Cancelled", Rf.colors.subtle, Modifier.padding(top = 6.dp))
            else -> {}
        }
    }
    if (confirmDelete) ConfirmDialog("Delete ${s.name}?", "Past payments stay in your history.",
        "Delete", onConfirm = { vm.write("Deleted") { vm.repo.deleteSubscription(s.id) }; confirmDelete = false }, onDismiss = { confirmDelete = false }, destructive = true)
}

@Composable
private fun BillEditor(vm: MainViewModel, snap: Snapshot, s: SubscriptionEntity, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var emoji by remember { mutableStateOf(s.emoji) }
    var amount by remember { mutableStateOf(if (s.amount > 0) Money.toInput(s.amount) else "") }
    var freq by remember { mutableStateOf(s.freq()) }
    var interval by remember { mutableStateOf(s.intervalDays.toString()) }
    var due by remember { mutableStateOf(LocalDate.ofEpochDay(s.nextDue)) }
    var catId by remember { mutableStateOf(s.categoryId) }
    var accId by remember { mutableStateOf(s.accountId) }
    var autopay by remember { mutableStateOf(s.autopay) }
    var r7 by remember { mutableStateOf(s.remind7) }
    var r3 by remember { mutableStateOf(s.remind3) }
    var r1 by remember { mutableStateOf(s.remind1) }
    var notes by remember { mutableStateOf(s.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New bill" else "Edit bill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { if (it.length <= 4) emoji = it }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, keyboardOptions = com.rupeeflow.app.ui.components.NameKeys, modifier = Modifier.weight(1f))
                }
                AmountField(amount, { amount = it }, "Amount")
                Text("How often", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Frequency.entries.forEach { f -> FilterChip(freq == f, { freq = f }, label = { Text(f.label) }) }
                }
                if (freq == Frequency.CUSTOM_DAYS) OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, label = { Text("Every N days") }, singleLine = true, keyboardOptions = com.rupeeflow.app.ui.components.NumberKeys)
                DatePickerButton("Next due date", due, { due = it }, Modifier.fillMaxWidth())
                Selector("Category", snap.categories.filter { it.categoryKind() == CategoryKind.EXPENSE && it.parentId == null }.map { it.id }, catId,
                    display = { id -> snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { catId = it })
                Selector("Paid from", snap.activeAccounts.map { it.id }, accId,
                    display = { id -> snap.account(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { accId = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-pay / auto-debit")
                        Text("You'll confirm when it goes through.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    }
                    Switch(autopay, { autopay = it })
                }
                Text("Remind me", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(r7, { r7 = !r7 }, label = { Text("7 days") })
                    FilterChip(r3, { r3 = !r3 }, label = { Text("3 days") })
                    FilterChip(r1, { r1 = !r1 }, label = { Text("1 day") })
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, keyboardOptions = com.rupeeflow.app.ui.components.PlainTextKeys, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parse(amount)
                if (a == null || name.isBlank()) { vm.toast("Add a name and amount"); return@TextButton }
                vm.write(if (isNew) "Bill added" else "Bill updated") {
                    vm.repo.saveSubscription(s.copy(name = name, emoji = emoji.ifBlank { "🔁" }, amount = a, frequency = freq.name,
                        intervalDays = interval.toIntOrNull()?.coerceIn(1, 999) ?: 30, anchorDay = due.dayOfMonth, nextDue = due.toEpochDay(),
                        categoryId = catId, accountId = accId, autopay = autopay, remind7 = r7, remind3 = r3, remind1 = r1, notes = notes.ifBlank { null }))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Confirms a bill payment. If a matching expense already exists (typed earlier or imported),
 * the user can link it instead of creating a second record — no double counting.
 */
@Composable
fun PayBillDialog(vm: MainViewModel, snap: Snapshot, b: BillView, onDismiss: () -> Unit) {
    val fmt = LocalMoney.current
    val s = b.sub
    var amount by remember { mutableStateOf(Money.toInput(s.amount)) }
    var accId by remember { mutableStateOf(s.accountId ?: snap.settings.defaultAccountId ?: snap.activeAccounts.firstOrNull { it.accountType() == AccountType.BANK }?.id) }
    var date by remember { mutableStateOf(if (b.nextDue.isAfter(snap.today)) snap.today else b.nextDue.coerceAtMost(snap.today)) }
    var match by remember { mutableStateOf<TransactionEntity?>(null) }
    var useMatch by remember { mutableStateOf(true) }
    LaunchedEffect(s.id, s.nextDue) { match = vm.repo.findBillMatch(s, b.nextDue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.emoji} Pay ${s.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("For the payment due ${fullDate(b.nextDue)}", style = MaterialTheme.typography.bodySmall, color = Rf.colors.subtle)
                match?.let { m ->
                    RfCard(padding = 10, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("Found a matching entry", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("${fmt.f(m.amount)} · ${m.merchant ?: m.note ?: snap.category(m.categoryId)?.name ?: "Expense"} · ${shortDate(LocalDate.ofEpochDay(m.epochDay))}",
                            style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(useMatch, { useMatch = true }); Text("Link it (recommended)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(!useMatch, { useMatch = false }); Text("Record a new payment", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (match == null || !useMatch) {
                    AmountField(amount, { amount = it }, "Amount paid")
                    Selector("Paid from", snap.activeAccounts.map { it.id }, accId,
                        display = { id -> snap.account(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { accId = it })
                    DatePickerButton("Paid on", date, { date = it }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val link = match?.takeIf { useMatch }
                val a = link?.amount ?: Money.parse(amount)
                val acc = link?.accountId ?: accId
                if (a == null || acc == null) { vm.toast("Enter the amount and account"); return@TextButton }
                vm.write("${s.name} marked paid ✅") {
                    vm.repo.confirmBill(s.id, s.nextDue, a, acc, date, if (s.autopay) PaymentMethod.AUTOPAY.name else null, link?.id)
                }
                onDismiss()
            }) { Text("Confirm paid") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LocalDate.coerceAtMost(o: LocalDate): LocalDate = if (this.isAfter(o)) o else this
