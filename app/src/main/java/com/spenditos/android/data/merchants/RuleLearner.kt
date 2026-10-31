package com.spenditos.android.data.merchants

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.scan.counted
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/** What the review is asking: "Always file {merchant} under {category}?" */
data class RuleAsk(
    val id: Long,
    val merchant: String,
    val categoryId: String,
    val categoryName: String,
    /** The review rows this choice covers (all rows of a group), by scan item id. */
    val itemKeys: List<String>,
)

data class RuleLearnOutcome(
    /** The saved rule; null when the upsert failed. */
    val rule: MerchantMapping?,
    /** Past transactions updated; null when not requested or the call failed. */
    val pastUpdated: Int?,
    /** Text for the toast. */
    val message: String,
) {
    val failed: Boolean get() = rule == null
}

/**
 * The "Always" half of Rules (iOS `RuleLearner`): upsert the user's rule for a merchant, optionally apply it to past
 * transactions, and say what happened in the contract's words. Rule calls are best effort: a failure comes back as a
 * light message and never blocks saving the scan.
 */
class RuleLearner @Inject constructor(private val mappings: MerchantMappingActions) {

    suspend fun always(merchant: String, categoryId: String, categoryName: String, alsoUpdatePast: Boolean): RuleLearnOutcome {
        val result = try {
            mappings.always(merchant, categoryId, alsoUpdatePast)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return RuleLearnOutcome(rule = null, pastUpdated = null, message = FAILURE_MESSAGE)
        }
        val message = if (result.applyFailed) {
            Strings.get(R.string.data_merchants_rule_saved_past_transactions_couldn, merchant, categoryName, CHANGE_ANYTIME)
        } else {
            savedMessage(merchant, categoryName, result.pastUpdated)
        }
        return RuleLearnOutcome(result.rule, result.pastUpdated, message)
    }

    companion object {
        const val FAILURE_MESSAGE = "Couldn't save the rule. Your transactions will still be saved."
        private const val CHANGE_ANYTIME = "Change it anytime in Settings › Rules."

        /** "Rule saved: Tim Hortons → Dining. 7 past transactions updated. Change it anytime in Settings › Rules." */
        fun savedMessage(merchant: String, categoryName: String, pastUpdated: Int?): String {
            var text = Strings.get(R.string.data_merchants_rule_saved, merchant, categoryName)
            if (pastUpdated != null && pastUpdated > 0) text += " ${pastUpdated.counted("past transaction")} updated."
            return "$text $CHANGE_ANYTIME"
        }
    }
}
