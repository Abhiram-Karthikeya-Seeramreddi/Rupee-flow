package com.rupeeflow.app.data

import com.rupeeflow.core.DateRange
import com.rupeeflow.core.Money
import java.time.LocalDate

object CsvExporter {

    private fun esc(s: String?): String {
        val v = s ?: ""
        // Prevent spreadsheet formula injection from merchant/note text.
        val safe = if (v.isNotEmpty() && v[0] in "=+-@") "'$v" else v
        return if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun export(
        txs: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        range: DateRange?,
        categoryIds: Set<String>?,
    ): String {
        val acc = accounts.associateBy { it.id }
        val cat = categories.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("Date,Time,Type,Amount (INR),Category,Subcategory,Account,To account,Payment method,Merchant,Note,Tags,Bill,ID\n")
        txs.asSequence()
            .filter { range == null || range.contains(it.epochDay) }
            .filter { t ->
                categoryIds == null || t.categoryId in categoryIds || cat[t.categoryId]?.parentId in categoryIds
            }
            .sortedWith(compareBy<TransactionEntity> { it.epochDay }.thenBy { it.minuteOfDay })
            .forEach { t ->
                val c = cat[t.categoryId]
                val parent = c?.parentId?.let { cat[it] }
                val catName = parent?.name ?: c?.name
                val subName = if (parent != null) c?.name else null
                val sign = when (t.txType()) {
                    com.rupeeflow.core.TxType.EXPENSE -> -1
                    com.rupeeflow.core.TxType.ADJUSTMENT -> 1
                    else -> 1
                }
                val amt = Money.toInput(t.amount).let { if (sign < 0 || t.amount < 0) "-$it" else it }
                sb.append(LocalDate.ofEpochDay(t.epochDay)).append(',')
                    .append(String.format("%02d:%02d", t.minuteOfDay / 60, t.minuteOfDay % 60)).append(',')
                    .append(esc(t.txType().label)).append(',')
                    .append(amt).append(',')
                    .append(esc(catName)).append(',')
                    .append(esc(subName)).append(',')
                    .append(esc(acc[t.accountId]?.name)).append(',')
                    .append(esc(acc[t.toAccountId]?.name)).append(',')
                    .append(esc(t.paymentMethod()?.label)).append(',')
                    .append(esc(t.merchant)).append(',')
                    .append(esc(t.note)).append(',')
                    .append(esc(t.tags.replace(",", ";"))).append(',')
                    .append(if (t.subscriptionId != null) "yes" else "").append(',')
                    .append(t.id).append('\n')
            }
        return sb.toString()
    }
}
