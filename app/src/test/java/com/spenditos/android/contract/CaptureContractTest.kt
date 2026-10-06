package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.CaptureAnalysisStatus
import com.spenditos.android.data.model.CaptureBulkResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Handler-only audio and bulk response shapes, using fake values. */
class CaptureContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/$name.json")).readText()

    @Test
    fun `completed analysis transcript and two items decode`() {
        val envelope = SpenditosJson.decodeFromString(ApiEnvelope.serializer(CaptureAnalysisStatus.serializer()), fixture("capture_analysis_completed"))
        val analysis = envelope.data!!
        assertEquals("success", analysis.status)
        assertEquals("Paid 24 for lunch and received 80 from a side job.", analysis.transcript)
        assertEquals(2, analysis.items.size)
        assertEquals("income", analysis.items[1].transactionType)
    }

    @Test
    fun `bulk response preserves failed indexes and duplicate count`() {
        val envelope = SpenditosJson.decodeFromString(ApiEnvelope.serializer(CaptureBulkResponse.serializer()), fixture("capture_bulk_response"))
        val result = envelope.data!!
        assertEquals(2, result.results.size)
        assertEquals("transaction-fake-lunch", result.results[0].transaction?.id)
        assertEquals(1, result.failed.single().index)
        assertEquals(1, result.duplicates)
        assertTrue(result.failed.single().reason.isNotBlank())
    }
}
