package com.spenditos.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthAnnouncementTest {
    @Test
    fun `strength bars announcement maps the three levels`() {
        assertEquals("Password strength: weak", passwordStrengthAnnouncement(PasswordStrength.WEAK))
        assertEquals("Password strength: fair", passwordStrengthAnnouncement(PasswordStrength.MEDIUM))
        assertEquals("Password strength: strong", passwordStrengthAnnouncement(PasswordStrength.STRONG))
    }
}
