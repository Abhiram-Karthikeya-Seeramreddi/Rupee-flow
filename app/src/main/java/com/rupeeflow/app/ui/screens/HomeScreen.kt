@file:OptIn(ExperimentalMaterial3Api::class)

package com.rupeeflow.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.data.BillView
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.accountType
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.EmojiBadge
import com.rupeeflow.app.ui.components.KeyValueRow
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.Pill
import com.rupeeflow.app.ui.components.ProgressBar
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.SectionHeader
import com.rupeeflow.app.ui.components.StatTile
import com.rupeeflow.app.ui.components.dueText
import com.rupeeflow.app.ui.components.fullDate
import com.rupeeflow.app.ui.components.shortDate
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.BudgetState
import com.rupeeflow.core.Insight
import java.time.LocalTime

@Composable
fun HomeScreen(
    vm: MainViewModel,
    snap: Snapshot,
    onAdd: (AddRequest) -> Unit,
    onEdit: (String) -> Unit,
    navigate: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val fmt = LocalMoney.current
    var payBill by remember { mutableStateOf<BillView?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val h = LocalTime.now().hour
                    val greet = when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
                    Text(greet + (snap.settings.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
                        style = MaterialTheme.typography.headlineSmall)
                    Text("Cycle ${snap.cycleRange.label()} · ${snap.cycleRange.daysLeft(snap.today)} days left",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.updateSettings { it.copy(hideAmounts = !it.hideAmounts) } }) {
                    Icon(if (snap.settings.hideAmounts) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (snap.settings.hideAmounts) "Show amounts" else "Hide amounts")
                }
                IconButton(onClick = { navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
            }
        }

        item { HeroCard(snap) }

        if (snap.salaryPrompt) item {
            RfCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💼", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Salary day " + if (snap.daysToSalary <= 2 && snap.daysToSalary > 0) "is coming" else "— did it arrive?", style = MaterialTheme.typography.titleSmall)
                        Text("Expected ${fmt.f(snap.settings.expectedSalary)}. It's only counted once you confirm.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val acc = snap.activeAccounts.firstOrNull { it.id == snap.settings.defaultAccountId }
                            ?: snap.activeAccounts.firstOrNull { it.accountType() == AccountType.BANK }
                        if (acc == null) vm.toast("Add a bank account first") else
                            vm.write("Salary recorded 🎉") { vm.repo.recordSalary(snap.settings.expectedSalary, acc.id, snap.today) }
                    }) { Text("Received ${fmt.f(snap.settings.expectedSalary)}") }
                    OutlinedButton(onClick = { onAdd(AddRequest(type = com.rupeeflow.core.TxType.INCOME)) }) { Text("Different amount") }
                }
            }
        }

        item { LoggingNudge(vm, snap, onAdd) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Today", fmt.f(snap.spentToday), Modifier.weight(1f))
                StatTile("This week", fmt.f(snap.spentWeek), Modifier.weight(1f))
                StatTile("This cycle", fmt.f(snap.spentCycle), Modifier.weight(1f))
            }
        }

        item { CycleCard(snap, navigate) }

        item {
            SectionHeader("Accounts", action = "Manage", onAction = { navigate("accounts") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(snap.activeAccounts) { a ->
                    val bal = snap.balances[a.id] ?: a.openingBalance
                    val type = a.accountType()
                    RfCard(Modifier.width(168.dp), onClick = { navigate("accounts") }, padding = 14) {
                        Text("${a.emoji} ${a.name}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        if (type == AccountType.CREDIT_CARD) {
                            Text(if (bal < 0) fmt.f(-bal) else fmt.f(0), style = MaterialTheme.typography.titleLarge,
                                color = if (bal < 0) Rf.colors.negative else MaterialTheme.colorScheme.onSurface)
                            Text("outstanding", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        } else {
                            Text(fmt.f(bal), style = MaterialTheme.typography.titleLarge, color = if (bal < 0) Rf.colors.negative else MaterialTheme.colorScheme.onSurface)
                            Text(type.label.lowercase(), style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        }
                    }
                }
                item {
                    RfCard(Modifier.width(120.dp), onClick = { navigate("accounts") }, padding = 14) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Add account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val catBudgets = snap.budgets.filter { it.budget.categoryId != null }
        item {
            SectionHeader("Budgets", action = if (catBudgets.isEmpty()) "Set up" else "All", onAction = { navigate("budgets") })
            if (catBudgets.isEmpty()) {
                RfCard(onClick = { navigate("budgets") }) {
                    Text("Give each category a limit", style = MaterialTheme.typography.titleSmall)
                    Text("e.g. Food ₹5,000, Badminton ₹2,000 per salary cycle. You'll get a heads-up at 50/75/90/100%.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else RfCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Allocated", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        Text(fmt.f(snap.budgetAllocated), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Left in budgets", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
                        Text(fmt.f(snap.budgetRemaining), style = MaterialTheme.typography.titleMedium, color = Rf.colors.positive)
                    }
                }
                Spacer(Modifier.height(8.dp))
                catBudgets.take(4).forEach { b -> BudgetMini(b.emoji, b.name, b.status.spent, b.status.limit, b.status.state, Color(b.color)) }
            }
        }

        val upcoming = snap.bills.filter { it.sub.status == "ACTIVE" && it.daysUntil <= 10 }.take(4)
        if (upcoming.isNotEmpty() || snap.bills.isEmpty()) item {
            SectionHeader("Upcoming bills", action = if (snap.bills.isEmpty()) "Add" else "All", onAction = { navigate("bills") })
            if (upcoming.isEmpty()) {
                RfCard(onClick = { navigate("bills") }) {
                    Text("Track Netflix, gym, Wi-Fi, EMIs…", style = MaterialTheme.typography.titleSmall)
                    Text("Get reminded before they're due. A bill only counts as spent when you confirm it's paid.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else RfCard(padding = 8) {
                upcoming.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiBadge(b.sub.emoji, MaterialTheme.colorScheme.primary, 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.sub.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(dueText(b.daysUntil) + " · " + shortDate(b.nextDue) + if (b.sub.autopay) " · auto-pay" else "",
                                style = MaterialTheme.typography.labelMedium, color = if (b.daysUntil < 0) Rf.colors.negative else Rf.colors.subtle)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt.f(b.sub.amount), style = MaterialTheme.typography.titleSmall)
                            if (b.daysUntil <= 3) Text("Mark paid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { payBill = b }.padding(4.dp))
                        }
                    }
                }
            }
        }

        if (snap.homeInsights.isNotEmpty()) item {
            SectionHeader("Insights", action = "More", onAction = { navigate("insights") })
            RfCard(padding = 12) {
                snap.homeInsights.forEach { InsightRow(it) }
            }
        }

        item { SectionHeader("Recent", action = "See all", onAction = { navigate("activity") }) }
        val recent = snap.transactions.filter { it.epochDay <= snap.today.toEpochDay() }.take(6)
        if (recent.isEmpty()) item {
            RfCard {
                Text("No entries yet", style = MaterialTheme.typography.titleSmall)
                Text("Tap the + button and add your first expense. It takes about 4 taps.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else item {
            RfCard(padding = 4) { recent.forEach { t -> TransactionRow(t, snap, onClick = { onEdit(t.id) }) } }
        }
    }

    payBill?.let { b -> PayBillDialog(vm, snap, b) { payBill = null } }
}

@Composable
private fun HeroCard(snap: Snapshot) {
    val fmt = LocalMoney.current
    var expanded by remember { mutableStateOf(false) }
    val s = snap.safe
    val over = s.leftToday < 0
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Rf.colors.heroStart, Rf.colors.heroEnd)))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(20.dp),
    ) {
        Column {
            Text(if (over) "Over today's safe-to-spend" else "Safe to spend today", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text(fmt.f(kotlin.math.abs(s.leftToday)), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "${fmt.f(s.perDay)}/day · salary in ${snap.daysToSalary} day${if (snap.daysToSalary == 1) "" else "s"} (${shortDate(snap.nextSalaryDate)})",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            ProgressBar(
                if (s.perDay <= 0) (if (s.spentToday > 0) 1f else 0f) else s.spentToday.toFloat() / s.perDay,
                if (over) Color(0xFFFFB4B4) else Color.White, track = Color.White.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Text("Spent today ${fmt.f(s.spentToday)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text(if (expanded) "Hide maths" else "How?", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    val w = Color.White
                    HeroLine("Spendable balance", fmt.f(s.spendable), w)
                    HeroLine("− Bills due before salary", fmt.f(snap.billsDueThisCycle), w)
                    if (snap.position.cardDues > 0) HeroLine("− Credit card dues", fmt.f(snap.position.cardDues), w)
                    if (s.plannedSavingsLeft > 0) HeroLine("− Savings plan still to move", fmt.f(s.plannedSavingsLeft), w)
                    HeroLine("= Free until salary", fmt.f(s.free), w, bold = true)
                    HeroLine("÷ ${s.daysLeft} days left (incl. today)", fmt.f(s.perDay) + "/day", w)
                    Text("Spendable = bank, cash and wallets you've marked as spendable. Savings and investments aren't included.",
                        style = MaterialTheme.typography.labelSmall, color = w.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroLine(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun LoggingNudge(vm: MainViewModel, snap: Snapshot, onAdd: (AddRequest) -> Unit) {
    if (!snap.loggedToday) {
        RfCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📝", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Anything spent today?", style = MaterialTheme.typography.titleSmall)
                    Text(if (snap.streak > 0) "Keep your ${snap.streak}-day streak going 🔥" else "Log it now or mark a no-spend day.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAdd(AddRequest()) }) { Text("Add expense") }
                FilledTonalButton(onClick = { vm.write("Nice — a no-spend day 🌱") { vm.repo.setNoSpendToday(true, snap.today) } }) { Text("No spend today") }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(
                if (snap.noSpendToday && snap.spentToday == 0L) "No-spend day logged. ${snap.streak}-day logging streak!"
                else "${snap.streak}-day logging streak. Every rupee accounted for.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (snap.noSpendToday) Text("Undo", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { vm.write { vm.repo.setNoSpendToday(false, snap.today) } }.padding(4.dp))
        }
    }
}

@Composable
private fun CycleCard(snap: Snapshot, navigate: (String) -> Unit) {
    val fmt = LocalMoney.current
    RfCard(onClick = { navigate("insights") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This salary cycle", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Pill("${snap.cycleRange.daysElapsed(snap.today)}/${snap.cycleRange.days} days", MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Income received", fmt.f(snap.incomeCycle), Rf.colors.positive)
        KeyValueRow("Spent", fmt.f(snap.spentCycle))
        KeyValueRow("Moved to savings & investments", fmt.f(snap.savedCycle), MaterialTheme.colorScheme.primary)
        if (snap.settings.plannedSavingsPerCycle > 0) {
            val planned = snap.settings.plannedSavingsPerCycle
            ProgressBar(snap.savedCycle.toFloat() / planned, MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 4.dp), height = 6)
            Text(if (snap.plannedSavingsLeft > 0) "${fmt.f(snap.plannedSavingsLeft)} to go for your ${fmt.f(planned)} savings plan" else "Savings plan done ✅",
                style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
        }
        Spacer(Modifier.height(4.dp))
        KeyValueRow("Available in spendable accounts", fmt.f(snap.position.spendable), bold = true)
        if (snap.position.savings + snap.position.investments > 0)
            KeyValueRow("Savings + investments (total)", fmt.f(snap.position.savings + snap.position.investments))
        if (snap.position.cardDues > 0) KeyValueRow("Credit card owed", fmt.f(snap.position.cardDues), Rf.colors.negative)
        if (snap.scheduled.isNotEmpty()) Text("${snap.scheduled.size} future-dated entr${if (snap.scheduled.size == 1) "y" else "ies"} not counted yet.",
            style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
        Text("Next salary ${fullDate(snap.nextSalaryDate)}", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
    }
}

@Composable
fun BudgetMini(emoji: String, name: String, spent: Long, limit: Long, state: BudgetState, color: Color) {
    val fmt = LocalMoney.current
    val barColor = when (state) {
        BudgetState.OVER -> Rf.colors.negative
        BudgetState.NEAR_LIMIT, BudgetState.FAST -> Rf.colors.warning
        else -> color
    }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$emoji $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${fmt.f(spent)} / ${fmt.f(limit)}", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
        }
        Spacer(Modifier.height(4.dp))
        ProgressBar(if (limit <= 0) 1f else spent.toFloat() / limit, barColor, height = 6)
    }
}

@Composable
fun InsightRow(i: Insight) {
    val color = when (i.tone) {
        Insight.Tone.GOOD -> Rf.colors.positive
        Insight.Tone.WARN -> Rf.colors.warning
        Insight.Tone.BAD -> Rf.colors.negative
        Insight.Tone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(i.emoji)
        }
        Spacer(Modifier.width(10.dp))
        Text(i.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 6.dp))
    }
}

