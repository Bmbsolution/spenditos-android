package com.spenditos.android.feature.recurring

import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.BillLifecycle
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.ui.components.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * When a bill or income item falls due (issue #403): its dates in a month and its next date.
 * iOS `BillSchedule`. The dates come from [RecurrenceMath], which follows the backend generator
 * (skipped dates left out), so previews match what generation creates.
 */
object BillSchedule {
    /** The item's dates in [month], oldest first. Skipped dates are left out. */
    fun dates(template: RecurringTemplate, month: YearMonth): List<LocalDate> =
        RecurrenceMath.occurrencesInMonth(template, month).sorted()

    /** The first date strictly after [after], looking up to a year ahead. */
    fun nextDate(template: RecurringTemplate, after: LocalDate): LocalDate? {
        var month = YearMonth.from(after)
        repeat(13) {
            dates(template, month).firstOrNull { it.isAfter(after) }?.let { return it }
            month = month.plusMonths(1)
        }
        return null
    }
}

/** The month strip at the top of Income & bills (#403). */
data class BillsMonthSummary(
    val month: YearMonth,
    val billCount: Int,
    val billsTotal: Double,
    /** Null when this month's entries could not be loaded. */
    val paidCount: Int?,
    val stillToPay: Double?,
    val nextName: String?,
    val nextDate: LocalDate?,
)

/** One row of the Generate sheet: a bill or income item and its dates that month. */
data class GenerateItem(
    val template: RecurringTemplate,
    val dates: List<LocalDate>,
    val loggedCount: Int,
    val isIncome: Boolean,
) {
    val id: String get() = template.id
    val isFullyLogged: Boolean get() = loggedCount >= dates.size
    val amountToLog: Double
        get() = BigDecimal.valueOf(template.amount).multiply(BigDecimal(dates.size - loggedCount)).toDouble()
}

/**
 * This month's bills, what is paid, what is still to pay and the next one due. Null when there are
 * no active bills. Paid = this month's entries of those bills dated today or earlier.
 */
fun billsMonthSummary(
    templates: List<RecurringTemplate>,
    isIncome: (RecurringTemplate) -> Boolean,
    monthEntries: List<Transaction>?,
    today: LocalDate,
): BillsMonthSummary? {
    val month = YearMonth.from(today)
    val bills = templates.filter { it.lifecycle == BillLifecycle.ACTIVE && !it.isLegacyEstimate && !isIncome(it) }
    if (bills.isEmpty()) return null

    var total = BigDecimal.ZERO
    var dueCount = 0
    var nextName: String? = null
    var nextDate: LocalDate? = null
    for (bill in bills) {
        val dates = BillSchedule.dates(bill, month)
        dueCount += dates.size
        total += BigDecimal.valueOf(bill.amount).multiply(BigDecimal(dates.size))
        val next = BillSchedule.nextDate(bill, today)
        if (next != null && (nextDate == null || next.isBefore(nextDate))) {
            nextName = bill.description
            nextDate = next
        }
    }

    var paidCount: Int? = null
    var stillToPay: Double? = null
    if (monthEntries != null) {
        val ids = bills.map { it.id }.toSet()
        val todayKey = today.toString()
        // Dates are YYYY-MM-DD, so string order is date order.
        val paid = monthEntries.filter { it.templateId in ids && it.date <= todayKey }
        paidCount = minOf(paid.size, dueCount)
        val paidTotal = paid.fold(BigDecimal.ZERO) { sum, entry -> sum + BigDecimal.valueOf(Math.abs(entry.amount)) }
        stillToPay = (total - paidTotal).max(BigDecimal.ZERO).toDouble()
    }
    return BillsMonthSummary(month, dueCount, total.toDouble(), paidCount, stillToPay, nextName, nextDate)
}

/**
 * What Generate would log for [month]: one row per active bill or income item with dates that
 * month, and how many of them are already logged. Legacy estimates, Paused and Ended items are
 * left out.
 */
fun generateItems(
    templates: List<RecurringTemplate>,
    isIncome: (RecurringTemplate) -> Boolean,
    month: YearMonth,
    logged: List<Transaction>,
    only: RecurringTemplate? = null,
): List<GenerateItem> {
    val candidates = (only?.let { listOf(it) } ?: templates)
        .filter { it.lifecycle == BillLifecycle.ACTIVE && !it.isLegacyEstimate }
    return candidates.mapNotNull { item ->
        val dates = BillSchedule.dates(item, month)
        if (dates.isEmpty()) return@mapNotNull null
        val loggedDates = logged.filter { it.templateId == item.id }.map { it.date }.toSet()
        GenerateItem(item, dates, dates.count { it.toString() in loggedDates }, isIncome(item))
    }.sortedBy { it.dates.first() }
}

/** "1 bill", "9 bills", "1 entry", "2 entries". */
fun counted(n: Int, singular: String, plural: String = singular + "s"): String =
    "$n ${if (n == 1) singular else plural}"

/** Wording for Income & bills, the Generate sheet and the Ended archive (iOS strings, value by value). */
object BillText {
    fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.format(DateTimeFormatter.ofPattern("MMMM", locale))

    fun shortMonthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.format(DateTimeFormatter.ofPattern("MMM", locale))

    /** "Oct 28" */
    fun monthDay(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("MMM d", locale))

    /** "$418.00 still to pay", or the month's total when entries aren't loaded. */
    fun summaryHeadline(summary: BillsMonthSummary, currency: String): String {
        val stillToPay = summary.stillToPay
        if (stillToPay != null) {
            return if (stillToPay > 0) "${Money.format(stillToPay, currency)} still to pay" else "All bills paid"
        }
        return "${Money.format(summary.billsTotal, currency)} in bills"
    }

    /** "5 of 9 bills paid · Next: Bell internet, Nov 3" */
    fun summaryDetail(summary: BillsMonthSummary, locale: Locale = Locale.getDefault()): String {
        val parts = mutableListOf<String>()
        val paid = summary.paidCount
        parts += if (paid != null) "$paid of ${counted(summary.billCount, "bill")} paid" else counted(summary.billCount, "bill")
        val name = summary.nextName
        val date = summary.nextDate
        if (name != null && date != null) parts += "Next: $name, ${monthDay(date, locale)}"
        return parts.joinToString(" · ")
    }

    fun parseInstant(raw: String?): Instant? =
        raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

    /** When a bill was paused, if it says. */
    fun pausedDate(template: RecurringTemplate): Instant? = parseInstant(template.pausedAt)

    /** When a bill ended: `endedAt`, else its last update (instalments that finished before `endedAt` existed). */
    fun endedDate(template: RecurringTemplate): Instant? =
        parseInstant(template.endedAt) ?: parseInstant(template.updatedAt)

    /** Paused for 90 days or more: the row offers "End it?" (#403). */
    fun isLongPaused(template: RecurringTemplate, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (template.lifecycle != BillLifecycle.PAUSED) return false
        val since = pausedDate(template) ?: return false
        return ChronoUnit.DAYS.between(since.atZone(zone), now.atZone(zone)) >= 90
    }

    /**
     * The line after the frequency: "Next Oct 28", "Last payment Nov 3", "Paused since Sep 12",
     * "Paused 3 months". Null when there is nothing to say.
     */
    fun rowStatus(
        template: RecurringTemplate,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String? = when (template.lifecycle) {
        BillLifecycle.PAUSED -> {
            val since = pausedDate(template)
            when {
                since == null -> "Paused"
                isLongPaused(template, now, zone) -> {
                    val months = ChronoUnit.MONTHS.between(since.atZone(zone), now.atZone(zone)).toInt()
                    "Paused ${counted(months, "month")}"
                }
                else -> "Paused since ${monthDay(since.atZone(zone).toLocalDate(), locale)}"
            }
        }
        BillLifecycle.ENDED -> "Ended"
        BillLifecycle.ACTIVE -> {
            val today = now.atZone(zone).toLocalDate()
            // Strictly after yesterday: a payment due today still reads "Next".
            val next = if (template.isLegacyEstimate) null else BillSchedule.nextDate(template, today.minusDays(1))
            next?.let {
                val day = monthDay(it, locale)
                if (template.occurrencesRemaining == 1) "Last payment $day" else "Next $day"
            }
        }
    }

    /** "Oct 3" · "Oct 3, 17" · "Oct 3, 10, 17 +2" · "2 of 4 logged". */
    fun generateDates(item: GenerateItem, locale: Locale = Locale.getDefault()): String {
        val first = item.dates.firstOrNull() ?: return ""
        val monthShort = shortMonthName(YearMonth.from(first), locale)
        val days = item.dates.map { it.dayOfMonth }
        var text = "$monthShort " + days.take(3).joinToString(", ")
        if (days.size > 3) text += " +${days.size - 3}"
        if (item.loggedCount > 0) text += " · ${item.loggedCount} of ${item.dates.size} logged"
        return text
    }

    /** "Net +$6,320.00 for October" */
    fun net(selected: List<GenerateItem>, month: YearMonth, currency: String, locale: Locale = Locale.getDefault()): String {
        val net = selected.fold(BigDecimal.ZERO) { sum, item ->
            val amount = BigDecimal.valueOf(item.amountToLog)
            if (item.isIncome) sum + amount else sum - amount
        }
        val sign = if (net.signum() >= 0) "+" else "−"
        return "Net $sign${Money.format(net.abs().toDouble(), currency)} for ${monthName(month, locale)}"
    }

    /** "Ended Oct 2026 · 12 payments · $1,378.32" (income: "payments received"). */
    fun endedSubtitle(
        template: RecurringTemplate,
        history: BillHistorySummary?,
        isIncome: Boolean,
        currency: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val parts = mutableListOf<String>()
        val ended = endedDate(template)
        parts += if (ended != null) {
            "Ended " + ended.atZone(zone).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
        } else {
            "Ended"
        }
        if (history != null && history.count > 0) {
            parts += if (isIncome) counted(history.count, "payment received", "payments received") else counted(history.count, "payment")
            parts += Money.format(history.total, currency)
        }
        return parts.joinToString(" · ")
    }

    /** "Jan – Oct 2026" */
    fun endedSpan(history: BillHistorySummary, locale: Locale = Locale.getDefault()): String? {
        val first = history.firstDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val last = history.lastDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val start = if (first.year == last.year) {
            first.format(DateTimeFormatter.ofPattern("MMM", locale))
        } else {
            first.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
        }
        return "$start – ${last.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))}"
    }

    /** "Ended in 2026"; year 0 (unknown) reads just "Ended". */
    fun endedGroupTitle(year: Int): String = if (year == 0) "Ended" else "Ended in $year"

    /** The end-bill question's dialog title: "End Bell internet?" */
    fun endDialogTitle(template: RecurringTemplate?): String =
        if (template == null) "End this bill?" else "End ${template.description}?"
}

/** The calendar year a bill ended in (its `endedAt`, else `updatedAt`), 0 if unknown. */
fun endedYear(template: RecurringTemplate, zone: ZoneId = ZoneId.systemDefault()): Int =
    BillText.endedDate(template)?.let { ZonedDateTime.ofInstant(it, zone).year } ?: 0
