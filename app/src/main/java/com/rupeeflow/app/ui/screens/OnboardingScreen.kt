@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.rupeeflow.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.BudgetEntity
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.GoalEntity
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.SubscriptionEntity
import com.rupeeflow.app.data.categoryKind
import com.rupeeflow.app.notify.Scheduling
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.AmountField
import com.rupeeflow.app.ui.components.ProgressBar
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.BudgetPeriod
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.Frequency
import com.rupeeflow.core.Money
import com.rupeeflow.core.SalaryCycle
import java.time.YearMonth

private data class BillDraft(val emoji: String, val name: String, val category: String, val amount: String, val day: Int)

@Composable
fun OnboardingScreen(vm: MainViewModel, snap: Snapshot) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    val total = 10

    var name by rememberSaveable { mutableStateOf("") }
    var salaryDay by rememberSaveable { mutableStateOf(28) }
    var salary by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("Bank account") }
    var bankBal by rememberSaveable { mutableStateOf("") }
    var cashBal by rememberSaveable { mutableStateOf("") }
    var hasCard by rememberSaveable { mutableStateOf(false) }
    var cardOwed by rememberSaveable { mutableStateOf("") }
    var hasSavings by rememberSaveable { mutableStateOf(false) }
    var savingsBal by rememberSaveable { mutableStateOf("") }
    val favorites = remember { mutableStateListOf<String>() }
    var favTouched by remember { mutableStateOf(false) }
    val budgets = remember {
        mutableStateMapOf("food" to "5000", "restaurants" to "3000", "fuel" to "2000", "entertainment" to "3000", "shopping" to "2500", "badminton" to "2000")
    }
    val bills = remember { mutableStateListOf<BillDraft>() }
    var goalName by rememberSaveable { mutableStateOf("Emergency fund") }
    var goalTarget by rememberSaveable { mutableStateOf("") }
    var plan by rememberSaveable { mutableStateOf("") }
    var daily by rememberSaveable { mutableStateOf(true) }
    var dailyMinute by rememberSaveable { mutableStateOf(21 * 60) }
    var alerts by rememberSaveable { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun finish() {
        vm.write {
            val r = vm.repo
            r.ensureDefaultCategories()
            val bankId = FinanceRepository.newId()
            r.saveAccount(AccountEntity(bankId, bankName.ifBlank { "Bank account" }, AccountType.BANK.name, Money.parse(bankBal) ?: 0, true, "🏦", sortOrder = 0))
            r.saveAccount(AccountEntity(FinanceRepository.newId(), "Cash", AccountType.CASH.name, Money.parse(cashBal) ?: 0, true, "💵", sortOrder = 1))
            if (hasCard) r.saveAccount(AccountEntity(FinanceRepository.newId(), "Credit card", AccountType.CREDIT_CARD.name, -(Money.parse(cardOwed) ?: 0), false, "💳", sortOrder = 2))
            var savingsId: String? = null
            if (hasSavings) {
                savingsId = FinanceRepository.newId()
                r.saveAccount(AccountEntity(savingsId, "Savings", AccountType.SAVINGS.name, Money.parse(savingsBal) ?: 0, false, "🐷", sortOrder = 3))
            }
            if (favTouched) snap.categories.filter { it.parentId == null }.forEach { c ->
                val fav = c.id in favorites
                if (c.favorite != fav) r.saveCategory(c.copy(favorite = fav))
            }
            budgets.forEach { (cat, amt) ->
                val a = Money.parse(amt) ?: return@forEach
                r.saveBudget(BudgetEntity(FinanceRepository.newId(), cat, a, BudgetPeriod.SALARY_CYCLE.name))
            }
            val today = snap.today
            bills.forEach { b ->
                val a = Money.parse(b.amount) ?: return@forEach
                var ym = YearMonth.from(today)
                var due = ym.atDay(minOf(b.day, ym.lengthOfMonth()))
                if (due.isBefore(today)) { ym = ym.plusMonths(1); due = ym.atDay(minOf(b.day, ym.lengthOfMonth())) }
                r.saveSubscription(SubscriptionEntity(FinanceRepository.newId(), b.name, b.emoji, a, b.category, bankId, Frequency.MONTHLY.name, 30, b.day, due.toEpochDay()))
            }
            Money.parse(goalTarget)?.let { t ->
                r.saveGoal(GoalEntity(FinanceRepository.newId(), goalName.ifBlank { "Savings goal" }, "🛟", t, null, savingsId))
            }
            vm.c.settings.update {
                it.copy(onboarded = true, userName = name.trim(), salaryDay = salaryDay, expectedSalary = Money.parse(salary) ?: 0,
                    defaultAccountId = bankId, plannedSavingsPerCycle = Money.parse(plan) ?: 0, dailyReminder = daily,
                    dailyReminderMinute = dailyMinute, budgetAlerts = alerts, billReminders = alerts, cycleReminders = alerts, savingsReminders = alerts)
            }
            Scheduling.scheduleAll(ctx, vm.c.settings.current())
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(step.toFloat() / (total - 1), MaterialTheme.colorScheme.primary, Modifier.weight(1f), height = 6)
                Spacer(Modifier.width(12.dp))
                Text("${step}/${total - 1}", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
            }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(40.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                            .background(Brush.linearGradient(listOf(Rf.colors.heroStart, Rf.colors.heroEnd))).padding(28.dp)) {
                            Column {
                                Text("₹", fontSize = 56.sp, color = Color.White)
                                Text("RupeeFlow", style = MaterialTheme.typography.displaySmall, color = Color.White)
                                Text("Every rupee earned, spent, saved or moved — easy to account for.", style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                        Bullet("⚡", "Add an expense in 4 taps — or just say “250 biryani UPI”.")
                        Bullet("🗓️", "Built around your salary cycle (28th → 27th), not the calendar.")
                        Bullet("🧘", "One number that matters: how much is safe to spend today.")
                        Bullet("🔒", "Offline and private. No account, no internet permission.")
                    }
                    1 -> {
                        Title("When does your salary arrive?", "Your money month runs from this day to the day before it next month.")
                        DayGrid(salaryDay) { salaryDay = it }
                        val c = SalaryCycle(salaryDay).cycleContaining(snap.today)
                        RfCard { Text("Current cycle: ${c.label()}", style = MaterialTheme.typography.bodyMedium) }
                    }
                    2 -> {
                        Title("Your salary", "Optional. Used for reminders and the salary-day prompt — it's only counted once you confirm it arrived.")
                        AmountField(salary, { salary = it }, "Monthly take-home")
                        OutlinedTextField(name, { name = it.take(24) }, label = { Text("Your first name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    3 -> {
                        Title("Your money right now", "Enter today's balances. You can add more accounts later.")
                        OutlinedTextField(bankName, { bankName = it }, label = { Text("Main bank account name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        AmountField(bankBal, { bankBal = it }, "Bank balance")
                        Text("UPI payments will come out of this account.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                        AmountField(cashBal, { cashBal = it }, "Cash in wallet")
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("I use a credit card", Modifier.weight(1f)); Switch(hasCard, { hasCard = it }) }
                        if (hasCard) AmountField(cardOwed, { cardOwed = it }, "Amount currently owed on it")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("I keep a separate savings account")
                                Text("Money there won't count as spendable", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                            }
                            Switch(hasSavings, { hasSavings = it })
                        }
                        if (hasSavings) AmountField(savingsBal, { savingsBal = it }, "Savings balance")
                    }
                    4 -> {
                        Title("What do you spend on most?", "Tap to star favourites — they show first when adding. All 25 categories stay available.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val defaults = snap.categories.filter { it.favorite && it.parentId == null }.map { it.id }
                            snap.categories.filter { it.parentId == null && it.categoryKind() == CategoryKind.EXPENSE }.forEach { c ->
                                val on = if (favTouched) c.id in favorites else c.id in defaults
                                FilterChip(on, {
                                    if (!favTouched) { favorites.clear(); favorites.addAll(defaults); favTouched = true }
                                    if (c.id in favorites) favorites.remove(c.id) else favorites.add(c.id)
                                }, label = { Text("${c.emoji} ${c.name}") })
                            }
                        }
                    }
                    5 -> {
                        Title("Budgets per salary cycle", "Suggested starting points — change or clear any. You'll get alerts at 50/75/90/100%.")
                        budgets.keys.toList().forEach { id ->
                            val c = snap.category(id) ?: return@forEach
                            AmountField(budgets[id] ?: "", { budgets[id] = it }, "${c.emoji} ${c.name}")
                        }
                        val sumB = budgets.values.mapNotNull { Money.parse(it) }.sum()
                        Text("Total: ${Money.format(sumB)}" + (Money.parse(salary)?.let { " · ${Money.percent(sumB, it)}% of salary" } ?: ""),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    6 -> {
                        Title("Regular bills", "Tap the ones you pay. You'll be reminded before each is due, and they only count as spent when you confirm.")
                        val presets = listOf(
                            BillDraft("🏋️", "Gym", "gym", "1500", 5), BillDraft("🏸", "Badminton coaching", "badminton", "2000", 1),
                            BillDraft("🌐", "Wi-Fi", "internet", "799", 10), BillDraft("📱", "Mobile recharge", "mobile", "349", 15),
                            BillDraft("🎬", "Netflix", "subscriptions", "649", 12), BillDraft("▶️", "YouTube Premium", "subscriptions", "149", 20),
                            BillDraft("🎵", "Spotify", "subscriptions", "119", 18), BillDraft("🏠", "Rent", "rent", "15000", 1),
                            BillDraft("🏦", "Loan EMI", "emi", "5000", 5),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.forEach { p ->
                                val on = bills.any { it.name == p.name }
                                FilterChip(on, { if (on) bills.removeAll { it.name == p.name } else bills.add(p) }, label = { Text("${p.emoji} ${p.name}") })
                            }
                        }
                        bills.toList().forEachIndexed { i, b ->
                            RfCard(padding = 12) {
                                Text("${b.emoji} ${b.name}", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AmountField(b.amount, { v -> bills[i] = b.copy(amount = v) }, "Amount", Modifier.weight(1f))
                                    OutlinedTextField(b.day.toString(), { v -> v.toIntOrNull()?.takeIf { it in 1..31 }?.let { bills[i] = b.copy(day = it) } },
                                        label = { Text("Due day") }, singleLine = true, modifier = Modifier.width(100.dp))
                                }
                            }
                        }
                    }
                    7 -> {
                        Title("Save with purpose", "Optional. A goal and a savings plan per cycle. Progress only counts money you actually move.")
                        OutlinedTextField(goalName, { goalName = it }, label = { Text("Goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        AmountField(goalTarget, { goalTarget = it }, "Target amount")
                        AmountField(plan, { plan = it }, "Set aside every salary cycle")
                        Text("The savings plan is held back from safe-to-spend until you transfer it.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                    }
                    8 -> {
                        Title("Gentle reminders", "The #1 reason people lose track is forgetting to log. A nightly nudge fixes that.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily check-in")
                                Text("Around ${"%02d:%02d".format(dailyMinute / 60, dailyMinute % 60)}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Switch(daily, { daily = it })
                        }
                        TextButton(onClick = { TimePickerDialog(ctx, { _, h, m -> dailyMinute = h * 60 + m }, dailyMinute / 60, dailyMinute % 60, false).show() }) { Text("Change time") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Budget, bill & salary alerts", Modifier.weight(1f)); Switch(alerts, { alerts = it })
                        }
                        if (Build.VERSION.SDK_INT >= 33) Button(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                    }
                    else -> {
                        Spacer(Modifier.height(40.dp))
                        Text("🎉", fontSize = 64.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("You're set", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("Try it now: tap + and add today's first expense. Everything else updates instantly.",
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        RfCard {
                            Text("Tip: long-press the app icon for “Add expense” and “Say an expense” shortcuts, or add the home-screen widget.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (step in 2..8) TextButton(onClick = {
                // Skipping a step means "don't set this up", so drop its suggestions.
                when (step) {
                    5 -> budgets.clear()
                    6 -> bills.clear()
                    7 -> { goalTarget = ""; plan = "" }
                }
                step++
            }) { Text("Skip") }
            Button(onClick = { if (step < total - 1) step++ else finish() }, shape = RoundedCornerShape(16.dp)) {
                Text(when (step) { 0 -> "Get started"; total - 1 -> "Open my dashboard"; else -> "Next" })
            }
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bullet(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
