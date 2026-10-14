package com.spenditos.android.feature.scan

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanPossibleDuplicate
import com.spenditos.android.feature.capture.CaptureReviewItem
import com.spenditos.android.feature.capture.reviewFlagLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ScanReviewModelsTest {
    private val categories = listOf(
        Category("cat_dining", "u", "Dining", "#000000", isIncome = false),
        Category("cat_groceries", "u", "Groceries", "#000000", isIncome = false),
        Category("cat_shopping", "u", "Shopping", "#000000", isIncome = false),
        Category("cat_salary", "u", "Salary", "#000000", isIncome = true),
    )

    private fun fixtureItems(): List<ScanItem> {
        val text = requireNotNull(javaClass.classLoader!!.getResource("contract/scanReadyListDetailFixture.json")).readText()
        return SpenditosJson.decodeFromString(ApiEnvelope.serializer(ScanDetail.serializer()), text).data!!.items!!
    }

    // region Default checked

    @Test
    fun `new rows start checked and flagged rows unchecked with their reason`() {
        val rows = fixtureItems().map { it.toReviewItem(categories) }
        val byId = rows.associateBy { it.idempotencyKeyId }

        // Plain, pending and rule-categorized rows are included.
        assertTrue(byId.getValue("scn_0002-0-0").include)
        assertTrue(byId.getValue("scn_0002-0-2").include)
        assertTrue(byId.getValue("scn_0002-1-1").include)
        // A possible duplicate the server skipped, a card payment and a rule skip start unchecked.
        assertFalse(byId.getValue("scn_0002-0-1").include)
        assertFalse(byId.getValue("scn_0002-1-0").include)
        assertFalse(byId.getValue("scn_0002-1-2").include)
        assertEquals(listOf("duplicate", "payment", "rule"), rows.mapNotNull { it.skipReason })
    }

    @Test
    fun `the scan item id is the idempotency key and the row keeps its rule and pending state`() {
        val rows = fixtureItems().map { it.toReviewItem(categories) }
        assertEquals(fixtureItems().map { it.id }, rows.map { it.idempotencyKeyId })
        assertEquals("a3f1c2d4-5b6e-4f70-8a91-0b1c2d3e4f50", rows[1].ruleId)
        assertEquals(true, rows[0].isPending)
        assertEquals("2026-10-08", rows[2].duplicateDate)
        assertEquals(62.18, rows[2].duplicateAmount!!, 0.0)
    }

    @Test
    fun `a possible duplicate without a skip decision stays included but labelled`() {
        val row = ScanItem(
            id = "x", amount = 5.0, date = "2026-10-01",
            possibleDuplicateOf = ScanPossibleDuplicate("txn_1", "2026-10-01", 5.0),
        ).toReviewItem(categories)
        assertTrue(row.include)
        assertNull(row.skipReason)
        assertEquals(listOf("Possible duplicate"), reviewFlagLabels(row, "CAD", Locale.US))
    }

    @Test
    fun `an unknown skip reason does not uncheck a row`() {
        val row = ScanItem(id = "x", amount = 5.0, date = "2026-10-01", skipReason = "from_the_future").toReviewItem(categories)
        assertTrue(row.include)
        assertNull(row.skipReason)
    }

    // endregion

    // region Conversion

    @Test
    fun `amounts date and description convert to the editable row`() {
        val row = ScanItem(id = "a", amount = 62.184, date = "2026-10-08", description = " groceries ", merchantName = "Loblaws", transactionType = "income").toReviewItem(categories)
        assertEquals("62.18", row.amountText)
        assertEquals("groceries", row.description)
        assertEquals("2026-10-08", row.date)
        assertEquals("income", row.transactionType)
        assertEquals("4.5", ScanItem(id = "b", amount = 4.5).toReviewItem(categories).amountText)
        assertEquals("250", ScanItem(id = "c", amount = 250.0).toReviewItem(categories).amountText)
    }

    @Test
    fun `a row without a usable amount or date keeps those blank and a merchant stands in for the description`() {
        val row = ScanItem(id = "a", amount = null, date = "not-a-date", description = null, merchantName = "Amazon").toReviewItem(categories)
        assertEquals("", row.amountText)
        assertEquals("", row.date)
        assertEquals("Amazon", row.description)
        assertEquals("", ScanItem(id = "b", amount = -3.0).toReviewItem(categories).amountText)
        assertEquals("", ScanItem(id = "c").displayDescription())
        // Incomplete rows start unchecked so they never block saving the rest.
        assertFalse(row.include)
    }

    @Test
    fun `the server category is kept when the user has it with the right kind`() {
        assertEquals("cat_dining", ScanItem(id = "a", categoryId = "cat_dining").toReviewItem(categories).categoryId)
        // Unknown id, or an expense category on an income row: no category, the hint decides.
        assertNull(ScanItem(id = "b", categoryId = "cat_gone").toReviewItem(categories).categoryId)
        assertNull(ScanItem(id = "c", categoryId = "cat_dining", transactionType = "income").toReviewItem(categories).categoryId)
    }

    @Test
    fun `a category hint resolves by name then by containment within the same kind`() {
        assertEquals("cat_shopping", ScanItem(id = "a", categoryHint = "shopping").toReviewItem(categories).categoryId)
        assertEquals("cat_groceries", ScanItem(id = "b", categoryHint = "Groceries & Food").toReviewItem(categories).categoryId)
        assertEquals("cat_salary", ScanItem(id = "c", categoryHint = "salary", transactionType = "income").toReviewItem(categories).categoryId)
        assertNull(ScanItem(id = "d", categoryHint = "salary").toReviewItem(categories).categoryId)
        assertNull(ScanItem(id = "e", categoryHint = "  ").toReviewItem(categories).categoryId)
    }

    @Test
    fun `categories arriving later fill untouched rows only`() {
        val rows = listOf(
            ScanItem(id = "a", categoryHint = "dining").toReviewItem(emptyList()),
            ScanItem(id = "b", categoryHint = "dining").toReviewItem(emptyList()).copy(categoryEdited = true),
        )
        val resolved = resolveScanCategories(rows, categories)
        assertEquals("cat_dining", resolved[0].categoryId)
        assertNull(resolved[1].categoryId)
    }

    // endregion

    // region Flag labels

    private fun row(vararg pairs: Pair<String, Any?>): CaptureReviewItem {
        var item = CaptureReviewItem("k", "1", "d", "2026-10-01")
        pairs.forEach { (name, value) ->
            item = when (name) {
                "skip" -> item.copy(skipReason = value as String?)
                "rule" -> item.copy(ruleId = value as String?)
                "pending" -> item.copy(isPending = value as Boolean?)
                "dupDate" -> item.copy(duplicateDate = value as String?)
                "dupAmount" -> item.copy(duplicateAmount = value as Double?)
                else -> item
            }
        }
        return item
    }

    @Test
    fun `flag labels explain why a row is unchecked`() {
        assertEquals(listOf("Card payment"), reviewFlagLabels(row("skip" to "payment"), "CAD", Locale.US))
        assertEquals(listOf("Transfer"), reviewFlagLabels(row("skip" to "transfer"), "CAD", Locale.US))
        assertEquals(listOf("Skipped by your rule"), reviewFlagLabels(row("skip" to "rule", "rule" to "r1"), "CAD", Locale.US))
        assertEquals(listOf("Already added"), reviewFlagLabels(row("skip" to "duplicate"), "CAD", Locale.US))
        assertEquals(
            listOf("Already added (Oct 8 · CA$62.18)"),
            reviewFlagLabels(row("skip" to "duplicate", "dupDate" to "2026-10-08", "dupAmount" to 62.18), "CAD", Locale.US),
        )
    }

    @Test
    fun `a rule filed row and a pending row are labelled and plain rows are not`() {
        assertEquals(listOf("✓ Your rule"), reviewFlagLabels(row("rule" to "r1"), "CAD", Locale.US))
        assertEquals(listOf("✓ Your rule", "Pending"), reviewFlagLabels(row("rule" to "r1", "pending" to true), "CAD", Locale.US))
        assertEquals(emptyList<String>(), reviewFlagLabels(row("pending" to false), "CAD", Locale.US))
        assertEquals(emptyList<String>(), reviewFlagLabels(row(), "CAD", Locale.US))
    }

    // endregion

    // region Months

    @Test
    fun `month keys accept only real months`() {
        assertEquals("2026-09", ScanReviewGrouping.monthKey("2026-09-14"))
        assertEquals("2026-12", ScanReviewGrouping.monthKey("2026-12"))
        assertEquals("", ScanReviewGrouping.monthKey("2026-13-01"))
        assertEquals("", ScanReviewGrouping.monthKey("2026-00-01"))
        assertEquals("", ScanReviewGrouping.monthKey("26-09-14"))
        assertEquals("", ScanReviewGrouping.monthKey(""))
        assertEquals("", ScanReviewGrouping.monthKey(null))
        assertEquals("", ScanReviewGrouping.monthKey("abcd-ef-gh"))
    }

    @Test
    fun `month titles omit the current year and undated rows say so`() {
        val now = LocalDate.of(2026, 10, 14)
        assertEquals("September", ScanReviewGrouping.monthTitle("2026-09", now, Locale.US))
        assertEquals("December 2025", ScanReviewGrouping.monthTitle("2025-12", now, Locale.US))
        assertEquals("No date", ScanReviewGrouping.monthTitle("", now, Locale.US))
    }

    @Test
    fun `rows group by month newest first with undated rows last and review order kept`() {
        fun r(id: String, date: String) = CaptureReviewItem(id, "1", id, date)
        val sections = ScanReviewGrouping.monthSections(
            listOf(r("a", "2026-09-28"), r("b", "2026-10-09"), r("c", ""), r("d", "2026-10-07"), r("e", "2025-12-31"), r("f", "2026-09-02")),
            now = LocalDate.of(2026, 10, 14),
            locale = Locale.US,
        )
        assertEquals(listOf("2026-10", "2026-09", "2025-12", ""), sections.map { it.key })
        assertEquals(listOf("October", "September", "December 2025", "No date"), sections.map { it.title })
        assertEquals(listOf("b", "d"), sections[0].items.map { it.idempotencyKeyId })
        assertEquals(listOf("a", "f"), sections[1].items.map { it.idempotencyKeyId })
        assertEquals(listOf("c"), sections[3].items.map { it.idempotencyKeyId })
    }

    @Test
    fun `the golden list groups into october and september`() {
        val rows = fixtureItems().map { it.toReviewItem(categories) }
        val sections = ScanReviewGrouping.monthSections(rows, now = LocalDate.of(2026, 10, 14), locale = Locale.US)
        assertEquals(listOf("October", "September"), sections.map { it.title })
        assertEquals(5, sections[0].items.size)
        assertEquals(1, sections[1].items.size)
    }

    // endregion
}
