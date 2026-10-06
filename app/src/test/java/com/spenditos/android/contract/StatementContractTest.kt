package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementAnalyzeRequest
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.data.model.StatementImportRequest
import com.spenditos.android.data.model.StatementImportResult
import com.spenditos.android.data.model.StatementImportTransaction
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.StatementPreviewRequest
import com.spenditos.android.data.model.StatementUploadUrlResponse
import com.spenditos.android.data.model.StatementUploadUrlRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Handwritten response fixtures copied from the statement/document handlers with fake values. */
class StatementContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/$name.json")) { "missing $name fixture" }.readText()

    private fun <T> decode(name: String, serializer: kotlinx.serialization.KSerializer<T>): T =
        SpenditosJson.decodeFromString(serializer, fixture(name))

    private fun <T> envelope(name: String, serializer: kotlinx.serialization.KSerializer<T>): ApiEnvelope<T> =
        decode(name, ApiEnvelope.serializer(serializer))

    @Test
    fun `upload url response matches document analysis handler`() {
        val response = envelope("statement-upload-url", StatementUploadUrlResponse.serializer()).data!!
        assertEquals("https://uploads.example.test/fake-policy", response.uploadUrl)
        assertEquals("users/fake-user/statements/fake-file.csv", response.documentKey)
        assertEquals("fake-policy-value", response.fields["policy"])
        assertEquals("bank_statement", SpenditosJson.decodeFromString(
            StatementUploadUrlRequest.serializer(),
            """{"mimeType":"text/csv","documentType":"bank_statement"}""",
        ).documentType)
        val analyzeRequest = StatementAnalyzeRequest("fake.csv", response.documentKey)
        val analyzeJson = SpenditosJson.encodeToString(StatementAnalyzeRequest.serializer(), analyzeRequest)
        assertEquals(analyzeRequest, SpenditosJson.decodeFromString(StatementAnalyzeRequest.serializer(), analyzeJson))
    }

    @Test
    fun `analysis response decodes tolerant cell scalars`() {
        val result = envelope("statement-analysis", StatementAnalysisResult.serializer()).data!!
        assertEquals("Transaction Date", result.mapping.dateColumn)
        assertEquals("MM/DD/YYYY", result.mapping.dateFormat)
        assertEquals(0.87, result.mapping.confidence, 0.0001)
        assertEquals(listOf("09/01/2026", "Fake Grocery", "-64.2"), result.preview.first().map { it.text })
        assertEquals(listOf("", "true", "", "2500"), result.preview.last().map { it.text })
    }

    @Test
    fun `debit and credit analysis mapping decodes`() {
        val raw = """{"mapping":{"dateColumn":"Date","descriptionColumn":"Details","debitColumn":"Out","creditColumn":"In","amountIsNegativeForDebit":false,"confidence":0.5},"headers":["Date","Details","Out","In"],"preview":[]}"""
        val result = SpenditosJson.decodeFromString(StatementAnalysisResult.serializer(), raw)
        assertNull(result.mapping.amountColumn)
        assertEquals("Out", result.mapping.debitColumn)
        assertEquals("In", result.mapping.creditColumn)
        assertEquals(false, result.mapping.amountIsNegativeForDebit)
    }

    @Test
    fun `preview response keeps signed amounts and category-name suggestion`() {
        val result = envelope("statement-preview", StatementPreviewResult.serializer()).data!!
        assertEquals("groceries", result.transactions.first().categoryId)
        assertEquals(-64.2, result.transactions.first().amount, 0.0001)
        assertEquals("Groceries", result.categories.first().name)
    }

    @Test
    fun `import response decodes created updated and skipped counts`() {
        val result = envelope("statement-import", StatementImportResult.serializer()).data!!
        assertEquals(2, result.count)
        assertEquals(2, result.stats!!.created)
        assertEquals(0, result.stats.updated)
        assertEquals(1, result.stats.skipped)
    }

    @Test
    fun `import response without stats remains readable`() {
        val result = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(StatementImportResult.serializer()),
            """{"data":{"message":"Imported fake rows.","count":2}}""",
        ).data!!
        assertEquals(2, result.count)
        assertNull(result.stats)
    }

    @Test
    fun `optional request fields are omitted from zod payloads`() {
        val preview = StatementPreviewRequest("fake.csv", "fake-document-key", StatementColumnMapping(
            dateColumn = "Date", descriptionColumn = "Details", amountColumn = "Value", confidence = 0.6,
        ))
        val previewJson = SpenditosJson.parseToJsonElement(SpenditosJson.encodeToString(StatementPreviewRequest.serializer(), preview)).jsonObject
        assertEquals(preview, SpenditosJson.decodeFromString(StatementPreviewRequest.serializer(), previewJson.toString()))
        val mapping = previewJson["mapping"]!!.jsonObject
        assertTrue(mapping.containsKey("confidence"))
        assertNull(mapping["debitColumn"])

        val sparseMapping = SpenditosJson.parseToJsonElement(
            SpenditosJson.encodeToString(StatementColumnMapping.serializer(), StatementColumnMapping(confidence = 0.0)),
        ).jsonObject
        assertEquals(setOf("confidence"), sparseMapping.keys)

        val request = StatementImportRequest(listOf(StatementImportTransaction(LocalDate.parse("2026-09-01"), "Fake Shop", -5.0)))
        val encodedRequest = SpenditosJson.encodeToString(StatementImportRequest.serializer(), request)
        assertEquals(request, SpenditosJson.decodeFromString(StatementImportRequest.serializer(), encodedRequest))
        val requestJson = SpenditosJson.parseToJsonElement(encodedRequest).jsonObject
        val row = requestJson["transactions"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("date", "description", "amount"), row.keys)
        assertNull(row["categoryId"])
        assertNull(row["newCategoryName"])
    }
}
