package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.entitlement.ProAccessState
import com.spenditos.android.data.model.PremiumStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the Settings plan rows show, derived from the effective Pro state. */
data class SettingsPlan(
    val isPro: Boolean = false,
    /** "Welcome Premium · ends Nov 12, 2026" while the server reports a welcome grant; null otherwise. */
    val welcomeText: String? = null,
    /** "Switches to yearly on Dec 1, 2026" while the server reports a scheduled plan change; null otherwise. */
    val pendingChangeText: String? = null,
)

/** Copy for the Settings plan row (iOS `SettingsPlanLabel`). */
object SettingsPlanLabel {
    fun plan(state: ProAccessState, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()) =
        SettingsPlan(
            isPro = state.effectiveIsPro,
            welcomeText = welcomePremiumText(state, locale, zone),
            pendingChangeText = pendingChangeText(state, locale, zone),
        )

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

    /** The scheduled plan change, when there is a date to give (a revoked plan has nothing to switch to). */
    fun pendingChangeText(state: ProAccessState, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String? {
        if (!state.effectiveIsPro) return null
        val change = state.premium?.pendingChange ?: return null
        val effective = PremiumStatus.parseInstant(change.effectiveAt) ?: return null
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(effective)
        val target = when (change.period) {
            "P1M" -> "monthly"
            "P1Y" -> "yearly"
            else -> "a new plan"
        }
        return "Switches to $target on $date"
    }
}
