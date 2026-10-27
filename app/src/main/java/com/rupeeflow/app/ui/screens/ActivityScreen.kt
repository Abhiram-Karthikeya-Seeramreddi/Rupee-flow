@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.rupeeflow.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.data.date
import com.rupeeflow.app.data.paymentMethod
import com.rupeeflow.app.data.txType
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.EmojiBadge
import com.rupeeflow.app.ui.components.EmptyState
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.friendlyDay
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.DateRange
import com.rupeeflow.core.Money
import com.rupeeflow.core.PeriodKind
import com.rupeeflow.core.Periods
import com.rupeeflow.core.TxType

private enum class TypeFilter(val label: String) { ALL("All"), EXPENSE("Spending"), INCOME("Income"), MOVES("Transfers & savings") }
private enum class SortBy(val label: String) { NEWEST("Newest first"), OLDEST("Oldest first"), HIGH("Highest amount"), LOW("Lowest amount") }

@Composable
fun ActivityScreen(
    vm: MainViewModel,
    snap: Snapshot,
    onEdit: (String) -> Unit,
    contentPadding: PaddingValues,
    initialCategory: String? = null,
) {
    val fmt = LocalMoney.current
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf(TypeFilter.ALL) }
    var period by rememberSaveable { mutableStateOf<PeriodKind?>(null) }
    var categoryFilter by rememberSaveable(initialCategory) { mutableStateOf(initialCategory) }
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortBy.NEWEST) }
    var sortOpen by remember { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(initialCategory != null) }

    val range: DateRange? = period?.let { Periods.resolve(it, snap.today, snap.cycle, snap.settings.firstDayOfWeek) }
    val q = query.trim().lowercase()
    val filtered = remember(snap.transactions, q, typeFilter, range, categoryFilter, accountFilter, sort) {
        snap.transactions.asSequence()
            .filter { t ->
                when (typeFilter) {
                    TypeFilter.ALL -> true
                    TypeFilter.EXPENSE -> t.txType() == TxType.EXPENSE || t.txType().isConsumptionOffset
                    TypeFilter.INCOME -> t.txType() == TxType.INCOME
                    TypeFilter.MOVES -> t.txType().isMovement || t.txType() == TxType.ADJUSTMENT
                }
            }
            .filter { range == null || range.contains(it.epochDay) }
            .filter { categoryFilter == null || it.categoryId == categoryFilter || snap.category(it.categoryId)?.parentId == categoryFilter }
            .filter { accountFilter == null || it.accountId == accountFilter || it.toAccountId == accountFilter }
            .filter { t ->
                q.isEmpty() || listOfNotNull(t.merchant, t.note, t.tags, snap.category(t.categoryId)?.name, snap.account(t.accountId)?.name,
                    Money.toInput(kotlin.math.abs(t.amount))).any { it.lowercase().contains(q) }
            }
            .toList()
            .let { list ->
                when (sort) {
                    SortBy.NEWEST -> list
                    SortBy.OLDEST -> list.reversed()
                    SortBy.HIGH -> list.sortedByDescending { kotlin.math.abs(it.amount) }
                    SortBy.LOW -> list.sortedBy { kotlin.math.abs(it.amount) }
                }
            }
    }
    val todayDay = snap.today.toEpochDay()
    val scheduled = filtered.filter { it.epochDay > todayDay }
    val past = filtered.filter { it.epochDay <= todayDay }
    val grouped = if (sort == SortBy.NEWEST || sort == SortBy.OLDEST) past.groupBy { it.epochDay } else null
    val totalOut = past.filter { it.txType() == TxType.EXPENSE }.sumOf { it.amount } - past.filter { it.txType().isConsumptionOffset && it.categoryId != null }.sumOf { it.amount }
    val totalIn = past.filter { it.txType() == TxType.INCOME }.sumOf { it.amount }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        SortBy.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortOpen = false }) }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TypeFilter.entries.toList()) { f -> FilterChip(typeFilter == f, { typeFilter = f }, label = { Text(f.label) }) }
                item { FilterChip(showFilters || categoryFilter != null || accountFilter != null || period != null, { showFilters = !showFilters }, label = { Text("More filters") }) }
            }
        }
        if (showFilters) item {
            RfCard(padding = 12) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(period == null, { period = null }, label = { Text("All time") }) }
                    items(listOf(PeriodKind.TODAY, PeriodKind.THIS_WEEK, PeriodKind.SALARY_CYCLE, PeriodKind.LAST_CYCLE, PeriodKind.THIS_MONTH, PeriodKind.LAST_3_MONTHS)) { p ->
                        FilterChip(period == p, { period = p }, label = { Text(p.label) })
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Selector("Category", listOf<String?>(null) + snap.categories.filter { it.parentId == null }.map { it.id }, categoryFilter,
                        display = { id -> id?.let { snap.category(it)?.let { c -> "${c.emoji} ${c.name}" } } ?: "All categories" },
                        onSelect = { categoryFilter = it }, modifier = Modifier.weight(1f))
                    Selector("Account", listOf<String?>(null) + snap.accounts.map { it.id }, accountFilter,
                        display = { id -> id?.let { snap.account(it)?.name } ?: "All accounts" },
                        onSelect = { accountFilter = it }, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("${past.size} entr${if (past.size == 1) "y" else "ies"}", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle, modifier = Modifier.weight(1f))
                Text("Out ${fmt.f(maxOf(0, totalOut))}  ·  In ${fmt.f(totalIn)}", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
            }
        }

        if (scheduled.isNotEmpty()) {
            item { Text("Scheduled (not counted yet)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            item { RfCard(padding = 4) { scheduled.forEach { t -> TransactionRow(t, snap, onClick = { onEdit(t.id) }) } } }
        }

        if (past.isEmpty() && scheduled.isEmpty()) item {
            EmptyState("🔎", if (snap.transactions.isEmpty()) "Nothing here yet" else "No matches",
                if (snap.transactions.isEmpty()) "Your expenses, income and transfers will appear here." else "Try a different search or filter.")
        }

        if (grouped != null) {
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    val dayOut = list.filter { it.txType() == TxType.EXPENSE }.sumOf { it.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp)) {
                        Text(friendlyDay(java.time.LocalDate.ofEpochDay(day), snap.today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (dayOut > 0) Text("−" + fmt.f(dayOut), style = MaterialTheme.typography.labelLarge, color = Rf.colors.subtle)
                    }
                }
                items(list, key = { it.id }) { t -> SwipeRow(vm, t, snap, onEdit) }
            }
        } else {
            items(past, key = { it.id }) { t -> SwipeRow(vm, t, snap, onEdit) }
        }
    }
}

@Composable
private fun SwipeRow(vm: MainViewModel, t: TransactionEntity, snap: Snapshot, onEdit: (String) -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v == SwipeToDismissBoxValue.EndToStart) { vm.deleteTransaction(t.id); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Rf.colors.negative.copy(alpha = 0.85f)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.White)
            }
        },
    ) {
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            TransactionRow(t, snap, onClick = { onEdit(t.id) })
        }
    }
}

@Composable
fun TransactionRow(t: TransactionEntity, snap: Snapshot, onClick: () -> Unit) {
    val fmt = LocalMoney.current
    val type = t.txType()
    val cat = snap.category(t.categoryId)
    val parent = cat?.parentId?.let { snap.category(it) }
    val acc = snap.account(t.accountId)
    val to = snap.account(t.toAccountId)
    val (emoji, color) = when {
        type.isMovement -> (if (type == TxType.INVESTMENT) "📈" else if (type == TxType.SAVINGS) "🐷" else "🔄") to MaterialTheme.colorScheme.primary
        type == TxType.ADJUSTMENT -> "⚖️" to Rf.colors.subtle
        cat != null -> cat.emoji to Color(cat.color)
        else -> "🧾" to Rf.colors.subtle
    }
    val title = when {
        type.isMovement -> t.note ?: "${acc?.name ?: "?"} → ${to?.name ?: "?"}"
        type == TxType.ADJUSTMENT -> t.note ?: "Balance adjustment"
        else -> t.merchant ?: t.note ?: cat?.name ?: type.label
    }
    val subtitle = buildList {
        if (!type.isMovement && type != TxType.ADJUSTMENT) add(if (parent != null) "${parent.name} › ${cat?.name}" else cat?.name ?: "Uncategorised")
        if (type.isMovement) add("${acc?.name ?: "?"} → ${to?.name ?: "?"}") else acc?.name?.let { add(it) }
        t.paymentMethod()?.let { add(it.label) }
        if (t.subscriptionId != null) add("bill")
        if (t.importFingerprint != null) add("imported")
    }.joinToString(" · ")
    val (amountText, amountColor) = when {
        type == TxType.EXPENSE -> ("−" + fmt.f(t.amount)) to MaterialTheme.colorScheme.onSurface
        type == TxType.INCOME || type.isConsumptionOffset -> ("+" + fmt.f(t.amount)) to Rf.colors.positive
        type == TxType.ADJUSTMENT -> fmt.f(t.amount, sign = true) to Rf.colors.subtle
        else -> fmt.f(t.amount) to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji, color, 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = amountColor)
            if (t.epochDay > snap.today.toEpochDay()) Text(friendlyDay(t.date(), snap.today), style = MaterialTheme.typography.labelSmall, color = Rf.colors.warning)
            else if (t.receiptPath != null) Text("📎", style = MaterialTheme.typography.labelSmall)
        }
    }
}
