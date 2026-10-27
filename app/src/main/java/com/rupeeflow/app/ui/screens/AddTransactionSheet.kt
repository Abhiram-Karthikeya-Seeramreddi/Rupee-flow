@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.friendlyDay
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Money
import com.rupeeflow.core.PaymentMethod
import com.rupeeflow.core.QuickParse
import com.rupeeflow.core.TxType
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** What the sheet should open with. */
data class AddRequest(
    val editId: String? = null,
    val type: TxType = TxType.EXPENSE,
    val startVoice: Boolean = false,
    val prefillText: String? = null,
    val token: Long = System.nanoTime(),
)

@Composable
fun AddTransactionSheet(vm: MainViewModel, snap: Snapshot, request: AddRequest, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
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
    var showDetails by rememberSaveable(request.token) { mutableStateOf(editing != null && (editing.note != null || editing.tags.isNotBlank())) }
    var showDate by remember { mutableStateOf(false) }
    var moreTypes by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var duplicateOf by remember { mutableStateOf<TransactionEntity?>(null) }
    val newId = remember(request.token) { editing?.id ?: FinanceRepository.newId() }

    val amount: Long? = Money.parse(amountText)

    fun applyParsed(text: String) {
        if (text.isBlank()) return
        val p = QuickParse.parse(text, snap.keywords, snap.rules)
        p.amount?.let { amountText = Money.toInput(it) }
        if (p.type != type && (p.type == TxType.INCOME || p.type == TxType.REFUND)) type = p.type
        p.categoryId?.let { categoryId = it }
        p.payment?.let { pm ->
            payment = pm.name
            accountForPayment(pm, accounts)?.let { accountId = it.id }
        }
        if (p.note.isNotBlank()) note = p.note
        day = snap.today.plusDays(p.dayOffset.toLong()).toEpochDay()
        magicHint = buildString {
            append("Filled: ")
            append(p.amount?.let { fmt.f(it) } ?: "no amount")
            p.categoryId?.let { id -> append(" · ").append(snap.category(id)?.name ?: id) }
            p.payment?.let { append(" · ").append(it.label) }
            if (p.dayOffset != 0) append(" · ").append(friendlyDay(snap.today.plusDays(p.dayOffset.toLong()), snap.today))
            append(". Check and tap Save.")
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) { magic = text; applyParsed(text) }
    }
    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say it like: spent 250 on biryani using UPI")
        }
        try { voiceLauncher.launch(i) } catch (_: ActivityNotFoundException) {
            vm.toast("Voice input isn't available on this phone. Type it instead — it works offline.")
        }
    }
    LaunchedEffect(request.token) {
        if (request.startVoice) startVoice()
        request.prefillText?.let { applyParsed(it) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val path = saveReceipt(ctx, uri, newId)
            if (path != null) receiptPath = path else vm.toast("Couldn't attach that image.")
        }
    }

    fun close() { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }

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
        vm.write {
            vm.repo.saveTransaction(t)
            if (saveTemplate) {
                vm.repo.saveTemplate(TemplateEntity(FinanceRepository.newId(),
                    (t.merchant ?: t.note ?: snap.category(t.categoryId)?.name ?: type.label).take(24),
                    t.type, kotlin.math.abs(t.amount), t.categoryId, t.accountId, t.payment, t.merchant))
            }
            if (type == TxType.EXPENSE) vm.c.settings.update { it.copy(defaultPayment = payment, defaultAccountId = t.accountId) }
            val label = snap.category(t.categoryId)?.name ?: type.label
            vm.toast("${if (editing == null) "Saved" else "Updated"} ${fmt.f(kotlin.math.abs(t.amount))} · $label")
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        close()
    }

    fun trySave() {
        val t = buildEntity()
        when {
            amount == null -> { vm.toast("Enter an amount"); return }
            accountId == null -> { vm.toast("Add an account first (Settings → Accounts)"); return }
            type.needsCategory && categoryId == null && type == TxType.EXPENSE -> { vm.toast("Pick a category"); return }
            type.isMovement && (toAccountId == null || toAccountId == accountId) -> { vm.toast("Pick a different destination account"); return }
            t == null -> return
        }
        val entity = t!!
        if (editing == null) {
            scope.launch {
                val dup = vm.duplicateOf(entity)
                if (dup != null) duplicateOf = dup else doSave(entity)
            }
        } else doSave(entity)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            // Header: type selector
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val main = listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)
                    items(main) { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                    item {
                        Box {
                            FilterChip(selected = type !in main, onClick = { moreTypes = true },
                                label = { Text(if (type in main) "More" else type.label) },
                                trailingIcon = { Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) })
                            androidx.compose.material3.DropdownMenu(expanded = moreTypes, onDismissRequest = { moreTypes = false }) {
                                listOf(TxType.SAVINGS, TxType.INVESTMENT, TxType.REFUND, TxType.REIMBURSEMENT, TxType.ADJUSTMENT).forEach { t ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(t.label) }, onClick = { type = t; moreTypes = false })
                                }
                            }
                        }
                    }
                }
                if (editing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                IconButton(onClick = { close() }) { Icon(Icons.Default.Close, "Close") }
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // Magic text / voice
                OutlinedTextField(
                    value = magic,
                    onValueChange = { magic = it },
                    placeholder = { Text("Type or say: 250 biryani upi") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        Row {
                            if (magic.isNotBlank()) TextButton(onClick = { applyParsed(magic); focus.clearFocus() }) { Text("Fill") }
                            IconButton(onClick = { startVoice() }) { Icon(Icons.Default.Mic, "Speak the expense") }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyParsed(magic); focus.clearFocus() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AnimatedVisibility(magicHint != null) {
                    Text(magicHint ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }

                // Amount display
                val amountColor = when (type) {
                    TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                    TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> Rf.colors.positive
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (type == TxType.ADJUSTMENT) {
                        TextButton(onClick = { negativeAdjust = !negativeAdjust }) { Text(if (negativeAdjust) "−" else "+", fontSize = 28.sp) }
                    }
                    Text("₹", style = MaterialTheme.typography.headlineMedium, color = amountColor.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (amountText.isEmpty()) "0" else groupInput(amountText, snap.settings.indianGrouping),
                        style = MaterialTheme.typography.displayMedium, color = if (amountText.isEmpty()) amountColor.copy(alpha = 0.3f) else amountColor,
                        maxLines = 1, modifier = Modifier.semantics { contentDescription = "Amount ${amountText.ifEmpty { "zero" }} rupees" },
                    )
                }

                // Quick picks & templates (new expenses only)
                if (editing == null && type == TxType.EXPENSE && (snap.quickPicks.isNotEmpty() || snap.templates.isNotEmpty())) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.height(8.dp))
                }

                // Category
                if (type.needsCategory) {
                    val kind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                    val roots = snap.categories.filter { it.parentId == null && !it.archived && it.categoryKind() == kind }
                    val order = (snap.recentCategoryIds.mapNotNull { id -> snap.root(id) } + roots.filter { it.favorite }.map { it.id }).distinct()
                    val sorted = roots.sortedWith(compareBy<CategoryEntity> { c -> order.indexOf(c.id).let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { it.sortOrder })
                    val selectedRoot = snap.root(categoryId)
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(sorted, key = { it.id }) { c ->
                            CategoryTile(c, selected = selectedRoot == c.id) { categoryId = c.id }
                        }
                    }
                    val subs = snap.categories.filter { it.parentId == selectedRoot && !it.archived }
                    if (subs.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subs) { s ->
                                FilterChip(selected = categoryId == s.id, onClick = { categoryId = if (categoryId == s.id) selectedRoot else s.id },
                                    label = { Text(s.name) })
                            }
                        }
                    }
                }

                // Accounts
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(if (type.isMovement) "From" else "Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    AccountChips(accounts, accountId, snap) { accountId = it }
                    if (type.isMovement) {
                        Text("To", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        val targets = accounts.filter { it.id != accountId }.let { list ->
                            if (type == TxType.SAVINGS || type == TxType.INVESTMENT)
                                list.sortedBy { if (it.accountType() == AccountType.SAVINGS || it.accountType() == AccountType.INVESTMENT) 0 else 1 }
                            else list
                        }
                        if (targets.isEmpty()) Text("Add a second account (e.g. a savings account) in Settings → Accounts to record transfers.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        AccountChips(targets, toAccountId, snap) { toAccountId = it }
                        if (snap.goals.isNotEmpty() && (type == TxType.SAVINGS || type == TxType.INVESTMENT || type == TxType.TRANSFER)) {
                            Spacer(Modifier.height(8.dp))
                            Selector("For goal (optional)", listOf<String?>(null) + snap.goals.map { it.goal.id }, goalId,
                                display = { id -> id?.let { g -> snap.goals.firstOrNull { it.goal.id == g }?.goal?.let { "${it.emoji} ${it.name}" } } ?: "No goal" },
                                onSelect = { goalId = it })
                        }
                        val toType = accounts.firstOrNull { it.id == toAccountId }?.accountType()
                        val hint = when {
                            toType == AccountType.CREDIT_CARD -> "Paying a card bill: this clears what you owe. The purchases were already counted when you made them."
                            else -> "Transfers move your own money — they're never counted as spending or income."
                        }
                        Text(hint, style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(top = 6.dp))
                    }
                    if (type == TxType.EXPENSE || type == TxType.INCOME || type == TxType.REFUND) {
                        Text("Paid with", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(PaymentMethod.entries.toList()) { pm ->
                                FilterChip(selected = payment == pm.name, onClick = {
                                    payment = pm.name
                                    accountForPayment(pm, accounts)?.let { accountId = it.id }
                                }, label = { Text(pm.label) })
                            }
                        }
                        if (payment == PaymentMethod.UPI.name) {
                            val acc = accounts.firstOrNull { it.id == accountId }
                            Text("UPI takes money from ${acc?.name ?: "your bank account"}.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    if (type == TxType.ADJUSTMENT) {
                        Text("Adjustments correct a balance without counting as spending or income. Use Settings → Accounts → Reconcile for the easiest way.",
                            style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(top = 6.dp))
                    }

                    // Date
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val d = LocalDate.ofEpochDay(day)
                        FilterChip(selected = d == snap.today, onClick = { day = snap.today.toEpochDay() }, label = { Text("Today") })
                        FilterChip(selected = d == snap.today.minusDays(1), onClick = { day = snap.today.minusDays(1).toEpochDay() }, label = { Text("Yesterday") })
                        FilterChip(selected = d != snap.today && d != snap.today.minusDays(1), onClick = { showDate = true },
                            label = { Text(if (d != snap.today && d != snap.today.minusDays(1)) friendlyDay(d, snap.today) else "Pick date") })
                    }
                    if (LocalDate.ofEpochDay(day).isAfter(snap.today)) {
                        Text("Future date: this will be shown as scheduled and won't affect balances until that day.",
                            style = MaterialTheme.typography.labelSmall, color = Rf.colors.warning)
                    }

                    // Details
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showDetails = !showDetails }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Note, merchant, tags & receipt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (!showDetails && note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, color = Rf.colors.subtle)
                    AnimatedVisibility(showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / payee") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                            OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(4.dp)); Text(if (receiptPath == null) "Attach receipt" else "Replace receipt")
                                }
                                receiptPath?.let { p -> ReceiptThumb(p); TextButton(onClick = { receiptPath = null }) { Text("Remove") } }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Recurring payment", modifier = Modifier.weight(1f)); Switch(recurring, { recurring = it })
                            }
                            if (editing == null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Save as one-tap template", modifier = Modifier.weight(1f)); Switch(saveTemplate, { saveTemplate = it })
                            }
                        }
                    }
                }
            }

            // Keypad + save
            Keypad(
                onKey = { k ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    amountText = appendKey(amountText, k)
                },
                onBackspace = { amountText = amountText.dropLast(1) },
                onClear = { amountText = "" },
            )
            Button(
                onClick = { trySave() },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (amount == null) "Enter amount" else "Save ${fmt.f(amount)}" + (snap.category(categoryId)?.takeIf { type.needsCategory }?.let { " · ${it.name}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showDate) DatePickDialog(LocalDate.ofEpochDay(day), { showDate = false }) { day = it.toEpochDay(); showDate = false }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("Balances, budgets and reports will update. You can undo right after.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteTransaction(editing.id); close() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    duplicateOf?.let { d ->
        AlertDialog(
            onDismissRequest = { duplicateOf = null },
            title = { Text("Already added?") },
            text = { Text("You saved ${fmt.f(kotlin.math.abs(d.amount))} from the same account a few minutes ago" +
                (snap.category(d.categoryId)?.let { " (${it.name})" } ?: "") + ". Add this one too?") },
            confirmButton = { TextButton(onClick = { val t = buildEntity(); duplicateOf = null; if (t != null) doSave(t) }) { Text("Add anyway") } },
            dismissButton = { TextButton(onClick = { duplicateOf = null; close() }) { Text("Don't add") } },
        )
    }
}

private fun accountForPayment(pm: PaymentMethod, accounts: List<AccountEntity>): AccountEntity? = when (pm) {
    PaymentMethod.CASH -> accounts.firstOrNull { it.accountType() == AccountType.CASH }
    PaymentMethod.CREDIT_CARD -> accounts.firstOrNull { it.accountType() == AccountType.CREDIT_CARD }
    PaymentMethod.WALLET -> accounts.firstOrNull { it.accountType() == AccountType.WALLET }
    PaymentMethod.UPI, PaymentMethod.DEBIT_CARD, PaymentMethod.NET_BANKING, PaymentMethod.AUTOPAY ->
        accounts.firstOrNull { it.accountType() == AccountType.BANK }
    PaymentMethod.OTHER -> null
}

private fun appendKey(cur: String, k: String): String {
    if (k == ".") return if (cur.contains('.')) cur else (if (cur.isEmpty()) "0." else "$cur.")
    if (cur.contains('.') && cur.substringAfter('.').length >= 2) return cur
    if (cur == "0") return k
    if (cur.substringBefore('.').length >= 10) return cur
    return cur + k
}

/** Shows what the user typed with live digit grouping, e.g. "125000.5" -> "1,25,000.5". */
private fun groupInput(s: String, indian: Boolean): String {
    val whole = s.substringBefore('.').toLongOrNull() ?: 0L
    val g = if (indian) Money.groupIndian(whole) else Money.groupWestern(whole)
    return if (s.contains('.')) g + "." + s.substringAfter('.') else g
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), maxItemsInEachRow = 3, horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { k ->
            Box(
                Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .combinedClickable(onClick = { if (k == "⌫") onBackspace() else onKey(k) }, onLongClick = { if (k == "⌫") onClear() })
                    .semantics { contentDescription = if (k == "⌫") "Backspace, long press to clear" else k },
                contentAlignment = Alignment.Center,
            ) {
                if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, null)
                else Text(k, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun ReceiptThumb(path: String, sizeDp: Int = 48) {
    val bmp = remember(path) { decodeSampled(path, 160) }
    if (bmp != null) Image(bmp.asImageBitmap(), "Receipt", Modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
}

fun decodeSampled(path: String, target: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Copies a picked image into private app storage (downscaled) so the receipt survives. */
private fun saveReceipt(ctx: Context, uri: Uri, txId: String): String? = runCatching {
    val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
    val out = File(dir, "$txId.jpg")
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / (sample * 2) >= 1600 || opts.outHeight / (sample * 2) >= 1600) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    out.absolutePath
}.getOrNull()
