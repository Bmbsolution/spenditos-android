package com.spenditos.android.data.transactions

import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillMatchingTest {
    private val day = LocalDate.of(2026, 10, 7)

    private fun entry(id: String, amount: Double, date: String, description: String = "", category: String = "cat-bills", template: String? = "tpl-1") =
        Transaction(id = id, userId = "u", categoryId = category, templateId = template, description = description, amount = amount, date = date)

    private fun template(description: String, amount: Double = 50.0, category: String = "cat-bills", type: String = "fixed", active: Boolean = true) =
        RecurringTemplate(
            id = "tpl-$description", userId = "u", categoryId = category, description = description, amount = amount,
            type = type, frequency = "monthly", firstOccurrenceDate = "2026-10-01", isActive = active,
        )

    @Test
    fun `whole words only - rent never matches car rental`() {
        assertTrue(BillMatching.sameMerchant("Rent", "October rent"))
        assertFalse(BillMatching.sameMerchant("Rent", "Car rental"))
        assertTrue(BillMatching.sameMerchant("Disney+", "disney+ yearly"))
    }

    @Test
    fun `a merchant rule links two different names`() {
        val rule = MerchantMapping("m1", "u", "netflix", "Netflix", categoryId = "cat-subs")
        assertTrue(BillMatching.sameMerchant("NETFLIX.COM 123", "netflix", listOf(rule)))
        assertEquals("m1", BillMatching.rule("Paid netflix today", listOf(rule))?.id)
        assertNull(BillMatching.rule("Paid netflix today", listOf(rule.copy(isRegex = true))))
    }

    @Test
    fun `candidates need a template, the date window and the amount tolerance, best first`() {
        val entries = listOf(
            entry("a", 100.0, "2026-10-09", "Internet"), // by name, 2 days off
            entry("b", 118.0, "2026-10-07", "Internet"), // by name, 18% off: still within 20%
            entry("c", 100.0, "2026-10-12", "Internet"), // 5 days: outside the window
            entry("d", 100.0, "2026-10-07", "Internet", template = null), // not generated
            entry("e", 104.0, "2026-10-07", "Gym"), // category only, 4% off: within 5%
            entry("f", 110.0, "2026-10-07", "Gym"), // category only, 10% off: rejected
        )
        val result = BillMatching.candidates(100.0, "internet", "cat-bills", day, entries)
        // b is 15% off but on the day; it outranks a (exact amount, 2 days off).
        assertEquals(listOf("b", "a", "e"), result.map { it.entry.id })
    }

    @Test
    fun `an empty entry description takes the bill name from its template`() {
        val result = BillMatching.candidates(
            50.0, "Phone", "other", day, listOf(entry("a", 50.0, "2026-10-07", template = "tpl-Phone")), listOf(template("Phone")),
        )
        assertEquals("Phone", result.single().billName)
    }

    @Test
    fun `existing bill ignores paused and legacy estimates, and falls back to category within 5 percent`() {
        val templates = listOf(template("Rent", active = false), template("Rent", type = "variable"), template("Water", amount = 40.0))
        assertNull(BillMatching.existingTemplate("Rent", "cat-bills", 1000.0, templates))
        assertEquals("tpl-Water", BillMatching.existingTemplate("", "cat-bills", 41.0, templates)?.id)
        assertNull(BillMatching.existingTemplate("", "cat-bills", 45.0, templates))
    }

    @Test
    fun `presets step one period and clamp month ends`() {
        assertEquals(LocalDate.of(2026, 2, 28), RecurrencePreset.MONTHLY.next(LocalDate.of(2026, 1, 31)))
        assertEquals(LocalDate.of(2026, 10, 21), RecurrencePreset.BI_WEEKLY.next(day))
        assertEquals(28, RecurrencePreset.secondDay(15))
        assertEquals(5, RecurrencePreset.secondDay(20))
    }
}
