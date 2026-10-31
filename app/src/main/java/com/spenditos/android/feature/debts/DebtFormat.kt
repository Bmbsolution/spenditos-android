package com.spenditos.android.feature.debts

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.debts.DebtSummary
import com.spenditos.android.data.debts.DebtWithPlan
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.floor

/**
 * Plain-language text for debts (iOS `DebtFormat.swift`; docs/features/debts.md "Wording"):
 * money in the user's currency, "Jul 2028" style months, and a payment phrased with its real
 * interval ("$100 every 3 weeks"). Never "liability", "amortization", "principal", "template" or "rule".
 */

/**
 * Payments were stopped (`payment: null`): the server keeps the bill for history, so a stopped
 * debt still has a `payment` but nothing scheduled. Don't use `debt.billId == null` for this.
 */
internal val DebtWithPlan.paymentsStopped: Boolean
    get() = !summary.isPaidOff && summary.payment != null && summary.nextPaymentDate == null && projection.schedule.isEmpty()

/** Swift's `.rounded()` (half away from zero); Kotlin's `round` is half-even. */
internal fun roundHalfAwayFromZero(value: Double): Double =
    if (value < 0) -floor(-value + 0.5) else floor(value + 0.5)

internal object DebtFormat {
    // region Money

    /**
     * Whole amounts drop the cents ("$6,240"); anything else keeps two decimals ("$6,240.50").
     */
    fun money(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val rounded = roundHalfAwayFromZero(value * 100) / 100
        val isWhole = rounded == floor(rounded)
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        val digits = if (isWhole) 0 else 2
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(rounded)
    }

    /** Rounds to cents (half away from zero), for sums shown as money. */
    fun cents(value: Double): Double = roundHalfAwayFromZero(value * 100) / 100

    // endregion

    // region Dates

    private fun parse(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** "Jul 2028" from "2028-07-01". */
    fun monthYear(iso: String?, locale: Locale = Locale.getDefault()): String? =
        parse(iso)?.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

    /** "Oct 1, 2026" from "2026-10-01". */
    fun shortDate(iso: String?, locale: Locale = Locale.getDefault()): String? =
        parse(iso)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /** "2026-10" from a date, the `targetMonth` the preview endpoint takes. */
    fun monthKey(date: LocalDate): String = "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue)

    // endregion

    // region Payment interval

    /**
     * How often a payment happens: "/ month" style when it is every period, "every 3 weeks" style
     * otherwise. `repeatInterval` multiplies the frequency's own period: monthly with an interval
     * of 3 is every 3 months, bi-weekly with 2 is every 4 weeks. An unknown frequency has no phrase.
     */
    fun paymentPeriod(frequency: String, repeatInterval: Int?): String {
        val n = maxOf(1, repeatInterval ?: 1)
        return when (frequency) {
            "daily" -> if (n == 1) "/ day" else "every $n days"
            "weekly" -> if (n == 1) "/ week" else "every $n weeks"
            "bi-weekly" -> "every ${2 * n} weeks"
            "semi-monthly" -> if (n == 1) "twice a month" else "every $n half-months"
            "monthly" -> if (n == 1) "/ month" else "every $n months"
            "quarterly" -> if (n == 1) "/ quarter" else "every ${3 * n} months"
            "semi-annually" -> "every ${6 * n} months"
            "yearly" -> if (n == 1) "/ year" else "every $n years"
            else -> ""
        }
    }

    /** "$320 / month", "$100 every 3 weeks". */
    fun paymentLine(
        amount: Double,
        frequency: String,
        repeatInterval: Int?,
        currencyCode: String,
        locale: Locale = Locale.getDefault(),
    ): String = "${money(amount, currencyCode, locale)} ${paymentPeriod(frequency, repeatInterval)}".trim()

    /** The bill's payment as one phrase, or null when the debt has no bill. */
    fun paymentLine(summary: DebtSummary, currencyCode: String, locale: Locale = Locale.getDefault()): String? {
        val payment = summary.payment ?: return null
        val frequency = summary.frequency ?: return null
        return paymentLine(payment, frequency, summary.repeatInterval, currencyCode, locale)
    }

    // endregion

    // region Row text

    /**
     * "$6,240 left of $10,000 · done by Jul 2028", or "... · Payment too low to finish". A paid-off
     * debt reads "Paid off on Aug 15, 2026".
     */
    fun progressLine(plan: DebtWithPlan, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val summary = plan.summary
        if (summary.isPaidOff) {
            val date = shortDate(summary.paidOffDate, locale)
            return if (date != null) Strings.get(R.string.debts_paid_off_on, date) else Strings.get(R.string.debts_paid_off)
        }
        val parts = mutableListOf("${money(summary.balance, currencyCode, locale)} left of ${money(plan.debt.startingBalance, currencyCode, locale)}")
        if (plan.paymentsStopped) {
            parts += Strings.get(R.string.debts_payments_stopped)
        } else if (plan.projection.neverPaysOff) {
            parts += Strings.get(R.string.debts_payment_too_low_to_finish)
        } else {
            monthYear(plan.projection.payoffDate, locale)?.let { parts += "done by $it" }
        }
        return parts.joinToString(" · ")
    }

    // endregion
}

/**
 * Plain-language text for what can go wrong with a debt request (iOS `DebtErrorMessage`). The server
 * puts a short code in `error` ([ApiError.Server.serverMessage]); nothing here shows the code itself.
 */
internal object DebtErrorMessage {
    val OFFLINE: String get() = Strings.get(R.string.debts_you_re_offline_connect_to)
    val NOT_FOUND: String get() = Strings.get(R.string.debts_this_debt_couldn_t_be)
    val TARGET_TOO_SOON: String get() = Strings.get(R.string.debts_that_month_is_too_soon)
    val FUTURE_DATE: String get() = Strings.get(R.string.debts_the_date_can_t_be)
    val PAYMENT_INCOMPLETE: String get() = Strings.get(R.string.debts_add_how_much_you_pay)
    val INVALID_INPUT: String get() = Strings.get(R.string.debts_check_the_amounts_and_dates)
    val CANT_REACH: String get() = Strings.get(R.string.debts_can_t_reach_spenditos_right)
    val GENERIC: String get() = Strings.get(R.string.auth_something_went_wrong_please_try)
    val CATEGORY_UNAVAILABLE: String get() = Strings.get(R.string.debts_the_category_for_this_debt)

    /**
     * [offline] is true when a write was refused because the device has no connection (nothing was
     * sent); any other transport failure reads [CANT_REACH].
     */
    fun message(error: Throwable, offline: Boolean = false): String = when (error) {
        is ApiError.Network -> if (offline) OFFLINE else CANT_REACH
        is ApiError.Server -> {
            val code = error.serverMessage
            if (error.status == 404 || code == "DebtNotFound") {
                NOT_FOUND
            } else {
                when (code) {
                    "TARGET_TOO_SOON" -> TARGET_TOO_SOON
                    "DateInFuture" -> FUTURE_DATE
                    "PaymentIncomplete" -> PAYMENT_INCOMPLETE
                    "ValidationError", "InvalidAsOf", "InvalidIdempotencyKey" -> INVALID_INPUT
                    "CategoryNotFound", "CategoryMustBeExpense", "CategoryBeingDeleted" -> CATEGORY_UNAVAILABLE
                    else -> GENERIC
                }
            }
        }
        else -> GENERIC
    }
}

/** The user's text as an amount: `,` or `.` as the decimal mark, finite numbers only. Null when it is not a number. */
internal fun parseDebtAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** "Enter how much you paid" style validation shared by the two debt sheets (iOS `amountError`). */
internal fun extraPaymentAmountError(text: String, showErrors: Boolean): String? {
    if (text.isBlank()) return if (showErrors) Strings.get(R.string.debts_enter_how_much_you_paid) else null
    val amount = parseDebtAmount(text)
    return if (amount == null || amount <= 0) Strings.get(R.string.common_enter_an_amount_above_zero) else null
}

internal fun correctBalanceAmountError(text: String, showErrors: Boolean): String? {
    if (text.isBlank()) return if (showErrors) Strings.get(R.string.debts_enter_what_you_owe_today) else null
    val amount = parseDebtAmount(text)
    return if (amount == null || amount < 0) Strings.get(R.string.debts_enter_zero_or_more) else null
}

/** The balance as the Correct balance field's starting text: "6040" or "6040.50". */
internal fun balanceFieldText(balance: Double): String =
    if (balance == floor(balance)) balance.toLong().toString() else "%.2f".format(Locale.ROOT, balance)

internal fun currencySymbol(code: String, locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)
