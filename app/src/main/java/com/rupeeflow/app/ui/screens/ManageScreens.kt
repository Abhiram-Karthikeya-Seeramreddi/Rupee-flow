@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.CategoryEntity
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.AmountField
import com.rupeeflow.app.ui.components.ConfirmDialog
import com.rupeeflow.app.ui.components.EmojiBadge
import com.rupeeflow.app.ui.components.KeyValueRow
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.Pill
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Money

/* ======================= Accounts ======================= */

fun defaultEmoji(t: AccountType) = when (t) {
    AccountType.BANK -> "🏦"; AccountType.CASH -> "💵"; AccountType.WALLET -> "👛"
    AccountType.CREDIT_CARD -> "💳"; AccountType.SAVINGS -> "🐷"; AccountType.INVESTMENT -> "📈"
}

@Composable
fun AccountsScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onBack: () -> Unit) {
    val fmt = LocalMoney.current
    var edit by remember { mutableStateOf<AccountEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var reconcile by remember { mutableStateOf<AccountEntity?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    val p = snap.position
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Accounts", onBack) }
        item {
            RfCard {
                KeyValueRow("Spendable (bank, cash, wallets)", fmt.f(p.spendable), bold = true)
                KeyValueRow("Savings", fmt.f(p.savings), MaterialTheme.colorScheme.primary)
                KeyValueRow("Investments", fmt.f(p.investments), MaterialTheme.colorScheme.primary)
                if (p.cardDues > 0) KeyValueRow("Credit card owed", "−" + fmt.f(p.cardDues), Rf.colors.negative)
                KeyValueRow("Net worth", fmt.f(p.netWorth), bold = true)
                Text("UPI isn't an account — UPI payments come out of the bank account they're linked to.",
                    style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
            }
        }
        val list = snap.accounts.filter { showArchived || !it.archived }
        items(list, key = { it.id }) { a ->
            val bal = snap.balances[a.id] ?: a.openingBalance
            val type = a.accountType()
            RfCard(onClick = { edit = a; isNew = false }, padding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(a.emoji, MaterialTheme.colorScheme.primary, 44)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall)
                        Text(type.label + if (type != AccountType.CREDIT_CARD) (if (a.includeInSpendable) " · spendable" else " · set aside") else "",
                            style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (type == AccountType.CREDIT_CARD) {
                            Text(fmt.f(maxOf(0, -bal)), style = MaterialTheme.typography.titleMedium, color = if (bal < 0) Rf.colors.negative else MaterialTheme.colorScheme.onSurface)
                            Text("owed", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        } else Text(fmt.f(bal), style = MaterialTheme.typography.titleMedium, color = if (bal < 0) Rf.colors.negative else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (a.archived) Pill("Archived", Rf.colors.subtle)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { reconcile = a }) { Text("Reconcile") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    edit = AccountEntity(FinanceRepository.newId(), "", AccountType.BANK.name, 0, true, "🏦", sortOrder = snap.accounts.size)
                    isNew = true
                }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add account") }
                if (snap.accounts.any { it.archived }) OutlinedButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide archived" else "Show archived") }
            }
        }
    }
    edit?.let { a -> AccountEditor(vm, snap, a, isNew) { edit = null } }
    reconcile?.let { a -> ReconcileDialog(vm, snap, a) { reconcile = null } }
}

@Composable
fun AccountEditor(vm: MainViewModel, snap: Snapshot, a: AccountEntity, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var type by remember { mutableStateOf(a.accountType()) }
    var emoji by remember { mutableStateOf(a.emoji) }
    // Credit cards: users think in "amount owed" (positive); we store it as a negative balance.
    var opening by remember { mutableStateOf(if (a.openingBalance != 0L) Money.toInput(kotlin.math.abs(a.openingBalance)) else "") }
    var openingNegative by remember { mutableStateOf(a.openingBalance < 0 && a.accountType() != AccountType.CREDIT_CARD) }
    var spendable by remember { mutableStateOf(a.includeInSpendable) }
    var archived by remember { mutableStateOf(a.archived) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(type == t, {
                            if (emoji == defaultEmoji(type)) emoji = defaultEmoji(t)
                            type = t; spendable = t.spendableByDefault
                        }, label = { Text("${defaultEmoji(t)} ${t.label}") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { if (it.length <= 4) emoji = it }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. HDFC Savings)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                AmountField(opening, { opening = it }, if (type == AccountType.CREDIT_CARD) "Amount owed when you start" else "Balance when you start")
                if (type != AccountType.CREDIT_CARD) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Balance is negative (overdrawn)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(openingNegative, { openingNegative = it })
                }
                if (type != AccountType.CREDIT_CARD) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count as spendable")
                        Text("Off for money you've set aside (emergency fund, separate savings).", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    }
                    Switch(spendable, { spendable = it })
                }
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Archived (hidden)", modifier = Modifier.weight(1f)); Switch(archived, { archived = it }) }
                    Text("Changing the starting balance shifts all balances. To match your bank today, use Reconcile instead.",
                        style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = if (opening.isBlank()) 0L else Money.parse(opening) ?: run { vm.toast("Check the balance"); return@TextButton }
                val signed = when {
                    type == AccountType.CREDIT_CARD -> -amt
                    openingNegative -> -amt
                    else -> amt
                }
                vm.write(if (isNew) "Account added" else "Account updated") {
                    vm.repo.saveAccount(a.copy(name = name, type = type.name, emoji = emoji.ifBlank { defaultEmoji(type) }, openingBalance = signed,
                        includeInSpendable = if (type == AccountType.CREDIT_CARD) false else spendable, archived = archived))
                    if (snap.settings.defaultAccountId == null && type == AccountType.BANK) vm.c.settings.update { it.copy(defaultAccountId = a.id) }
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) ConfirmDialog("Delete ${a.name}?", "If it has transactions it will be archived instead, so your history stays correct.", "Continue",
        onConfirm = {
            confirmDelete = false
            vm.write { vm.toast(vm.repo.deleteOrArchiveAccount(a.id)) }
            onDismiss()
        }, onDismiss = { confirmDelete = false }, destructive = true)
}

@Composable
private fun ReconcileDialog(vm: MainViewModel, snap: Snapshot, a: AccountEntity, onDismiss: () -> Unit) {
    val fmt = LocalMoney.current
    val recorded = snap.balances[a.id] ?: a.openingBalance
    val isCard = a.accountType() == AccountType.CREDIT_CARD
    var actual by remember { mutableStateOf("") }
    val parsed = if (actual.isBlank()) null else Money.parse(actual) ?: if (actual.trim() == "0") 0L else null
    val actualSigned = parsed?.let { if (isCard) -it else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reconcile ${a.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RupeeFlow shows ${if (isCard) fmt.f(maxOf(0, -recorded)) + " owed" else fmt.f(recorded)}. What does your ${if (isCard) "card statement" else "bank/wallet"} show right now?")
                AmountField(actual, { actual = it }, if (isCard) "Actual amount owed" else "Actual balance")
                actualSigned?.let { act ->
                    val d = act - recorded
                    Text(if (d == 0L) "Already matches 👍" else "We'll add a ${fmt.f(d, sign = true)} adjustment dated today. Your past entries stay untouched.",
                        style = MaterialTheme.typography.bodySmall, color = if (d == 0L) Rf.colors.positive else Rf.colors.warning)
                }
                Text("Tip: a gap usually means a missed expense. Check Activity first if you can.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
            }
        },
        confirmButton = {
            TextButton(enabled = actualSigned != null, onClick = {
                val act = actualSigned ?: return@TextButton
                vm.write("Balance reconciled") { vm.repo.reconcile(a.id, act, recorded) }
                onDismiss()
            }) { Text("Reconcile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* ======================= Categories ======================= */

private val palette = listOf(0xFF2E9E6B, 0xFFF2784B, 0xFF4F7CF7, 0xFFE0A100, 0xFF8E6CEF, 0xFF26A7C9, 0xFF17B890, 0xFFE5534B,
    0xFFD65DB1, 0xFF7A5AF8, 0xFFFF6F91, 0xFF00A6A6, 0xFF5C6BC0, 0xFF607D8B, 0xFF795548, 0xFF90A4AE)

@Composable
fun CategoriesScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onBack: () -> Unit) {
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var edit by remember { mutableStateOf<CategoryEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val roots = snap.categories.filter { it.parentId == null && it.categoryKind() == kind && (showArchived || !it.archived) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Categories", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == CategoryKind.EXPENSE, { kind = CategoryKind.EXPENSE }, label = { Text("Spending") })
                FilterChip(kind == CategoryKind.INCOME, { kind = CategoryKind.INCOME }, label = { Text("Income") })
                FilterChip(showArchived, { showArchived = !showArchived }, label = { Text("Show archived") })
            }
            Text("Transfers, savings and investments aren't categories — they're transaction types, so they never count as spending.",
                style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(top = 6.dp))
        }
        items(roots, key = { it.id }) { c ->
            val subs = snap.categories.filter { it.parentId == c.id && (showArchived || !it.archived) }
            RfCard(padding = 10) {
                CategoryLine(c) { edit = c; isNew = false }
                subs.forEach { s -> Row { Spacer(Modifier.width(28.dp)); Box(Modifier.weight(1f)) { CategoryLine(s) { edit = s; isNew = false } } } }
                TextButton(onClick = {
                    edit = CategoryEntity(FinanceRepository.newId(), "", c.emoji, c.color, c.kind, c.id, c.essential, c.fixed, sortOrder = c.sortOrder + 1)
                    isNew = true
                }) { Text("+ Sub-category") }
            }
        }
        item {
            Button(onClick = {
                edit = CategoryEntity(FinanceRepository.newId(), "", "🏷️", palette.random(), kind.name, sortOrder = 1000)
                isNew = true
            }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New category") }
        }
    }
    edit?.let { c -> CategoryEditor(vm, snap, c, isNew) { edit = null } }
}

@Composable
private fun CategoryLine(c: CategoryEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiBadge(c.emoji, Color(c.color), 36)
        Spacer(Modifier.width(10.dp))
        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (c.favorite) Text("⭐")
        if (c.essential) Pill("essential", Rf.colors.positive, Modifier.padding(start = 4.dp))
        if (c.fixed) Pill("fixed", MaterialTheme.colorScheme.primary, Modifier.padding(start = 4.dp))
        if (c.archived) Pill("archived", Rf.colors.subtle, Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun CategoryEditor(vm: MainViewModel, snap: Snapshot, c: CategoryEntity, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var emoji by remember { mutableStateOf(c.emoji) }
    var color by remember { mutableStateOf(c.color) }
    var essential by remember { mutableStateOf(c.essential) }
    var fixed by remember { mutableStateOf(c.fixed) }
    var favorite by remember { mutableStateOf(c.favorite) }
    var archived by remember { mutableStateOf(c.archived) }
    var deleteAsk by remember { mutableStateOf(false) }
    var moveTo by remember { mutableStateOf<String?>(if (c.categoryKind() == CategoryKind.INCOME) "other_income" else "misc") }
    val usage = snap.transactions.count { it.categoryId == c.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) (if (c.parentId != null) "New sub-category" else "New category") else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { if (it.length <= 4) emoji = it }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { p ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(p))
                            .then(if (p == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = p })
                    }
                }
                if (c.categoryKind() == CategoryKind.EXPENSE) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Essential (needs, not wants)", Modifier.weight(1f)); Switch(essential, { essential = it }) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Fixed cost (same every month)", Modifier.weight(1f)); Switch(fixed, { fixed = it }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Favourite (shown first)", Modifier.weight(1f)); Switch(favorite, { favorite = it }) }
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Archive")
                            Text("Hidden from pickers; history keeps it.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        }
                        Switch(archived, { archived = it })
                    }
                    Text("$usage transaction${if (usage == 1) "" else "s"} use this category.", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                    if (!deleteAsk) TextButton(onClick = { deleteAsk = true }) { Text("Delete…", color = MaterialTheme.colorScheme.error) }
                    else {
                        if (usage > 0) Selector("Move its transactions to", snap.categories.filter { it.id != c.id && it.categoryKind() == c.categoryKind() && !it.archived }.map { it.id },
                            moveTo, display = { id -> snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { moveTo = it })
                        Row {
                            TextButton(onClick = { deleteAsk = false }) { Text("Keep") }
                            TextButton(onClick = {
                                vm.write("Category deleted") { vm.repo.deleteCategory(c.id, moveTo) }
                                onDismiss()
                            }) { Text("Delete for good", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { vm.toast("Give it a name"); return@TextButton }
                vm.write(if (isNew) "Category added" else "Saved") {
                    vm.repo.saveCategory(c.copy(name = name, emoji = emoji.ifBlank { "🏷️" }, color = color, essential = essential, fixed = fixed,
                        favorite = favorite, archived = archived))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* ======================= Rules ======================= */

@Composable
fun RulesScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onBack: () -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>("restaurants") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Auto-categorise", onBack) }
        item {
            Text("When a note, merchant, SMS or bank row contains a keyword, RupeeFlow suggests the category. It learns from what you save, and you can add your own.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            RfCard {
                OutlinedTextField(keyword, { keyword = it }, label = { Text("Keyword (e.g. SWIGGY, ramu mess)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.padding(4.dp))
                Selector("Category", snap.categories.filter { !it.archived }.map { it.id }, cat,
                    display = { id -> snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { cat = it })
                TextButton(onClick = {
                    val c = cat ?: return@TextButton
                    vm.write("Rule added") { vm.repo.saveRule(keyword, c) }
                    keyword = ""
                }) { Text("Add rule") }
            }
        }
        items(snap.rules.entries.sortedBy { it.key }.toList(), key = { it.key }) { (k, id) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("“$k”", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "?", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.write { vm.repo.deleteRule(k) } }) { Icon(Icons.Default.Delete, "Delete rule") }
            }
        }
        if (snap.rules.isEmpty()) item { Text("No learned rules yet. Built-in keywords (Swiggy, Uber, petrol, Netflix…) already work.", color = Rf.colors.subtle) }
    }
}
