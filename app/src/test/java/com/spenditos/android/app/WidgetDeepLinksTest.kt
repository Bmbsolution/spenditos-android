package com.spenditos.android.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The pending widget link: offered once by MainActivity, consumed/cleared by the main screen. */
@RunWith(RobolectricTestRunner::class)
class WidgetDeepLinksTest {
    @Before
    fun reset() {
        WidgetDeepLinks.clear()
    }

    @Test
    fun `a widget VIEW intent pends its host and consume clears it`() {
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://add-expense")))
        assertEquals(WidgetDeepLinks.ADD_EXPENSE, WidgetDeepLinks.pending.value)
        WidgetDeepLinks.consume()
        assertNull(WidgetDeepLinks.pending.value)

        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://scan-receipt")))
        assertEquals(WidgetDeepLinks.SCAN_RECEIPT, WidgetDeepLinks.pending.value)
    }

    @Test
    fun `null intents unknown schemes and unknown hosts are ignored`() {
        WidgetDeepLinks.offer(null)
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("https://add-expense")))
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://elsewhere")))
        WidgetDeepLinks.offer(Intent())
        assertNull(WidgetDeepLinks.pending.value)
    }

    @Test
    fun `the latest offer replaces an unconsumed one`() {
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://add-expense")))
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://scan-receipt")))
        assertEquals(WidgetDeepLinks.SCAN_RECEIPT, WidgetDeepLinks.pending.value)
    }

    @Test
    fun `clear drops a pending link so it never fires after the next sign-in`() {
        WidgetDeepLinks.offer(Intent(Intent.ACTION_VIEW, Uri.parse("spenditos://add-expense")))
        WidgetDeepLinks.clear()
        assertNull(WidgetDeepLinks.pending.value)
    }
}
