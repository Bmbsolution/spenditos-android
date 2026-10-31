package com.spenditos.android.core.i18n

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date formats that follow the locale's own ordering and 12/24-hour convention. The English-style
 * [pattern] doubles as the skeleton ("MMM d, h:mm a" -> "MMMdhmma") for `getBestDateTimePattern`,
 * and as the fallback where Android's date utilities are unavailable (JVM unit tests).
 */
object LocalizedDate {
    fun formatter(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(localizedPattern(pattern, locale), locale)

    fun localizedPattern(pattern: String, locale: Locale): String {
        val skeleton = buildString {
            var quoted = false
            for (c in pattern) {
                if (c == '\'') { quoted = !quoted; continue }
                if (!quoted && c.isLetter()) append(c)
            }
        }
        val best = runCatching { DateFormat.getBestDateTimePattern(locale, skeleton) }.getOrNull()
        return if (best.isNullOrBlank()) pattern else best
    }
}
