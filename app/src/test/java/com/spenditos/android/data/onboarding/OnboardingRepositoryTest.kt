package com.spenditos.android.data.onboarding

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CountryDetectionResponse
import com.spenditos.android.data.model.OnboardingCategoryRequest
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.feature.onboarding.OnboardingData
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(RobolectricTestRunner::class)
class OnboardingRepositoryTest {
    private val server = MockWebServer()
    private lateinit var database: SpenditosDatabase
    private lateinit var repository: OnboardingRepository

    @Before
    fun setUp() {
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("a", "b") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        repository = OnboardingRepository(
            retrofit.create(OnboardingApi::class.java),
            UserSettingsRepository(
                ApplicationProvider.getApplicationContext(),
                retrofit.create(UserApi::class.java),
                session,
            ),
            CategoryRepository(retrofit.create(CategoriesApi::class.java), database.categories(), session),
        )
    }

    @After
    fun tearDown() {
        server.close()
        database.close()
    }

    @Test
    fun `launch gate is true only for successful incomplete settings`() = runTest {
        server.enqueue(json(200, settingsBody(setupCompleted = false)))
        assertTrue(repository.needsOnboarding())

        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        assertFalse(repository.needsOnboarding())
    }

    @Test
    fun `launch gate fails open for client and network errors`() = runTest {
        server.enqueue(json(404, """{"error":"NotFound"}"""))
        assertFalse(repository.needsOnboarding())

        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertFalse(repository.needsOnboarding())
    }

    @Test
    fun `country detection decodes a country and permits an absent country`() = runTest {
        server.enqueue(json(200, """{"data":{"country":"CA"}}"""))
        assertEquals("CA", repository.detectCountry())

        server.enqueue(json(200, """{"data":{}}"""))
        assertNull(repository.detectCountry())

        val fixture = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/country-detection.json"))
        val decoded = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(CountryDetectionResponse.serializer()),
            fixture.readText(),
        )
        assertEquals("ZZ", decoded.data!!.country)
    }

    @Test
    fun `country detection propagates client and network errors`() = runTest {
        // Network case first: it needs a fresh connection (a kept-alive one would be retried by OkHttp).
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertTrue(runCatching { repository.detectCountry() }.exceptionOrNull() is ApiError.Network)

        server.enqueue(json(400, """{"error":"ValidationError"}"""))
        assertTrue(runCatching { repository.detectCountry() }.exceptionOrNull() is ApiError.Server)
    }

    @Test
    fun `completion sends the exact settings fields and seeds the iOS categories`() = runTest {
        server.enqueue(json(200, """{"data":[]}"""))
        server.enqueue(json(201, categoriesBody()))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, categoriesBody()))

        repository.complete("Rae", "CAD", "CA", "fr", seedRequests())

        val categoriesCheck = server.takeRequest()
        assertEquals("GET", categoriesCheck.method)
        assertEquals("/categories", categoriesCheck.url.encodedPath)
        val seedRequest = server.takeRequest()
        assertEquals("POST", seedRequest.method)
        assertEquals("/categories/batch", seedRequest.url.encodedPath)
        val seedBody = SpenditosJson.parseToJsonElement(seedRequest.body!!.utf8()).jsonArray
        assertEquals(18, seedBody.size) // the referenced iOS seed contains 18 entries
        assertEquals(
            setOf("name", "color", "icon", "isIncome", "sortOrder", "isSystemSuggestion"),
            seedBody.first().jsonObject.keys,
        )
        assertEquals("#22c55e", seedBody.first().jsonObject["color"]!!.jsonPrimitive.content)
        assertEquals(true, seedBody.first().jsonObject["isIncome"]!!.jsonPrimitive.content.toBoolean())

        val settingsUpdate = server.takeRequest()
        assertEquals("PUT", settingsUpdate.method)
        assertEquals("/user/settings", settingsUpdate.url.encodedPath)
        val settingsBody = SpenditosJson.parseToJsonElement(settingsUpdate.body!!.utf8()).jsonObject
        assertEquals(setOf("displayName", "currency", "country", "language", "setupCompleted"), settingsBody.keys)
        assertEquals("Rae", settingsBody["displayName"]!!.jsonPrimitive.content)
        assertEquals("CAD", settingsBody["currency"]!!.jsonPrimitive.content)
        assertEquals("CA", settingsBody["country"]!!.jsonPrimitive.content)
        assertEquals("fr", settingsBody["language"]!!.jsonPrimitive.content)
        assertTrue(settingsBody["setupCompleted"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("GET", server.takeRequest().method) // settings cache refresh
        assertEquals("GET", server.takeRequest().method) // category cache refresh
    }

    @Test
    fun `completion skips seed when categories already exist`() = runTest {
        server.enqueue(json(200, categoriesBody()))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, categoriesBody()))

        repository.complete("Rae", "CAD", "CA", "en", seedRequests())

        assertEquals(4, server.requestCount)
        assertTrue((0 until server.requestCount).none { server.takeRequest().url.encodedPath == "/categories/batch" })
    }

    @Test
    fun `completion does not report success when settings update is rejected`() = runTest {
        server.enqueue(json(200, """{"data":[]}"""))
        server.enqueue(json(201, categoriesBody()))
        server.enqueue(json(400, """{"error":"ValidationError"}"""))

        assertTrue(runCatching { repository.complete("Rae", "CAD", "CA", "en", seedRequests()) }.exceptionOrNull() is ApiError.Server)
    }

    @Test
    fun `completion surfaces network failure`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertTrue(runCatching { repository.complete("Rae", "CAD", "CA", "en", seedRequests()) }.exceptionOrNull() is ApiError.Network)
    }

    @Test
    fun `retry checks categories before seeding again`() = runTest {
        server.enqueue(json(200, """{"data":[]}"""))
        // A failed reply can follow a committed batch, so the next attempt must re-read categories.
        server.enqueue(json(500, """{"error":"InternalError"}"""))
        assertTrue(runCatching { repository.complete("Rae", "CAD", "CA", "en", seedRequests()) }.isFailure)

        server.enqueue(json(200, categoriesBody()))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, categoriesBody()))
        repository.complete("Rae", "CAD", "CA", "en", seedRequests())

        val requests = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(1, requests.count { it.url.encodedPath == "/categories/batch" })
        assertEquals(1, requests.count { it.method == "PUT" && it.url.encodedPath == "/user/settings" })
    }

    @Test
    fun `completion repeats update when first settings write creates the row without setup flag`() = runTest {
        server.enqueue(json(200, categoriesBody()))
        server.enqueue(json(200, settingsBody(setupCompleted = false)))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, settingsBody(setupCompleted = true)))
        server.enqueue(json(200, categoriesBody()))

        repository.complete("Rae", "CAD", "CA", "en", seedRequests())

        val requests = (0 until server.requestCount).map { server.takeRequest() }
        assertEquals(2, requests.count { it.method == "PUT" && it.url.encodedPath == "/user/settings" })
    }

    private fun json(code: Int, body: String) = MockResponse(code = code, body = body)

    private fun seedRequests(): List<OnboardingCategoryRequest> = OnboardingData.categorySeeds.map { it.toRequest() }

    private fun settingsBody(setupCompleted: Boolean) =
        """{"data":{"userId":"user-1","currency":"CAD","mainIncomeTemplateId":null,"country":"CA","timezone":"America/Toronto","setupCompleted":$setupCompleted,"displayName":"Rae","language":"fr"}}"""

    private fun categoriesBody(): String {
        val rows = OnboardingData.categorySeeds.mapIndexed { index, seed ->
            Category(
                id = "category-${index + 1}",
                userId = "user-1",
                name = seed.name,
                color = seed.color,
                icon = seed.icon,
                isIncome = seed.isIncome,
                sortOrder = seed.sortOrder,
                isSystemSuggestion = true,
            )
        }
        return """{"data":${SpenditosJson.encodeToString(ListSerializer(Category.serializer()), rows)}}"""
    }
}
