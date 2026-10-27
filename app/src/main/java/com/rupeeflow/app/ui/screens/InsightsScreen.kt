@file:OptIn(ExperimentalMaterial3Api::class)

package com.rupeeflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.components.BarChart
import com.rupeeflow.app.ui.components.DonutChart
import com.rupeeflow.app.ui.components.KeyValueRow
import com.rupeeflow.app.ui.components.LegendRow
import com.rupeeflow.app.ui.components.LocalMoney
import com.rupeeflow.app.ui.components.RfCard
import com.rupeeflow.app.ui.components.Ring
import com.rupeeflow.app.ui.components.SectionHeader
import com.rupeeflow.app.ui.components.Slice
import com.rupeeflow.app.ui.components.SplitBar
import com.rupeeflow.app.ui.components.StatTile
import com.rupeeflow.app.ui.components.shortDate
import com.rupeeflow.app.ui.theme.Rf
import com.rupeeflow.core.DateRange
import com.rupeeflow.core.Money
import com.rupeeflow.core.PeriodKind
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun InsightsScreen(vm: MainViewModel, snap: Snapshot, contentPadding: PaddingValues, onCategory: (String) -> Unit, onEdit: (String) -> Unit) {
    val fmt = LocalMoney.current
    val period by vm.insightsPeriod.collectAsState()
    val data by vm.insights.collectAsState()
    var pickRange by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Insights", style = MaterialTheme.typography.headlineSmall) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PeriodKind.entries.toList()) { k ->
                    FilterChip(period == k, {
                        if (k == PeriodKind.CUSTOM) pickRange = true else vm.insightsPeriod.value = k
                    }, label = { Text(k.label) })
                }
            }
        }
        val d = data
        if (d == null) {
            item { Row(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        item { Text(d.range.label(), style = MaterialTheme.typography.labelLarge, color = Rf.colors.subtle) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val diff = d.spent - d.previousSpent
                StatTile("Spent", fmt.f(d.spent), Modifier.weight(1f),
                    hint = if (d.previousSpent > 0) (if (diff >= 0) "▲ " else "▼ ") + fmt.f(kotlin.math.abs(diff)) + " vs before" else null)
                StatTile("Income", fmt.f(d.income), Modifier.weight(1f), valueColor = Rf.colors.positive)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Saved", fmt.f(d.saved), Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
                StatTile("Savings rate", d.savingsRate?.let { "$it%" } ?: "—", Modifier.weight(1f))
                StatTile("Avg / day", fmt.f((d.avgDaily + 50) / 100 * 100), Modifier.weight(1f))
            }
        }

        if (d.insights.isNotEmpty()) item {
            RfCard(padding = 12) { d.insights.forEach { InsightRow(it) } }
        }

        item {
            SectionHeader("Where it went")
            RfCard {
                if (d.byCategory.isEmpty()) Text("No spending in this period.", color = Rf.colors.subtle)
                else {
                    val top = d.byCategory.take(7)
                    val rest = d.byCategory.drop(7).sumOf { it.second }
                    val slices = top.map { (c, v) -> Slice(c?.name ?: "Uncategorised", v, c?.let { Color(it.color) } ?: Rf.colors.subtle) } +
                        (if (rest > 0) listOf(Slice("Other", rest, Rf.colors.subtle)) else emptyList())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        DonutChart(slices, "Total", fmt.f(d.spent), sizeDp = 210) { fmt.f(it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    d.byCategory.forEach { (c, v) ->
                        LegendRow(c?.let { Color(it.color) } ?: Rf.colors.subtle, "${c?.emoji ?: "🧾"} ${c?.name ?: "Uncategorised"}", fmt.f(v),
                            sub = "${Money.percent(v, d.spent)}%", onClick = c?.let { cat -> { onCategory(cat.id) } })
                    }
                }
            }
        }

        item {
            SectionHeader("Daily spending")
            RfCard {
                val pts = d.daily
                val perDay = if (period == PeriodKind.SALARY_CYCLE) snap.safe.perDay else null
                BarChart(pts.map { it.second }, pts.map { "${it.first.dayOfMonth}" }, highlightLast = true, limitLine = perDay,
                    showEveryNthLabel = (pts.size / 7).coerceAtLeast(1)) { fmt.f(it) }
                if (perDay != null) Text("Dashed line: today's safe-to-spend per day (${fmt.f(perDay)})", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
                val zero = pts.dropWhile { it.second == 0L }.count { it.second == 0L && !it.first.isAfter(snap.today) }
                if (zero > 0) Text("$zero day${if (zero == 1) "" else "s"} with no spending 🌱", style = MaterialTheme.typography.labelMedium, color = Rf.colors.positive)
            }
        }

        item {
            SectionHeader("Trend")
            RfCard {
                Text("Spending vs income", style = MaterialTheme.typography.labelLarge)
                BarChart(d.trend.map { it.second }, d.trend.map { it.first }, secondary = d.incomeTrend.map { it.second }, highlightLast = false) { fmt.c(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, "Spent")
                    LegendDot(Rf.colors.positive, "Income")
                }
            }
        }

        item {
            SectionHeader("Spending mix")
            RfCard {
                Text("Essential vs non-essential", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SplitBar(d.essential.a, d.essential.b, Rf.colors.positive, Rf.colors.warning)
                Row { LegendDot(Rf.colors.positive, "Essential ${fmt.f(d.essential.a)}"); Spacer(Modifier.width(12.dp)); LegendDot(Rf.colors.warning, "Non-essential ${fmt.f(d.essential.b)}") }
                Spacer(Modifier.height(12.dp))
                Text("Fixed vs variable", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SplitBar(d.fixed.a, d.fixed.b, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                Row { LegendDot(MaterialTheme.colorScheme.primary, "Fixed ${fmt.f(d.fixed.a)}"); Spacer(Modifier.width(12.dp)); LegendDot(MaterialTheme.colorScheme.tertiary, "Variable ${fmt.f(d.fixed.b)}") }
                Text("Change in Settings → Categories.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (d.merchants.isNotEmpty()) item {
            SectionHeader("Top merchants")
            RfCard(padding = 12) { d.merchants.forEachIndexed { i, (m, v) -> KeyValueRow("${i + 1}. $m", fmt.f(v)) } }
        }

        item {
            SectionHeader("How you paid")
            RfCard(padding = 12) {
                if (d.byPayment.isEmpty()) Text("—", color = Rf.colors.subtle)
                d.byPayment.forEach { (p, v) -> KeyValueRow(p.label, fmt.f(v)) }
                if (d.byAccount.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("By account", style = MaterialTheme.typography.labelLarge)
                    d.byAccount.forEach { (a, v) -> KeyValueRow(a, fmt.f(v)) }
                }
            }
        }

        item {
            SectionHeader("Bills & budgets")
            RfCard(padding = 12) {
                KeyValueRow("Bills paid in this period", fmt.f(d.subscriptionsSpent))
                KeyValueRow("Budgets within limit", d.adherence?.let { "$it%" } ?: "No budgets")
                Spacer(Modifier.height(6.dp))
                Text("Bills paid per cycle", style = MaterialTheme.typography.labelLarge)
                BarChart(d.subscriptionTrend.map { it.second }, d.subscriptionTrend.map { it.first }, heightDp = 100, color = MaterialTheme.colorScheme.tertiary) { fmt.c(it) }
            }
        }

        if (d.unusual.isNotEmpty()) item {
            SectionHeader("Unusually large")
            RfCard(padding = 4) {
                Text("At least 3× your usual amount for that category.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle, modifier = Modifier.padding(8.dp))
                d.unusual.take(5).forEach { t -> TransactionRow(t, snap) { onEdit(t.id) } }
            }
        }

        item { HealthCard(snap) }
    }

    if (pickRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = snap.cycleRange.start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = snap.today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val sd = Instant.ofEpochMilli(s).atZone(ZoneOffset.UTC).toLocalDate()
                        val ed = Instant.ofEpochMilli(e).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.insightsCustom.value = DateRange(sd, if (ed.isBefore(sd)) sd else ed)
                        vm.insightsPeriod.value = PeriodKind.CUSTOM
                    }
                    pickRange = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, Modifier.weight(1f)) }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        com.rupeeflow.app.ui.components.Dot(color, 8)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HealthCard(snap: Snapshot) {
    val h = snap.health
    RfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(h.total / 100f, when { h.total >= 75 -> Rf.colors.positive; h.total >= 50 -> Rf.colors.warning; else -> Rf.colors.negative }, sizeDp = 72) {
                Text("${h.total}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Money habits score", style = MaterialTheme.typography.titleSmall)
                Text("This salary cycle, out of 100. No black box:", style = MaterialTheme.typography.labelMedium, color = Rf.colors.subtle)
            }
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Budgets within limit (max 40)", "${h.budgetPart}")
        KeyValueRow("Savings rate, 20%+ = full (max 30)", "${h.savingsPart}")
        KeyValueRow("Days you logged (max 30)", "${h.habitPart} · ${snap.loggedDaysInCycle}/${snap.cycleRange.daysElapsed(snap.today)} days")
        Text("No budgets yet counts as 20/40. Started ${shortDate(snap.cycleRange.start)}.", style = MaterialTheme.typography.labelSmall, color = Rf.colors.subtle)
    }
}
