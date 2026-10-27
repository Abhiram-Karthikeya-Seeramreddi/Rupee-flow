package com.rupeeflow.core

import kotlin.math.abs

/**
 * All money in RupeeFlow is stored as whole paise in a [Long].
 * ₹1 = 100 paise. No floating point is ever used for stored amounts.
 */
object Money {

    /** Formats paise as rupees, e.g. 12500000 -> "₹1,25,000". */
    fun format(
        paise: Long,
        indianGrouping: Boolean = true,
        showPaise: Boolean? = null,
        showSign: Boolean = false,
        symbol: String = "₹",
    ): String {
        val negative = paise < 0
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else abs(paise)
        val rupees = absPaise / 100
        val fraction = absPaise % 100
        val withPaise = showPaise ?: (fraction != 0L)
        val grouped = if (indianGrouping) groupIndian(rupees) else groupWestern(rupees)
        val body = if (withPaise) "$grouped.${fraction.toString().padStart(2, '0')}" else grouped
        val sign = when {
            negative -> "-"
            showSign && paise > 0 -> "+"
            else -> ""
        }
        return "$sign$symbol$body"
    }

    /** Short form for charts and tight spaces: ₹950, ₹12.5K, ₹1.25L, ₹2.1Cr. */
    fun compact(paise: Long, symbol: String = "₹"): String {
        val negative = paise < 0
        val rupees = abs(paise) / 100.0
        val text = when {
            rupees >= 1_00_00_000 -> trim(rupees / 1_00_00_000) + "Cr"
            rupees >= 1_00_000 -> trim(rupees / 1_00_000) + "L"
            rupees >= 1_000 -> trim(rupees / 1_000) + "K"
            else -> (abs(paise) / 100).toString()
        }
        return (if (negative) "-" else "") + symbol + text
    }

    private fun trim(v: Double): String {
        val s = String.format(java.util.Locale.US, "%.2f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    fun groupWestern(n: Long): String {
        val s = n.toString()
        val sb = StringBuilder()
        s.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.reverse().toString()
    }

    /**
     * Parses user input like "250", "1,250.50", "₹ 99.9", "2k", "1.5L" into paise.
     * Returns null when the text is not a valid positive amount.
     */
    fun parse(text: String): Long? {
        var t = text.trim().lowercase()
            .replace("₹", "").replace("rs.", "").replace("rs", "").replace("inr", "")
            .replace("rupees", "").replace("rupee", "")
            .replace(",", "").replace(" ", "")
        if (t.isEmpty()) return null
        var multiplier = 1L
        when {
            t.endsWith("lakh") -> { multiplier = 1_00_000; t = t.removeSuffix("lakh") }
            t.endsWith("lac") -> { multiplier = 1_00_000; t = t.removeSuffix("lac") }
            t.endsWith("l") -> { multiplier = 1_00_000; t = t.removeSuffix("l") }
            t.endsWith("k") -> { multiplier = 1_000; t = t.removeSuffix("k") }
        }
        if (!Regex("^\\d+(\\.\\d+)?$").matches(t)) return null
        val parts = t.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val fracStr = if (parts.size > 1) parts[1] else ""
        // Exact decimal arithmetic: scale fraction to the multiplier first.
        val totalPaiseWhole = whole * multiplier * 100
        val fracPaise = if (fracStr.isEmpty()) 0L else {
            // fraction * multiplier * 100, computed exactly then rounded half-up
            val digits = fracStr.take(8)
            val denom = pow10(digits.length)
            val num = digits.toLong() * multiplier * 100
            (num * 2 + denom) / (denom * 2)
        }
        val result = totalPaiseWhole + fracPaise
        return if (result > 0) result else null
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    /** Converts paise to an editable plain string, e.g. 12550 -> "125.50", 15000 -> "150". */
    fun toInput(paise: Long): String {
        val r = abs(paise) / 100
        val f = abs(paise) % 100
        return if (f == 0L) r.toString() else "$r.${f.toString().padStart(2, '0')}"
    }

    /** Percentage (0..∞) with integer math, rounded. */
    fun percent(part: Long, whole: Long): Int {
        if (whole <= 0) return if (part > 0) 100 else 0
        return ((part * 1000 / whole + 5) / 10).toInt()
    }
}

/** Convenience to build paise from rupees in code and tests. */
fun rupees(r: Long): Long = r * 100
