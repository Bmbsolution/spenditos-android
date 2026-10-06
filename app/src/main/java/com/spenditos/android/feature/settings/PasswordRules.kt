package com.spenditos.android.feature.settings

data class PasswordRequirement(val label: String, val isMet: Boolean)

/** The five password checks used by iOS AuthViewModel.swift:91-99. */
fun passwordRequirements(password: String): List<PasswordRequirement> = listOf(
    PasswordRequirement("At least 8 characters", password.codePointCount(0, password.length) >= 8),
    PasswordRequirement("Contains uppercase letter", password.anyCodePoint { Character.isUpperCase(it) }),
    PasswordRequirement("Contains lowercase letter", password.anyCodePoint { Character.isLowerCase(it) }),
    PasswordRequirement("Contains number", password.anyCodePoint { codePoint ->
        val type = Character.getType(codePoint)
        type == Character.DECIMAL_DIGIT_NUMBER.toInt() ||
            type == Character.LETTER_NUMBER.toInt() ||
            type == Character.OTHER_NUMBER.toInt()
    }),
    PasswordRequirement("Contains special character", password.anyCodePoint { !it.isAsciiLetterOrDigit() }),
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
