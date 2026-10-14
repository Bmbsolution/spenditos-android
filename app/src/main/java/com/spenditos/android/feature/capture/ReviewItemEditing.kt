package com.spenditos.android.feature.capture

import com.spenditos.android.data.model.Category
import java.time.LocalDate

/**
 * The edits a review row supports, shared by every review list that shows [CaptureReviewItem]s (voice, typed and
 * Scan). Pure functions: the view models decide when an item may be edited (frozen rows are skipped before calling).
 */
internal object ReviewItemEditing {
    fun toggled(item: CaptureReviewItem) = item.copy(include = !item.include, failureReason = null)

    fun withAmountText(item: CaptureReviewItem, text: String) =
        item.copy(amountText = text.filter { char -> char.isDigit() || char == '.' || char == ',' }, failureReason = null)

    fun withDescription(item: CaptureReviewItem, text: String) = item.copy(description = text.take(200), failureReason = null)

    fun withDate(item: CaptureReviewItem, date: LocalDate) = item.copy(date = date.toString(), failureReason = null)

    /**
     * iOS TransactionItemEditorView type picker. The category decides the saved kind, so switching kinds drops a
     * category of the other kind instead of saving a mismatched pair.
     */
    fun withType(item: CaptureReviewItem, income: Boolean): CaptureReviewItem {
        val wasIncome = item.transactionType.equals("income", ignoreCase = true)
        if (wasIncome == income) return item
        return item.copy(
            transactionType = if (income) "income" else "expense",
            categoryId = null,
            categoryEdited = item.categoryId != null || item.categoryEdited,
            failureReason = null,
        )
    }

    /** An explicit category choice; a category of the other kind is not accepted. A user rule no longer applies to it. */
    fun withCategory(item: CaptureReviewItem, categoryId: String?, categories: List<Category>): CaptureReviewItem {
        val wantsIncome = item.transactionType.equals("income", ignoreCase = true)
        val valid = categories.firstOrNull { it.id == categoryId && it.isIncome == wantsIncome }
        val changed = valid?.id != item.categoryId
        return item.copy(
            categoryId = valid?.id,
            categoryEdited = true,
            failureReason = null,
            ruleId = if (changed) null else item.ruleId,
        )
    }
}
