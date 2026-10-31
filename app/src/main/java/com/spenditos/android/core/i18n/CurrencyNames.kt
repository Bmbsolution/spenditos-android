package com.spenditos.android.core.i18n

import java.util.Currency
import java.util.Locale

/** Currency names in the app language, from the platform's own data (English [fallback] when unknown). */
object CurrencyNames {
    fun display(code: String, fallback: String = code, locale: Locale = Locale.getDefault()): String {
        val name = runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()
        if (name.isNullOrBlank() || name == code) return fallback
        return name.replaceFirstChar { it.titlecase(locale) }
    }
}
