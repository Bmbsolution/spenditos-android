package com.spenditos.android.feature.scan

import com.spenditos.android.data.merchants.normalizeMerchantKey
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
        merchantName = merchantName,
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

/** Several rows of one merchant inside one month (iOS `ReviewGroup`). */
data class ReviewGroup(
    /** "<monthKey>|<merchantKey>": stable while the user edits rows. */
    val id: String,
    val merchantKey: String,
    /** Name shown for the group (the first member's merchant name). */
    val merchant: String,
    val items: List<CaptureReviewItem>,
) {
    enum class IncludeState { ALL, NONE, MIXED }

    val itemKeys: List<String> get() = items.map { it.idempotencyKeyId }
    val count: Int get() = items.size

    /** Net of the group (income positive, expense negative), like the footer. */
    val signedTotal: BigDecimal
        get() = items.fold(BigDecimal.ZERO) { sum, item ->
            val amount = item.editableAmount() ?: BigDecimal.ZERO
            if (item.isIncomeRow()) sum + amount else sum - amount
        }

    val hasMissingAmount: Boolean get() = items.any { it.editableAmount() == null }
    val allAmountsMissing: Boolean get() = items.all { it.editableAmount() == null }

    val includeState: IncludeState
        get() {
            val included = items.count { it.include }
            return when (included) {
                0 -> IncludeState.NONE
                items.size -> IncludeState.ALL
                else -> IncludeState.MIXED
            }
        }

    /** The category every row shares, null when none is set or they differ. */
    val sharedCategoryId: String?
        get() {
            val first = items.firstOrNull()?.categoryId?.takeIf { it.isNotEmpty() } ?: return null
            return first.takeIf { items.all { it.categoryId == first } }
        }

    val hasMixedCategories: Boolean get() = items.map { it.categoryId.orEmpty() }.toSet().size > 1

    /** Income when every row is income, so the picker lists the right kind. */
    val isIncome: Boolean get() = items.all { it.isIncomeRow() }

    /** "✓ Your rule" when a categorize rule filed every row. */
    val allRuleFiled: Boolean get() = items.all { it.ruleId != null && it.skipReason != "rule" }

    /** "Skipped by your rule" when a skip rule left every row unchecked. */
    val allSkippedByRule: Boolean get() = items.all { it.skipReason == "rule" }

    /** Rows flagged for another reason (duplicate, card payment, transfer). */
    val flaggedCount: Int get() = items.count { it.skipReason != null && it.skipReason != "rule" }
}

/** One line of the review list: a single row, or several rows of one merchant (iOS `ReviewEntry`). */
sealed interface ReviewEntry {
    val key: String

    data class Single(val item: CaptureReviewItem) : ReviewEntry {
        override val key: String get() = item.idempotencyKeyId
    }

    data class Group(val group: ReviewGroup) : ReviewEntry {
        override val key: String get() = "group|${group.id}"
    }
}

/** Rows of one month, in review order (iOS `ReviewMonthSection`). */
data class ReviewMonthSection(val key: String, val title: String, val entries: List<ReviewEntry>) {
    /** Every underlying row, in display order. */
    val items: List<CaptureReviewItem>
        get() = entries.flatMap { entry ->
            when (entry) {
                is ReviewEntry.Single -> listOf(entry.item)
                is ReviewEntry.Group -> entry.group.items
            }
        }
}

internal fun CaptureReviewItem.isIncomeRow() = transactionType.equals("income", ignoreCase = true)

/** The amount as typed (the review row's own reading of it), null when empty or not a number. */
internal fun CaptureReviewItem.editableAmount(): BigDecimal? = amountText.trim().replace(',', '.').toBigDecimalOrNull()

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

    /**
     * Rows grouped by month, newest month first (rows without a usable date last). With [grouping], same-merchant
     * rows (two or more) inside a month collapse into one group placed where the first of them was. Row order is
     * otherwise the review order.
     *
     * [merchantKey] defaults to the contract's key of the merchant name; the review passes a cached lookup so
     * thousands of rows are not re-normalized on every change.
     */
    fun monthSections(
        items: List<CaptureReviewItem>,
        now: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault(),
        grouping: Boolean = false,
        merchantKey: (CaptureReviewItem) -> String = { normalizeMerchantKey(it.merchantName) },
    ): List<ReviewMonthSection> {
        val order = LinkedHashMap<String, MutableList<CaptureReviewItem>>()
        for (item in items) order.getOrPut(monthKey(item.date)) { mutableListOf() }.add(item)
        val keys = order.keys.sortedWith(Comparator { a, b ->
            when {
                a.isEmpty() != b.isEmpty() -> if (a.isEmpty()) 1 else -1
                else -> b.compareTo(a)
            }
        })
        return keys.map { key ->
            val rows = order.getValue(key)
            ReviewMonthSection(
                key,
                monthTitle(key, now, locale),
                if (grouping) entries(rows, key, merchantKey) else rows.map { ReviewEntry.Single(it) },
            )
        }
    }

    private fun entries(rows: List<CaptureReviewItem>, month: String, merchantKey: (CaptureReviewItem) -> String): List<ReviewEntry> {
        val keys = rows.map(merchantKey)
        val members = HashMap<String, MutableList<CaptureReviewItem>>()
        rows.forEachIndexed { index, row -> if (keys[index].isNotEmpty()) members.getOrPut(keys[index]) { mutableListOf() }.add(row) }

        val placed = HashSet<String>()
        val result = ArrayList<ReviewEntry>(rows.size)
        rows.forEachIndexed { index, row ->
            val key = keys[index]
            val group = members[key]
            if (key.isEmpty() || group == null || group.size < 2) {
                result += ReviewEntry.Single(row)
                return@forEachIndexed
            }
            if (!placed.add(key)) return@forEachIndexed
            val name = group.firstNotNullOfOrNull { it.merchantName?.trim()?.takeIf(String::isNotEmpty) } ?: key
            result += ReviewEntry.Group(ReviewGroup("$month|$key", key, name, group))
        }
        return result
    }
}

/** True for the skip reasons iOS calls "flagged": already in Spenditos or not spending (a rule skip is counted apart). */
internal fun CaptureReviewItem.isFlagged() = skipReason != null
