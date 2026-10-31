package com.spenditos.android.core.i18n

import android.content.Context
import android.os.Build
import java.util.Locale

/** The language the app is shown in: the device (or Android 13+ per-app) locale decides; Spenditos ships `en` and `fr`. */
object AppLanguage {
    val supported = listOf("en", "fr")

    /**
     * The locale the app is shown in right now. On Android 13+ the per-app language set through
     * `LocaleManager` wins (it changes without a process restart); otherwise the default locale.
     */
    fun currentLocale(context: Context): Locale {
        if (Build.VERSION.SDK_INT >= 33) {
            val app = runCatching { context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales }.getOrNull()
            if (app != null && !app.isEmpty) app.get(0)?.let { return it }
        }
        return Locale.getDefault()
    }

    /** `"fr"` for any French locale, `"en"` otherwise. */
    fun resolved(locale: Locale = Locale.getDefault()): String = if (locale.language == "fr") "fr" else "en"

    /** `Accept-Language` value, resolved app language first: `fr-CA,fr;q=0.9`, `en-US,en;q=0.9`, or just `fr` with no region. */
    fun acceptLanguage(locale: Locale = Locale.getDefault()): String {
        val lang = resolved(locale)
        val region = locale.country.takeIf { locale.language == lang && it.length == 2 }
        return if (region != null) "$lang-$region,$lang;q=0.9" else lang
    }
}
