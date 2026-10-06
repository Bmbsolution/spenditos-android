package com.spenditos.android.data.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.spenditos.android.testing.OfflineInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
class UserSettingsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: UserSettingsRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(UserApi::class.java)
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("access", "id") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        repository = UserSettingsRepository(ApplicationProvider.getApplicationContext<Context>(), api, session)
    }

    @After
    fun tearDown() = server.close()

    private fun settings(name: String? = "Fake User", currency: String = "USD") =
        """{"data":{"userId":"user-1","displayName":${name?.let { "\"$it\"" } ?: "null"},"currency":"$currency","timezone":"UTC","setupCompleted":true}}"""

    private suspend fun failure(block: suspend () -> Any?): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "expected failure" }

    private val offline = OfflineInterceptor()

    @Test
    fun `handwritten user request fixture decodes nullable display name and password body`() {
        val raw = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/userSettingsUpdate.json")).readText()
        val root = SpenditosJson.parseToJsonElement(raw).jsonObject
        val update = SpenditosJson.decodeFromString(UserSettingsUpdate.serializer(), root["settings"].toString())
        val password = SpenditosJson.decodeFromString(ChangePasswordRequest.serializer(), root["changePassword"].toString())
        assertEquals("CAD", update.currency)
        assertEquals(JsonNull, update.displayName)
        assertEquals("Fake-Current-123!", password.currentPassword)
        assertEquals("Fake-New-456!", password.newPassword)
    }

    @Test
    fun `refresh returns settings and handles 4xx and network failures`() = runTest {
        server.enqueue(MockResponse(body = settings(currency = "CAD")))
        assertEquals("CAD", repository.refresh().currency)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals("CAD", repository.currency().first())

        server.enqueue(MockResponse(code = 404, body = """{"error":"User settings not found"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)

        offline.failNext = 1
        assertTrue(failure { repository.refresh() } is ApiError.Network)
    }

    @Test
    fun `update sends explicit null to clear display name and refreshes currency`() = runTest {
        server.enqueue(MockResponse(body = settings(name = null, currency = "CAD")))
        server.enqueue(MockResponse(body = settings(name = null, currency = "CAD")))
        val updated = repository.updateSettings(UserSettingsUpdate("CAD", JsonNull))
        assertEquals(null, updated.displayName)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/user/settings", put.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(put.body!!.utf8()).jsonObject
        assertEquals(JsonNull, body["displayName"])
        assertEquals("CAD", body["currency"]!!.jsonPrimitive.content)
        assertEquals("/user/settings", server.takeRequest().url.encodedPath)
        assertEquals("CAD", repository.currency().first())
    }

    @Test
    fun `update settings surfaces 4xx and network failures`() = runTest {
        val update = UserSettingsUpdate("CAD", JsonNull)
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid input"}"""))
        assertTrue(failure { repository.updateSettings(update) } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.updateSettings(update) } is ApiError.Server)

        offline.failNext = 1
        assertTrue(failure { repository.updateSettings(update) } is ApiError.Network)
    }

    @Test
    fun `change password posts only the two request fields and handles failures`() = runTest {
        server.enqueue(MockResponse(body = """{"data":{"message":"Password changed successfully"}}"""))
        repository.changePassword("Fake-Current-123!", "Fake-New-456!")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/user/change-password", request.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(setOf("currentPassword", "newPassword"), body.keys)
        assertEquals("Fake-Current-123!", body["currentPassword"]!!.jsonPrimitive.content)

        server.enqueue(MockResponse(code = 400, body = """{"error":"Current password is incorrect"}"""))
        assertTrue(failure { repository.changePassword("Fake-Current-123!", "Fake-New-456!") } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.changePassword("Fake-Current-123!", "Fake-New-456!") } is ApiError.Server)

        offline.failNext = 1
        assertTrue(failure { repository.changePassword("Fake-Current-123!", "Fake-New-456!") } is ApiError.Network)
    }
}
