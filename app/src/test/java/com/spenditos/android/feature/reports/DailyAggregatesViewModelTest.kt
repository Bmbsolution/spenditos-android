package com.spenditos.android.feature.reports

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.CategorySummary
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.DashboardSummary
import com.spenditos.android.data.model.PaycheckPeriodsData
import com.spenditos.android.data.model.ReportDateRange
import com.spenditos.android.data.reports.ReportsDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class DailyAggregatesViewModelTest {
    private lateinit var fake: FakeReportsDataSource

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        fake = FakeReportsDataSource()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `daily aggregates load and calculate exact summaries and moving averages`() = runTest {
        fake.dailyRows = sampleDailyRows()
        val viewModel = DailyAggregatesViewModel(fake)
        viewModel.load()
        advanceUntilIdle()

        assertEquals("CAD", viewModel.state.value.currency)
        assertEquals(BigDecimal("35.25"), viewModel.state.value.summary.totalExpenses)
        assertEquals(BigDecimal("11.75"), viewModel.state.value.summary.averageDailyExpense)
        assertEquals(BigDecimal("11.75"), viewModel.movingAverageAt(2))
        assertEquals(BigDecimal.ZERO, viewModel.movingAverageAt(-1))
        assertEquals(LocalDate.now().withDayOfMonth(1).plusDays(1), viewModel.state.value.summary.highestSpendingDay)
    }

    @Test
    fun `daily aggregate client error is shown as an error state`() = runTest {
        fake.failure = ApiError.Server(400, "InvalidQuery")
        val viewModel = DailyAggregatesViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.ERROR, viewModel.state.value.loadStatus)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `daily aggregate network error is shown as offline`() = runTest {
        fake.failure = ApiError.Network(IOException("offline"))
        val viewModel = DailyAggregatesViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.OFFLINE, viewModel.state.value.loadStatus)
    }

    @Test
    fun `empty daily data is safe for averages and marks the screen empty`() = runTest {
        fake.dailyRows = emptyList()
        val viewModel = DailyAggregatesViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isEmpty)
        assertNull(viewModel.state.value.summary.averageDailyExpense)
        assertEquals(BigDecimal.ZERO, viewModel.movingAverageAt(0))
    }

    @Test
    fun `reports hub success and category remainder use the fake source`() = runTest {
        val month = LocalDate.now().withDayOfMonth(1)
        fake.dashboardResponse = DashboardData(
            dateRange = ReportDateRange(month, month.withDayOfMonth(month.lengthOfMonth())),
            summary = DashboardSummary(1200.0, 500.0, 700.0),
            categoryBreakdown = listOf(
                CategorySummary("a", "A", totalAmount = 40.0, transactionCount = 1, isIncome = false),
                CategorySummary("b", "B", totalAmount = 30.0, transactionCount = 1, isIncome = false),
                CategorySummary("c", "C", totalAmount = 20.0, transactionCount = 1, isIncome = false),
                CategorySummary("d", "D", totalAmount = 10.0, transactionCount = 1, isIncome = false),
                CategorySummary("income", "Income", totalAmount = 1200.0, transactionCount = 1, isIncome = true),
            ),
            totalTransactionCount = 4,
        )
        val viewModel = ReportsHubViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(700.0, viewModel.state.value.dashboard!!.summary!!.netAmount, 0.0)
        assertEquals("CAD", viewModel.state.value.currency)
        val slices = categorySlices(viewModel.state.value.dashboard!!.categoryBreakdown)
        assertEquals(listOf("a", "b", "c", "everything-else"), slices.map { it.id })
        assertEquals(BigDecimal("10.0"), slices.last().amount)
    }

    @Test
    fun `reports hub client error and network error are surfaced`() = runTest {
        val viewModel = ReportsHubViewModel(fake.apply { failure = ApiError.Server(400, "InvalidQuery") })
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.ERROR, viewModel.state.value.loadStatus)

        fake.failure = ApiError.Network(IOException("offline"))
        val offlineViewModel = ReportsHubViewModel(fake)
        offlineViewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.OFFLINE, offlineViewModel.state.value.loadStatus)
    }
}

private class FakeReportsDataSource : ReportsDataSource {
    var failure: Throwable? = null
    var dailyRows: List<DailyAggregate> = sampleDailyRows()
    var dashboardResponse: DashboardData = DashboardData(
        dateRange = ReportDateRange(LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())),
        summary = DashboardSummary(0.0, 0.0, 0.0),
        totalTransactionCount = 0,
    )

    override suspend fun getDashboard(startDate: LocalDate, endDate: LocalDate): DashboardData {
        failure?.let { throw it }
        return dashboardResponse
    }

    override suspend fun getDailyAggregates(startDate: LocalDate, endDate: LocalDate): List<DailyAggregate> {
        failure?.let { throw it }
        return dailyRows
    }

    override suspend fun getPaycheckPeriods(startDate: LocalDate, endDate: LocalDate): PaycheckPeriodsData {
        failure?.let { throw it }
        return PaycheckPeriodsData(ReportDateRange(startDate, endDate), emptyList())
    }

    override fun currency(): Flow<String> = flowOf("CAD")
}

private fun sampleDailyRows(): List<DailyAggregate> {
    val first = LocalDate.now().withDayOfMonth(1)
    return listOf(
        DailyAggregate(first, 0.0, 10.25, 1),
        DailyAggregate(first.plusDays(1), 100.0, 20.0, 1),
        DailyAggregate(first.plusDays(2), 0.0, 5.0, 0),
    )
}
