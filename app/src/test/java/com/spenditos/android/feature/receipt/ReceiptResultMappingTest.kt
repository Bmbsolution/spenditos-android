package com.spenditos.android.feature.receipt

import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.model.ReceiptUploadData
import com.spenditos.android.data.model.ReceiptUploadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptResultMappingTest {
    private fun fixture(name: String) = requireNotNull(
        javaClass.classLoader!!.getResource("contract/handwritten/$name.json"),
    ).readText()

    private fun receipt(): ReceiptResult = SpenditosJson.decodeFromString(ReceiptResult.serializer(), fixture("receipt_result"))

    @Test
    fun `receipt upload wire models decode handwritten handler fixtures`() {
        val upload = SpenditosJson.decodeFromString(ReceiptUploadData.serializer(), fixture("receipt_upload_data"))
        val uploadRequest = SpenditosJson.decodeFromString(ReceiptUploadRequest.serializer(), fixture("receipt_upload_request"))

        assertEquals("users/fake-user/receipt-fake.jpg", upload.documentKey)
        assertEquals("image/jpeg", uploadRequest.mimeType)
        assertEquals("receipt", uploadRequest.documentType)
    }

    @Test
    fun `receipt result maps to add form with merchant preferred`() {
        val prefill = receipt().toPrefill()

        assertEquals(12.5, prefill.amount!!, 0.0)
        assertEquals("Fake Market", prefill.description)
        assertEquals("2026-09-15", prefill.date)
        assertEquals("category-fake-food", prefill.categoryId)
        assertEquals("Food", prefill.categoryHint)
    }

    @Test
    fun `description is used when merchant is absent and optional values stay absent`() {
        val prefill = ReceiptResult(description = "Cafe").toPrefill()

        assertEquals("Cafe", prefill.description)
        assertNull(prefill.amount)
        assertNull(prefill.date)
        assertNull(prefill.categoryId)
        assertNull(prefill.categoryHint)
    }
}
