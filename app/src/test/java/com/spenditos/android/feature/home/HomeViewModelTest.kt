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
import com.spenditos.android.core.sync.Aggregate
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val server = MockWebServer()
    private val offline = OfflineInterceptor()
    private val bus = FinanceInvalidationBus()
    private var clock = MutableClock(Instant.parse("2026-10-01T16:00:00Z"), TORONTO)
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
    fun `insights use the same Room month and agree with the status total`() = runTest {
        db.categories().insert(
            listOf(
                category("income", "Income", isIncome = true),
                category("housing", "Housing"),
                category("food", "Food"),
            ),
        )
        server.enqueue(
            MockResponse(body = page(
                tx("pay", "income", "Income", "2026-10-01", 2_000.0, "income-template"),
                tx("rent", "housing", "Rent", "2026-10-01", 1_000.0, "rent-template"),
                tx("coffee", "food", "Coffee", "2026-10-01", 30.0),
                tx("dentist", "food", "Dentist", "2026-10-12", 120.0),
                tx("pay-2", "income", "Income", "2026-10-15", 2_000.0, "income-template"),
            )),
        )
        viewModel = createViewModel()

        val state = viewModel.state.first { it.hasLoaded && it.transactionCount == 5 && it.insights != null }
        val insights = state.insights!!

        assertEquals("dentist", insights.nextBill?.id)
        assertEquals("Food", insights.topCategory?.name)
        assertEquals(30.0, insights.weekTotal.toDouble(), 0.0)
        assertEquals(state.status!!.leftAfterBills.toDouble(), insights.monthEndGap.toDouble(), 0.0)
        assertEquals("/transactions", server.takeRequest().url.encodedPath)
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
        assertNull(state.insights)
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
        assertEquals(4.5, state.insights!!.monthOut.toDouble(), 0.0)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh recomputes today without recreating the ViewModel`() = runTest {
        db.categories().insert(listOf(category("food", "Food")))
        server.enqueue(MockResponse(body = page(tx("coffee", "food", "Coffee", "2026-10-01", 30.0))))
        viewModel = createViewModel()
        viewModel.state.first { it.hasLoaded && it.transactionCount == 1 }
        assertEquals(LocalDate.parse("2026-10-01"), viewModel.state.value.today)

        clock.set(Instant.parse("2026-10-02T16:00:00Z")) // the device crossed midnight
        server.enqueue(MockResponse(body = page(tx("coffee", "food", "Coffee", "2026-10-01", 30.0))))
        viewModel.refresh()

        val state = viewModel.state.first { it.today == LocalDate.parse("2026-10-02") }
        assertEquals("2026-10", state.month.toString())
        // The seven-day window follows the new day, not the ViewModel's creation date.
        assertEquals(LocalDate.parse("2026-10-02"), state.history.last().date)
    }

    @Test
    fun `a rollover at midnight moves the month and fetched ranges on the same ViewModel`() = runTest {
        clock = MutableClock(Instant.parse("2026-10-31T16:00:00Z"), TORONTO)
        db.categories().insert(listOf(category("food", "Food")))
        server.enqueue(MockResponse(body = page(tx("coffee", "food", "Coffee", "2026-10-30", 30.0))))
        viewModel = createViewModel()
        viewModel.state.first { it.hasLoaded && it.transactionCount == 1 }
        assertEquals(LocalDate.parse("2026-10-31"), viewModel.state.value.today)

        // Midnight Toronto (04:00Z) while Home stays alive; the new month needs fetching.
        clock.set(Instant.parse("2026-11-01T04:00:00Z"))
        server.enqueue(MockResponse(body = page(tx("rent", "food", "Rent", "2026-11-01", 900.0))))
        testScheduler.advanceTimeBy(millisUntilNextLocalMidnight(Instant.parse("2026-10-31T16:00:00Z")))

        val state = viewModel.state.first { it.today == LocalDate.parse("2026-11-01") }
        assertEquals("2026-11", state.month.toString())

        server.takeRequest(5, TimeUnit.SECONDS) // October's initial load, already received
        // The November fetch may still be in flight: receive it while the scheduler runs.
        val november = withContext(Dispatchers.IO) { server.takeRequest(10, TimeUnit.SECONDS) }
        assertNotNull(november)
        assertEquals("2026-11-01", november!!.url.queryParameter("startDate"))
        assertEquals("2026-11-30", november.url.queryParameter("endDate"))
    }

    @Test
    fun `a transaction invalidation refreshes Home without a new mutation event`() = runTest {
        db.categories().insert(listOf(category("food", "Food")))
        server.enqueue(MockResponse(body = page(tx("coffee", "food", "Coffee", "2026-10-01", 30.0))))
        viewModel = createViewModel()
        viewModel.state.first { it.hasLoaded && it.transactionCount == 1 }
        server.takeRequest(5, TimeUnit.SECONDS)

        // The first refresh also issues a best-effort week-tail request (the 1st spans September)
        // which is still parked on the empty response queue; feed it before the next refresh.
        server.enqueue(MockResponse(body = page()))
        server.enqueue(MockResponse(body = page(tx("coffee", "food", "Coffee", "2026-10-01", 30.0), tx("bus", "food", "Bus", "2026-10-01", 3.0))))
        bus.invalidate(Aggregate.TRANSACTION)
        testScheduler.advanceTimeBy(250)
        testScheduler.advanceUntilIdle()

        viewModel.state.first { it.transactionCount == 2 }
        assertTrue(server.requestCount >= 3) // the invalidation caused a fresh month fetch
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
            bus,
        )
        val categoryRepository = CategoryRepository(
            retrofit.create(CategoriesApi::class.java), db.categories(), session, bus,
        )
        return HomeViewModel(
            transactionRepository,
            categoryRepository,
            FakeSettings(),
            bus,
            clock,
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

    private companion object {
        val TORONTO = ZoneId.of("America/Toronto")

        fun millisUntilNextLocalMidnight(from: Instant): Long =
            java.time.Duration.between(from, Instant.parse("2026-11-01T04:00:00Z")).toMillis()
    }

    /** A [Clock] the test moves by hand; Home keeps reading it as time passes. */
    private class MutableClock(private var value: Instant, private val zoneId: ZoneId) : Clock() {
        fun set(instant: Instant) {
            value = instant
        }

        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(value, zone)

        override fun instant(): Instant = value
    }
}
