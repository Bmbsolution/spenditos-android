package com.spenditos.android.data.merchants

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.CreateMerchantMappingRequest
import com.spenditos.android.data.model.MerchantMapping
import com.spenditos.android.data.model.UpdateMerchantMappingRequest
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MerchantMappingRepositoryTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var repository: MerchantMappingRepository

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType())).build()
            .create(MerchantMappingApi::class.java)
        repository = MerchantMappingRepository(api)
    }

    @After fun tearDown() { server.close() }

    private fun fixture(): String = requireNotNull(javaClass.classLoader).getResource("contract/handwritten/merchant-mappings.json")!!.readText()

    @Test
    fun `list decodes the server shape and sorts by clean name`() = runTest {
        server.enqueue(MockResponse.Builder().body(fixture()).build())
        val rows = repository.list()
        assertEquals(listOf("Amazon", "Coffee"), rows.map { it.cleanMerchantName })
        val coffee = rows.first { it.cleanMerchantName == "Coffee" }
        assertEquals("SQ *COFFEE", coffee.rawPattern)
        assertEquals("cat-fake", coffee.categoryId)
        assertTrue(rows.first { it.cleanMerchantName == "Amazon" }.isRegex)
        assertEquals("/settings/merchant-mappings", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `create and update send the server field names`() = runTest {
        val one = SpenditosJson.encodeToString(
            ApiEnvelope.serializer(MerchantMapping.serializer()),
            ApiEnvelope(data = MerchantMapping("id", "u", "RAW", "Clean")),
        )
        server.enqueue(MockResponse.Builder().code(201).body(one).build())
        repository.create(CreateMerchantMappingRequest("RAW", "Clean", null, false))
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        val body = create.body!!.utf8()
        assertTrue(body.contains("\"rawPattern\":\"RAW\"") && body.contains("\"cleanMerchantName\":\"Clean\""))
        assertFalse("userId and source are set by the server", body.contains("userId") || body.contains("source"))

        server.enqueue(MockResponse.Builder().body(one).build())
        repository.update("id", UpdateMerchantMappingRequest("RAW2", "Clean2", "cat", true))
        val update = server.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/settings/merchant-mappings/id", update.url.encodedPath)
        assertTrue(update.body!!.utf8().contains("\"isRegex\":true"))
    }

    @Test
    fun `delete accepts a 204`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())
        repository.delete("id")
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `validation 4xx and 5xx and network failures are typed`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"rawPattern is required"}""").build())
        try { repository.create(CreateMerchantMappingRequest("", "x")); fail() } catch (e: ApiError.Server) {
            assertEquals(400, e.status); assertEquals("rawPattern is required", e.serverMessage)
        }
        server.enqueue(MockResponse.Builder().code(500).body("""{"error":"Internal server error"}""").build())
        try { repository.list(); fail() } catch (e: ApiError.Server) { assertEquals(500, e.status) }
        offline.failNext = 1
        try { repository.list(); fail() } catch (e: ApiError.Network) { /* expected */ }
    }
}
