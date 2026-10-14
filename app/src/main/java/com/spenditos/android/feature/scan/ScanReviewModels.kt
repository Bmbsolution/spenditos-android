package com.spenditos.android.feature.scan

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.feature.capture.CaptureReviewItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A scan row as the shared review list shows it (iOS `ScanItem.asAnalyzedItem`). The scan item id travels with the
 * row and is the save's idempotency key, so a retried chunk never double-saves. Rows the backend flags (duplicate /
 * card payment / transfer / rule skip) start unchecked and say why; a possible duplicate without a skip decision is
 * included but labelled.
 */
fun ScanItem.toReviewItem(categories: List<Category>): CaptureReviewItem {
    val income = isIncome
    val matched = categoryMatch(categoryId, categoryHint, income, categories)
    val amountText = amount?.takeIf { it.isFinite() && it > 0 }?.let {
        // Wire amounts are positive numbers with at most two decimals; round anything longer so it can be saved.
        BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }.orEmpty()
    return CaptureReviewItem(
        idempotencyKeyId = id,
        amountText = amountText,
        description = displayDescription().take(200),
        // A row without a date keeps it blank: the user sets it in the editor before it can be saved.
        date = date?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }.orEmpty(),
        categoryId = matched?.id,
        suggestedCategoryId = categoryId,
        categoryHint = categoryHint,
        currency = currency,
        transactionType = transactionType,
        include = skip == null,
        skipReason = skipReason?.takeIf { skip != null },
        ruleId = ruleId?.takeIf { it.isNotEmpty() },
        isPending = isPending,
        duplicateDate = possibleDuplicateOf?.date,
        duplicateAmount = possibleDuplicateOf?.amount,
    )
}

/** The edited description, else the merchant name (screenshot rows often carry only a merchant), else "" (iOS `displayDescription`). */
fun ScanItem.displayDescription(): String {
    for (candidate in listOf(description, merchantName)) {
        val trimmed = candidate?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return trimmed
    }
    return ""
}

/** The server's category when the user still has it (and it is the right kind), else the best match for the hint. */
internal fun categoryMatch(categoryId: String?, hint: String?, income: Boolean, categories: List<Category>): Category? {
    val sameKind = categories.filter { it.isIncome == income }
    sameKind.firstOrNull { it.id == categoryId }?.let { return it }
    val wanted = normalizedKey(hint.orEmpty())
    if (wanted.isEmpty()) return null
    return sameKind.firstOrNull { normalizedKey(it.name) == wanted }
        ?: sameKind.firstOrNull {
            val name = normalizedKey(it.name)
            name.isNotEmpty() && (name.contains(wanted) || wanted.contains(name))
        }
}

private fun normalizedKey(value: String): String =
    value.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ")

/** Fills in categories for rows the user has not touched, once the user's categories are known (hints resolve to ids). */
fun resolveScanCategories(items: List<CaptureReviewItem>, categories: List<Category>): List<CaptureReviewItem> = items.map { item ->
    if (item.categoryId != null || item.categoryEdited) return@map item
    val match = categoryMatch(item.suggestedCategoryId, item.categoryHint, item.transactionType.equals("income", ignoreCase = true), categories)
    if (match != null) item.copy(categoryId = match.id) else item
}

/** Rows of one month, in review order (iOS `ReviewMonthSection`). */
data class ReviewMonthSection(val key: String, val title: String, val items: List<CaptureReviewItem>)

object ScanReviewGrouping {
    /** "2026-09" for "2026-09-14", else "" (no date). */
    fun monthKey(date: String?): String {
        if (date == null || date.length < 7) return ""
        val prefix = date.take(7)
        val parts = prefix.split('-')
        if (parts.size != 2 || parts[0].length != 4 || parts[1].length != 2) return ""
        val year = parts[0].toIntOrNull() ?: return ""
        val month = parts[1].toIntOrNull() ?: return ""
        return if (month in 1..12 && year > 0) prefix else ""
    }

    /** "September" in the current year, "September 2025" otherwise, "No date" without a month. */
    fun monthTitle(key: String, now: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
        if (key.isEmpty()) return "No date"
        val month = runCatching { YearMonth.parse(key) }.getOrNull() ?: return key
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return if (month.year == now.year) name else "$name ${month.year}"
    }

    /** Rows grouped by month, newest month first (rows without a usable date last); row order is the review order. */
    fun monthSections(items: List<CaptureReviewItem>, now: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): List<ReviewMonthSection> {
        val order = LinkedHashMap<String, MutableList<CaptureReviewItem>>()
        for (item in items) order.getOrPut(monthKey(item.date)) { mutableListOf() }.add(item)
        val keys = order.keys.sortedWith(Comparator { a, b ->
            when {
                a.isEmpty() != b.isEmpty() -> if (a.isEmpty()) 1 else -1
                else -> b.compareTo(a)
            }
        })
        return keys.map { key -> ReviewMonthSection(key, monthTitle(key, now, locale), order.getValue(key)) }
    }
}

/** True for the skip reasons iOS calls "flagged": already in Spenditos or not spending (a rule skip is counted apart). */
internal fun CaptureReviewItem.isFlagged() = skipReason != null
