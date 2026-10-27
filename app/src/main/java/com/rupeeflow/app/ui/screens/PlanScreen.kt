@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupeeflow.app.data.BudgetEntity
import com.rupeeflow.app.data.BudgetView
import com.rupeeflow.app.data.ChallengeEntity
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.GoalEntity
import com.rupeeflow.app.data.GoalView
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.data.budgetPeriod
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.AmountField
import com.rupeeflow.app.ui.components.ConfirmDialog
import com.rupeeflow.app.ui.components.DatePickerButton
import com.rupeeflow.app.ui.components.EmptyState
import com.rupeeflow.app.ui.components.KeyValueRow
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.Pill
import com.rupeeflow.app.ui.components.ProgressBar
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Ring
import com.rupeeflow.app.ui.components.Selector
import com.rupeeflow.app.ui.components.fullDate
import com.rupeeflow.app.ui.components.shortDate
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.BudgetPeriod
import com.rupeeflow.core.BudgetState
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Ledger
import com.rupeeflow.core.Money
import com.rupeeflow.core.Periods
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class PlanTab(val label: String) { BUDGETS("Budgets"), GOALS("Goals"), CHALLENGES("Challenges") }

@Composable
fun PlanScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, initialTab: PlanTab = PlanTab.BUDGETS) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    var newBudget by remember { mutableStateOf<String?>(null) } // category id or "" for overall
    var editGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var moveGoal by remember { mutableStateOf<Pair<GoalView, Boolean>?>(null) }
    val fmt = LocalMoney.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Plan", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanTab.entries.forEach { t -> FilterChip(tab == t, { tab = t }, label = { Text(t.label) }) }
            }
        }
        when (tab) {
            PlanTab.BUDGETS -> {
                val cat = snap.budgets.filter { it.budget.categoryId != null }
                val overall = snap.budgets.filter { it.budget.categoryId == null }
                item {
                    RfCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("Allocated", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                                Text(fmt.f(snap.budgetAllocated), style = MaterialTheme.typography.titleLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Spent", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                                Text(fmt.f(cat.sumOf { it.status.spent }), style = MaterialTheme.typography.titleLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Left", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                                Text(fmt.f(snap.budgetRemaining), style = MaterialTheme.typography.titleLarge, color = Rf.colors.positive)
                            }
                        }
                        val overCount = cat.count { it.status.state == BudgetState.OVER }
                        if (overCount > 0) Text("$overCount over budget", style = MaterialTheme.typography.labelMedium,
                            color = Rf.colors.negative, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                // Budgets with spending get full cards; untouched ones collapse into one tidy list.
                val active = cat.filter { it.status.spent > 0 }
                val unused = cat.filter { it.status.spent == 0L }
                items(overall, key = { it.budget.id }) { b -> BudgetCard(b, snap) { editBudget = b.budget } }
                items(active, key = { it.budget.id }) { b -> BudgetCard(b, snap) { editBudget = b.budget } }
                if (unused.isNotEmpty()) item(key = "unused") {
                    UnusedBudgets(unused) { editBudget = it.budget }
                }
                if (snap.budgets.isEmpty()) item {
                    EmptyState("🎯", "No budgets yet", "Set a limit per category. You'll get alerts as you spend.")
                }
                // Suggestions from real spending without a budget
                val budgeted = cat.mapNotNull { it.budget.categoryId }.toSet()
                val prev = snap.cycle.previous(snap.cycleRange)
                val suggestions = Ledger.spendingByCategory(snap.effective, prev).entries
                    .filter { it.key !in budgeted && snap.category(it.key) != null }
                    .sortedByDescending { it.value }.take(4)
                if (suggestions.isNotEmpty()) item {
                    Text("Suggested from last cycle", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(suggestions) { (id, v) ->
                            val c = snap.category(id)!!
                            FilledTonalButton(onClick = { newBudget = id }) { Text("${c.emoji} ${c.name.substringBefore(" &")} · ${fmt.c(v)}") }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { newBudget = "food" }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Category budget") }
                        if (overall.isEmpty()) OutlinedButton(onClick = { newBudget = "" }) { Text("Overall limit") }
                    }
                }
            }

            PlanTab.GOALS -> {
                if (snap.goals.isEmpty()) item {
                    EmptyState("🏝️", "Save for something", "Emergency fund, trip, new phone…")
                }
                items(snap.goals, key = { it.goal.id }) { g -> GoalCard(g, snap, onEdit = { editGoal = g.goal }, onMove = { w -> moveGoal = g to w }) }
                item {
                    Button(onClick = { newGoal = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("New goal") }
                }
                item {
                    RfCard {
                        Text("Savings plan per salary cycle", style = MaterialTheme.typography.titleSmall)
                        Text("Kept aside from safe-to-spend each cycle.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var plan by remember(snap.settings.plannedSavingsPerCycle) { mutableStateOf(if (snap.settings.plannedSavingsPerCycle > 0) Money.toInput(snap.settings.plannedSavingsPerCycle) else "") }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AmountField(plan, { plan = it }, "Plan per cycle", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { vm.updateSettings { it.copy(plannedSavingsPerCycle = Money.parse(plan) ?: 0) }; vm.toast("Savings plan updated") }) { Text("Save") }
                        }
                        if (snap.settings.plannedSavingsPerCycle > 0) {
                            Spacer(Modifier.height(8.dp))
                            ProgressBar(snap.savedCycle.toFloat() / snap.settings.plannedSavingsPerCycle, MaterialTheme.colorScheme.primary)
                            Text("${fmt.f(snap.savedCycle)} moved this cycle", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        }
                    }
                }
            }

            PlanTab.CHALLENGES -> {
                item {
                    Text("Small wins build the habit.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(snap.challenges, key = { it.challenge.id }) { c ->
                    RfCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.challenge.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            val (label, color) = when (c.status) {
                                "DONE" -> "Done 🎉" to Rf.colors.positive
                                "MISSED" -> "Missed — try again" to Rf.colors.warning
                                "ENDED" -> "Ended" to Rf.colors.subtle
                                else -> "In progress" to MaterialTheme.colorScheme.primary
                            }
                            Pill(label, color)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(c.percent / 100f, if (c.status == "MISSED") Rf.colors.warning else Rf.colors.positive)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text(c.detail, style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle, modifier = Modifier.weight(1f))
                            Text("${shortDate(LocalDate.ofEpochDay(c.challenge.startEpochDay))} – ${shortDate(LocalDate.ofEpochDay(c.challenge.endEpochDay))}",
                                style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        }
                        TextButton(onClick = { vm.write { vm.repo.deleteChallenge(c.challenge.id) } }) { Text("Remove") }
                    }
                }
                item { ChallengePresets(vm, snap) }
            }
        }
    }

    editBudget?.let { b -> BudgetEditor(vm, snap, b, onDismiss = { editBudget = null }) }
    newBudget?.let { catId ->
        BudgetEditor(vm, snap, BudgetEntity(FinanceRepository.newId(), catId.ifEmpty { null }, 0, BudgetPeriod.SALARY_CYCLE.name), isNew = true, onDismiss = { newBudget = null })
    }
    editGoal?.let { g -> GoalEditor(vm, snap, g, false) { editGoal = null } }
    if (newGoal) GoalEditor(vm, snap, GoalEntity(FinanceRepository.newId(), "", "🎯", 0, null, null), true) { newGoal = false }
    moveGoal?.let { (g, withdraw) -> MoveGoalMoneyDialog(vm, snap, g, withdraw) { moveGoal = null } }
}

@Composable
private fun UnusedBudgets(list: List<BudgetView>, onEdit: (BudgetView) -> Unit) {
    val fmt = LocalMoney.current
    var open by remember { mutableStateOf(false) }
    RfCard(padding = 12) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open }.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("No spending yet (${list.size})", style = MaterialTheme.typography.titleSmall)
                Text(list.joinToString("  ") { it.emoji }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Text(fmt.f(list.sumOf { it.status.limit }), style = MaterialTheme.typography.labelLarge, color = Rf.colors.subtle)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Hide" else "Show")
        }
        AnimatedVisibility(open) {
            Column {
                list.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEdit(b) }.padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${b.emoji}  ${b.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(fmt.f(b.status.limit), style = MaterialTheme.typography.bodyMedium, color = Rf.colors.subtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(b: BudgetView, snap: Snapshot, onClick: () -> Unit) {
    val fmt = LocalMoney.current
    val s = b.status
    val color = when (s.state) {
        BudgetState.OVER -> Rf.colors.negative
        BudgetState.NEAR_LIMIT, BudgetState.FAST -> Rf.colors.warning
        else -> Color(b.color)
    }
    RfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.titleSmall)
                Text("${b.budget.budgetPeriod().label} · ${s.daysLeft}d left", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
            }
            Pill(s.state.label, color)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(if (s.limit <= 0) 1f else s.spent.toFloat() / s.limit, color, height = 10)
        Spacer(Modifier.height(8.dp))
        Row {
            Text("${fmt.f(s.spent)} of ${fmt.f(s.limit)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("${s.percentUsed}%", style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Text(
            when {
                s.remaining < 0 -> "Over by ${fmt.f(-s.remaining)}"
                s.daysLeft > 1 -> "${fmt.f(s.remaining)} left · ${fmt.f(s.perDayLeft)}/day"
                else -> "${fmt.f(s.remaining)} left"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (s.remaining < 0) Rf.colors.negative else Rf.colors.subtle,
        )
        if (s.state == BudgetState.FAST) Text("At this pace ≈ ${fmt.f(s.projectedSpend)}", style = MaterialTheme.typography.labelSmall, color = Rf.colors.warning)
    }
}

@Composable
private fun BudgetEditor(vm: MainViewModel, snap: Snapshot, b: BudgetEntity, isNew: Boolean = false, onDismiss: () -> Unit) {
    var catId by remember { mutableStateOf(b.categoryId) }
    var amount by remember { mutableStateOf(if (b.amount > 0) Money.toInput(b.amount) else "") }
    var period by remember { mutableStateOf(b.budgetPeriod()) }
    var start by remember { mutableStateOf(b.customStart?.let { LocalDate.ofEpochDay(it) } ?: snap.today) }
    var end by remember { mutableStateOf(b.customEnd?.let { LocalDate.ofEpochDay(it) } ?: snap.today.plusDays(29)) }
    var rollover by remember { mutableStateOf(b.rollover) }
    var thresholds by remember { mutableStateOf(b.thresholds.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val roots = snap.categories.filter { it.categoryKind() == CategoryKind.EXPENSE && !it.archived }
    // Show what was spent last period to help pick a realistic number.
    val hint = run {
        val r = Periods.budgetRange(period, snap.today, snap.cycle, snap.settings.firstDayOfWeek, start, end)
        val prev = Periods.previousBudgetRange(period, r, snap.cycle, snap.settings.firstDayOfWeek)
        prev?.let { Ledger.spending(snap.effective, it, catId?.let { id -> setOf(id) }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New budget" else "Edit budget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Selector("Category", listOf<String?>(null) + roots.map { it.id }, catId,
                    display = { id -> id?.let { snap.category(it)?.let { c -> (if (c.parentId != null) "   ↳ " else "") + "${c.emoji} ${c.name}" } } ?: "🎯 Overall spending" },
                    onSelect = { catId = it })
                AmountField(amount, { amount = it }, "Limit")
                hint?.let { Text("Spent last period: ${Money.format(it, snap.settings.indianGrouping)}", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle) }
                Text("Period", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BudgetPeriod.entries.forEach { p -> FilterChip(period == p, { period = p }, label = { Text(p.label) }) }
                }
                if (period == BudgetPeriod.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerButton("Start", start, { start = it }, Modifier.weight(1f))
                    DatePickerButton("End", end, { end = it }, Modifier.weight(1f))
                }
                Text("Alert me at", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 75, 90, 100).forEach { t ->
                        FilterChip(t in thresholds, { thresholds = if (t in thresholds) thresholds - t else thresholds + t }, label = { Text("$t%") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over", style = MaterialTheme.typography.bodyLarge)
                        Text("Carry over what's left", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    }
                    Switch(rollover, { rollover = it })
                }
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete budget", color = MaterialTheme.colorScheme.error) }
                Text("Changing a budget never changes your transactions.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parse(amount)
                if (a == null) { vm.toast("Enter a limit"); return@TextButton }
                vm.write(if (isNew) "Budget added" else "Budget updated") {
                    vm.repo.saveBudget(b.copy(categoryId = catId, amount = a, period = period.name,
                        customStart = if (period == BudgetPeriod.CUSTOM) start.toEpochDay() else null,
                        customEnd = if (period == BudgetPeriod.CUSTOM) end.toEpochDay() else null,
                        rollover = rollover, thresholds = thresholds.sorted().joinToString(",")))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) ConfirmDialog("Delete budget?", "Your transactions stay exactly as they are.", "Delete",
        onConfirm = { vm.write("Budget deleted") { vm.repo.deleteBudget(b.id) }; confirmDelete = false; onDismiss() },
        onDismiss = { confirmDelete = false }, destructive = true)
}

@Composable
private fun GoalCard(g: GoalView, snap: Snapshot, onEdit: () -> Unit, onMove: (Boolean) -> Unit) {
    val fmt = LocalMoney.current
    var history by remember { mutableStateOf(false) }
    val p = g.progress
    RfCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(p.percent / 100f, if (p.done) Rf.colors.positive else MaterialTheme.colorScheme.primary, sizeDp = 64) {
                Text(g.goal.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.goal.name, style = MaterialTheme.typography.titleMedium)
                Text("${fmt.f(p.saved)} of ${fmt.f(p.target)} · ${p.percent}%", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        p.done -> "Goal reached 🎉"
                        g.goal.targetEpochDay == null -> "${fmt.f(p.remaining)} to go"
                        else -> "${fmt.f(p.perCycle)}/cycle · by ${fullDate(LocalDate.ofEpochDay(g.goal.targetEpochDay))}"
                    },
                    style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle,
                )
                g.accountName?.let { Text("Kept in $it", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onMove(false) }) { Text("Add money") }
            if (p.saved > 0) OutlinedButton(onClick = { onMove(true) }) { Text("Withdraw") }
            Spacer(Modifier.weight(1f))
            if (g.contributions.isNotEmpty()) Text(if (history) "Hide" else "History (${g.contributions.size})", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { history = !history }.padding(6.dp))
        }
        AnimatedVisibility(history) {
            Column(Modifier.padding(top = 6.dp)) {
                g.contributions.sortedByDescending { it.epochDay }.forEach { t ->
                    val out = g.goal.accountId != null && t.accountId == g.goal.accountId
                    KeyValueRow(shortDate(LocalDate.ofEpochDay(t.epochDay)) + " · " + (snap.account(if (out) t.toAccountId else t.accountId)?.name ?: ""),
                        (if (out) "−" else "+") + fmt.f(t.amount), if (out) Rf.colors.negative else Rf.colors.positive)
                }
                if (g.goal.initialSaved > 0) KeyValueRow("Already saved before", fmt.f(g.goal.initialSaved))
            }
        }
    }
}

@Composable
private fun GoalEditor(vm: MainViewModel, snap: Snapshot, g: GoalEntity, isNew: Boolean, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    var emoji by remember { mutableStateOf(g.emoji) }
    var target by remember { mutableStateOf(if (g.target > 0) Money.toInput(g.target) else "") }
    var hasDate by remember { mutableStateOf(g.targetEpochDay != null) }
    var date by remember { mutableStateOf(g.targetEpochDay?.let { LocalDate.ofEpochDay(it) } ?: snap.today.plusMonths(6)) }
    val scope = rememberCoroutineScope()
    var accountId by remember { mutableStateOf(g.accountId ?: savingsAccounts(snap).firstOrNull()?.id) }
    var initial by remember { mutableStateOf(if (g.initialSaved > 0) Money.toInput(g.initialSaved) else "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val presets = listOf("🛟" to "Emergency fund", "🏝️" to "Vacation", "📱" to "New phone", "🖥️" to "Gaming PC", "📈" to "Investments", "🏦" to "Long-term savings")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isNew) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (e, n) -> FilterChip(name == n, { name = n; emoji = e }, label = { Text("$e $n") }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, keyboardOptions = com.rupeeflow.app.ui.components.NameKeys, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(emoji, { if (it.length <= 4) emoji = it }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AmountField(target, { target = it }, "Target amount")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target date", modifier = Modifier.weight(1f)); Switch(hasDate, { hasDate = it })
                }
                if (hasDate) DatePickerButton("By", date, { date = it }, Modifier.fillMaxWidth())
                val keepers = savingsAccounts(snap)
                if (keepers.isEmpty()) {
                    FilledTonalButton(onClick = { scope.launch { accountId = vm.repo.ensureSavingsAccount() } }) { Text("+ Create Savings account") }
                } else {
                    Selector("Kept in", keepers.map { it.id }, accountId,
                        display = { id -> snap.account(id)?.let { a -> "${a.emoji} ${a.name}" } ?: "" }, onSelect = { accountId = it })
                }
                AmountField(initial, { initial = it }, "Already saved (optional)")
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete goal", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = Money.parse(target)
                if (t == null) { vm.toast("Set a target amount"); return@TextButton }
                vm.write(if (isNew) "Goal created" else "Goal updated") {
                    vm.repo.saveGoal(g.copy(name = name, emoji = emoji.ifBlank { "🎯" }, target = t,
                        targetEpochDay = if (hasDate) date.toEpochDay() else null, accountId = accountId, initialSaved = Money.parse(initial) ?: 0))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirmDelete) ConfirmDialog("Delete goal?", "Your transfers stay in history.", "Delete",
        onConfirm = { vm.write("Goal deleted") { vm.repo.deleteGoal(g.id) }; confirmDelete = false; onDismiss() }, onDismiss = { confirmDelete = false }, destructive = true)
}

/** Accounts that hold set-aside money (savings / investments / anything marked not spendable). */
private fun savingsAccounts(snap: Snapshot) = snap.activeAccounts.filter {
    it.accountType() == AccountType.SAVINGS || it.accountType() == AccountType.INVESTMENT ||
        (!it.includeInSpendable && it.accountType() != AccountType.CREDIT_CARD)
}

private fun accountLabel(snap: Snapshot, id: String?, fmt: com.rupeeflow.app.ui.components.MoneyFmt): String =
    snap.account(id)?.let { "${it.emoji} ${it.name} · ${fmt.f(snap.balances[it.id] ?: it.openingBalance)}" } ?: ""

@Composable
private fun MoveGoalMoneyDialog(vm: MainViewModel, snap: Snapshot, g: GoalView, withdraw: Boolean, onDismiss: () -> Unit) {
    val fmt = LocalMoney.current
    val scope = rememberCoroutineScope()
    val savings = savingsAccounts(snap)
    var goalAcc by remember { mutableStateOf(g.goal.accountId ?: savings.firstOrNull()?.id) }
    // Everyday accounts the money comes from (or goes back to). Never the goal's own account or a credit card.
    val everyday = snap.activeAccounts.filter { it.accountType() != AccountType.CREDIT_CARD && it.id != goalAcc && it !in savings }
    var amount by remember { mutableStateOf(if (!withdraw && g.goal.targetEpochDay != null && g.progress.perCycle > 0) Money.toInput(g.progress.perCycle) else "") }
    var other by remember { mutableStateOf(everyday.firstOrNull { it.id == snap.settings.defaultAccountId }?.id ?: everyday.firstOrNull()?.id) }
    var creating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (withdraw) "Take money out" else "Add money") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AmountField(amount, { amount = it }, "Amount")
                val savingsPicker: @Composable () -> Unit = {
                    if (savings.isEmpty()) {
                        Text("Keep goal money in a separate Savings account.", style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(enabled = !creating, onClick = {
                            creating = true
                            scope.launch { goalAcc = vm.repo.ensureSavingsAccount(); creating = false }
                        }) { Text("+ Create Savings account") }
                    } else {
                        Selector(if (withdraw) "From" else "To", savings.map { it.id }, goalAcc,
                            display = { id -> accountLabel(snap, id, fmt) }, onSelect = { goalAcc = it })
                    }
                }
                val everydayPicker: @Composable () -> Unit = {
                    Selector(if (withdraw) "To" else "From", everyday.map { it.id }, other,
                        display = { id -> accountLabel(snap, id, fmt) }, onSelect = { other = it })
                }
                if (withdraw) { savingsPicker(); everydayPicker() } else { everydayPicker(); savingsPicker() }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Money.parse(amount)
                val o = other
                val ga = goalAcc
                when {
                    a == null -> { vm.toast("Enter an amount"); return@TextButton }
                    ga == null -> { vm.toast("Create or pick a Savings account"); return@TextButton }
                    o == null || o == ga -> { vm.toast("Pick a different account"); return@TextButton }
                }
                vm.write(if (withdraw) "Took ${fmt.f(a!!)} out of ${g.goal.name}" else "Added ${fmt.f(a!!)} to ${g.goal.name} 🐷") {
                    if (g.goal.accountId != ga) vm.repo.saveGoal(g.goal.copy(accountId = ga))
                    if (withdraw) vm.repo.moveGoalMoney(g.goal.id, a, ga!!, o!!, true) else vm.repo.moveGoalMoney(g.goal.id, a, o!!, ga!!, false)
                }
                onDismiss()
            }) { Text(if (withdraw) "Take out" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChallengePresets(vm: MainViewModel, snap: Snapshot) {
    var capCat by remember { mutableStateOf<String?>("restaurants") }
    var capAmount by remember { mutableStateOf("") }
    val week = Periods.weekContaining(snap.today, snap.settings.firstDayOfWeek)
    val cycle = snap.cycleRange
    fun add(kind: String, title: String, target: Int, start: LocalDate, end: LocalDate, cat: String? = null, amount: Long? = null) {
        vm.write("Challenge started 💪") {
            vm.repo.saveChallenge(ChallengeEntity(FinanceRepository.newId(), kind, title, cat, amount, target, start.toEpochDay(), end.toEpochDay()))
        }
    }
    RfCard {
        Text("Start a challenge", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { add("NO_SPEND_DAYS", "2 no-spend days this week", 2, snap.today, week.end) }) { Text("🌱 2 no-spend days") }
            FilledTonalButton(onClick = { add("LOG_DAYS", "Log every day this week", 7, week.start, week.end) }) { Text("📒 Log 7 days") }
            FilledTonalButton(onClick = { add("NO_SPEND_DAYS", "5 no-spend days this cycle", 5, snap.today, cycle.end) }) { Text("🧘 5 no-spend days") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Keep a category under a limit this week", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Selector("Category", snap.categories.filter { it.parentId == null && it.categoryKind() == CategoryKind.EXPENSE }.map { it.id }, capCat,
                display = { id -> snap.category(id)?.let { "${it.emoji} ${it.name}" } ?: "" }, onSelect = { capCat = it }, modifier = Modifier.weight(1.4f))
            AmountField(capAmount, { capAmount = it }, "Under", Modifier.weight(1f))
        }
        TextButton(enabled = Money.parse(capAmount) != null && capCat != null, onClick = {
            val a = Money.parse(capAmount); val c = capCat
            if (a != null && c != null) {
                add("CATEGORY_CAP", "${snap.category(c)?.name} under ${Money.format(a)} this week", 0, week.start, week.end, c, a)
                capAmount = ""
            }
        }) { Text("Start") }
    }
}
