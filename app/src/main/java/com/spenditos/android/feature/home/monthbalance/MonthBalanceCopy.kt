package com.spenditos.android.feature.home.monthbalance

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.monthbalance.MonthBalanceData
import com.spenditos.android.data.monthbalance.MonthBalanceKind
import com.spenditos.android.feature.debts.add.DebtPrefill
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * The month-start card's sentences (iOS `MonthBalanceCopy`; the same as the web card). Month names are English
 * and never depend on the time zone: `2026-09` is always "September".
 */
object MonthBalanceCopy {
    private val english = Locale.US
    private val display: Locale get() = Locale.getDefault()

    /** "September" for `2026-09`; the key itself when it is not a month key. */
    fun monthName(key: String): String =
        runCatching { YearMonth.parse(key).month.getDisplayName(TextStyle.FULL_STANDALONE, display) }.getOrDefault(key)

    /** The month before [date] in the device calendar: "September" on Oct 3. */
    fun previousMonthName(date: LocalDate): String = monthName(monthKey(YearMonth.from(date).minusMonths(1)))

    /** `YYYY-MM`, the key `/month-balance` takes (always the device's local month). */
    fun monthKey(month: YearMonth): String = String.format(english, "%04d-%02d", month.year, month.monthValue)

    fun monthKey(date: LocalDate): String = monthKey(YearMonth.from(date))

    fun headline(data: MonthBalanceData, money: (Double) -> String): String? {
        val previous = monthName(data.previousMonth)
        return when (data.kind) {
            MonthBalanceKind.SHORTFALL -> Strings.get(R.string.home_month_ended_short, previous.replaceFirstChar { it.titlecase(display) }, money(abs(data.balance)))
            MonthBalanceKind.SURPLUS -> Strings.get(R.string.home_month_ended_ahead, previous.replaceFirstChar { it.titlecase(display) }, money(abs(data.balance)))
            MonthBalanceKind.NONE -> null
        }
    }

    /** "Includes $150 you carried into October." when last month carried something in, so the total is never a mystery. */
    fun note(data: MonthBalanceData, money: (Double) -> String): String? {
        if (data.kind == MonthBalanceKind.NONE || data.previousCarriedIn <= 0) return null
        return Strings.get(R.string.home_includes_you_carried_into, money(data.previousCarriedIn), monthName(data.previousMonth))
    }

    fun question(data: MonthBalanceData): String? = when (data.kind) {
        MonthBalanceKind.SHORTFALL -> Strings.get(R.string.home_how_do_you_want_to_handle_it)
        MonthBalanceKind.SURPLUS -> Strings.get(R.string.home_include_it_in, monthName(data.month))
        MonthBalanceKind.NONE -> null
    }

    /**
     * The math sheet's line for an answer that left something out: "Not included: September ended $150 ahead" or
     * "Not tracked: September ended $200 short" (same words on web, iOS and Android).
     */
    fun followUpLine(data: MonthBalanceData, money: (Double) -> String): String? {
        val previous = monthName(data.previousMonth)
        return when (data.kind) {
            MonthBalanceKind.SURPLUS -> Strings.get(R.string.home_not_included_ended_ahead, previous, money(abs(data.balance)))
            MonthBalanceKind.SHORTFALL -> Strings.get(R.string.home_not_tracked_ended_short, previous, money(abs(data.balance)))
            MonthBalanceKind.NONE -> null
        }
    }

    /** The Add debt sheet's starting point for a shortfall: "September shortfall", what was short, tied to that month. */
    fun shortfallPrefill(data: MonthBalanceData): DebtPrefill? {
        if (data.kind != MonthBalanceKind.SHORTFALL) return null
        return DebtPrefill(name = Strings.get(R.string.home_month_shortfall_debt_name, monthName(data.previousMonth)), owed = abs(data.balance), shortfallMonth = data.previousMonth)
    }

    /** "Pay it in October": the button names the month the card is shown in. */
    fun payIn(data: MonthBalanceData): String = Strings.get(R.string.home_pay_it_in, monthName(data.month))

    /**
     * "That leaves you $360 short in November. Spreading it out may fit better." when paying the whole shortfall
     * back this month is more than this month's [leftAfterBills]. Null when it fits or is unknown.
     */
    fun doesNotFit(data: MonthBalanceData, leftAfterBills: Double?, money: (Double) -> String): String? {
        if (data.kind != MonthBalanceKind.SHORTFALL || leftAfterBills == null) return null
        val gap = abs(data.balance) - leftAfterBills
        if (gap < 0.005) return null
        return Strings.get(R.string.home_that_leaves_you_short_in, money(gap), monthName(data.month))
    }

    /**
     * The day "Pay it in <Month>" puts the one payment on: the next income still to come this month (today
     * included), else the month's last day.
     */
    fun paymentDate(expectedIncome: List<LocalDate>, today: LocalDate): LocalDate {
        val month = YearMonth.from(today)
        return expectedIncome.filter { it >= today && YearMonth.from(it) == month }.minOrNull() ?: month.atEndOfMonth()
    }

    /** The toast after "Pay it in <Month>": "Added October shortfall · $200 on Nov 14". */
    fun paidToast(name: String, amount: String, date: LocalDate): String =
        Strings.get(
            R.string.home_added_on,
            name,
            amount,
            date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", display)),
        )

    val REMOVE: String get() = Strings.get(R.string.plan_remove)
    val REMOVE_DESCRIPTION: String get() = Strings.get(R.string.home_remove_carried_in_amount)

    val OFFLINE: String get() = Strings.get(R.string.home_you_re_offline_connect_to)
}
