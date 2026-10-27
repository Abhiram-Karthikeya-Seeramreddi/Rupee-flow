package com.rupeeflow.core

/**
 * Turns everyday sentences into a draft transaction, fully offline:
 *   "Spent 250 rupees on chicken biryani using UPI"  -> ₹250, Restaurants, UPI, "Chicken biryani"
 *   "120 auto yesterday"                               -> ₹120, Transport, yesterday
 *   "got salary 45000"                                 -> Income ₹45,000, Salary
 * The result is always shown to the user for confirmation; nothing is saved automatically.
 */
data class ParsedEntry(
    val amount: Long?,
    val type: TxType,
    val categoryId: String?,
    val payment: PaymentMethod?,
    val note: String,
    val dayOffset: Int,
    /** Which keyword decided the category, for the "why" hint in the UI. */
    val matchedKeyword: String? = null,
)

data class CategoryKeywords(val categoryId: String, val keywords: List<String>, val kind: CategoryKind)

object QuickParse {

    private val amountRegex = Regex(
        """(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*(k|l|lakh|lakhs|lac|thousand)?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val paymentWords: List<Pair<String, PaymentMethod>> = listOf(
        "google pay" to PaymentMethod.UPI, "gpay" to PaymentMethod.UPI, "phonepe" to PaymentMethod.UPI,
        "phone pe" to PaymentMethod.UPI, "bhim" to PaymentMethod.UPI, "upi" to PaymentMethod.UPI,
        "paytm" to PaymentMethod.UPI, "cred" to PaymentMethod.UPI,
        "credit card" to PaymentMethod.CREDIT_CARD, "cc" to PaymentMethod.CREDIT_CARD,
        "debit card" to PaymentMethod.DEBIT_CARD, "card" to PaymentMethod.DEBIT_CARD,
        "cash" to PaymentMethod.CASH,
        "net banking" to PaymentMethod.NET_BANKING, "netbanking" to PaymentMethod.NET_BANKING,
        "neft" to PaymentMethod.NET_BANKING, "imps" to PaymentMethod.NET_BANKING,
        "wallet" to PaymentMethod.WALLET, "amazon pay" to PaymentMethod.WALLET,
        "auto debit" to PaymentMethod.AUTOPAY, "autopay" to PaymentMethod.AUTOPAY,
    )

    private val incomeWords = listOf("received", "got paid", "credited", "salary", "income", "earned")
    private val refundWords = listOf("refund", "refunded", "cashback")

    private val filler = setOf(
        "spent", "spend", "paid", "pay", "payed", "bought", "buy", "on", "for", "using", "via", "with", "by", "through",
        "rupees", "rupee", "rs", "rs.", "inr", "today", "yesterday", "day", "before", "a", "an", "the", "of", "to",
        "at", "in", "from", "my", "i", "and", "got", "received", "credited", "k", "lakh", "lakhs", "thousand",
    )

    fun parse(
        input: String,
        categories: List<CategoryKeywords>,
        userRules: Map<String, String> = emptyMap(),
    ): ParsedEntry {
        val raw = input.trim()
        val lower = " " + raw.lowercase().replace(Regex("[!?;:]"), " ") + " "

        // Amount: first number in the sentence.
        var amount: Long? = null
        var amountSpan: IntRange? = null
        amountRegex.find(lower)?.let { m ->
            val num = m.groupValues[1].replace(",", "")
            val unit = m.groupValues[2].lowercase()
            val suffix = when (unit) { "k", "thousand" -> "k"; "l", "lakh", "lakhs", "lac" -> "l"; else -> "" }
            amount = Money.parse(num + suffix)
            amountSpan = m.range
        }

        // Day
        val dayOffset = when {
            "day before yesterday" in lower -> -2
            Regex("\\byesterday\\b").containsMatchIn(lower) -> -1
            else -> 0
        }

        // Payment method (longest phrase first so "credit card" beats "card").
        var payment: PaymentMethod? = null
        var paymentWord: String? = null
        for ((word, pm) in paymentWords.sortedByDescending { it.first.length }) {
            if (containsWord(lower, word)) { payment = pm; paymentWord = word; break }
        }

        // Type
        val type = when {
            refundWords.any { containsWord(lower, it) } -> TxType.REFUND
            incomeWords.any { containsWord(lower, it) } && !containsWord(lower, "spent") && !containsWord(lower, "paid") -> TxType.INCOME
            else -> TxType.EXPENSE
        }

        // Category: user-taught rules first, then built-in keywords. Longest keyword wins.
        val wantKind = if (type == TxType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        var categoryId: String? = null
        var matched: String? = null
        userRules.entries.sortedByDescending { it.key.length }.firstOrNull { containsWord(lower, it.key.lowercase()) }?.let {
            categoryId = it.value; matched = it.key
        }
        if (categoryId == null) {
            var best: Pair<String, String>? = null
            for (c in categories) {
                if (c.kind != wantKind) continue
                for (k in c.keywords) {
                    if (containsWord(lower, k.lowercase()) && (best == null || k.length > best.second.length)) {
                        best = c.categoryId to k
                    }
                }
            }
            best?.let { categoryId = it.first; matched = it.second }
        }

        // Note: the sentence minus amount, filler and payment words.
        var noteSrc = raw
        amountSpan?.let { span ->
            val s = (span.first - 1).coerceAtLeast(0)
            val e = span.last.coerceAtMost(raw.length)
            if (s < e) noteSrc = raw.substring(0, s) + " " + raw.substring(e)
        }
        paymentWord?.let { pw -> noteSrc = noteSrc.replace(Regex("(?i)\\b" + Regex.escape(pw) + "\\b"), " ") }
        noteSrc = noteSrc.replace("₹", " ")
        val words = noteSrc.split(Regex("\\s+")).map { it.trim(',', '.', '-') }.filter { it.isNotBlank() }
        val kept = words.filter { it.lowercase() !in filler && !Regex("^\\d+([.,]\\d+)?$").matches(it) }
        val note = kept.joinToString(" ").replaceFirstChar { it.uppercase() }

        return ParsedEntry(amount, type, categoryId, payment, note, dayOffset, matched)
    }

    /** Learns a merchant/keyword rule from a saved transaction note: the most distinctive word. */
    fun learnableKeyword(note: String): String? {
        val words = note.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 4 && it !in filler }
        return words.firstOrNull()
    }

    fun containsWord(haystack: String, word: String): Boolean {
        if (word.isBlank()) return false
        val pattern = "(?<![a-z0-9])" + Regex.escape(word.lowercase()) + "(?![a-z0-9])"
        return Regex(pattern).containsMatchIn(haystack)
    }
}
