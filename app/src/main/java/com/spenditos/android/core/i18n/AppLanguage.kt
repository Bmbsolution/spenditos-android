package com.spenditos.android.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** The language the app is shown in: the device (or Android 13+ per-app) locale decides; Spenditos ships `en` and `fr`. */
object AppLanguage {
    val supported = listOf("en", "fr")

    /**
     * The locale the app is shown in right now: the per-app language (`AppCompatDelegate`, which is the
     * system per-app setting on Android 13+ and the stored backport below that), else the default locale.
     */
    fun currentLocale(@Suppress("UNUSED_PARAMETER") context: Context? = null): Locale {
        val app = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        if (app != null && !app.isEmpty) app.get(0)?.let { return it }
        return Locale.getDefault()
    }

    /** The choice currently applied (Device language when no per-app locale is set). */
    fun currentChoice(): LanguageChoice =
        LanguageChoice.fromLocaleTags(runCatching { AppCompatDelegate.getApplicationLocales().toLanguageTags() }.getOrNull())

    /** Language tags of the system locale list (ignores the per-app override). */
    fun deviceTags(): List<String> {
        val list = android.content.res.Resources.getSystem().configuration.locales
        return (0 until list.size()).map { list.get(it).toLanguageTag() }
    }

    /**
     * Applies [choice] immediately (activities recreate, the stored per-app language is kept on Android 9+ and
     * mirrors system Settings > Apps > Spenditos > Language on 13+) and returns the account language to save.
     */
    fun apply(choice: LanguageChoice): String {
        val tags = deviceTags()
        AppCompatDelegate.setApplicationLocales(
            if (choice == LanguageChoice.DEVICE) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(choice.localeTags),
        )
        // Formatters, the Accept-Language interceptor and Strings read the default locale / app locale right away.
        val locale = if (choice == LanguageChoice.DEVICE) Locale.forLanguageTag(tags.firstOrNull() ?: "en") else Locale.forLanguageTag(choice.tag)
        Locale.setDefault(locale)
        return choice.effectiveLanguage(tags)
    }

    /** `"%"`, with the non-breaking space French typography puts before it (`42 %`). */
    fun percentSuffix(locale: Locale = Locale.getDefault()): String = if (locale.language == "fr") "\u00A0%" else "%"

    /** `"fr"` for any French locale, `"en"` otherwise. */
    fun resolved(locale: Locale = Locale.getDefault()): String = if (locale.language == "fr") "fr" else "en"

    /** `Accept-Language` value, resolved app language first: `fr-CA,fr;q=0.9`, `en-US,en;q=0.9`, or just `fr` with no region. */
    fun acceptLanguage(locale: Locale = Locale.getDefault()): String {
        val lang = resolved(locale)
        val region = locale.country.takeIf { locale.language == lang && it.length == 2 }
        return if (region != null) "$lang-$region,$lang;q=0.9" else lang
    }
}
