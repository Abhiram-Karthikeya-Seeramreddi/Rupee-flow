package com.rupeeflow.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** A row found in an external source (bank CSV, SMS) waiting for the user's review. */
data class ImportedRow(
    val date: LocalDate,
    val description: String,
    val amount: Long,
    val isDebit: Boolean,
    val reference: String? = null,
    val accountHint: String? = null,
) {
    /** Stable fingerprint used to skip rows that were already imported. */
    val fingerprint: String
        get() = reference?.takeIf { it.length >= 6 }?.let { "ref:$it" }
            ?: "${date.toEpochDay()}|$amount|${if (isDebit) "D" else "C"}|${normalize(description)}"

    companion object {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "").take(24)
    }
}

data class CsvParseResult(val rows: List<ImportedRow>, val skippedLines: Int, val error: String? = null)

object CsvImport {

    private val dateFormats: List<DateTimeFormatter> = listOf(
        "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy", "dd.MM.yyyy", "dd/MM/yy", "dd-MM-yy",
        "yyyy-MM-dd", "yyyy/MM/dd", "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy", "dd-MMM-yy", "d-MMM-yy",
        "dd MMM yy", "MMM dd, yyyy",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim().trim('"')
        if (s.isEmpty()) return null
        // Drop a trailing time, e.g. "2026-10-02 14:33:10", "02/10/2026 14:33", "2026-10-02T14:33"
        val noTime = s.replace(Regex("""[T\s]+\d{1,2}:\d{2}(:\d{2})?(\s*[AaPp][Mm])?.*$"""), "").trim()
        for (candidate in listOf(s, noTime).distinct()) {
            for (f in dateFormats) {
                try { return LocalDate.parse(candidate, f) } catch (_: Exception) {}
            }
        }
        return null
    }

    /** RFC-4180-ish CSV line splitting with quotes. Supports , ; and tab delimiters. */
    fun splitLine(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out.add(sb.toString().trim()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(30)
        return listOf(',', ';', '\t', '|').maxByOrNull { d -> sample.sumOf { l -> l.count { it == d } } } ?: ','
    }

    private fun amountOf(s: String): Long? {
        val t = s.replace(Regex("(?i)(cr|dr|inr|rs\\.?|₹)"), "").replace(",", "").replace(" ", "").trim()
        if (t.isEmpty() || t == "-" || t == "0" || t == "0.00") return null
        val neg = t.startsWith("-") || (t.startsWith("(") && t.endsWith(")"))
        val clean = t.trim('(', ')', '-', '+')
        val v = Money.parse(clean) ?: return null
        return if (neg) -v else v
    }

    fun parse(text: String): CsvParseResult {
        val lines = text.replace("﻿", "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvParseResult(emptyList(), 0, "The file is empty.")
        val delim = detectDelimiter(lines)

        // Find the header row: banks often put account details above the table.
        var headerIdx = -1
        var cols: List<String> = emptyList()
        for ((i, l) in lines.take(40).withIndex()) {
            val c = splitLine(l, delim).map { it.lowercase() }
            val hasDate = c.any { it.contains("date") }
            val hasMoney = c.any { it.contains("amount") || it.contains("debit") || it.contains("withdraw") || it.contains("credit") || it.contains("deposit") }
            if (hasDate && hasMoney) { headerIdx = i; cols = c; break }
        }
        if (headerIdx < 0) return CsvParseResult(emptyList(), lines.size, "Couldn't find a header row with Date and Amount/Debit/Credit columns.")

        fun idx(vararg keys: String, exclude: String? = null) = cols.indexOfFirst { h -> keys.any { h.contains(it) } && (exclude == null || !h.contains(exclude)) }
        val dateCol = cols.indexOfFirst { it.contains("txn date") || it.contains("transaction date") }.takeIf { it >= 0 } ?: idx("date", exclude = "value")
            .takeIf { it >= 0 } ?: idx("date")
        val descCol = idx("narration", "description", "particular", "remark", "details", "merchant", "payee", "name")
        val debitCol = idx("debit", "withdraw", "dr amount", "paid out")
        val creditCol = idx("credit", "deposit", "cr amount", "paid in")
        val amountCol = idx("amount", exclude = "balance")
        val typeCol = cols.indexOfFirst { it == "type" || it.contains("dr/cr") || it.contains("cr/dr") || it == "dr / cr" }
        val refCol = idx("ref", "utr", "chq", "cheque")

        val rows = ArrayList<ImportedRow>()
        var skipped = 0
        for (l in lines.drop(headerIdx + 1)) {
            val c = splitLine(l, delim)
            fun at(i: Int) = if (i >= 0 && i < c.size) c[i] else ""
            val date = parseDate(at(dateCol))
            if (date == null) { skipped++; continue }
            val desc = at(descCol).ifBlank { "Imported transaction" }
            val ref = at(refCol).takeIf { it.isNotBlank() && it != "0" }
            val debit = amountOf(at(debitCol))?.let { kotlin.math.abs(it) }
            val credit = amountOf(at(creditCol))?.let { kotlin.math.abs(it) }
            when {
                debitCol >= 0 && debitCol != creditCol && debit != null && debit > 0 -> rows.add(ImportedRow(date, desc, debit, true, ref))
                creditCol >= 0 && debitCol != creditCol && credit != null && credit > 0 -> rows.add(ImportedRow(date, desc, credit, false, ref))
                amountCol >= 0 -> {
                    val a = amountOf(at(amountCol))
                    if (a == null) { skipped++; continue }
                    val t = at(typeCol).lowercase()
                    val isDebit = when {
                        t.startsWith("dr") || t.contains("debit") -> true
                        t.startsWith("cr") || t.contains("credit") -> false
                        at(amountCol).lowercase().contains("dr") -> true
                        at(amountCol).lowercase().contains("cr") -> false
                        else -> a < 0
                    }
                    rows.add(ImportedRow(date, desc, kotlin.math.abs(a), isDebit, ref))
                }
                else -> skipped++
            }
        }
        return CsvParseResult(rows, skipped, if (rows.isEmpty()) "No transactions found in the file." else null)
    }
}

/**
 * Parses Indian bank / UPI SMS alerts. Only used when the user taps "Scan SMS" and grants
 * permission; every result is shown for review before anything is saved.
 */
object SmsParser {

    private val amountRx = Regex("""(?i)(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""")
    private val debitRx = Regex("""(?i)\b(debited|spent|paid|sent|withdrawn|purchase|txn of|transferred|deducted)\b""")
    private val creditRx = Regex("""(?i)\b(credited|received|deposited|refunded)\b""")
    private val ignoreRx = Regex(
        """(?i)\b(otp|one time password|will be debited|due on|due date|minimum amount|min amt|requested|request|payment reminder|statement|offer|cashback upto|pre-approved|loan offer|limit|declined|failed|reversed|emi of .* due)\b""",
    )
    private val upiToRx = Regex("""(?i)(?:to|trf to|vpa)\s+([a-z0-9.\-_]+@[a-z]+)""")
    private val atRx = Regex("""(?i)\b(?:at|to|towards|info:?)\s+([A-Za-z][A-Za-z0-9 &.'*\-]{2,30}?)(?=\s+(?:on|via|ref|upi|avl|avbl|\.|,)|[.,]|$)""")
    private val refRx = Regex("""(?i)(?:ref(?:erence)?\.?\s*(?:no\.?|num(?:ber)?)?|utr|upi ref|rrn)[:\s.]*([0-9]{6,20})""")
    private val acctRx = Regex("""(?i)(?:a/c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*)?[x*]*(\d{3,6})""")

    fun parse(body: String, date: LocalDate): ImportedRow? {
        if (ignoreRx.containsMatchIn(body)) return null
        val amt = amountRx.find(body)?.groupValues?.get(1)?.replace(",", "")?.let { Money.parse(it) } ?: return null
        val debitPos = debitRx.find(body)?.range?.first
        val creditPos = creditRx.find(body)?.range?.first
        val isDebit = when {
            debitPos != null && creditPos != null -> debitPos < creditPos
            debitPos != null -> true
            creditPos != null -> false
            else -> return null
        }
        val merchant = upiToRx.find(body)?.groupValues?.get(1)
            ?: atRx.find(body)?.groupValues?.get(1)?.trim()
            ?: if (isDebit) "Bank debit" else "Bank credit"
        val ref = refRx.find(body)?.groupValues?.get(1)
        val acct = acctRx.find(body)?.groupValues?.get(1)
        return ImportedRow(date, cleanMerchant(merchant), amt, isDebit, ref, acct)
    }

    private fun cleanMerchant(m: String): String {
        val base = if (m.contains("@")) m.substringBefore("@") else m
        return base.replace(Regex("[._\\-]+"), " ").trim().split(" ")
            .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } }
            .take(40)
    }
}
