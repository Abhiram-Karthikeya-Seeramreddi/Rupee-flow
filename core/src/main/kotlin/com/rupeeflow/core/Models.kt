package com.rupeeflow.core

/**
 * Transaction types. Only EXPENSE counts as consumption. REFUND and REIMBURSEMENT
 * reduce consumption in their category. TRANSFER / SAVINGS / INVESTMENT move money
 * between two of your own accounts and are never income or expense.
 */
enum class TxType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    SAVINGS("Savings"),
    INVESTMENT("Investment"),
    REFUND("Refund"),
    REIMBURSEMENT("Reimbursement"),
    ADJUSTMENT("Balance adjustment");

    val isMovement: Boolean get() = this == TRANSFER || this == SAVINGS || this == INVESTMENT
    val isConsumptionOffset: Boolean get() = this == REFUND || this == REIMBURSEMENT
    val needsCategory: Boolean get() = this == EXPENSE || this == REFUND || this == REIMBURSEMENT || this == INCOME
}

enum class AccountType(val label: String, val spendableByDefault: Boolean, val isLiability: Boolean = false) {
    BANK("Bank account", true),
    CASH("Cash", true),
    WALLET("Digital wallet", true),
    CREDIT_CARD("Credit card", false, isLiability = true),
    SAVINGS("Savings / emergency fund", false),
    INVESTMENT("Investments", false),
}

enum class PaymentMethod(val label: String) {
    UPI("UPI"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    CASH("Cash"),
    NET_BANKING("Net banking"),
    WALLET("Wallet"),
    AUTOPAY("Auto-debit"),
    OTHER("Other"),
}

enum class Frequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    HALF_YEARLY("Half-yearly"),
    YEARLY("Yearly"),
    CUSTOM_DAYS("Every N days"),
}

enum class SubStatus(val label: String) { ACTIVE("Active"), PAUSED("Paused"), CANCELLED("Cancelled") }

enum class BudgetPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Calendar month"),
    SALARY_CYCLE("Salary cycle"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    CUSTOM("Custom dates"),
}

/** Minimal, Android-free view of a stored transaction used by all calculations. */
data class LedgerTx(
    val id: String,
    val type: TxType,
    /** Always positive, except ADJUSTMENT which carries a signed delta. */
    val amount: Long,
    val accountId: String,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val epochDay: Long,
    val merchant: String? = null,
    val payment: PaymentMethod? = null,
    val subscriptionId: String? = null,
    val goalId: String? = null,
)

data class AccountInfo(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: Long,
    val includeInSpendable: Boolean,
    val archived: Boolean = false,
)
