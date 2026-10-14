package com.spenditos.android.data.merchants

import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RuleAction
import java.util.Locale

private val NOT_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]")

/**
 * Kotlin mirror of the shared `normalizeMerchantKey` (packages/core/src/services/merchant-rules/matcher.ts): lowercase,
 * then keep only letters and digits.
 *
 *     "TIM HORTONS #4521" -> "timhortons4521"
 *     "Tim Hortons"       -> "timhortons"
 *
 * The server applies rules; the app only needs the key to group review rows by merchant and to tell whether the user
 * already has an identical rule.
 */
fun normalizeMerchantKey(text: String?): String =
    if (text.isNullOrEmpty()) "" else text.lowercase(Locale.ROOT).replace(NOT_LETTER_OR_DIGIT, "")

/** iOS `MerchantMatcher`. */
object MerchantMatcher {
    /**
     * The user rule that already covers [merchant] with exactly this [action], or null. "Identical" means: same
     * normalized key as the rule's rawPattern or cleanMerchantName, same action, and for categorize the same category.
     * Only user rules count, and regex rules never do (their pattern is not a merchant name).
     */
    fun identicalRule(
        merchant: String?,
        categoryId: String?,
        rules: List<MerchantMapping>,
        action: RuleAction = RuleAction.CATEGORIZE,
    ): MerchantMapping? {
        val key = normalizeMerchantKey(merchant)
        if (key.isEmpty()) return null
        return rules.firstOrNull { rule ->
            if (!rule.isUserRule || rule.isRegex || rule.ruleAction != action) return@firstOrNull false
            if (action == RuleAction.CATEGORIZE && rule.categoryId != categoryId) return@firstOrNull false
            normalizeMerchantKey(rule.rawPattern) == key || normalizeMerchantKey(rule.cleanMerchantName) == key
        }
    }

    /** The user rule (any action or category) with this merchant's key: the row an upsert would update. */
    fun ruleFor(merchant: String?, rules: List<MerchantMapping>): MerchantMapping? {
        val key = normalizeMerchantKey(merchant)
        if (key.isEmpty()) return null
        return rules.firstOrNull { it.isUserRule && !it.isRegex && normalizeMerchantKey(it.rawPattern) == key }
    }

    /**
     * Whether picking [categoryId] for a row or group of [merchant] should offer "Always file ... under ...?": the
     * merchant name has something in it and no identical user rule already exists.
     */
    fun shouldOfferRule(merchant: String?, categoryId: String?, rules: List<MerchantMapping>): Boolean {
        if (categoryId.isNullOrEmpty()) return false
        if (normalizeMerchantKey(merchant).isEmpty()) return false
        return identicalRule(merchant, categoryId, rules) == null
    }
}
