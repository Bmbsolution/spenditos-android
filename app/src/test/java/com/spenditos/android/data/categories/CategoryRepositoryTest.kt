package com.spenditos.android.data.categories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
class CategoryRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: SpenditosDatabase
    private lateinit var repository: CategoryRepository
    private lateinit var invalidations: FinanceInvalidationBus

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build().create(CategoriesApi::class.java)
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("access", "id") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        invalidations = FinanceInvalidationBus()
        repository = CategoryRepository(api, db.categories(), session, invalidations)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun category(id: String = "cat-1", name: String = "Fake Meals", isIncome: Boolean = false) =
        """{"id":"$id","userId":"user-1","name":"$name","color":"#B85F42","icon":"🍲","isIncome":$isIncome,"sortOrder":0,"isSystemSuggestion":false}"""

    private fun listBody(vararg items: String) = """{"data":[${items.joinToString(",")}] }"""

    private fun writeBody(item: String) = """{"data":$item}"""

    private suspend fun failure(block: suspend () -> Any?): Throwable =
        requireNotNull(runCatching { block() }.exceptionOrNull()) { "expected failure" }

    private fun networkFailure() = MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()

    @Test
    fun `handwritten category request fixture decodes create and update bodies`() {
        val raw = requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/categoryRequests.json")).readText()
        val root = SpenditosJson.parseToJsonElement(raw).jsonObject
        val create = SpenditosJson.decodeFromString(CategoryCreateRequest.serializer(), root["create"].toString())
        val update = SpenditosJson.decodeFromString(CategoryUpdateRequest.serializer(), root["update"].toString())
        assertEquals("Fake Meals", create.name)
        assertEquals("🍲", create.icon)
        assertEquals(true, update.isIncome)
        assertEquals("", update.icon)
    }

    @Test
    fun `refresh caches categories and maps list 4xx and network failures`() = runTest {
        server.enqueue(MockResponse(body = listBody(category())))
        repository.refresh()
        assertEquals("/categories", server.takeRequest().url.encodedPath)
        assertEquals("Fake Meals", db.categories().observe("user-1").first().single().name)

        server.enqueue(MockResponse(code = 400, body = """{"error":"Validation failed"}"""))
        val clientError = failure { repository.refresh() }
        assertTrue(clientError is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.refresh() } is ApiError.Server)

        server.enqueue(networkFailure())
        val networkError = failure { repository.refresh() }
        assertTrue(networkError is ApiError.Network)
    }

    @Test
    fun `create posts then refreshes cache and invalidates categories`() = runTest {
        val event = async(start = CoroutineStart.UNDISPATCHED) { invalidations.events.first() }
        server.enqueue(MockResponse(code = 201, body = writeBody(category())))
        server.enqueue(MockResponse(body = listBody(category())))
        val created = repository.create(CategoryCreateRequest("Fake Meals", "#B85F42", false, "🍲"))
        assertEquals("cat-1", created.id)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/categories", create.url.encodedPath)
        val body = SpenditosJson.parseToJsonElement(create.body!!.utf8()).jsonObject
        assertEquals("Fake Meals", body["name"]!!.jsonPrimitive.content)
        assertEquals("cat-1", db.categories().observe("user-1").first().single().id)
        assertEquals(setOf(Aggregate.CATEGORY), event.await())
        assertEquals("/categories", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `create surfaces 4xx and network failures`() = runTest {
        val request = CategoryCreateRequest("Fake Meals", "#B85F42", false)
        server.enqueue(MockResponse(code = 409, body = """{"error":"Name already exists"}"""))
        assertTrue(failure { repository.create(request) } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.create(request) } is ApiError.Server)

        server.enqueue(networkFailure())
        assertTrue(failure { repository.create(request) } is ApiError.Network)
    }

    @Test
    fun `update puts category and refreshes cache`() = runTest {
        server.enqueue(MockResponse(body = writeBody(category(name = "Fake Food"))))
        server.enqueue(MockResponse(body = listBody(category(name = "Fake Food"))))
        val updated = repository.update("cat-1", CategoryUpdateRequest(name = "Fake Food", color = "#B85F42", isIncome = false))
        assertEquals("Fake Food", updated.name)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/categories/cat-1", request.url.encodedPath)
        assertEquals("/categories", server.takeRequest().url.encodedPath)
        assertEquals("Fake Food", db.categories().observe("user-1").first().single().name)
    }

    @Test
    fun `update surfaces 4xx and network failures`() = runTest {
        val update = CategoryUpdateRequest(name = "Fake Food")
        server.enqueue(MockResponse(code = 404, body = """{"error":"Category not found"}"""))
        assertTrue(failure { repository.update("cat-1", update) } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.update("cat-1", update) } is ApiError.Server)

        server.enqueue(networkFailure())
        assertTrue(failure { repository.update("cat-1", update) } is ApiError.Network)
    }

    @Test
    fun `delete removes from cache after 204 and refresh`() = runTest {
        db.categories().insert(listOf(com.spenditos.android.core.db.CategoryEntity("cat-1", "user-1", "Fake Meals", "#B85F42", null, false, 0, false)))
        server.enqueue(MockResponse(code = 204))
        server.enqueue(MockResponse(body = listBody()))
        repository.delete("cat-1")
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals("/categories/cat-1", server.takeRequest().url.encodedPath)
        assertTrue(db.categories().observe("user-1").first().isEmpty())
    }

    @Test
    fun `delete surfaces 4xx and network failures`() = runTest {
        server.enqueue(MockResponse(code = 409, body = """{"error":"Category has existing references"}"""))
        assertTrue(failure { repository.delete("cat-1") } is ApiError.Server)

        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        assertTrue(failure { repository.delete("cat-1") } is ApiError.Server)

        server.enqueue(networkFailure())
        assertTrue(failure { repository.delete("cat-1") } is ApiError.Network)
    }
}
