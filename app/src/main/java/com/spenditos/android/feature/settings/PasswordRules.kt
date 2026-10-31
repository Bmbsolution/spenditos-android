package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R

data class PasswordRequirement(val label: String, val isMet: Boolean)

/** The five password checks used by iOS AuthViewModel.swift:91-99. */
fun passwordRequirements(password: String): List<PasswordRequirement> = listOf(
    PasswordRequirement(Strings.get(R.string.common_at_least_8_characters), password.codePointCount(0, password.length) >= 8),
    PasswordRequirement(Strings.get(R.string.settings_contains_uppercase_letter), password.anyCodePoint { Character.isUpperCase(it) }),
    PasswordRequirement(Strings.get(R.string.settings_contains_lowercase_letter), password.anyCodePoint { Character.isLowerCase(it) }),
    PasswordRequirement(Strings.get(R.string.settings_contains_number), password.anyCodePoint { codePoint ->
        val type = Character.getType(codePoint)
        type == Character.DECIMAL_DIGIT_NUMBER.toInt() ||
            type == Character.LETTER_NUMBER.toInt() ||
            type == Character.OTHER_NUMBER.toInt()
    }),
    PasswordRequirement(Strings.get(R.string.settings_contains_special_character), password.anyCodePoint { !it.isAsciiLetterOrDigit() }),
)

private fun String.anyCodePoint(predicate: (Int) -> Boolean): Boolean {
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        if (predicate(codePoint)) return true
        offset += Character.charCount(codePoint)
    }
    return false
}

private fun Int.isAsciiLetterOrDigit(): Boolean =
    this in 'A'.code..'Z'.code || this in 'a'.code..'z'.code || this in '0'.code..'9'.code
