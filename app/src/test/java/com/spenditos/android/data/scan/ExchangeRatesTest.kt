package com.spenditos.android.data.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExchangeRatesTest {
    @Test
    fun `the target's rate is read from the feed`() {
        assertEquals(1.37, OkHttpExchangeRates.parseRate("""{"base":"USD","rates":{"CAD":1.37,"EUR":0.92}}""", "CAD"), 0.0)
    }

    @Test
    fun `a missing, zero or unreadable rate is unavailable`() {
        listOf(
            """{"rates":{"EUR":0.9}}""",
            """{"rates":{"CAD":0}}""",
            """{"rates":{"CAD":-2}}""",
            """{"rates":{"CAD":"abc"}}""",
            """{"nothing":1}""",
            """[1,2]""",
        ).forEach { body ->
            assertThrows(body, ExchangeRateUnavailable::class.java) { OkHttpExchangeRates.parseRate(body, "CAD") }
        }
    }

    @Test
    fun `codes that are not three letters never reach the network`() = runTest {
        val rates = OkHttpExchangeRates(OkHttpClient(), Dispatchers.Unconfined)
        listOf("US", "USDX", "../x", "U\$D", "").forEach { bad ->
            assertThrows(bad, ExchangeRateUnavailable::class.java) { kotlinx.coroutines.runBlocking { rates.rate(bad, "CAD") } }
        }
        assertThrows(ExchangeRateUnavailable::class.java) { kotlinx.coroutines.runBlocking { rates.rate("USD", "C D") } }
    }

    @Test
    fun `a rate is fetched for the source currency with no body, query or credentials`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 200, body = """{"rates":{"CAD":1.25}}"""))
            server.start()
            val rates = OkHttpExchangeRates(OkHttpClient(), Dispatchers.Unconfined).apply { baseUrl = server.url("/v4/latest/").toString() }

            assertEquals(1.25, rates.rate("usd", "cad"), 0.0)

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/v4/latest/USD", request.url.encodedPath)
            assertEquals(null, request.headers["Authorization"])
            assertEquals(0L, request.bodySize)
        }
    }

    @Test
    fun `a failing or garbled feed is unavailable rather than a crash`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = 500))
            server.enqueue(MockResponse(code = 200, body = "not json"))
            server.start()
            val rates = OkHttpExchangeRates(OkHttpClient(), Dispatchers.Unconfined).apply { baseUrl = server.url("/v4/latest/").toString() }

            assertThrows(ExchangeRateUnavailable::class.java) { kotlinx.coroutines.runBlocking { rates.rate("USD", "CAD") } }
            assertThrows(ExchangeRateUnavailable::class.java) { kotlinx.coroutines.runBlocking { rates.rate("USD", "CAD") } }
        }
    }
}
