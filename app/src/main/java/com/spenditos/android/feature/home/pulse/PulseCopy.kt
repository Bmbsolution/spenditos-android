package com.spenditos.android.feature.home.pulse

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.feature.home.CashFlowStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sentence under the hero amount, ported from iOS `FinancialPulseView.reason(for:)`. */
object PulseCopy {
    /** [money] formats a positive amount in the user's currency. */
    fun reason(status: CashFlowStatus, locale: Locale, money: (BigDecimal) -> String): String {
        fun perDay(amount: BigDecimal) = Strings.get(R.string.home_amount_per_day, money(amount))
        fun day(date: LocalDate) = date.format(com.spenditos.android.core.i18n.LocalizedDate.formatter("MMM d", locale))
        return when (status.state) {
            CashFlowStatus.State.SETUP ->
                if (status.spent.signum() > 0) {
                    Strings.get(R.string.home_no_income_this_month_yet, money(status.spent))
                } else {
                    Strings.get(R.string.home_add_your_income_and_log)
                }
            CashFlowStatus.State.OVER ->
                if (status.carriedIn.signum() > 0) Strings.get(R.string.home_over_income_carried, money(status.leftAfterBills.negate())) else Strings.get(R.string.home_over_income, money(status.leftAfterBills.negate()))
            CashFlowStatus.State.TIGHT, CashFlowStatus.State.WATCH ->
                when {
                    // A pace from the first few days (one big purchase) isn't a pace.
                    status.daysElapsed < 7 ->
                        Strings.get(R.string.home_left_after_bills_for_days, money(status.leftAfterBills), days(status.daysRemaining), perDay(status.affordablePace))
                    status.state == CashFlowStatus.State.TIGHT -> {
                        val runOut = status.runOutDate(status.today)
                        if (runOut != null) {
                            Strings.get(R.string.home_at_about_you_ll_run, perDay(status.pace), day(runOut))
                        } else {
                            Strings.get(R.string.home_you_re_spending_about_keeps, perDay(status.pace), perDay(status.affordablePace))
                        }
                    }
                    else -> Strings.get(R.string.home_you_re_spending_about_up, perDay(status.pace), perDay(status.affordablePace))
                }
            CashFlowStatus.State.ON_TRACK ->
                Strings.get(R.string.home_after_bills_you_can_spend, perDay(status.affordablePace), day(status.monthEnd))
        }
    }

    fun days(count: Int): String = Strings.plural(R.plurals.home_days_count, count)

    /** Whole percent, but never "0%" while something is left. */
    fun percentLabel(share: BigDecimal): String {
        if (share.signum() > 0 && share < BigDecimal("0.01")) return Strings.get(R.string.home_percent_under_one)
        return java.text.NumberFormat.getPercentInstance(Locale.getDefault()).apply { maximumFractionDigits = 0 }.format(share.setScale(2, java.math.RoundingMode.HALF_UP).toDouble())
    }
}
