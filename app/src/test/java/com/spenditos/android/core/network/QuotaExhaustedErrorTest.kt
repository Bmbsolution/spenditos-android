package com.spenditos.android.core.network

import com.spenditos.android.data.quota.QuotaApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The flat 403 `QuotaExhausted` body (no envelope) keeps its camelCase `quotaType` on [ApiError.Server]. */
class QuotaExhaustedErrorTest {
    private val server = MockWebServer()
    private lateinit var api: QuotaApi

    @Before
    fun setUp() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(QuotaApi::class.java)
    }

    @After
    fun tearDown() = server.close()

    private suspend fun failure(): ApiError.Server =
        runCatching { apiCall { api.quota() } }.exceptionOrNull() as ApiError.Server

    @Test
    fun `quota 403 carries the quota type`() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"error":"QuotaExhausted","quotaType":"sentenceEntries","limit":30,"used":30,"remaining":0,"resetDate":"2026-11-01"}"""))
        val error = failure()
        assertEquals(403, error.status)
        assertEquals("QuotaExhausted", error.serverMessage)
        assertEquals("sentenceEntries", error.quotaType)
        assertTrue(error.isQuotaExhausted)
    }

    @Test
    fun `other errors have no quota type`() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"message":"Access denied to this document"}"""))
        val denied = failure()
        assertNull(denied.quotaType)
        assertFalse(denied.isQuotaExhausted)

        server.enqueue(MockResponse(code = 500, body = "not json"))
        val broken = failure()
        assertNull(broken.quotaType)
        assertFalse(broken.isQuotaExhausted)
    }

    @Test
    fun `a 429 is not a quota error`() {
        assertFalse(ApiError.Server(429, "QuotaExhausted").isQuotaExhausted)
    }
}
