package com.spenditos.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun `five requirements match the iOS password cases`() {
        val cases = listOf(
            "" to listOf(false, false, false, false, false),
            "abc" to listOf(false, false, true, false, false),
            "abcdefgh" to listOf(true, false, true, false, false),
            "Aaaaaaaa" to listOf(true, true, true, false, false),
            "aaaaaaaa1" to listOf(true, false, true, true, false),
            "aaaaaaaa!" to listOf(true, false, true, false, true),
            "Abcdef1!" to listOf(true, true, true, true, true),
        )
        cases.forEach { (password, expected) ->
            assertEquals(password, expected, PasswordRules.requirements(password).map { it.isMet })
        }
    }

    @Test
    fun `strength matches the iOS thresholds`() {
        val cases = listOf(
            "abc" to PasswordStrength.WEAK,
            "abcdefgh" to PasswordStrength.WEAK,
            "Abcdefgh" to PasswordStrength.MEDIUM,
            "Abcdef1!" to PasswordStrength.STRONG,
        )
        cases.forEach { (password, expected) -> assertEquals(password, expected, PasswordRules.strength(password)) }
    }

    @Test
    fun `requirement labels stay in the same order as iOS`() {
        assertEquals(
            listOf("At least 8 characters", "Uppercase letter", "Lowercase letter", "Number", "Special character"),
            PasswordRules.requirements("").map { it.label },
        )
        assertFalse(PasswordRules.requirements("").any { it.isMet })
        assertTrue(PasswordRules.requirements("Abcdef1!").all { it.isMet })
    }
}
