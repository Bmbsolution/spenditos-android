package com.spenditos.android.core.i18n

import java.util.Locale

/**
 * What the user picks in Settings > Language. Names are always shown in their own language
 * ("English", "Français"); [DEVICE] follows the system and has a translated label instead.
 */
enum class LanguageChoice(val tag: String, val nativeName: String?) {
    DEVICE("", null),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français");

    /** The `AppCompatDelegate.setApplicationLocales` argument: empty for [DEVICE]. */
    val localeTags: String get() = tag

    /** Account `settings.language` for this choice; [DEVICE] resolves through [deviceLanguage]. */
    fun effectiveLanguage(deviceTags: List<String>): String =
        if (this == DEVICE) deviceLanguage(deviceTags) else tag

    companion object {
        /** The choice a per-app locale list (`"fr-CA"`, `""`) stands for; anything unsupported is [DEVICE]. */
        fun fromLocaleTags(tags: String?): LanguageChoice {
            val first = tags.orEmpty().split(',').firstOrNull { it.isNotBlank() }?.trim() ?: return DEVICE
            return when (Locale.forLanguageTag(first).language) {
                "en" -> ENGLISH
                "fr" -> FRENCH
                else -> DEVICE
            }
        }

        /** The first supported language (`en`/`fr`) among the device's locale tags, `en` when none. */
        fun deviceLanguage(deviceTags: List<String>): String =
            deviceTags.map { Locale.forLanguageTag(it).language }.firstOrNull { it in AppLanguage.supported } ?: "en"
    }
}
