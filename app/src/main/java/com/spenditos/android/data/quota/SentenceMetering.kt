package com.spenditos.android.data.quota

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.QuotaOverview
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Free-tier metering helpers with no UI state (port of iOS `SentenceMetering.swift`): the typed-sentence
 * counter rules, the on-device amount parser used when a sentence can't be sent to the server, and the
 * quota-exhausted copy. Pure, so every rule is unit-tested.
 */

/** What the sentence entry screen shows about the monthly smart-entry quota. */
data class SentenceMeter(
    val used: Int,
    /** Null when unlimited (Pro, or a server that does not meter sentences). */
    val remaining: Int?,
) {
    /** Quiet "N smart entries left" line: free users only, after 3 uses, until the banner takes over. */
    val showsCounter: Boolean get() = remaining != null && used >= COUNTER_THRESHOLD && remaining > BANNER_THRESHOLD

    /** Soft inline banner at <= 5 left (replaces the quiet line). */
    val showsBanner: Boolean get() = remaining != null && remaining <= BANNER_THRESHOLD

    val isExhausted: Boolean get() = (remaining ?: 1) <= 0

    val counterText: String? get() = if (showsCounter) counterCopy(remaining!!) else null

    val bannerText: String? get() = if (showsBanner) bannerCopy(remaining!!) else null

    companion object {
        /** The counter stays hidden until the user has used sentences this many times. */
        const val COUNTER_THRESHOLD = 3

        /** The soft Pro banner shows at this many remaining (or fewer). */
        const val BANNER_THRESHOLD = 5

        fun from(overview: QuotaOverview): SentenceMeter {
            val remaining = overview.remainingSentenceEntries
            return SentenceMeter(used = overview.sentencesUsed, remaining = remaining.takeIf { it != QuotaOverview.UNLIMITED })
        }

        fun counterCopy(remaining: Int): String =
            if (remaining == 1) "1 smart entry left this month" else "$remaining smart entries left this month"

        fun bannerCopy(remaining: Int): String =
            Strings.get(R.string.data_quota_smart_entries_are_a_pro, maxOf(0, remaining))
    }
}

/** Plain-language copy for quota errors. The raw server string ("QuotaExhausted") is never shown. */
object QuotaCopy {
    val SCAN_EXHAUSTED: String get() = Strings.get(R.string.quota_scan_exhausted)
    val SCAN_UPGRADE: String get() = Strings.get(R.string.quota_scan_upgrade)
    val VOICE_EXHAUSTED: String get() = Strings.get(R.string.quota_voice_exhausted)
    val SENTENCE_EXHAUSTED_TITLE: String get() = Strings.get(R.string.quota_sentence_exhausted_title)
    val SENTENCE_EXHAUSTED_BODY: String get() = Strings.get(R.string.data_quota_your_text_is_in_the)
}

/** What the manual form gets when a sentence can't be parsed because the monthly quota is used up. */
data class SentenceManualFallback(val description: String, val amount: String?) {
    companion object {
        fun from(typedText: String): SentenceManualFallback {
            val description = typedText.trim()
            return SentenceManualFallback(description, SentenceAmountParser.firstAmount(description))
        }
    }
}

/** True when a 403 `QuotaExhausted` is the typed-sentence bucket (or an unlabeled one from a typed parse). */
fun isSentenceQuota(quotaType: String?): Boolean = quotaType == null || quotaType == "sentenceEntries"

/** Simple on-device parse: the first currency-like number in the text. */
object SentenceAmountParser {
    private val pattern = Regex("""([$€£¥])?\s?(\d[\d.,]*\d|\d)""")
    private const val MAX_AMOUNT = 1_000_000_000.0
    private val currencyCodes = listOf("usd", "cad", "eur", "gbp", "aud")

    /**
     * The amount as a 2-decimal string ("12.50"), or null. A number with a currency symbol wins over a
     * bare one; otherwise the first number counts. Dates ("2026-10-13", "3/4") and numbers glued to
     * letters ("7eleven") are ignored.
     */
    fun firstAmount(text: String): String? {
        var firstBare: String? = null
        for (match in pattern.findAll(text)) {
            val numberGroup = match.groups[2] ?: continue
            val start = numberGroup.range.first
            val endExclusive = numberGroup.range.last + 1
            val hasSymbol = match.groups[1] != null

            if (start > 0) {
                val before = text[start - 1]
                if (before.isLetter() || before == '/' || (before == '-' && start - 1 > 0 && text[start - 2].isDigit())) continue
            }
            if (endExclusive < text.length) {
                val after = text[endExclusive]
                if (after.isLetter() && !hasSymbol && !isCurrencyCode(text, endExclusive)) continue
                val nextIsDigit = endExclusive + 1 < text.length && text[endExclusive + 1].isDigit()
                if (after == '/' || (after == '-' && nextIsDigit)) continue
            }
            val value = normalize(numberGroup.value) ?: continue
            if (hasSymbol) return value
            if (firstBare == null) firstBare = value
        }
        return firstBare
    }

    private fun isCurrencyCode(text: String, index: Int): Boolean {
        val tail = text.substring(index).lowercase()
        return currencyCodes.any { tail.startsWith(it) }
    }

    /** Resolves thousands/decimal separators and returns "%.2f", or null for zero / absurd values. */
    fun normalize(raw: String): String? {
        var text = raw
        val commas = text.count { it == ',' }
        val dots = text.count { it == '.' }
        if (commas > 0 && dots > 0) {
            // The last separator is the decimal point.
            text = if (text.lastIndexOf('.') > text.lastIndexOf(',')) {
                text.replace(",", "")
            } else {
                text.replace(".", "").replace(",", ".")
            }
        } else if (commas > 0) {
            val last = text.split(",").last()
            text = if (commas == 1 && last.length <= 2) text.replace(",", ".") else text.replace(",", "")
        } else if (dots > 0) {
            val last = text.split(".").last()
            if (dots > 1 || last.length > 2) text = text.replace(".", "")
        }
        val value = text.toDoubleOrNull() ?: return null
        if (value <= 0 || value >= MAX_AMOUNT) return null
        return BigDecimal(text).setScale(2, RoundingMode.HALF_UP).toPlainString()
    }
}
