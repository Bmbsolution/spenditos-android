package com.spenditos.android.feature.home.pulse

import com.spenditos.android.feature.home.CashFlowStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sentence under the hero amount, ported from iOS `FinancialPulseView.reason(for:)`. */
object PulseCopy {
    /** [money] formats a positive amount in the user's currency. */
    fun reason(status: CashFlowStatus, locale: Locale, money: (BigDecimal) -> String): String {
        fun perDay(amount: BigDecimal) = "${money(amount)} a day"
        fun day(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d", locale))
        return when (status.state) {
            CashFlowStatus.State.SETUP ->
                if (status.spent.signum() > 0) {
                    "No income this month yet. You've spent ${money(status.spent)}. Add income to see what's left."
                } else {
                    "Add your income and log a first expense. This card then shows what's left after bills."
                }
            CashFlowStatus.State.OVER ->
                "Spending and bills due are ${money(status.leftAfterBills.negate())} more than this month's income${if (status.carriedIn.signum() > 0) " and what you carried in" else ""}."
            CashFlowStatus.State.TIGHT, CashFlowStatus.State.WATCH ->
                when {
                    // A pace from the first few days (one big purchase) isn't a pace.
                    status.daysElapsed < 7 ->
                        "${money(status.leftAfterBills)} left after bills for ${days(status.daysRemaining)}, about ${perDay(status.affordablePace)}."
                    status.state == CashFlowStatus.State.TIGHT -> {
                        val runOut = status.runOutDate(status.today)
                        if (runOut != null) {
                            "At about ${perDay(status.pace)}, you'll run short around ${day(runOut)}."
                        } else {
                            "You're spending about ${perDay(status.pace)}; ${perDay(status.affordablePace)} keeps you on track."
                        }
                    }
                    else -> "You're spending about ${perDay(status.pace)}. Up to ${perDay(status.affordablePace)} keeps you on track."
                }
            CashFlowStatus.State.ON_TRACK ->
                "After bills, you can spend about ${perDay(status.affordablePace)} until ${day(status.monthEnd)}."
        }
    }

    fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

    /** Whole percent, but never "0%" while something is left. */
    fun percentLabel(share: BigDecimal): String {
        if (share.signum() > 0 && share < BigDecimal("0.01")) return "<1%"
        return "${share.multiply(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toInt()}%"
    }
}
