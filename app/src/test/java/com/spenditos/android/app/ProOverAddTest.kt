package com.spenditos.android.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProOverAddTest {
    @Test
    fun `pro shows over the open add sheet so the form stays`() {
        assertTrue(proOverAddVisible(addSheetOpen = true, proRequested = true))
    }

    @Test
    fun `pro is not shown unless requested`() {
        assertFalse(proOverAddVisible(addSheetOpen = true, proRequested = false))
    }

    @Test
    fun `a stale pro request never shows once the sheet is closed`() {
        assertFalse(proOverAddVisible(addSheetOpen = false, proRequested = true))
    }
}
