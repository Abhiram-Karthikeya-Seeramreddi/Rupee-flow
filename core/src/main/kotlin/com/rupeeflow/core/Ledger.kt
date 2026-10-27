package com.rupeeflow.core

/**
 * The single source of truth for how a transaction changes account balances.
 *
 * Invariants (all covered by unit tests):
 *  - A transfer/savings/investment moves money between two accounts: net worth unchanged,
 *    never counted as income or expense.
 *  - A credit-card purchase changes only the card balance (owed amount grows). Your bank
 *    balance changes only when you pay the bill, which is a TRANSFER bank -> card and is
 *    therefore not counted as consumption a second time.
 *  - Transactions dated in the future are "scheduled" and do not touch balances until
 *    their date arrives.
 */
object Ledger {

    fun effects(tx: LedgerTx): List<Pair<String, Long>> = when (tx.type) {
        TxType.EXPENSE -> listOf(tx.accountId to -tx.amount)
        TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> listOf(tx.accountId to tx.amount)
        TxType.TRANSFER, TxType.SAVINGS, TxType.INVESTMENT -> {
            val to = requireNotNull(tx.toAccountId) { "Movement ${tx.id} has no destination account" }
            require(to != tx.accountId) { "Movement ${tx.id} has the same source and destination" }
            listOf(tx.accountId to -tx.amount, to to tx.amount)
        }
        TxType.ADJUSTMENT -> listOf(tx.accountId to tx.amount)
    }

    /** Balance of every account as of [asOfEpochDay] (inclusive). */
    fun balances(
        accounts: List<AccountInfo>,
        txs: List<LedgerTx>,
        asOfEpochDay: Long = Long.MAX_VALUE,
    ): Map<String, Long> {
        val result = HashMap<String, Long>()
        accounts.forEach { result[it.id] = it.openingBalance }
        for (tx in txs) {
            if (tx.epochDay > asOfEpochDay) continue
            for ((acc, delta) in effects(tx)) {
                result[acc] = (result[acc] ?: 0L) + delta
            }
        }
        return result
    }

    data class Position(
        /** Money in bank/cash/wallets that you've marked as spendable. */
        val spendable: Long,
        /** Savings-type accounts (emergency fund, RD, separate savings account). */
        val savings: Long,
        val investments: Long,
        /** Total outstanding on credit cards, as a positive number. */
        val cardDues: Long,
        /** Everything you own minus what you owe. */
        val netWorth: Long,
    )

    fun position(accounts: List<AccountInfo>, balances: Map<String, Long>): Position {
        var spendable = 0L; var savings = 0L; var invest = 0L; var dues = 0L; var net = 0L
        for (a in accounts) {
            if (a.archived && (balances[a.id] ?: 0L) == 0L) continue
            val b = balances[a.id] ?: a.openingBalance
            net += b
            when (a.type) {
                AccountType.CREDIT_CARD -> if (b < 0) dues += -b
                AccountType.INVESTMENT -> invest += b
                AccountType.SAVINGS -> savings += b
                else -> if (a.includeInSpendable) spendable += b else savings += b
            }
        }
        return Position(spendable, savings, invest, dues, net)
    }

    private fun inRange(tx: LedgerTx, range: DateRange) = tx.epochDay in range.startDay..range.endDay

    /** Net consumption: expenses minus refunds/reimbursements, never below zero per category. */
    fun spending(txs: List<LedgerTx>, range: DateRange, categoryIds: Set<String>? = null): Long {
        return spendingByCategory(txs, range)
            .filterKeys { categoryIds == null || it in categoryIds }
            .values.sum()
    }

    fun spendingByCategory(txs: List<LedgerTx>, range: DateRange): Map<String, Long> {
        val map = HashMap<String, Long>()
        for (tx in txs) {
            if (!inRange(tx, range)) continue
            val key = tx.categoryId ?: UNCATEGORIZED
            when {
                tx.type == TxType.EXPENSE -> map[key] = (map[key] ?: 0L) + tx.amount
                tx.type.isConsumptionOffset && tx.categoryId != null -> map[key] = (map[key] ?: 0L) - tx.amount
            }
        }
        return map.mapValues { maxOf(0L, it.value) }.filterValues { it > 0 }
    }

    /** Gross expenses (before refunds). */
    fun grossExpenses(txs: List<LedgerTx>, range: DateRange): Long =
        txs.filter { it.type == TxType.EXPENSE && inRange(it, range) }.sumOf { it.amount }

    fun income(txs: List<LedgerTx>, range: DateRange): Long =
        txs.filter { it.type == TxType.INCOME && inRange(it, range) }.sumOf { it.amount }

    /** Money moved into savings/investment accounts minus money taken back out. */
    fun savedAndInvested(txs: List<LedgerTx>, range: DateRange, accounts: List<AccountInfo>): Long {
        val savingAccounts = accounts.filter {
            it.type == AccountType.SAVINGS || it.type == AccountType.INVESTMENT ||
                (!it.includeInSpendable && it.type != AccountType.CREDIT_CARD)
        }.map { it.id }.toSet()
        var total = 0L
        for (tx in txs) {
            if (!tx.type.isMovement || !inRange(tx, range)) continue
            val toSaving = tx.toAccountId in savingAccounts
            val fromSaving = tx.accountId in savingAccounts
            if (toSaving && !fromSaving) total += tx.amount
            if (fromSaving && !toSaving) total -= tx.amount
        }
        return total
    }

    /** Savings rate = (income - consumption) / income, as a whole percent; null without income. */
    fun savingsRate(income: Long, spending: Long): Int? {
        if (income <= 0) return null
        return Money.percent(income - spending, income)
    }

    /** Delta needed so that recorded balance matches what the bank actually shows. */
    fun reconciliationDelta(recorded: Long, actual: Long): Long = actual - recorded

    const val UNCATEGORIZED = "uncategorized"
}
