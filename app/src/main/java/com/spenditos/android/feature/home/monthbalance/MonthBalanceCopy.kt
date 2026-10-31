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
        MonthBalanceKind.SHORTFALL -> Strings.get(R.string.home_track_it_as_a_debt)
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

    val REMOVE: String get() = Strings.get(R.string.plan_remove)
    val REMOVE_DESCRIPTION: String get() = Strings.get(R.string.home_remove_carried_in_amount)

    val OFFLINE: String get() = Strings.get(R.string.home_you_re_offline_connect_to)
}
