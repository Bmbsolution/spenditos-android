package com.spenditos.android.feature.debts.add

import com.spenditos.android.core.network.ApiError
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Plain-language text for the "Add a debt" sheet (iOS `DebtFormat`, `DebtErrorMessage`,
 * `DebtFormViewModel.frequencyLabel`; docs/features/debts.md "Wording"). Kept in this package so the
 * sheet does not depend on the debts list/detail screens.
 *
 * Never "liability", "amortization", "principal", "template" or "rule".
 */
internal object DebtAddText {
    /** How often the payment happens. The daily option is left out on purpose. */
    val frequencies: List<String> =
        listOf("weekly", "bi-weekly", "semi-monthly", "monthly", "quarterly", "semi-annually", "yearly")

    fun frequencyLabel(frequency: String): String = when (frequency) {
        "daily" -> "Every day"
        "weekly" -> "Every week"
        "bi-weekly" -> "Every 2 weeks"
        "semi-monthly" -> "Twice a month"
        "monthly" -> "Every month"
        "quarterly" -> "Every 3 months"
        "semi-annually" -> "Every 6 months"
        "yearly" -> "Every year"
        else -> "Every month"
    }

    /** Whole amounts drop the cents ("$6,240"); anything else keeps two decimals ("$6,240.50"). */
    fun money(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val rounded = (value * 100).roundToLong() / 100.0
        val isWhole = abs(rounded - Math.rint(rounded)) < 1e-9
        val format = java.text.NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        val digits = if (isWhole) 0 else 2
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(rounded)
    }

    /** "Jul 2028" from "2028-07-01"; null when absent or not a date. */
    fun monthYear(iso: String?, locale: Locale = Locale.getDefault()): String? =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

    /** "2026-10": the `targetMonth` the preview endpoint takes. */
    fun monthKey(month: YearMonth): String = month.toString()

    /** "/ month" style when it is every period (the add sheet never sets a repeat interval). */
    fun paymentPeriod(frequency: String): String = when (frequency) {
        "daily" -> "/ day"
        "weekly" -> "/ week"
        "bi-weekly" -> "every 2 weeks"
        "semi-monthly" -> "twice a month"
        "quarterly" -> "/ quarter"
        "semi-annually" -> "every 6 months"
        "yearly" -> "/ year"
        else -> "/ month"
    }

    /** "$447 / month". */
    fun paymentLine(amount: Double, frequency: String, currencyCode: String, locale: Locale = Locale.getDefault()): String =
        "${money(amount, currencyCode, locale)} ${paymentPeriod(frequency)}"

    // region Errors (iOS DebtErrorMessage)

    const val OFFLINE = "You're offline. Connect to the internet to change your debts."
    const val NOT_FOUND = "This debt couldn't be found. It may have been deleted."
    const val TARGET_TOO_SOON = "That month is too soon. Pick a later month, or a payment you can make."
    const val FUTURE_DATE = "The date can't be in the future."
    const val PAYMENT_INCOMPLETE = "Add how much you pay, how often, and the date of the next payment."
    const val INVALID_INPUT = "Check the amounts and dates and try again."
    private const val CANNOT_REACH = "Can't reach Spenditos right now. Check your connection and try again."
    private const val GENERIC = "Something went wrong. Please try again."

    /**
     * The repository fails a debt write with [ApiError.Network] before anything is sent when the device is
     * offline; [isWrite] with [online] false therefore reads as the offline copy (iOS `DebtServiceError.offline`).
     */
    fun message(error: Throwable, isWrite: Boolean, online: Boolean): String = when (error) {
        is ApiError.Network -> if (isWrite && !online) OFFLINE else CANNOT_REACH
        is ApiError.Unauthorized -> "Sign in again to continue."
        is ApiError.Server -> serverMessage(error)
        else -> GENERIC
    }

    private fun serverMessage(error: ApiError.Server): String {
        val code = error.serverMessage
        if (error.status == 404 || code == "DebtNotFound") return NOT_FOUND
        return when (code) {
            "TARGET_TOO_SOON" -> TARGET_TOO_SOON
            "DateInFuture" -> FUTURE_DATE
            "PaymentIncomplete" -> PAYMENT_INCOMPLETE
            "ValidationError", "InvalidAsOf", "InvalidIdempotencyKey" -> INVALID_INPUT
            "CategoryNotFound", "CategoryMustBeExpense", "CategoryBeingDeleted" ->
                "The category for this debt isn't available anymore. Pick another one and try again."
            else -> GENERIC
        }
    }

    // endregion
}

/** iOS `AmountParser.decimal`: "7,5" and "1.234,56" both work; null when it is not a plain number. */
internal object DebtAmountParser {
    private val plain = Regex("""^(\d+\.?\d*|\.\d+)$""")

    fun decimal(value: String): Double? {
        val text = value.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
        if (text.isEmpty()) return null
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        val normalized = if (lastDot >= 0 && lastComma >= 0) {
            // Both present ("1.234,56"): the later one separates the fraction, the other is grouping.
            val decimalSeparator = if (lastDot > lastComma) '.' else ','
            val grouping = if (decimalSeparator == '.') ',' else '.'
            text.replace(grouping.toString(), "").replace(decimalSeparator, '.')
        } else {
            text.replace(',', '.')
        }
        if (!plain.matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
