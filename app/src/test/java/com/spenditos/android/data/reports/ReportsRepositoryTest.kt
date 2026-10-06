package com.spenditos.android.data.reports

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.LocalDate

class ReportsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: ReportsRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(SpenditosJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReportsApi::class.java)
        repository = ReportsRepository(api) { flowOf("CAD") }
    }

    @After
    fun tearDown() = server.close()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/$name.json")).readText()

    private fun droppedConnection() = MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()

    @Test
    fun `dashboard succeeds with inclusive ISO dates`() = runTest {
        server.enqueue(MockResponse(code = 200, body = fixture("dashboardSuccessFixture")))
        val result = repository.getDashboard(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        val request = server.takeRequest()
        assertEquals("/reports/dashboard", request.url.encodedPath)
        assertEquals("2026-01-01", request.url.queryParameter("startDate"))
        assertEquals("2026-01-31", request.url.queryParameter("endDate"))
        assertEquals(6400.0, result.summary!!.totalIncome, 0.0)
    }

    @Test
    fun `dashboard maps client errors`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"message":"InvalidQuery","error":"InvalidQuery"}"""))
        val failure = runCatching { repository.getDashboard(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        assertEquals(400, (failure as ApiError.Server).status)
    }

    @Test
    fun `dashboard maps server errors`() = runTest {
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        val failure = runCatching { repository.getDashboard(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        assertEquals(503, (failure as ApiError.Server).status)
    }

    @Test
    fun `dashboard maps network failures`() = runTest {
        server.enqueue(droppedConnection())
        val failure = runCatching { repository.getDashboard(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Network)
    }

    @Test
    fun `daily aggregates unwrap the backend double envelope`() = runTest {
        server.enqueue(MockResponse(code = 200, body = fixture("dailyAggregatesSuccessFixture")))
        val result = repository.getDailyAggregates(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        val request = server.takeRequest()
        assertEquals("/reports/daily-aggregates", request.url.encodedPath)
        assertEquals(3, result.size)
        assertEquals(LocalDate.parse("2026-01-16"), result[1].date)
        assertEquals(1600.0, result[1].income, 0.0)
    }

    @Test
    fun `daily aggregates maps client errors`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"message":"InvalidQuery","error":"InvalidQuery"}"""))
        val failure = runCatching { repository.getDailyAggregates(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        assertEquals(400, (failure as ApiError.Server).status)
    }

    @Test
    fun `daily aggregates maps server errors`() = runTest {
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        val failure = runCatching { repository.getDailyAggregates(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        assertEquals(503, (failure as ApiError.Server).status)
    }

    @Test
    fun `daily aggregates maps network failures`() = runTest {
        server.enqueue(droppedConnection())
        val failure = runCatching { repository.getDailyAggregates(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Network)
    }

    @Test
    fun `paycheck periods decode success`() = runTest {
        server.enqueue(MockResponse(code = 200, body = fixture("paycheckPeriodsSuccessFixture")))
        val result = repository.getPaycheckPeriods(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
        val request = server.takeRequest()
        assertEquals("/reports/paycheck-periods", request.url.encodedPath)
        assertEquals(LocalDate.parse("2026-01-01"), result.periods.single().startDate)
        assertEquals(3200.0, result.periods.single().income.total, 0.0)
    }

    @Test
    fun `paycheck periods preserves the server ProRequired error`() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"message":"Pro subscription required to access this feature","error":"ProRequired"}"""))
        val failure = runCatching { repository.getPaycheckPeriods(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        val serverError = failure as ApiError.Server
        assertEquals(403, serverError.status)
        assertEquals("ProRequired", serverError.serverMessage)
    }

    @Test
    fun `paycheck periods maps other server errors`() = runTest {
        server.enqueue(MockResponse(code = 503, body = """{"error":"Unavailable"}"""))
        val failure = runCatching { repository.getPaycheckPeriods(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Server)
        assertEquals(503, (failure as ApiError.Server).status)
    }

    @Test
    fun `paycheck periods maps network failures`() = runTest {
        server.enqueue(droppedConnection())
        val failure = runCatching { repository.getPaycheckPeriods(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")) }.exceptionOrNull()
        assertTrue(failure is ApiError.Network)
    }
}
