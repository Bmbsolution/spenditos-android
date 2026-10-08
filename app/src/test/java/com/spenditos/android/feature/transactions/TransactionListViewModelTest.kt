package com.spenditos.android.feature.transactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.auth.EpochClock
import com.spenditos.android.core.auth.RefreshedTokens
import com.spenditos.android.core.auth.SessionManager
import com.spenditos.android.core.config.AppEnvironment
import com.spenditos.android.core.db.SpenditosDatabase
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.core.sync.SyncRequester
import com.spenditos.android.data.categories.CategoriesApi
import com.spenditos.android.data.categories.CategoryRepository
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.transactions.TransactionRepository
import com.spenditos.android.data.transactions.TransactionsApi
import com.spenditos.android.data.user.UserApi
import com.spenditos.android.data.user.UserSettingsRepository
import com.spenditos.android.testing.FakeTokenStore
import com.spenditos.android.testing.FakeTokenStore.Companion.NOW
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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

@RunWith(RobolectricTestRunner::class)
class TransactionListViewModelTest {
    private val server = MockWebServer()
    private var clock = MutableClock(Instant.parse("2026-10-31T16:00:00Z"), TORONTO)
    private lateinit var db: SpenditosDatabase
    private lateinit var viewModel: TransactionListViewModel

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

    private fun createViewModel(): TransactionListViewModel {
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
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
            SyncRequester {},
            FinanceInvalidationBus(),
        )
        val categoryRepository = CategoryRepository(
            retrofit.create(CategoriesApi::class.java), db.categories(), session, FinanceInvalidationBus(),
        )
        val settings = UserSettingsRepository(
            ApplicationProvider.getApplicationContext<Context>(),
            object : UserApi {
                override suspend fun settings(): ApiEnvelope<UserSettings> = error("not used")
                override suspend fun updateSettings(body: UserSettingsUpdate): ApiEnvelope<UserSettings> = error("not used")
                override suspend fun changePassword(body: ChangePasswordRequest) = error("not used")
                override suspend fun deleteAccount() = error("not used")
            },
            session,
        )
        return TransactionListViewModel(
            transactionRepository,
            categoryRepository,
            settings,
            object : com.spenditos.android.core.network.ConnectivityStatus {
                override val isOnline = kotlinx.coroutines.flow.MutableStateFlow(true)
            },
            FinanceInvalidationBus(),
            FilterPresetStore(ApplicationProvider.getApplicationContext<Context>(), session),
            clock,
        )
    }

    private fun emptyPage() = MockResponse(body = """{"data":{"transactions":[],"pagination":{"nextCursor":null,"hasMore":false}}}""")

    /** Keeps the state flow subscribed (WhileSubscribed) while the test moves the clock. */
    private fun kotlinx.coroutines.test.TestScope.collectMonths(months: MutableList<YearMonth>): Job =
        launch { viewModel.state.collect { months += it.month } }

    private suspend fun receivedRequests(): List<RecordedRequest> {
        val requests = mutableListOf<RecordedRequest>()
        while (true) {
            val request = withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) } ?: break
            requests.add(request)
        }
        return requests
    }

    @Test
    fun `a midnight rollover advances the default month and fetches the new range`() = runTest {
        repeat(4) { server.enqueue(emptyPage()) }
        viewModel = createViewModel()
        val months = mutableListOf<YearMonth>()
        val collector = collectMonths(months)

        testScheduler.advanceUntilIdle()
        println("DIAG after initial: months=$months state=${viewModel.state.value.month} req=${server.requestCount} vtime=${testScheduler.currentTime}")

        // Midnight Toronto (04:00Z) while the list stays open on the same ViewModel.
        clock.set(Instant.parse("2026-11-01T04:00:00Z"))
        testScheduler.advanceTimeBy(Duration.between(Instant.parse("2026-10-31T16:00:00Z"), Instant.parse("2026-11-01T04:00:00Z")).toMillis())
        println("DIAG after advanceTimeBy: vtime=${testScheduler.currentTime} months=$months")
        testScheduler.advanceUntilIdle()
        println("DIAG after tick: months=$months state=${viewModel.state.value.month} req=${server.requestCount} vtime=${testScheduler.currentTime}")

        assertEquals(YearMonth.of(2026, 11), viewModel.state.value.month)
        assertTrue(months.contains(YearMonth.of(2026, 11)))
        collector.cancelAndJoin()
        val requests = receivedRequests()
        assertTrue(
            requests.any { it.url.queryParameter("startDate") == "2026-11-01" && it.url.queryParameter("endDate") == "2026-11-30" },
        )
    }

    @Test
    fun `an explicitly browsed month is never reset by the rollover`() = runTest {
        repeat(4) { server.enqueue(emptyPage()) }
        viewModel = createViewModel()
        val months = mutableListOf<YearMonth>()
        val collector = collectMonths(months)

        testScheduler.advanceUntilIdle()
        viewModel.previousMonth() // browsing away from the default month pins it
        testScheduler.advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)

        clock.set(Instant.parse("2026-11-01T04:00:00Z"))
        testScheduler.advanceTimeBy(Duration.between(Instant.parse("2026-10-31T16:00:00Z"), Instant.parse("2026-11-01T04:00:00Z")).toMillis())
        testScheduler.advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)
        assertFalse(months.contains(YearMonth.of(2026, 11)))
        collector.cancelAndJoin()
        assertTrue(receivedRequests().none { it.url.queryParameter("startDate") == "2026-11-01" })
    }

    private companion object {
        val TORONTO = ZoneId.of("America/Toronto")
    }

    /** A [Clock] the test moves by hand; the list keeps reading it as time passes. */
    private class MutableClock(private var value: Instant, private val zoneId: ZoneId) : Clock() {
        fun set(instant: Instant) {
            value = instant
        }

        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(value, zoneId)

        override fun instant(): Instant = value
    }
}
