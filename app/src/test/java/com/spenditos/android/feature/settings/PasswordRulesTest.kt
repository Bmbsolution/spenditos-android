package com.spenditos.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun `five password checks match the iOS rule table`() {
        data class Case(val password: String, val expected: List<Boolean>)
        val cases = listOf(
            Case("", listOf(false, false, false, false, false)),
            Case("Abcdefg1", listOf(true, true, true, true, false)),
            Case("Abc!defg", listOf(true, true, true, false, true)),
            Case("Abcdef1!", listOf(true, true, true, true, true)),
            Case("Äbcdef1!", listOf(true, true, true, true, true)),
            Case("Abcdefg²", listOf(true, true, true, true, true)),
        )

        cases.forEach { case ->
            assertEquals(case.password, case.expected, passwordRequirements(case.password).map { it.isMet })
        }
    }
}
