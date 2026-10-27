package com.rupeeflow.app.data

import com.rupeeflow.core.AccountInfo
import com.rupeeflow.core.AccountType
import com.rupeeflow.core.Analytics
import com.rupeeflow.core.BillLite
import com.rupeeflow.core.Bills
import com.rupeeflow.core.BudgetLine
import com.rupeeflow.core.BudgetMath
import com.rupeeflow.core.BudgetPeriod
import com.rupeeflow.core.BudgetStatus
import com.rupeeflow.core.CategoryKeywords
import com.rupeeflow.core.CategoryKind
import com.rupeeflow.core.CategoryMeta
import com.rupeeflow.core.DateRange
import com.rupeeflow.core.DefaultCategories
import com.rupeeflow.core.Frequency
import com.rupeeflow.core.GoalMath
import com.rupeeflow.core.GoalProgress
import com.rupeeflow.core.Insight
import com.rupeeflow.core.Ledger
import com.rupeeflow.core.LedgerTx
import com.rupeeflow.core.PaymentMethod
import com.rupeeflow.core.PeriodKind
import com.rupeeflow.core.Periods
import com.rupeeflow.core.SafeToSpend
import com.rupeeflow.core.SafeToSpendCalc
import com.rupeeflow.core.SalaryCycle
import com.rupeeflow.core.Schedule
import com.rupeeflow.core.SubStatus
import com.rupeeflow.core.TxType
import java.time.LocalDate
import java.time.YearMonth

/* ---------- entity <-> domain helpers ---------- */

fun TransactionEntity.txType(): TxType = runCatching { TxType.valueOf(type) }.getOrDefault(TxType.EXPENSE)
fun TransactionEntity.paymentMethod(): PaymentMethod? = payment?.let { p -> runCatching { PaymentMethod.valueOf(p) }.getOrNull() }
fun TransactionEntity.date(): LocalDate = LocalDate.ofEpochDay(epochDay)
fun AccountEntity.accountType(): AccountType = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.BANK)
fun SubscriptionEntity.freq(): Frequency = runCatching { Frequency.valueOf(frequency) }.getOrDefault(Frequency.MONTHLY)
fun SubscriptionEntity.subStatus(): SubStatus = runCatching { SubStatus.valueOf(status) }.getOrDefault(SubStatus.ACTIVE)
fun BudgetEntity.budgetPeriod(): BudgetPeriod = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.SALARY_CYCLE)
fun CategoryEntity.categoryKind(): CategoryKind = runCatching { CategoryKind.valueOf(kind) }.getOrDefault(CategoryKind.EXPENSE)

fun AccountEntity.toInfo() = AccountInfo(id, name, accountType(), openingBalance, includeInSpendable, archived)

/* ---------- view models of derived data ---------- */

data class BudgetView(
    val budget: BudgetEntity,
    val name: String,
    val emoji: String,
    val color: Long,
    val range: DateRange,
    val status: BudgetStatus,
    val previousSpent: Long?,
)

data class BillView(
    val sub: SubscriptionEntity,
    val nextDue: LocalDate,
    val daysUntil: Int,
    val overdue: Boolean,
    val monthly: Long,
    val yearly: Long,
    val categoryName: String?,
    val skippedInARow: Int,
    val lastPaid: LocalDate?,
    val paidCount: Int,
)

data class GoalView(
    val goal: GoalEntity,
    val progress: GoalProgress,
    val contributions: List<TransactionEntity>,
    val accountName: String?,
)

data class ChallengeView(
    val challenge: ChallengeEntity,
    val current: Long,
    val target: Long,
    val percent: Int,
    val status: String,
    val detail: String,
)

data class QuickPick(
    val label: String,
    val type: TxType,
    val amount: Long,
    val categoryId: String?,
    val accountId: String,
    val payment: String?,
    val merchant: String?,
    val note: String?,
)

data class Snapshot(
    val today: LocalDate,
    val settings: Settings,
    val cycle: SalaryCycle,
    val cycleRange: DateRange,
    val accounts: List<AccountEntity>,
    val activeAccounts: List<AccountEntity>,
    val balances: Map<String, Long>,
    val position: Ledger.Position,
    val categories: List<CategoryEntity>,
    val categoryById: Map<String, CategoryEntity>,
    val transactions: List<TransactionEntity>,
    /** Everything dated today or earlier: the real ledger. */
    val effective: List<LedgerTx>,
    /** Future-dated entries: not yet counted anywhere. */
    val scheduled: List<TransactionEntity>,
    val spentToday: Long,
    val spentWeek: Long,
    val spentCycle: Long,
    val incomeCycle: Long,
    val savedCycle: Long,
    val budgets: List<BudgetView>,
    val budgetAllocated: Long,
    val budgetRemaining: Long,
    val bills: List<BillView>,
    val billsDueThisCycle: Long,
    val goals: List<GoalView>,
    val safe: SafeToSpend,
    val plannedSavingsLeft: Long,
    val nextSalaryDate: LocalDate,
    val daysToSalary: Int,
    val salaryPrompt: Boolean,
    val loggedToday: Boolean,
    val noSpendToday: Boolean,
    val streak: Int,
    val loggedDaysInCycle: Int,
    val quickPicks: List<QuickPick>,
    val recentCategoryIds: List<String>,
    val templates: List<TemplateEntity>,
    val rules: Map<String, String>,
    val keywords: List<CategoryKeywords>,
    val homeInsights: List<Insight>,
    val health: Analytics.HealthScore,
    val challenges: List<ChallengeView>,
    val payments: List<SubscriptionPaymentEntity>,
) {
    fun root(categoryId: String?): String? {
        var c = categoryId?.let { categoryById[it] } ?: return categoryId
        var guard = 0
        while (c.parentId != null && guard++ < 5) c = categoryById[c.parentId] ?: break
        return c.id
    }
    fun account(id: String?) = accounts.firstOrNull { it.id == id }
    fun category(id: String?) = id?.let { categoryById[it] }
    fun meta(): Map<String, CategoryMeta> = categories.associate { it.id to CategoryMeta(it.id, it.name, it.emoji, it.essential, it.fixed) }
}

object SnapshotBuilder {

    fun build(
        today: LocalDate,
        settings: Settings,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        txs: List<TransactionEntity>,
        budgets: List<BudgetEntity>,
        subs: List<SubscriptionEntity>,
        payments: List<SubscriptionPaymentEntity>,
        goals: List<GoalEntity>,
        rules: List<CategoryRuleEntity>,
        templates: List<TemplateEntity>,
        dayChecks: List<DayCheckEntity>,
        challenges: List<ChallengeEntity>,
    ): Snapshot {
        val todayDay = today.toEpochDay()
        val catById = categories.associateBy { it.id }
        fun rootOf(id: String?): String? {
            var c = id?.let { catById[it] } ?: return id
            var g = 0
            while (c.parentId != null && g++ < 5) c = catById[c.parentId] ?: break
            return c.id
        }
        val salaryIds = categories.filter { it.id == "salary" || it.parentId == "salary" }.map { it.id }.toSet()
        val salaryDates = txs.filter { it.txType() == TxType.INCOME && it.categoryId in salaryIds }.map { it.date() }.toSet()

        val cycle = SalaryCycle(settings.salaryDay, salaryDates, settings.followActualSalary)
        val cycleRange = cycle.cycleContaining(today)
        val infos = accounts.map { it.toInfo() }

        fun toLedger(t: TransactionEntity, rollUp: Boolean) = LedgerTx(
            id = t.id, type = t.txType(), amount = t.amount, accountId = t.accountId, toAccountId = t.toAccountId,
            categoryId = if (rollUp) rootOf(t.categoryId) else t.categoryId, epochDay = t.epochDay,
            merchant = t.merchant?.takeIf { it.isNotBlank() } ?: t.note?.takeIf { it.isNotBlank() },
            payment = t.paymentMethod(), subscriptionId = t.subscriptionId, goalId = t.goalId,
        )
        // Defensive: a malformed movement (no/same destination) is ignored rather than crashing every screen.
        val accountIds = accounts.map { it.id }.toSet()
        fun valid(t: TransactionEntity): Boolean {
            val ty = t.txType()
            if (t.accountId !in accountIds) return false
            return !ty.isMovement || (t.toAccountId != null && t.toAccountId != t.accountId && t.toAccountId in accountIds)
        }
        val all = txs.filter(::valid).map { toLedger(it, true) }
        val effective = all.filter { it.epochDay <= todayDay }
        val effectiveExact = txs.filter { it.epochDay <= todayDay && valid(it) }.map { toLedger(it, false) }
        val scheduled = txs.filter { it.epochDay > todayDay }

        val balances = Ledger.balances(infos, all, todayDay)
        val position = Ledger.position(infos, balances)

        val week = Periods.weekContaining(today, settings.firstDayOfWeek)
        val spentToday = Ledger.spending(effective, DateRange.single(today))
        val spentWeek = Ledger.spending(effective, week)
        val spentCycle = Ledger.spending(effective, cycleRange)
        val incomeCycle = Ledger.income(effective, cycleRange)
        val savedCycle = Ledger.savedAndInvested(effective, cycleRange, infos)

        // Budgets
        val budgetViews = budgets.filter { it.active }.mapNotNull { b ->
            val cat = b.categoryId?.let { catById[it] }
            if (b.categoryId != null && cat == null) return@mapNotNull null
            val period = b.budgetPeriod()
            val range = Periods.budgetRange(period, today, cycle, settings.firstDayOfWeek,
                b.customStart?.let { LocalDate.ofEpochDay(it) }, b.customEnd?.let { LocalDate.ofEpochDay(it) })
            val isRoot = cat == null || cat.parentId == null
            val source = if (isRoot) effective else effectiveExact
            val ids = b.categoryId?.let { setOf(it) }
            val spent = Ledger.spending(source, range, ids)
            val prevRange = Periods.previousBudgetRange(period, range, cycle, settings.firstDayOfWeek)
            val prevSpent = prevRange?.let { Ledger.spending(source, it, ids) }
            val carry = if (b.rollover && prevSpent != null) BudgetMath.carryOver(b.amount, prevSpent) else 0L
            val status = BudgetMath.status(b.amount, spent, range, today, BudgetMath.parseThresholds(b.thresholds), carry)
            BudgetView(b, cat?.name ?: "Overall spending", cat?.emoji ?: "🎯", cat?.color ?: 0xFF5B4CF0, range, status, prevSpent)
        }.sortedWith(compareBy<BudgetView> { it.budget.categoryId != null }.thenByDescending { it.status.percentUsed })
        val catBudgets = budgetViews.filter { it.budget.categoryId != null }
        val allocated = catBudgets.sumOf { it.status.limit }
        val remaining = catBudgets.sumOf { maxOf(0L, it.status.remaining) }

        // Bills
        val payBySub = payments.groupBy { it.subscriptionId }
        val billViews = subs.map { s ->
            val due = LocalDate.ofEpochDay(s.nextDue)
            val hist = (payBySub[s.id] ?: emptyList()).sortedByDescending { it.dueEpochDay }
            val skippedRun = hist.takeWhile { it.status == "SKIPPED" }.size
            BillView(
                sub = s, nextDue = due, daysUntil = Schedule.daysUntil(today, due).toInt(),
                overdue = due.isBefore(today) && s.subStatus() == SubStatus.ACTIVE,
                monthly = Schedule.monthlyEquivalent(s.amount, s.freq(), s.intervalDays),
                yearly = Schedule.yearlyCost(s.amount, s.freq(), s.intervalDays),
                categoryName = s.categoryId?.let { catById[it]?.name },
                skippedInARow = skippedRun,
                lastPaid = hist.firstOrNull { it.status == "PAID" }?.let { LocalDate.ofEpochDay(it.dueEpochDay) },
                paidCount = hist.count { it.status == "PAID" },
            )
        }.sortedBy { it.nextDue }
        val activeLite = subs.map { BillLite(it.id, it.amount, LocalDate.ofEpochDay(it.nextDue), it.freq(), it.intervalDays, it.anchorDay, it.subStatus() == SubStatus.ACTIVE) }
        val billsDue = Bills.dueUntil(activeLite, today, cycleRange.end)

        // Goals: progress only from real transfers tagged with the goal.
        val goalViews = goals.filter { !it.archived }.map { g ->
            val contribs = txs.filter { it.goalId == g.id && it.epochDay <= todayDay }
            var saved = g.initialSaved
            for (c in contribs) {
                val t = c.txType()
                if (!t.isMovement) continue
                // Money moving into the goal's account (or any movement tagged when no account set) adds; out subtracts.
                saved += if (g.accountId != null && c.accountId == g.accountId && c.toAccountId != g.accountId) -c.amount else c.amount
            }
            GoalView(g, GoalMath.progress(g.target, maxOf(0L, saved), today, g.targetEpochDay?.let { LocalDate.ofEpochDay(it) }, cycle),
                contribs, g.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name })
        }

        val plannedLeft = maxOf(0L, settings.plannedSavingsPerCycle - savedCycle)
        val safe = SafeToSpendCalc.compute(position.spendable, billsDue + position.cardDues, plannedLeft, cycleRange.daysLeft(today), spentToday)

        val nextSalary = cycle.nextSalaryDate(today)
        val daysToSalary = Schedule.daysUntil(today, nextSalary).toInt()
        val recentSalary = salaryDates.any { !it.isAfter(today) && !it.isBefore(today.minusDays(10)) }
        val receivedThisCycle = salaryDates.any { it in cycleRange }
        val salaryPrompt = settings.expectedSalary > 0 && !recentSalary &&
            ((!receivedThisCycle && cycleRange.daysElapsed(today) <= 10) || daysToSalary <= 2)

        // Logging habit
        val loggedDays = HashSet<Long>()
        txs.forEach { loggedDays.add(it.epochDay) }
        dayChecks.forEach { loggedDays.add(it.epochDay) }
        val streak = Analytics.streak(loggedDays, today)
        val loggedInCycle = loggedDays.count { it in cycleRange.startDay..todayDay }

        // Quick picks: the user's own most repeated entries over the last 90 days.
        val since = todayDay - 90
        val picks = txs.asSequence()
            .filter { it.epochDay >= since && it.txType() == TxType.EXPENSE && it.subscriptionId == null && it.importFingerprint == null }
            .groupBy { Triple(it.categoryId, it.amount, (it.merchant ?: it.note ?: "").trim().lowercase()) }
            .filter { it.value.size >= 2 }
            .entries.sortedWith(compareByDescending<Map.Entry<Triple<String?, Long, String>, List<TransactionEntity>>> { it.value.size }
                .thenByDescending { e -> e.value.maxOf { it.createdAt } })
            .take(8)
            .map { (_, list) ->
                val t = list.maxBy { it.createdAt }
                val name = t.merchant?.takeIf { it.isNotBlank() } ?: t.note?.takeIf { it.isNotBlank() } ?: catById[t.categoryId]?.name ?: "Expense"
                QuickPick(name, TxType.EXPENSE, t.amount, t.categoryId, t.accountId, t.payment, t.merchant, t.note)
            }.toList()
        val recentCats = txs.asSequence().filter { it.txType() == TxType.EXPENSE }.mapNotNull { it.categoryId }.distinct().take(12).toList()

        val ruleMap = rules.associate { it.keyword to it.categoryId }
        val keywordList = categories.filter { !it.archived }.map { c ->
            val defaults = DefaultCategories.keywordsFor(c.id)
            CategoryKeywords(c.id, defaults + c.name.lowercase(), c.categoryKind())
        }

        val meta = categories.associate { it.id to CategoryMeta(it.id, it.name, it.emoji, it.essential, it.fixed) }
        val prevCycle = cycle.previous(cycleRange)
        val lines = catBudgets.map { BudgetLine(it.name, it.emoji, it.status) }
        val homeInsights = Analytics.insights(effective, cycleRange, prevCycle, meta, lines, today, safe.free, "this cycle")

        val adherence = Analytics.budgetAdherence(catBudgets.map { it.status })
        val savingsRate = Ledger.savingsRate(incomeCycle, spentCycle)
        val health = Analytics.healthScore(adherence, savingsRate, loggedInCycle, cycleRange.daysElapsed(today))

        val challengeViews = challenges.filter { it.active }.map { buildChallenge(it, effective, loggedDays, dayChecks, today) }

        return Snapshot(
            today = today, settings = settings, cycle = cycle, cycleRange = cycleRange,
            accounts = accounts, activeAccounts = accounts.filter { !it.archived },
            balances = balances, position = position,
            categories = categories, categoryById = catById,
            transactions = txs, effective = effective, scheduled = scheduled,
            spentToday = spentToday, spentWeek = spentWeek, spentCycle = spentCycle,
            incomeCycle = incomeCycle, savedCycle = savedCycle,
            budgets = budgetViews, budgetAllocated = allocated, budgetRemaining = remaining,
            bills = billViews, billsDueThisCycle = billsDue,
            goals = goalViews, safe = safe, plannedSavingsLeft = plannedLeft,
            nextSalaryDate = nextSalary, daysToSalary = daysToSalary, salaryPrompt = salaryPrompt,
            loggedToday = todayDay in loggedDays, noSpendToday = dayChecks.any { it.epochDay == todayDay },
            streak = streak, loggedDaysInCycle = loggedInCycle,
            quickPicks = picks, recentCategoryIds = recentCats, templates = templates,
            rules = ruleMap, keywords = keywordList, homeInsights = homeInsights.take(4),
            health = health, challenges = challengeViews, payments = payments,
        )
    }

    private fun buildChallenge(c: ChallengeEntity, effective: List<LedgerTx>, logged: Set<Long>, checks: List<DayCheckEntity>, today: LocalDate): ChallengeView {
        val range = DateRange.ofDays(c.startEpochDay, maxOf(c.startEpochDay, c.endEpochDay))
        val ended = today.isAfter(range.end)
        val upto = if (ended) range.end else today
        return when (c.kind) {
            "NO_SPEND_DAYS" -> {
                val days = if (today.isBefore(range.start)) emptyList() else Analytics.dailySeries(effective, DateRange(range.start, upto))
                val zero = days.count { (d, v) -> v == 0L && (d.toEpochDay() in logged) }
                val target = c.target.toLong().coerceAtLeast(1)
                ChallengeView(c, zero.toLong(), target, (zero * 100 / target).toInt().coerceAtMost(100),
                    if (zero >= target) "DONE" else if (ended) "ENDED" else "ACTIVE",
                    "$zero of ${c.target} no-spend days (only days you checked in or logged count)")
            }
            "CATEGORY_CAP" -> {
                val spent = Ledger.spending(effective, range, c.categoryId?.let { setOf(it) })
                val cap = c.amount ?: 0
                val pct = com.rupeeflow.core.Money.percent(spent, cap)
                ChallengeView(c, spent, cap, pct.coerceAtMost(100),
                    if (spent > cap) "MISSED" else if (ended) "DONE" else "ACTIVE",
                    "${com.rupeeflow.core.Money.format(spent)} of ${com.rupeeflow.core.Money.format(cap)}")
            }
            else -> { // LOG_DAYS
                val n = (range.startDay..upto.toEpochDay()).count { it in logged }
                val target = c.target.toLong().coerceAtLeast(1)
                ChallengeView(c, n.toLong(), target, (n * 100 / target).toInt().coerceAtMost(100),
                    if (n >= target) "DONE" else if (ended) "ENDED" else "ACTIVE", "Logged on $n of ${c.target} days")
            }
        }
    }
}

/* ---------- Insights screen data ---------- */

data class InsightsData(
    val kind: PeriodKind,
    val range: DateRange,
    val previous: DateRange,
    val spent: Long,
    val previousSpent: Long,
    val income: Long,
    val saved: Long,
    val savingsRate: Int?,
    val avgDaily: Long,
    val byCategory: List<Pair<CategoryEntity?, Long>>,
    val daily: List<Pair<LocalDate, Long>>,
    val trend: List<Pair<String, Long>>,
    val incomeTrend: List<Pair<String, Long>>,
    val essential: Analytics.Split,
    val fixed: Analytics.Split,
    val byPayment: List<Pair<PaymentMethod, Long>>,
    val byAccount: List<Pair<String, Long>>,
    val merchants: List<Pair<String, Long>>,
    val unusual: List<TransactionEntity>,
    val subscriptionsSpent: Long,
    val subscriptionTrend: List<Pair<String, Long>>,
    val adherence: Int?,
    val insights: List<Insight>,
)

object InsightsBuilder {
    fun build(s: Snapshot, kind: PeriodKind, custom: DateRange?): InsightsData {
        val range = Periods.resolve(kind, s.today, s.cycle, s.settings.firstDayOfWeek, custom)
        val prev = Periods.previousOf(kind, range, s.cycle, s.settings.firstDayOfWeek)
        val e = s.effective
        val infos = s.accounts.map { it.toInfo() }
        val spent = Ledger.spending(e, range)
        val prevSpent = Ledger.spending(e, prev)
        val income = Ledger.income(e, range)
        val saved = Ledger.savedAndInvested(e, range, infos)
        val byCat = Ledger.spendingByCategory(e, range)
        val meta = s.meta()

        // Trend: last 6 salary cycles (or months if the period is calendar-based).
        val calendar = kind in setOf(PeriodKind.THIS_MONTH, PeriodKind.LAST_MONTH, PeriodKind.LAST_3_MONTHS, PeriodKind.LAST_6_MONTHS, PeriodKind.LAST_12_MONTHS)
        val buckets = ArrayList<Pair<String, DateRange>>()
        if (calendar) {
            var ym = YearMonth.from(s.today)
            repeat(if (kind == PeriodKind.LAST_12_MONTHS) 12 else 6) {
                buckets.add(0, ym.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } to DateRange(ym.atDay(1), ym.atEndOfMonth()))
                ym = ym.minusMonths(1)
            }
        } else {
            var r = s.cycleRange
            repeat(6) {
                buckets.add(0, (r.start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + r.start.dayOfMonth) to r)
                r = s.cycle.previous(r)
            }
        }
        val trend = buckets.map { (label, r) -> label to Ledger.spending(e, r) }
        val incomeTrend = buckets.map { (label, r) -> label to Ledger.income(e, r) }
        val subTrend = buckets.map { (label, r) -> label to e.filter { it.subscriptionId != null && it.type == TxType.EXPENSE && r.contains(it.epochDay) }.sumOf { it.amount } }

        val subsSpent = e.filter { it.subscriptionId != null && it.type == TxType.EXPENSE && range.contains(it.epochDay) }.sumOf { it.amount }
        val unusualIds = Analytics.unusual(e.filter { it.epochDay >= range.startDay - 120 && it.epochDay <= range.endDay }, range).map { it.id }.toSet()

        val budgetLines = s.budgets.filter { it.budget.categoryId != null }
        val insights = Analytics.insights(e, range, prev, meta, budgetLines.map { BudgetLine(it.name, it.emoji, it.status) },
            s.today, if (kind == PeriodKind.SALARY_CYCLE) s.safe.free else null, kind.label.lowercase())

        val catName = s.accounts.associate { it.id to it.name }
        return InsightsData(
            kind = kind, range = range, previous = prev,
            spent = spent, previousSpent = prevSpent, income = income, saved = saved,
            savingsRate = Ledger.savingsRate(income, spent),
            avgDaily = Analytics.averageDaily(spent, range, s.today),
            byCategory = byCat.entries.sortedByDescending { it.value }.map { s.categoryById[it.key] to it.value },
            daily = Analytics.dailySeries(e, if (range.days > 62) DateRange(maxOf(range.start, range.end.minusDays(61)), range.end) else range),
            trend = trend, incomeTrend = incomeTrend,
            essential = Analytics.essentialSplit(byCat, meta),
            fixed = Analytics.fixedSplit(byCat, meta, subsSpent),
            byPayment = Analytics.byPayment(e, range).entries.sortedByDescending { it.value }.map { it.key to it.value },
            byAccount = Analytics.byAccount(e, range).entries.sortedByDescending { it.value }.map { (catName[it.key] ?: "Account") to it.value },
            merchants = Analytics.topMerchants(e, range, 6),
            unusual = s.transactions.filter { it.id in unusualIds },
            subscriptionsSpent = subsSpent,
            subscriptionTrend = subTrend,
            adherence = Analytics.budgetAdherence(budgetLines.map { it.status }),
            insights = insights,
        )
    }
}
