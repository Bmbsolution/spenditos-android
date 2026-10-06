package com.spenditos.android.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.CategoryEntity
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.db.SyncStatus
import com.spenditos.android.core.db.TransactionEntity
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.user.UserSettingsActions
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import com.spenditos.android.testing.OfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private lateinit var db: SpenditosDatabase
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        server.start()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpenditosDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `home loads current month into Room and derives status with the pinned clock`() = runTest {
        db.categories().insert(listOf(category("income", "Income", true), category("food", "Food")))
        server.enqueue(MockResponse(body = page(
            tx("income", "income", "Income", "2026-10-15", 1200.0, "income-template"),
            tx("dentist", "food", "Dentist", "2026-10-06", 120.0),
        )))
        viewModel = createViewModel()

        val state = viewModel.state.first { it.hasLoaded && it.transactionCount == 2 }

        assertEquals(LocalDate.parse("2026-10-01"), state.today)
        assertEquals("CAD", state.currency)
        assertEquals("2026-10", state.month.toString())
        assertEquals(1200.0, state.status!!.expected.toDouble(), 0.0)
        assertEquals("Dentist", state.upcomingBills.first().name)
        assertFalse(state.isOffline)
        val request = server.takeRequest()
        assertEquals("/transactions", request.url.encodedPath)
        assertEquals("2026-10-01", request.url.queryParameter("startDate"))
        assertEquals("2026-10-31", request.url.queryParameter("endDate"))
        assertEquals(2, db.transactions().observeRange("user-1", "2026-10-01", "2026-10-31").first().size)
    }

    @Test
    fun `client error shows a refresh error after Room remains available`() = runTest {
        db.categories().insert(listOf(category("food", "Food")))
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid query"}"""))
        viewModel = createViewModel()

        val state = viewModel.state.first { it.hasLoaded && it.refreshError != null }

        assertEquals("Couldn't refresh Home. Pull down to try again.", state.refreshError)
        assertFalse(state.showingSavedData)
        assertTrue(state.status != null)
        assertEquals("/transactions", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `network failure keeps cached Room rows and shows the saved data note`() = runTest {
        db.categories().insert(listOf(category("food", "Food")))
        db.transactions().upsert(
            listOf(
                TransactionEntity(
                    id = "cached-coffee", userId = "user-1", amount = 4.5, categoryId = "food",
                    description = "Coffee", date = "2026-10-01", mood = null, templateId = null,
                    source = "manual", createdAt = null, updatedAt = null, syncStatus = SyncStatus.SYNCED,
                ),
            ),
        )
        offline.failNext = 1
        viewModel = createViewModel()

        val state = viewModel.state.first { it.hasLoaded && it.showingSavedData && it.transactionCount == 1 }

        assertTrue(state.isOffline)
        assertEquals(4.5, state.status!!.everyday.toDouble(), 0.0)
        assertEquals(0, server.requestCount)
    }

    private fun createViewModel(): HomeViewModel {
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(offline).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType())).build()
        val session = SessionManager(
            FakeTokenStore.signedIn("user-1"),
            { RefreshedTokens("access", "id") },
            EpochClock { NOW },
            AppEnvironment(),
        )
        val transactionRepository = TransactionRepository(
            retrofit.create(TransactionsApi::class.java), db, session,
            object : com.spenditos.android.core.network.ConnectivityStatus {
                override val isOnline = kotlinx.coroutines.flow.MutableStateFlow(true)
            },
            com.spenditos.android.core.sync.SyncRequester {},
            FinanceInvalidationBus(),
        )
        val categoryRepository = CategoryRepository(
            retrofit.create(CategoriesApi::class.java), db.categories(), session, FinanceInvalidationBus(),
        )
        return HomeViewModel(
            transactionRepository,
            categoryRepository,
            FakeSettings(),
            FinanceInvalidationBus(),
            Clock.fixed(Instant.parse("2026-10-01T16:00:00Z"), ZoneId.of("America/Toronto")),
        )
    }

    private fun category(id: String, name: String, isIncome: Boolean = false) =
        CategoryEntity(id, "user-1", name, "#445566", null, isIncome, 0, false)

    private fun tx(id: String, categoryId: String, description: String, date: String, amount: Double, templateId: String? = null) =
        """{"id":"$id","userId":"user-1","categoryId":"$categoryId","description":"$description","date":"$date","amount":$amount,"templateId":${templateId?.let { "\"$it\"" } ?: "null"}}"""

    private fun page(vararg transactions: String) =
        """{"data":{"transactions":[${transactions.joinToString(",")}],"pagination":{"nextCursor":null,"hasMore":false}}}"""

    private class FakeSettings : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("CAD")
        override suspend fun refresh(): UserSettings = error("not used")
        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings = error("not used")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = error("not used")
    }
}
