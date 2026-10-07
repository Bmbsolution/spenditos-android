package com.spenditos.android.data.purchases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasesMappingTest {
    private fun info(id: String, period: String?) = RevenueCatPurchasesService.Companion.PackageInfo(id, id, "$1", period)

    @Test
    fun `the yearly plan is best value only next to a monthly one`() {
        val both = RevenueCatPurchasesService.mapPackages(listOf(info("m", "month"), info("y", "year")))
        assertEquals(listOf(false, true), both.map { it.isBestValue })
        val yearlyOnly = RevenueCatPurchasesService.mapPackages(listOf(info("y", "year")))
        assertFalse(yearlyOnly.single().isBestValue)
        assertTrue(RevenueCatPurchasesService.mapPackages(emptyList()).isEmpty())
    }

    @Test
    fun `purchases need a key and never run in the test flavors`() {
        assertFalse(RevenueCatPurchasesService.isConfigurable("", isHarness = false, stage = "prod"))
        assertFalse(RevenueCatPurchasesService.isConfigurable("  ", isHarness = false, stage = "prod"))
        assertFalse(RevenueCatPurchasesService.isConfigurable("goog_key", isHarness = true, stage = "harness"))
        assertFalse(RevenueCatPurchasesService.isConfigurable("goog_key", isHarness = false, stage = "local"))
        assertTrue(RevenueCatPurchasesService.isConfigurable("goog_key", isHarness = false, stage = "prod"))
        assertTrue(RevenueCatPurchasesService.isConfigurable("goog_key", isHarness = false, stage = "staging"))
    }
}
