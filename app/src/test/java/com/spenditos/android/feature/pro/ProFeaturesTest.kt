package com.spenditos.android.feature.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProFeaturesTest {
    @Test
    fun `paywall lists exactly the iOS features`() {
        assertEquals(
            listOf(
                "Unlimited AI Receipt Scanning",
                "Smart Voice Entry",
                "Advanced Analytics",
                "Unlimited income items and bills",
            ),
            PRO_FEATURES.map { it.title },
        )
    }

    @Test
    fun `paywall no longer advertises statement import`() {
        assertFalse(PRO_FEATURES.any { it.title.contains("Statement", ignoreCase = true) || it.description.contains("CSV") })
    }
}
