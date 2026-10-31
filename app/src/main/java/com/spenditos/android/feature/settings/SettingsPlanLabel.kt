package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.entitlement.ProAccessState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the Settings plan rows show, derived from the effective Pro state. */
data class SettingsPlan(
    val isPro: Boolean = false,
    /** "Welcome Premium · ends Nov 12, 2026" while the server reports a welcome grant; null otherwise. */
    val welcomeText: String? = null,
)

/** Copy for the Settings plan row (iOS `SettingsPlanLabel`). */
object SettingsPlanLabel {
    fun plan(state: ProAccessState, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) =
        SettingsPlan(isPro = state.effectiveIsPro, welcomeText = welcomePremiumText(state, locale, zone))

    /**
     * "Welcome Premium · ends <date>" when the server reports an active welcome grant and the store
     * has no subscription. Without a usable date the row just says "Welcome Premium".
     */
    fun welcomePremiumText(state: ProAccessState, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String? {
        if (!state.isWelcomeOnly) return null
        val status = state.serverStatus ?: return null
        val expires = status.expiresAtInstant ?: return Strings.get(R.string.settings_welcome_premium)
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(expires)
        return Strings.get(R.string.settings_welcome_premium_ends, date)
    }
}
