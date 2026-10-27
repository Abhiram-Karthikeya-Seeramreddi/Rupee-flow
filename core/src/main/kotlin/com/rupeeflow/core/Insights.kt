package com.rupeeflow.core

import java.time.LocalDate

data class CategoryMeta(
    val id: String,
    val name: String,
    val emoji: String,
    val essential: Boolean,
    val fixed: Boolean,
)

data class Insight(val emoji: String, val text: String, val tone: Tone) {
    enum class Tone { GOOD, NEUTRAL, WARN, BAD }
}

data class BudgetLine(val name: String, val emoji: String, val status: BudgetStatus)

/**
 * Analytics computed only from stored transactions. Anything forward-looking is labelled
 * as an estimate in its text.
 */
object Analytics {

    fun dailySeries(txs: List<LedgerTx>, range: DateRange): List<Pair<LocalDate, Long>> {
        val byDay = HashMap<Long, Long>()
        for (tx in txs) {
            if (!range.contains(tx.epochDay)) continue
            when {
                tx.type == TxType.EXPENSE -> byDay[tx.epochDay] = (byDay[tx.epochDay] ?: 0) + tx.amount
                tx.type.isConsumptionOffset && tx.categoryId != null -> byDay[tx.epochDay] = (byDay[tx.epochDay] ?: 0) - tx.amount
            }
        }
        return (range.startDay..range.endDay).map { LocalDate.ofEpochDay(it) to maxOf(0L, byDay[it] ?: 0L) }
    }

    /** Groups an arbitrary series into buckets (e.g. salary cycles or months) for trend charts. */
    fun bucketed(txs: List<LedgerTx>, buckets: List<DateRange>): List<Pair<DateRange, Long>> =
        buckets.map { it to Ledger.spending(txs, it) }

    fun topMerchants(txs: List<LedgerTx>, range: DateRange, limit: Int = 5): List<Pair<String, Long>> =
        txs.filter { it.type == TxType.EXPENSE && range.contains(it.epochDay) && !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!!.trim().lowercase() }
            .map { (_, list) -> list.first().merchant!!.trim() to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(limit)

    fun byPayment(txs: List<LedgerTx>, range: DateRange): Map<PaymentMethod, Long> =
        txs.filter { it.type == TxType.EXPENSE && range.contains(it.epochDay) }
            .groupBy { it.payment ?: PaymentMethod.OTHER }
            .mapValues { e -> e.value.sumOf { it.amount } }

    fun byAccount(txs: List<LedgerTx>, range: DateRange): Map<String, Long> =
        txs.filter { it.type == TxType.EXPENSE && range.contains(it.epochDay) }
            .groupBy { it.accountId }
            .mapValues { e -> e.value.sumOf { it.amount } }

    data class Split(val a: Long, val b: Long)

    fun essentialSplit(byCat: Map<String, Long>, cats: Map<String, CategoryMeta>): Split {
        var ess = 0L; var non = 0L
        byCat.forEach { (id, v) -> if (cats[id]?.essential == true) ess += v else non += v }
        return Split(ess, non)
    }

    fun fixedSplit(byCat: Map<String, Long>, cats: Map<String, CategoryMeta>, subscriptionSpend: Long): Split {
        var fixed = 0L; var variable = 0L
        byCat.forEach { (id, v) -> if (cats[id]?.fixed == true) fixed += v else variable += v }
        return Split(fixed, variable)
    }

    /**
     * Transactions that are unusually large for their category: more than 3× the median of
     * that category over the history given, and at least ₹500.
     */
    fun unusual(history: List<LedgerTx>, range: DateRange): List<LedgerTx> {
        val expenses = history.filter { it.type == TxType.EXPENSE && it.categoryId != null }
        val medians = expenses.groupBy { it.categoryId!! }
            .filterValues { it.size >= 5 }
            .mapValues { (_, list) -> list.map { it.amount }.sorted().let { s -> s[s.size / 2] } }
        return expenses.filter { range.contains(it.epochDay) }
            .filter { tx -> val m = medians[tx.categoryId] ?: return@filter false; tx.amount >= 3 * m && tx.amount >= rupees(500) }
            .sortedByDescending { it.amount }
    }

    fun budgetAdherence(lines: List<BudgetStatus>): Int? {
        if (lines.isEmpty()) return null
        return lines.count { it.spent <= it.limit } * 100 / lines.size
    }

    fun averageDaily(spend: Long, range: DateRange, today: LocalDate): Long {
        val days = range.daysElapsed(today).coerceAtLeast(1)
        return spend / days
    }

    /** Logging streak: consecutive days up to today (or yesterday) with at least one entry or a no-spend check-in. */
    fun streak(loggedDays: Set<Long>, today: LocalDate): Int {
        var d = today.toEpochDay()
        if (d !in loggedDays) d -= 1
        var n = 0
        while (d in loggedDays) { n++; d-- }
        return n
    }

    data class HealthScore(val total: Int, val budgetPart: Int, val savingsPart: Int, val habitPart: Int)

    /**
     * Transparent score out of 100:
     *  - Budgets (40): share of budgets within limit.
     *  - Savings (30): savings rate this cycle; 20% or more earns full marks.
     *  - Habit (30): share of days this cycle on which you logged something.
     */
    fun healthScore(adherencePct: Int?, savingsRatePct: Int?, loggedDaysInCycle: Int, daysElapsed: Int): HealthScore {
        val b = ((adherencePct ?: 50) * 40 + 50) / 100
        val s = (((savingsRatePct ?: 0).coerceIn(0, 20)) * 30 + 10) / 20
        val h = if (daysElapsed <= 0) 0 else (loggedDaysInCycle.coerceAtMost(daysElapsed) * 30 + daysElapsed / 2) / daysElapsed
        return HealthScore(b + s + h, b, s, h)
    }

    fun insights(
        txs: List<LedgerTx>,
        current: DateRange,
        previous: DateRange,
        cats: Map<String, CategoryMeta>,
        budgets: List<BudgetLine>,
        today: LocalDate,
        safeFree: Long?,
        periodName: String,
    ): List<Insight> {
        val out = ArrayList<Insight>()
        val cur = Ledger.spendingByCategory(txs, current)
        val prev = Ledger.spendingByCategory(txs, previous)
        val curTotal = cur.values.sum()
        val prevTotal = prev.values.sum()
        val elapsed = current.daysElapsed(today)
        val isRunning = today in current

        // Overall comparison (pro-rated if the period is still running).
        if (prevTotal > 0 && curTotal > 0) {
            val comparablePrev = if (isRunning) prevTotal * elapsed / current.days.coerceAtLeast(1) else prevTotal
            val diff = curTotal - comparablePrev
            if (comparablePrev > 0 && kotlin.math.abs(diff) * 100 / comparablePrev >= 10) {
                val more = diff > 0
                val when_ = if (isRunning) "than at this point last time" else "than the previous period"
                out += Insight(if (more) "📈" else "📉",
                    "You've spent ${Money.format(kotlin.math.abs(diff))} ${if (more) "more" else "less"} $when_.",
                    if (more) Insight.Tone.WARN else Insight.Tone.GOOD)
            }
        }

        // Biggest category movers.
        val movers = (cur.keys + prev.keys).map { id -> id to ((cur[id] ?: 0) - (prev[id] ?: 0)) }
            .filter { kotlin.math.abs(it.second) >= rupees(500) }
            .sortedByDescending { kotlin.math.abs(it.second) }
            .take(2)
        for ((id, d) in movers) {
            val c = cats[id] ?: continue
            out += Insight(c.emoji,
                "You spent ${Money.format(kotlin.math.abs(d))} ${if (d > 0) "more" else "less"} on ${c.name.lowercase()} than in the previous period.",
                if (d > 0) Insight.Tone.NEUTRAL else Insight.Tone.GOOD)
        }

        // Budgets
        for (b in budgets.sortedByDescending { it.status.percentUsed }.take(3)) {
            val s = b.status
            when (s.state) {
                BudgetState.OVER -> out += Insight("🚨", "${b.name} is over budget by ${Money.format(-s.remaining)}. (This is the category budget, not your bank balance.)", Insight.Tone.BAD)
                BudgetState.NEAR_LIMIT -> out += Insight(b.emoji, "${b.name} has used ${s.percentUsed}% of its budget — ${Money.format(s.remaining)} left for ${s.daysLeft} days.", Insight.Tone.WARN)
                BudgetState.FAST -> out += Insight("⏩", "${b.name} is spending fast: at this pace you'd reach about ${Money.format(s.projectedSpend)} (estimate) vs a ${Money.format(s.limit)} budget.", Insight.Tone.WARN)
                else -> {}
            }
        }

        // Top category share
        cur.maxByOrNull { it.value }?.let { (id, v) ->
            val c = cats[id]
            if (c != null && curTotal > 0) {
                out += Insight(c.emoji, "${c.name} is your biggest spend: ${Money.format(v)} (${Money.percent(v, curTotal)}% of $periodName).", Insight.Tone.NEUTRAL)
            }
        }

        safeFree?.let {
            out += if (it >= 0) Insight("🧘", "You have ${Money.format(it)} free to spend until your next salary after bills and planned savings.", Insight.Tone.GOOD)
            else Insight("⚠️", "Upcoming bills and savings plans exceed your spendable balance by ${Money.format(-it)}.", Insight.Tone.BAD)
        }

        // No-spend days
        if (isRunning && elapsed >= 5 && curTotal > 0) {
            val zero = dailySeries(txs, DateRange(current.start, today)).count { it.second == 0L }
            if (zero > 0) out += Insight("🌱", "$zero no-spend day${if (zero == 1) "" else "s"} so far in $periodName.", Insight.Tone.GOOD)
        }
        return out
    }
}
