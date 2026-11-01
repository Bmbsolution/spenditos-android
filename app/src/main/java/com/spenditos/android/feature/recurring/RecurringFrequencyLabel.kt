package com.spenditos.android.feature.recurring

import com.spenditos.android.R
import com.spenditos.android.core.i18n.Strings

/** Localized name of a recurrence frequency as the server spells it (`bi-weekly`, `semi-annually`, ...). */
internal fun frequencyLabel(frequency: String): String = when (frequency) {
    "daily" -> Strings.get(R.string.recurring_frequency_daily)
    "weekly" -> Strings.get(R.string.recurring_frequency_weekly)
    "bi-weekly" -> Strings.get(R.string.recurring_frequency_bi_weekly)
    "semi-monthly" -> Strings.get(R.string.recurring_frequency_semi_monthly)
    "monthly" -> Strings.get(R.string.recurring_frequency_monthly)
    "quarterly" -> Strings.get(R.string.recurring_frequency_quarterly)
    "semi-annually" -> Strings.get(R.string.recurring_frequency_semi_annually)
    "yearly" -> Strings.get(R.string.recurring_frequency_yearly)
    else -> frequency.replaceFirstChar(Char::uppercase)
}
