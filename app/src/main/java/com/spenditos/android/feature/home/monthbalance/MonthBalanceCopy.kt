package com.spenditos.android.feature.home.monthbalance

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

    /** "September" for `2026-09`; the key itself when it is not a month key. */
    fun monthName(key: String): String =
        runCatching { YearMonth.parse(key).month.getDisplayName(TextStyle.FULL_STANDALONE, english) }.getOrDefault(key)

    /** The month before [date] in the device calendar: "September" on Oct 3. */
    fun previousMonthName(date: LocalDate): String = monthName(monthKey(YearMonth.from(date).minusMonths(1)))

    /** `YYYY-MM`, the key `/month-balance` takes (always the device's local month). */
    fun monthKey(month: YearMonth): String = String.format(english, "%04d-%02d", month.year, month.monthValue)

    fun monthKey(date: LocalDate): String = monthKey(YearMonth.from(date))

    fun headline(data: MonthBalanceData, money: (Double) -> String): String? {
        val previous = monthName(data.previousMonth)
        return when (data.kind) {
            MonthBalanceKind.SHORTFALL -> "$previous ended ${money(abs(data.balance))} short."
            MonthBalanceKind.SURPLUS -> "$previous ended ${money(abs(data.balance))} ahead."
            MonthBalanceKind.NONE -> null
        }
    }

    /** "Includes $150 you carried into October." when last month carried something in, so the total is never a mystery. */
    fun note(data: MonthBalanceData, money: (Double) -> String): String? {
        if (data.kind == MonthBalanceKind.NONE || data.previousCarriedIn <= 0) return null
        return "Includes ${money(data.previousCarriedIn)} you carried into ${monthName(data.previousMonth)}."
    }

    fun question(data: MonthBalanceData): String? = when (data.kind) {
        MonthBalanceKind.SHORTFALL -> "Track it as a debt?"
        MonthBalanceKind.SURPLUS -> "Include it in ${monthName(data.month)}?"
        MonthBalanceKind.NONE -> null
    }

    /**
     * The math sheet's line for an answer that left something out: "Not included: September ended $150 ahead" or
     * "Not tracked: September ended $200 short" (same words on web, iOS and Android).
     */
    fun followUpLine(data: MonthBalanceData, money: (Double) -> String): String? {
        val previous = monthName(data.previousMonth)
        return when (data.kind) {
            MonthBalanceKind.SURPLUS -> "Not included: $previous ended ${money(abs(data.balance))} ahead"
            MonthBalanceKind.SHORTFALL -> "Not tracked: $previous ended ${money(abs(data.balance))} short"
            MonthBalanceKind.NONE -> null
        }
    }

    /** The Add debt sheet's starting point for a shortfall: "September shortfall", what was short, tied to that month. */
    fun shortfallPrefill(data: MonthBalanceData): DebtPrefill? {
        if (data.kind != MonthBalanceKind.SHORTFALL) return null
        return DebtPrefill(name = "${monthName(data.previousMonth)} shortfall", owed = abs(data.balance), shortfallMonth = data.previousMonth)
    }

    const val REMOVE = "Remove"
    const val REMOVE_DESCRIPTION = "Remove carried-in amount"

    const val OFFLINE = "You're offline. Connect to the internet to answer this."
}
