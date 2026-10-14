package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.CreateScanRequest
import com.spenditos.android.data.model.CreateScanResponse
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanKind
import com.spenditos.android.data.model.ScanListData
import com.spenditos.android.data.model.ScanSkipReason
import com.spenditos.android.data.model.ScanStatus
import com.spenditos.android.data.model.ScanSummary
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes the backend's golden scan bodies (scn_0001..3, api-fixtures.ts) with the production Json. */
class ScanContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/$name.json")) { "missing fixture $name" }.readText()

    private fun <T> envelope(name: String, serializer: KSerializer<T>): T =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(serializer), fixture(name)).data!!

    @Test
    fun `create scan response decodes`() {
        val created = envelope("createScanResponseFixture", CreateScanResponse.serializer())
        assertEquals("scn_0001", created.scanId)
        assertEquals("processing", created.status)
        assertEquals(2, created.total)
    }

    @Test
    fun `processing summary has progress and no kind`() {
        val summary = envelope("scanProcessingSummaryFixture", ScanSummary.serializer())
        assertEquals(ScanStatus.PROCESSING, summary.state)
        assertEquals(1, summary.progress.done)
        assertEquals(2, summary.progress.total)
        assertNull(summary.scanKind)
        assertEquals("2 files", summary.sourceLabel)
        assertEquals("2026-10-11T10:00:20.000Z", summary.updatedAt)
    }

    @Test
    fun `ready list detail decodes every optional row state`() {
        val detail = envelope("scanReadyListDetailFixture", ScanDetail.serializer())
        assertEquals(ScanStatus.READY, detail.state)
        assertEquals(ScanKind.LIST, detail.kind)
        assertEquals(6, detail.summary.itemCount)
        assertEquals(3, detail.summary.flaggedCount)
        assertEquals(2, detail.summary.ruleCount)
        assertEquals(1, detail.summary.failedDocuments)
        val items = detail.items!!
        assertEquals(6, items.size)

        val pending = items[0]
        assertEquals("scn_0002-0-0", pending.id)
        assertEquals(true, pending.isPending)
        assertNull(pending.skip)
        assertNull(pending.ruleId)

        val ruleFiled = items[1]
        assertEquals("a3f1c2d4-5b6e-4f70-8a91-0b1c2d3e4f50", ruleFiled.ruleId)
        assertNull(ruleFiled.skip)

        val duplicate = items[2]
        assertEquals(ScanSkipReason.DUPLICATE, duplicate.skip)
        assertEquals("txn_existing-001", duplicate.possibleDuplicateOf!!.transactionId)
        assertEquals(62.18, duplicate.possibleDuplicateOf!!.amount, 0.0)
        assertEquals("Loblaws groceries", duplicate.possibleDuplicateOf!!.description)

        val payment = items[3]
        assertEquals(ScanSkipReason.PAYMENT, payment.skip)
        assertTrue(payment.isIncome)

        val skippedByRule = items[4]
        assertEquals(ScanSkipReason.RULE, skippedByRule.skip)
        assertEquals("b4a2d3e5-6c7f-4081-9ba2-1c2d3e4f5061", skippedByRule.ruleId)

        val hinted = items[5]
        assertNull(hinted.categoryId)
        assertEquals("shopping", hinted.categoryHint)
    }

    @Test
    fun `receipt detail decodes as a single receipt item`() {
        val detail = envelope("scanReceiptDetailFixture", ScanDetail.serializer())
        assertEquals(ScanKind.RECEIPT, detail.kind)
        val only = detail.items!!.single()
        assertEquals(38.74, only.amount!!, 0.0)
        assertEquals("Metro", only.merchantName)
        assertNull(only.isPending)
        assertFalse(only.isIncome)
    }

    @Test
    fun `scan list decodes and keeps the newest first order`() {
        val list = envelope("scanListFixture", ScanListData.serializer())
        assertEquals(listOf("scn_0001", "scn_0002"), list.scans.map { it.scanId }.take(2))
        assertEquals(ScanStatus.PROCESSING, list.scans[0].state)
        assertEquals(ScanStatus.READY, list.scans[1].state)
    }

    @Test
    fun `create request fixtures round trip with the pdf group id only on image pages`() {
        val plain = SpenditosJson.decodeFromString(CreateScanRequest.serializer(), fixture("createScanRequestFixture"))
        assertEquals(listOf("image", "spreadsheet"), plain.documents.map { it.kind })
        assertTrue(plain.documents.all { it.pdfGroupId == null })
        assertEquals("CAD", plain.userCurrency)

        val pdf = SpenditosJson.decodeFromString(CreateScanRequest.serializer(), fixture("createScanPdfRequestFixture"))
        assertEquals(setOf("pdf_7f3a9c1e"), pdf.documents.map { it.pdfGroupId }.toSet())
        // Encoding omits nulls: no pdfGroupId key on ungrouped documents.
        assertFalse(SpenditosJson.encodeToString(CreateScanRequest.serializer(), plain).contains("pdfGroupId"))
        assertEquals(
            SpenditosJson.parseToJsonElement(fixture("createScanRequestFixture")),
            SpenditosJson.parseToJsonElement(SpenditosJson.encodeToString(CreateScanRequest.serializer(), plain)),
        )
    }

    @Test
    fun `unknown enum values and malformed rows never break a scan`() {
        val body = """
            {"data":{"scanId":"s1","status":"queued-for-moon","kind":"mystery","progress":{"done":1,"total":2},
             "itemCount":3,"items":[
               {"id":"a","amount":4.5,"date":"2026-10-09","skipReason":"future_reason","transactionType":"expense"},
               {"amount":9},
               {"id":"b","amount":"oops-not-a-number"},
               {"id":"c","amount":1.25,"transactionType":"income","extraField":true}
             ]}}
        """.trimIndent()
        val detail = SpenditosJson.decodeFromString(ApiEnvelope.serializer(ScanDetail.serializer()), body).data!!
        assertEquals(ScanStatus.UNKNOWN, detail.state)
        assertNull(detail.kind)
        // The row without an id and the row with a bad amount are dropped; the unknown skipReason is just null.
        assertEquals(listOf("a", "c"), detail.items!!.map { it.id })
        assertNull(detail.items!![0].skip)
        assertTrue(detail.items!![1].isIncome)
    }

    @Test
    fun `scan list drops a summary without an id`() {
        val body = """{"data":{"scans":[{"status":"ready"},{"scanId":"ok","status":"ready"}]}}"""
        val list = SpenditosJson.decodeFromString(ApiEnvelope.serializer(ScanListData.serializer()), body).data!!
        assertEquals(listOf("ok"), list.scans.map { it.scanId })
    }
}
