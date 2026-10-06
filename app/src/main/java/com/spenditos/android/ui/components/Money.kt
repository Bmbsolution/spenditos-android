package com.spenditos.android.ui.components

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Formats a positive wire amount in the user's currency; the sign comes from the category. */
object Money {
    fun format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(amount)
    }

    fun signed(amount: Double, isIncome: Boolean, currencyCode: String, locale: Locale = Locale.getDefault()): String =
        (if (isIncome) "+" else "−") + format(amount, currencyCode, locale)
}
