package com.spenditos.android.feature.scan

import com.spenditos.android.data.merchants.normalizeMerchantKey
import com.spenditos.android.feature.capture.CaptureReviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** Ports of the grouping tests of iOS `RulesTests` (Rules "C"). */
class ReviewGroupingTest {
    private val now = LocalDate.of(2026, 10, 14)

    private fun row(
        id: String,
        merchant: String?,
        amount: String = "10",
        date: String = "2026-10-09",
        type: String = "expense",
        categoryId: String? = null,
    ) = CaptureReviewItem(id, amount, merchant.orEmpty(), date, categoryId = categoryId, transactionType = type, merchantName = merchant)

    private fun sections(items: List<CaptureReviewItem>, grouping: Boolean = true) =
        ScanReviewGrouping.monthSections(items, now = now, locale = Locale.US, grouping = grouping)

    private fun group(items: List<CaptureReviewItem>) = (sections(items)[0].entries[0] as ReviewEntry.Group).group

    @Test
    fun `rows of one merchant group and singles stay`() {
        val items = listOf(
            row("1", "Tim Hortons", "5.95", "2026-10-09"),
            row("2", "Uber", "14.75", "2026-10-08"),
            row("3", "TIM HORTONS", "3.20", "2026-10-07"),
            row("4", "tim hortons ", "6.45", "2026-10-06"),
            row("5", null, "9", "2026-10-05"),
            row("6", "  ", "9", "2026-10-04"),
        )
        val sections = sections(items)

        assertEquals(1, sections.size)
        val entries = sections[0].entries
        // The group sits where its first row was; Uber and the nameless rows stay single.
        assertEquals(4, entries.size)
        val group = (entries[0] as ReviewEntry.Group).group
        assertEquals("timhortons", group.merchantKey)
        assertEquals("Tim Hortons", group.merchant)
        assertEquals(3, group.count)
        assertEquals(listOf("1", "3", "4"), group.itemKeys)
        assertEquals(BigDecimal("-15.60"), group.signedTotal)
        assertEquals("Uber", (entries[1] as ReviewEntry.Single).item.merchantName)
        assertEquals(items.size, sections[0].items.size)
    }

    @Test
    fun `a merchant needs at least two rows to group`() {
        val entries = sections(listOf(row("1", "Shell"), row("2", "Metro")))[0].entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is ReviewEntry.Single })
    }

    @Test
    fun `groups split by month`() {
        val items = listOf(
            row("1", "Tim Hortons", date = "2026-10-09"),
            row("2", "Tim Hortons", date = "2026-10-02"),
            row("3", "Tim Hortons", date = "2026-09-28"),
            row("4", "Tim Hortons", date = "2026-09-12"),
            row("5", "Tim Hortons", date = "2026-08-30"),
        )
        val sections = sections(items)

        assertEquals(listOf("2026-10", "2026-09", "2026-08"), sections.map { it.key })
        val october = (sections[0].entries.single() as ReviewEntry.Group).group
        val september = (sections[1].entries.single() as ReviewEntry.Group).group
        assertEquals(2, october.count)
        assertEquals(2, september.count)
        assertNotEquals(october.id, september.id)
        assertEquals("2026-10|timhortons", october.id)
        // August has only one Tim Hortons: a plain row.
        assertTrue(sections[2].entries.single() is ReviewEntry.Single)
    }

    @Test
    fun `group totals mix income and expense`() {
        val group = group(listOf(row("1", "Amazon", "40"), row("2", "Amazon", "15", type = "income")))
        assertEquals(BigDecimal("-25"), group.signedTotal)
        assertFalse(group.isIncome)
        assertTrue(group(listOf(row("1", "Pay", "40", type = "income"), row("2", "Pay", "15", type = "income"))).isIncome)
    }

    @Test
    fun `rows with a missing amount count as zero and are reported`() {
        val group = group(listOf(row("1", "Shell", "12.50"), row("2", "Shell", "")))
        assertEquals(BigDecimal("-12.50"), group.signedTotal)
        assertTrue(group.hasMissingAmount)
        assertFalse(group.allAmountsMissing)
        assertTrue(group(listOf(row("1", "Shell", ""), row("2", "Shell", ""))).allAmountsMissing)
    }

    @Test
    fun `grouping off keeps every row single`() {
        val entries = sections(listOf(row("1", "Shell"), row("2", "Shell")), grouping = false)[0].entries
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is ReviewEntry.Single })
    }

    @Test
    fun `group states`() {
        var items = listOf(row("1", "Shell", categoryId = "gas"), row("2", "Shell", categoryId = "gas"))
        assertEquals(ReviewGroup.IncludeState.ALL, group(items).includeState)
        assertEquals("gas", group(items).sharedCategoryId)

        items = listOf(items[0], items[1].copy(include = false))
        assertEquals(ReviewGroup.IncludeState.MIXED, group(items).includeState)
        items = listOf(items[0].copy(include = false), items[1])
        assertEquals(ReviewGroup.IncludeState.NONE, group(items).includeState)

        items = listOf(items[0], items[1].copy(categoryId = "transport"))
        assertNull(group(items).sharedCategoryId)
        assertTrue(group(items).hasMixedCategories)

        items = items.map { it.copy(ruleId = "r") }
        assertTrue(group(items).allRuleFiled)
        items = items.map { it.copy(skipReason = "rule") }
        assertTrue(group(items).allSkippedByRule)
        assertFalse(group(items).allRuleFiled)
        assertEquals(0, group(items).flaggedCount)
        assertEquals(2, group(items.map { it.copy(skipReason = "duplicate") }).flaggedCount)
    }

    @Test
    fun `a row with no category makes the group's shared category null without being mixed`() {
        val group = group(listOf(row("1", "Shell"), row("2", "Shell")))
        assertNull(group.sharedCategoryId)
        assertFalse(group.hasMixedCategories)
    }

    @Test
    fun `two thousand rows group within their months`() {
        val shops = listOf("Metro", "Tim Hortons", "Shell", "Netflix", "Amazon", "Uber", "LCBO", "Starbucks")
        val items = (0 until 2000).map { index ->
            row("scn_big-$index", shops[index % shops.size], (index + 1).toString(), "2026-%02d-%02d".format((index % 3) + 7, (index % 28) + 1))
        }
        val keys = items.associate { it.idempotencyKeyId to normalizeMerchantKey(it.merchantName) }

        val sections = ScanReviewGrouping.monthSections(items, now = now, locale = Locale.US, grouping = true) { keys[it.idempotencyKeyId].orEmpty() }

        assertEquals(listOf("2026-09", "2026-08", "2026-07"), sections.map { it.key })
        assertEquals(2000, sections.sumOf { it.items.size })
        assertTrue(sections.all { it.entries.size <= shops.size })
    }

    @Test
    fun `group and line text follow the iOS formats`() {
        val expense = group(listOf(row("1", "Tim Hortons", "5.95"), row("2", "Tim Hortons", "3.20")))
        assertEquals("−$9.15", groupTotalText(expense, "USD", Locale.US))
        val mixed = group(listOf(row("1", "Amazon", "10"), row("2", "Amazon", "40", type = "income")))
        assertEquals("+$30.00", groupTotalText(mixed, "USD", Locale.US))
        // A total of nothing is not negative (iOS `signedTotal < 0`).
        assertEquals("+—", groupTotalText(group(listOf(row("1", "Shell", ""), row("2", "Shell", ""))), "USD", Locale.US))

        assertEquals("−$5.95", lineAmountText(row("1", "Tim", "5.95"), "USD", Locale.US))
        assertEquals("+$5.00", lineAmountText(row("1", "Tim", "5", type = "income"), "USD", Locale.US))
        assertEquals("−—", lineAmountText(row("1", "Tim", ""), "USD", Locale.US))

        assertEquals("Oct 8", groupDateText("2026-10-08", Locale.US))
        assertEquals("No date", groupDateText("", Locale.US))
        assertEquals("No date", groupDateText(null, Locale.US))
        assertEquals("not-a-date", groupDateText("not-a-date", Locale.US))
    }
}
