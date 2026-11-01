package com.spenditos.android.data.transactions

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A generated bill entry that the transaction being entered may be paying. */
data class BillCandidate(val entry: Transaction, val billName: String, val score: Double) {
    val estimate: Double get() = entry.amount
    val dueDate: String get() = entry.date
}

/**
 * Duplicate check between a transaction being entered and the bill entries already generated
 * from recurring templates. Port of iOS `BillMatching.swift` (decisions.md §3).
 */
object BillMatching {
    /** Days on either side of the transaction date an entry may fall on. */
    const val DATE_WINDOW_DAYS = 4

    /** Amount tolerance when the merchant matches the bill name. */
    const val NAME_TOLERANCE = 0.20

    /** Amount tolerance when only the category matches (weak evidence). */
    const val CATEGORY_TOLERANCE = 0.05
    const val MAX_CANDIDATES = 3

    /** Lowercased alphanumeric words ("Disney+" keeps its plus). */
    fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}+]+")).filter { it.isNotEmpty() }

    /**
     * True when one name appears in the other as whole consecutive words, or both map to the
     * same merchant rule. "Rent" never matches "Car rental".
     */
    fun sameMerchant(a: String, b: String, mappings: List<MerchantMapping> = emptyList()): Boolean {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (contains(x, y) || contains(y, x)) return true
        val ra = rule(a, mappings) ?: return false
        val rb = rule(b, mappings) ?: return false
        return ra.id == rb.id
    }

    /**
     * The first merchant rule whose pattern appears in [text] as whole words. Rules without a
     * category and regex rules are skipped (iOS has neither).
     */
    fun rule(text: String, mappings: List<MerchantMapping>): MerchantMapping? {
        val hay = words(text)
        if (hay.isEmpty()) return null
        return mappings.firstOrNull { !it.isRegex && it.categoryId != null && contains(hay, words(it.rawPattern)) }
    }

    /**
     * Generated entries the transaction may be paying, best first.
     * [entries] are transactions near the date; only those with a `templateId` count.
     * [templates] give the bill's name when the entry description is empty.
     */
    fun candidates(
        amount: Double,
        description: String,
        categoryId: String,
        date: LocalDate,
        entries: List<Transaction>,
        templates: List<RecurringTemplate> = emptyList(),
        mappings: List<MerchantMapping> = emptyList(),
    ): List<BillCandidate> {
        if (amount <= 0) return emptyList()
        val out = mutableListOf<BillCandidate>()
        for (entry in entries) {
            val templateId = entry.templateId ?: continue
            if (entry.amount <= 0) continue
            val entryDay = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: continue
            val days = abs(ChronoUnit.DAYS.between(date, entryDay))
            if (days > DATE_WINDOW_DAYS) continue

            val template = templates.firstOrNull { it.id == templateId }
            val name = entry.description.ifEmpty { template?.description ?: "" }
            val byName = description.isNotEmpty() && sameMerchant(description, name, mappings)
            val byCategory = entry.categoryId == categoryId
            if (!byName && !byCategory) continue

            val diff = abs(amount - entry.amount) / entry.amount
            if (diff > (if (byName) NAME_TOLERANCE else CATEGORY_TOLERANCE)) continue

            val evidence = (if (byName) 3.0 else 0.0) + (if (byCategory) 1.0 else 0.0)
            val closeness = (1.0 - diff) + (1.0 - days.toDouble() / (DATE_WINDOW_DAYS + 1))
            out += BillCandidate(entry, name.ifEmpty { Strings.get(R.string.common_bill) }, evidence + closeness)
        }
        return out.sortedByDescending { it.score }.take(MAX_CANDIDATES)
    }

    /**
     * An active bill that already covers this merchant, checked before a new repeat is created.
     * With no description, a same-category bill within ±5% counts.
     */
    fun existingTemplate(
        description: String,
        categoryId: String,
        amount: Double,
        templates: List<RecurringTemplate>,
        mappings: List<MerchantMapping> = emptyList(),
    ): RecurringTemplate? {
        val trimmed = description.trim()
        return templates.firstOrNull { template ->
            if (!template.isActive || template.isLegacyEstimate) return@firstOrNull false
            if (trimmed.isNotEmpty()) return@firstOrNull sameMerchant(trimmed, template.description, mappings)
            template.categoryId == categoryId && template.amount > 0 &&
                abs(amount - template.amount) / template.amount <= CATEGORY_TOLERANCE
        }
    }

    private fun contains(hay: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > hay.size) return false
        for (start in 0..(hay.size - needle.size)) {
            if (hay.subList(start, start + needle.size) == needle) return true
        }
        return false
    }
}

/** A legacy VARIABLE estimate (#336): never a real bill. */
val RecurringTemplate.isLegacyEstimate: Boolean get() = type.equals("variable", ignoreCase = true)

/** Repeat presets offered by "Make it recurring" in the add form. Port of iOS `RecurrencePreset`. */
enum class RecurrencePreset(@androidx.annotation.StringRes val labelRes: Int, val frequency: String) {
    WEEKLY(R.string.data_transactions_weekly, "weekly"),
    BI_WEEKLY(R.string.common_every_2_weeks, "bi-weekly"),
    SEMI_MONTHLY(R.string.common_twice_a_month, "semi-monthly"),
    MONTHLY(R.string.data_transactions_monthly, "monthly"),
    YEARLY(R.string.data_transactions_yearly, "yearly"),
    ;

    val label: String get() = Strings.get(labelRes)

    /** The due date one period after [date]; month arithmetic clamps to the month's last day. */
    fun next(date: LocalDate): LocalDate = when (this) {
        WEEKLY -> date.plusDays(7)
        BI_WEEKLY -> date.plusDays(14)
        SEMI_MONTHLY -> date.plusDays(15)
        MONTHLY -> date.plusMonths(1)
        YEARLY -> date.plusYears(1)
    }

    companion object {
        /** Second day of the month for twice-a-month bills: 15 days from the first, within 1–28. */
        fun secondDay(firstDay: Int): Int = if (firstDay <= 15) min(firstDay + 15, 28) else max(firstDay - 15, 1)
    }
}
