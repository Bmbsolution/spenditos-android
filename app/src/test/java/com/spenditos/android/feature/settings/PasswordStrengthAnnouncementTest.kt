package com.spenditos.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthAnnouncementTest {
    @Test
    fun `strength bars announcement uses the existing label lowercased`() {
        assertEquals("Password strength: weak", passwordStrengthAnnouncement("Weak"))
        assertEquals("Password strength: fair", passwordStrengthAnnouncement("Fair"))
        assertEquals("Password strength: good", passwordStrengthAnnouncement("Good"))
        assertEquals("Password strength: strong", passwordStrengthAnnouncement("Strong"))
    }
}
