package com.spenditos.android.feature.auth

data class PasswordRequirement(val label: String, val isMet: Boolean)

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

/** The five registration checks in iOS `Features/Auth/AuthViewModel.swift:91-99`. */
object PasswordRules {
    val labels = listOf("At least 8 characters", "Uppercase letter", "Lowercase letter", "Number", "Special character")

    fun requirements(password: String): List<PasswordRequirement> = listOf(
        PasswordRequirement(labels[0], password.length >= 8),
        PasswordRequirement(labels[1], password.any(Char::isUpperCase)),
        PasswordRequirement(labels[2], password.any(Char::isLowerCase)),
        PasswordRequirement(labels[3], password.any { Character.getType(it) in NUMBER_TYPES }),
        PasswordRequirement(labels[4], password.any { !it.isAsciiLetterOrDigit() }),
    )

    fun strength(password: String): PasswordStrength = when (requirements(password).count { it.isMet }) {
        in 0..2 -> PasswordStrength.WEAK
        in 3..4 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.STRONG
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private val NUMBER_TYPES = setOf(
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
        Character.LETTER_NUMBER.toInt(),
        Character.OTHER_NUMBER.toInt(),
    )
}
