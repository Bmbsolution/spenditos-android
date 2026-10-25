package com.spenditos.android.feature.scan

import com.spenditos.android.feature.capture.CaptureReviewItem
import com.spenditos.android.testing.FakeExchangeRates
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCurrencyTest {
    private fun item(id: String, amount: String = "10", currency: String? = "USD") =
        CaptureReviewItem(idempotencyKeyId = id, amountText = amount, description = id, date = "2026-10-01", currency = currency)

    @Test
    fun `an amount is converted at the rate and rounded to the cent`() = runTest {
        val result = prepareCurrencyAmounts(listOf(item("a", "19.99")), "CAD", FakeExchangeRates(mapOf("USD" to 1.3749)))
        val converted = result.items.getValue("a")
        assertEquals("27.48", converted.amountText) // 19.99 * 1.3749 = 27.4843
        assertEquals("CAD", converted.convertedTo)
        assertNull(converted.reviewCurrency)
        assertFalse(result.rateFailed)
    }

    @Test
    fun `whole results lose no digits and ask once per currency`() = runTest {
        val rates = FakeExchangeRates(mapOf("USD" to 2.0, "EUR" to 1.5))
        val result = prepareCurrencyAmounts(
            listOf(item("a", "50"), item("b", "100", "usd"), item("c", "10", "EUR")),
            "cad",
            rates,
        )
        assertEquals("100", result.items.getValue("a").amountText)
        assertEquals("200", result.items.getValue("b").amountText)
        assertEquals("15", result.items.getValue("c").amountText)
        assertEquals(setOf("USD", "EUR"), rates.asked.map { it.first }.toSet())
        assertEquals(2, rates.asked.size)
        assertTrue(rates.asked.all { it.second == "CAD" })
    }

    @Test
    fun `rows in the user's currency, without a currency or without an amount are left alone`() = runTest {
        val rates = FakeExchangeRates(mapOf("USD" to 2.0))
        val result = prepareCurrencyAmounts(
            listOf(item("same", currency = "cad"), item("none", currency = null), item("blank", currency = " "), item("noAmount", amount = "")),
            "CAD",
            rates,
        )
        assertTrue(result.items.keys == setOf<String>())
        // "noAmount" is in USD but has nothing to convert; its currency is still asked so the others share one request.
        assertEquals(listOf("USD" to "CAD"), rates.asked)
    }

    @Test
    fun `a currency with no rate stays in its own currency`() = runTest {
        val result = prepareCurrencyAmounts(listOf(item("a", "10"), item("b", "5", "EUR")), "CAD", FakeExchangeRates(mapOf("EUR" to 1.5)))
        val stuck = result.items.getValue("a")
        assertEquals("10", stuck.amountText)
        assertEquals("USD", stuck.reviewCurrency)
        assertEquals("CAD", stuck.convertedTo)
        assertEquals("7.5", result.items.getValue("b").amountText)
        assertTrue(result.rateFailed)
    }

    @Test
    fun `a decimal comma is read`() = runTest {
        val result = prepareCurrencyAmounts(listOf(item("a", "12,5")), "CAD", FakeExchangeRates(mapOf("USD" to 2.0)))
        assertEquals("25", result.items.getValue("a").amountText)
    }
}
