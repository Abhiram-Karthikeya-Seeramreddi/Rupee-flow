@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.CategoryEntity
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.TemplateEntity
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.data.txType
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.DatePickDialog
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.MoneyFmt
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.friendlyDay
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Money
import com.rupeeflow.core.PaymentMethod
import com.rupeeflow.core.QuickParse
import com.rupeeflow.core.TxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the Add screen should open with. */
data class AddRequest(
    val editId: String? = null,
    val type: TxType = TxType.EXPENSE,
    val startVoice: Boolean = false,
    val prefillText: String? = null,
    val token: Long = System.nanoTime(),
)

fun txKindName(type: TxType): String = when (type) {
    TxType.EXPENSE -> "Expense"
    TxType.INCOME -> "Income"
    TxType.TRANSFER -> "Transfer"
    TxType.SAVINGS -> "Savings"
    TxType.INVESTMENT -> "Investment"
    TxType.REFUND -> "Refund"
    TxType.REIMBURSEMENT -> "Reimbursement"
    TxType.ADJUSTMENT -> "Balance adjustment"
}

/** "Expense added · ₹250 · Groceries" — tells the user exactly what happened. */
fun confirmationMessage(t: TransactionEntity, isEdit: Boolean, snap: Snapshot, fmt: MoneyFmt): String {
    val amt = fmt.f(kotlin.math.abs(t.amount))
    val cat = snap.category(t.categoryId)?.name
    val to = snap.account(t.toAccountId)?.name
    val future = if (t.epochDay > snap.today.toEpochDay()) " (scheduled)" else ""
    if (isEdit) return "${txKindName(t.txType())} updated · $amt" + (cat?.let { " · $it" } ?: "") + future
    val main = when (t.txType()) {
        TxType.EXPENSE -> "Expense added · $amt" + (cat?.let { " · $it" } ?: "")
        TxType.INCOME -> "Income added · $amt" + (cat?.takeIf { it != "Other income" }?.let { " · $it" } ?: "")
        TxType.TRANSFER -> "Transfer done · $amt" + (to?.let { " → $it" } ?: "")
        TxType.SAVINGS -> "Saved $amt" + (to?.let { " → $it" } ?: "")
        TxType.INVESTMENT -> "Invested $amt" + (to?.let { " → $it" } ?: "")
        TxType.REFUND -> "Refund added · $amt" + (cat?.let { " · $it" } ?: "")
        TxType.REIMBURSEMENT -> "Reimbursement added · $amt"
        TxType.ADJUSTMENT -> "Balance adjusted · ${fmt.f(t.amount, sign = true)}"
    }
    return main + future
}

@Composable
fun AddTransactionScreen(vm: MainViewModel, snap: Snapshot, request: AddRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fmt = LocalMoney.current

    val editing = remember(request.token) { request.editId?.let { id -> snap.transactions.firstOrNull { it.id == id } } }
    val accounts = snap.activeAccounts.ifEmpty { snap.accounts }
    val defaultAccount = accounts.firstOrNull { it.id == snap.settings.defaultAccountId }
        ?: accounts.firstOrNull { it.accountType() == AccountType.BANK } ?: accounts.firstOrNull()

    var type by rememberSaveable(request.token) { mutableStateOf(editing?.txType() ?: request.type) }
    var amountText by rememberSaveable(request.token) { mutableStateOf(editing?.let { Money.toInput(kotlin.math.abs(it.amount)) } ?: "") }
    var negativeAdjust by rememberSaveable(request.token) { mutableStateOf((editing?.amount ?: 0) < 0) }
    var categoryId by rememberSaveable(request.token) { mutableStateOf(editing?.categoryId) }
    var accountId by rememberSaveable(request.token) { mutableStateOf(editing?.accountId ?: defaultAccount?.id) }
    var toAccountId by rememberSaveable(request.token) { mutableStateOf(editing?.toAccountId) }
    var payment by rememberSaveable(request.token) { mutableStateOf(editing?.payment ?: snap.settings.defaultPayment) }
    var day by rememberSaveable(request.token) { mutableStateOf(editing?.epochDay ?: snap.today.toEpochDay()) }
    var note by rememberSaveable(request.token) { mutableStateOf(editing?.note ?: "") }
    var merchant by rememberSaveable(request.token) { mutableStateOf(editing?.merchant ?: "") }
    var tags by rememberSaveable(request.token) { mutableStateOf(editing?.tags ?: "") }
    var receiptPath by rememberSaveable(request.token) { mutableStateOf(editing?.receiptPath) }
    var recurring by rememberSaveable(request.token) { mutableStateOf(editing?.isRecurring ?: false) }
    var goalId by rememberSaveable(request.token) { mutableStateOf(editing?.goalId) }
    var saveTemplate by rememberSaveable(request.token) { mutableStateOf(false) }
    var magic by rememberSaveable(request.token) { mutableStateOf(request.prefillText ?: "") }
    var magicHint by remember(request.token) { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable(request.token) {
        mutableStateOf(editing != null && (editing.note != null || editing.merchant != null || editing.tags.isNotBlank() || editing.receiptPath != null))
    }
    var showDate by remember { mutableStateOf(false) }
    var moreTypes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var duplicateOf by remember { mutableStateOf<TransactionEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    val newId = remember(request.token) { editing?.id ?: FinanceRepository.newId() }
    val amountFocus = remember { FocusRequester() }

    val amount: Long? = Money.parse(amountText)
    val dirty = editing == null && (amountText.isNotEmpty() || note.isNotEmpty() || receiptPath != null)

    fun applyParsed(text: String) {
        if (text.isBlank()) return
        val p = QuickParse.parse(text, snap.keywords, snap.rules)
        p.amount?.let { amountText = Money.toInput(it) }
        if (p.type != type && (p.type == TxType.INCOME || p.type == TxType.REFUND)) type = p.type
        p.categoryId?.let { id -> if (snap.category(id) != null) categoryId = id }
        p.payment?.let { pm ->
            payment = pm.name
            accountForPayment(pm, accounts)?.let { accountId = it.id }
        }
        if (p.note.isNotBlank()) note = p.note
        day = snap.today.plusDays(p.dayOffset.toLong()).toEpochDay()
        magicHint = buildList {
            add(p.amount?.let { fmt.f(it) } ?: "no amount")
            p.categoryId?.let { id -> snap.category(id)?.name?.let { add(it) } }
            p.payment?.let { add(it.label) }
            if (p.dayOffset != 0) add(friendlyDay(snap.today.plusDays(p.dayOffset.toLong()), snap.today))
        }.joinToString(" · ")
        keyboard?.hide()
        focus.clearFocus()
    }

    val voice = rememberVoiceController(
        onResult = { text -> magic = text; applyParsed(text) },
        onMessage = { msg, settings -> if (settings) vm.toast(msg, "Settings") { ctx.openAppSettings() } else vm.toast(msg) },
    )
    val receipts = rememberReceiptPicker(
        txId = newId,
        onSaved = { path -> receiptPath = path },
        onError = { vm.toast(it) },
    )

    LaunchedEffect(request.token) {
        // Let the screen settle before showing a keyboard or permission prompt.
        delay(250)
        when {
            request.startVoice -> { keyboard?.hide(); voice.start() }
            request.prefillText != null -> applyParsed(request.prefillText)
            editing == null -> runCatching { amountFocus.requestFocus(); keyboard?.show() }
        }
    }

    fun close() {
        voice.release()
        keyboard?.hide()
        onClose()
    }
    BackHandler { if (dirty) confirmDiscard = true else close() }

    fun buildEntity(): TransactionEntity? {
        val a = amount ?: return null
        val acc = accountId ?: return null
        val signed = if (type == TxType.ADJUSTMENT && negativeAdjust) -a else a
        return TransactionEntity(
            id = newId, type = type.name, amount = signed, accountId = acc,
            toAccountId = if (type.isMovement) toAccountId else null,
            categoryId = if (type.needsCategory) categoryId else null,
            epochDay = day, minuteOfDay = editing?.minuteOfDay ?: FinanceRepository.nowMinute(),
            payment = if (type == TxType.EXPENSE || type == TxType.INCOME || type == TxType.REFUND) payment else null,
            merchant = merchant.ifBlank { null }, note = note.ifBlank { null }, tags = tags,
            receiptPath = receiptPath, subscriptionId = editing?.subscriptionId,
            goalId = if (type.isMovement) goalId else null, isRecurring = recurring,
            importFingerprint = editing?.importFingerprint,
            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
        )
    }

    fun doSave(t: TransactionEntity) {
        if (saving) return
        saving = true
        val message = confirmationMessage(t, editing != null, snap, fmt)
        vm.write {
            vm.repo.saveTransaction(t)
            if (saveTemplate) {
                vm.repo.saveTemplate(TemplateEntity(FinanceRepository.newId(),
                    (t.merchant ?: t.note ?: snap.category(t.categoryId)?.name ?: type.label).take(24),
                    t.type, kotlin.math.abs(t.amount), t.categoryId, t.accountId, t.payment, t.merchant))
            }
            if (type == TxType.EXPENSE) vm.c.settings.update { it.copy(defaultPayment = payment, defaultAccountId = t.accountId) }
            vm.toast(message)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        close()
    }

    fun trySave() {
        when {
            amount == null -> { vm.toast("Enter an amount"); return }
            accountId == null -> { vm.toast("Add an account first"); return }
            type == TxType.EXPENSE && categoryId == null -> { vm.toast("Pick a category"); return }
            type.isMovement && (toAccountId == null || toAccountId == accountId) -> { vm.toast("Pick where the money goes"); return }
        }
        val entity = buildEntity() ?: return
        if (editing == null) {
            scope.launch {
                val dup = vm.duplicateOf(entity)
                if (dup != null) duplicateOf = dup else doSave(entity)
            }
        } else doSave(entity)
    }

    val amountColor = when (type) {
        TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> Rf.colors.positive
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding().imePadding(),
    ) {
        // ---- Top bar: close · type · delete
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (dirty) confirmDiscard = true else close() }) { Icon(Icons.Default.Close, "Close") }
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val main = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
                items(main) { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                item {
                    Box {
                        FilterChip(selected = type !in main, onClick = { moreTypes = true },
                            label = { Text(if (type in main) "More" else type.label) },
                            trailingIcon = { Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) })
                        DropdownMenu(expanded = moreTypes, onDismissRequest = { moreTypes = false }) {
                            listOf(TxType.SAVINGS, TxType.INVESTMENT, TxType.REFUND, TxType.REIMBURSEMENT, TxType.ADJUSTMENT).forEach { t ->
                                DropdownMenuItem(text = { Text(t.label) }, onClick = { type = t; moreTypes = false })
                            }
                        }
                    }
                }
            }
            if (editing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---- Amount: real text field with the phone's number keyboard
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).clickable { runCatching { amountFocus.requestFocus() } },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type == TxType.ADJUSTMENT) {
                    TextButton(onClick = { negativeAdjust = !negativeAdjust }) { Text(if (negativeAdjust) "−" else "+", fontSize = 28.sp) }
                }
                Text("₹", style = MaterialTheme.typography.displaySmall, color = amountColor.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = amountText,
                    onValueChange = { v -> cleanAmount(v)?.let { amountText = it } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displayMedium.copy(color = amountColor, textAlign = TextAlign.Start),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }),
                    modifier = Modifier.width(IntrinsicWidthFor(amountText)).focusRequester(amountFocus)
                        .semantics { contentDescription = "Amount in rupees" },
                    decorationBox = { inner ->
                        Box {
                            if (amountText.isEmpty()) Text("0", style = MaterialTheme.typography.displayMedium, color = amountColor.copy(alpha = 0.25f))
                            inner()
                        }
                    },
                )
            }
            if ((amount ?: 0) >= 100_000) {
                Text(fmt.f(amount!!), style = MaterialTheme.typography.labelLarge, color = Rf.colors.subtle,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // ---- Say or type it
            val listening = voice.state as? VoiceState.Listening
            if (listening != null) {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(listening.partial.ifBlank { "Listening… say “250 biryani UPI”" }, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { voice.stop() }) { Icon(Icons.Default.Stop, "Stop listening") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = magic,
                    onValueChange = { magic = it },
                    placeholder = { Text("Type or say: 250 biryani upi") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (magic.isNotBlank()) TextButton(onClick = { applyParsed(magic) }) { Text("Fill") }
                            IconButton(onClick = { keyboard?.hide(); voice.start() }) { Icon(Icons.Default.Mic, "Add by voice") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyParsed(magic) }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            magicHint?.let {
                Text("Filled: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp))
            }

            // ---- Quick picks (new expenses only)
            if (editing == null && type == TxType.EXPENSE && (snap.quickPicks.isNotEmpty() || snap.templates.isNotEmpty())) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snap.templates.filter { it.type == TxType.EXPENSE.name }) { t ->
                        QuickChip("⭐ ${t.label}" + (t.amount?.let { " · " + fmt.f(it) } ?: "")) {
                            t.amount?.let { amountText = Money.toInput(it) }
                            t.categoryId?.let { categoryId = it }
                            t.accountId?.let { id -> if (accounts.any { it.id == id }) accountId = id }
                            t.payment?.let { payment = it }
                            t.merchant?.let { merchant = it }
                        }
                    }
                    items(snap.quickPicks) { p ->
                        QuickChip("${snap.category(p.categoryId)?.emoji ?: "🧾"} ${p.label.take(16)} · ${fmt.f(p.amount)}") {
                            amountText = Money.toInput(p.amount)
                            categoryId = p.categoryId
                            if (accounts.any { it.id == p.accountId }) accountId = p.accountId
                            p.payment?.let { payment = it }
                            note = p.note ?: ""
                            merchant = p.merchant ?: ""
                        }
                    }
                }
            }

            // ---- Category
            if (type.needsCategory) {
                val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                val roots = snap.categories.filter { it.parentId == null && !it.archived && it.categoryKind() == kind }
                val order = (snap.recentCategoryIds.mapNotNull { id -> snap.root(id) } + roots.filter { it.favorite }.map { it.id }).distinct()
                val sorted = roots.sortedWith(compareBy<CategoryEntity> { c -> order.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { it.sortOrder })
                val selectedRoot = snap.root(categoryId)
                SectionLabelSmall("Category")
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(170.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(sorted, key = { it.id }) { c -> CategoryTile(c, selected = selectedRoot == c.id) { categoryId = c.id } }
                }
                val subs = if (selectedRoot == null) emptyList() else snap.categories.filter { it.parentId == selectedRoot && !it.archived }
                if (subs.isNotEmpty()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(subs) { s ->
                            FilterChip(selected = categoryId == s.id, onClick = { categoryId = if (categoryId == s.id) selectedRoot else s.id },
                                label = { Text("${s.emoji} ${s.name}") })
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                // ---- Accounts
                SectionLabelSmall(if (type.isMovement) "From" else "Account", inner = true)
                AccountChips(accounts, accountId, snap) { accountId = it }
                if (type.isMovement) {
                    SectionLabelSmall("To", inner = true)
                    val savingsOnly = type == TxType.SAVINGS || type == TxType.INVESTMENT
                    val targets = accounts.filter { it.id != accountId }.filter { a ->
                        !savingsOnly || a.accountType() == AccountType.SAVINGS || a.accountType() == AccountType.INVESTMENT ||
                            (!a.includeInSpendable && a.accountType() != AccountType.CREDIT_CARD)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f, fill = false)) { AccountChips(targets, toAccountId, snap) { toAccountId = it } }
                        if (savingsOnly && targets.none { it.accountType() == AccountType.SAVINGS }) {
                            if (targets.isNotEmpty()) Spacer(Modifier.width(6.dp))
                            FilterChip(selected = false, onClick = {
                                scope.launch { toAccountId = vm.repo.ensureSavingsAccount(); vm.toast("Savings account created") }
                            }, label = { Text("+ Savings account") })
                        }
                    }
                    if (!savingsOnly && targets.isEmpty()) {
                        Text("Add another account to move money.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (snap.goals.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Selector("For goal (optional)", listOf<String?>(null) + snap.goals.map { it.goal.id }, goalId,
                            display = { id -> id?.let { g -> snap.goals.firstOrNull { it.goal.id == g }?.goal?.let { "${it.emoji} ${it.name}" } } ?: "No goal" },
                            onSelect = { goalId = it })
                    }
                }

                // ---- Paid with
                if (type == TxType.EXPENSE || type == TxType.INCOME || type == TxType.REFUND) {
                    SectionLabelSmall(if (type == TxType.EXPENSE) "Paid with" else "Received via", inner = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(PaymentMethod.entries.toList()) { pm ->
                            FilterChip(selected = payment == pm.name, onClick = {
                                payment = pm.name
                                accountForPayment(pm, accounts)?.let { accountId = it.id }
                            }, label = { Text(pm.label) })
                        }
                    }
                }

                // ---- Date
                SectionLabelSmall("Date", inner = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val d = LocalDate.ofEpochDay(day)
                    val other = d != snap.today && d != snap.today.minusDays(1)
                    FilterChip(selected = d == snap.today, onClick = { day = snap.today.toEpochDay() }, label = { Text("Today") })
                    FilterChip(selected = d == snap.today.minusDays(1), onClick = { day = snap.today.minusDays(1).toEpochDay() }, label = { Text("Yesterday") })
                    FilterChip(selected = other, onClick = { showDate = true }, label = { Text(if (other) friendlyDay(d, snap.today) else "Pick date") })
                }
                if (LocalDate.ofEpochDay(day).isAfter(snap.today)) {
                    Text("Scheduled — counts from that day.", style = MaterialTheme.typography.labelMedium, color = Rf.colors.warning)
                }

                // ---- More details
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable { showDetails = !showDetails }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Note, receipt & more", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (receiptPath != null && !showDetails) Text("📎 ", style = MaterialTheme.typography.labelLarge)
                    Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                AnimatedVisibility(showDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                        OutlinedTextField(merchant, { merchant = it }, label = { Text("Shop / person") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                        OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, placeholder = { Text("trip, office") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))

                        Text("Receipt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { keyboard?.hide(); receipts.takePhoto() }, enabled = !receipts.busy()) {
                                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Camera")
                            }
                            OutlinedButton(onClick = { keyboard?.hide(); receipts.pickFromGallery() }, enabled = !receipts.busy()) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery")
                            }
                            if (receipts.busy()) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        receiptPath?.let { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ReceiptThumb(p)
                                Spacer(Modifier.width(8.dp))
                                Text("Tap to view", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle, modifier = Modifier.weight(1f))
                                TextButton(onClick = { receiptPath = null }) { Text("Remove") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeats every month", modifier = Modifier.weight(1f)); Switch(recurring, { recurring = it })
                        }
                        if (editing == null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Save as quick template", modifier = Modifier.weight(1f)); Switch(saveTemplate, { saveTemplate = it })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ---- Save (stays above the keyboard)
        Button(
            onClick = { trySave() },
            enabled = amount != null && !saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                if (amount == null) "Enter amount" else "Save ${fmt.f(amount)}" + (snap.category(categoryId)?.takeIf { type.needsCategory }?.let { " · ${it.name}" } ?: ""),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showDate) DatePickDialog(LocalDate.ofEpochDay(day), { showDate = false }) { day = it.toEpochDay(); showDate = false }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("You can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTransaction(editing.id, "${txKindName(editing.txType())} deleted")
                    close()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this entry?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; close() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    duplicateOf?.let { d ->
        AlertDialog(
            onDismissRequest = { duplicateOf = null },
            title = { Text("Already added?") },
            text = { Text("You saved ${fmt.f(kotlin.math.abs(d.amount))}" + (snap.category(d.categoryId)?.let { " for ${it.name}" } ?: "") + " a few minutes ago.") },
            confirmButton = { TextButton(onClick = { val t = buildEntity(); duplicateOf = null; if (t != null) doSave(t) }) { Text("Add again") } },
            dismissButton = { TextButton(onClick = { duplicateOf = null; close() }) { Text("Don't add") } },
        )
    }
}

/** Accepts digits and one decimal point with up to 2 decimals; returns null to reject the edit. */
internal fun cleanAmount(v: String): String? {
    val t = v.replace(",", "").replace(" ", "")
    if (t.isEmpty()) return ""
    if (!Regex("^\\d{0,10}(\\.\\d{0,2})?$").matches(t)) return null
    return if (t.startsWith(".")) "0$t" else t
}

/** Width that grows with the typed amount so the ₹ sign stays next to the digits. */
private fun IntrinsicWidthFor(text: String) = (maxOf(1, text.length) * 34 + 12).dp

@Composable
private fun SectionLabelSmall(text: String, inner: Boolean = false) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = if (inner) 0.dp else 16.dp, top = 10.dp, bottom = 4.dp))
}

private fun accountForPayment(pm: PaymentMethod, accounts: List<AccountEntity>): AccountEntity? = when (pm) {
    PaymentMethod.CASH -> accounts.firstOrNull { it.accountType() == AccountType.CASH }
    PaymentMethod.CREDIT_CARD -> accounts.firstOrNull { it.accountType() == AccountType.CREDIT_CARD }
    PaymentMethod.WALLET -> accounts.firstOrNull { it.accountType() == AccountType.WALLET }
    PaymentMethod.UPI, PaymentMethod.DEBIT_CARD, PaymentMethod.NET_BANKING, PaymentMethod.AUTOPAY ->
        accounts.firstOrNull { it.accountType() == AccountType.BANK }
    PaymentMethod.OTHER -> null
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Surface(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

@Composable
private fun CategoryTile(c: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val color = Color(c.color)
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, color, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics { contentDescription = c.name + if (selected) ", selected" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(c.emoji, fontSize = 24.sp)
        Text(c.name.substringBefore(" &").substringBefore(" and"), style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AccountChips(accounts: List<AccountEntity>, selected: String?, snap: Snapshot, onSelect: (String) -> Unit) {
    val fmt = LocalMoney.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(accounts) { a ->
            FilterChip(selected = selected == a.id, onClick = { onSelect(a.id) },
                label = { Text("${a.emoji} ${a.name} · ${fmt.c(snap.balances[a.id] ?: a.openingBalance)}", maxLines = 1) })
        }
    }
}
