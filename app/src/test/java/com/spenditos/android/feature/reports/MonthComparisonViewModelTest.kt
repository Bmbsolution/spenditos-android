package com.spenditos.android.feature.reports

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.data.model.DashboardData
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class MonthComparisonViewModelTest {
    private lateinit var fake: FakeComparisonReportsSource

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        fake = FakeComparisonReportsSource()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `month comparison loads two months and builds weekly paired totals`() = runTest {
        val currentMonth = LocalDate.now().withDayOfMonth(1)
        val previousMonth = currentMonth.minusMonths(1)
        fake.currentRows = listOf(DailyAggregate(currentMonth, 1200.0, 250.0, 2))
        fake.previousRows = listOf(DailyAggregate(previousMonth, 900.0, 125.0, 1))
        val viewModel = MonthComparisonViewModel(fake)
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasLoaded)
        assertEquals(2, fake.requestCount)
        assertEquals(BigDecimal("250.0"), viewModel.state.value.currentTotalExpenses)
        assertEquals(BigDecimal("125.0"), viewModel.state.value.previousTotalExpenses)
        assertEquals(BigDecimal("175.0"), viewModel.state.value.currentNet.subtract(viewModel.state.value.previousNet))
        assertEquals(BigDecimal("250.0"), viewModel.weeklyExpenses().fold(BigDecimal.ZERO) { sum, item -> sum.add(item.current) })
        assertEquals(BigDecimal("125.0"), viewModel.weeklyExpenses().fold(BigDecimal.ZERO) { sum, item -> sum.add(item.previous) })
        assertEquals("+100%", viewModel.percentageChange(BigDecimal("10"), BigDecimal.ZERO))
        assertEquals("+100%", viewModel.percentageChange(BigDecimal("250"), BigDecimal("125")))
    }

    @Test
    fun `empty comparison has no weekly values and keeps a scale of one`() = runTest {
        fake.currentRows = emptyList()
        fake.previousRows = emptyList()
        val viewModel = MonthComparisonViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isEmpty)
        assertTrue(viewModel.weeklyExpenses().isEmpty())
        assertTrue(viewModel.weeklyIncome().isEmpty())
        assertEquals(BigDecimal.ONE, viewModel.maxWeeklyValue(viewModel.weeklyExpenses()))
    }

    @Test
    fun `month comparison maps a client error`() = runTest {
        fake.failure = ApiError.Server(400, "InvalidQuery")
        val viewModel = MonthComparisonViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.ERROR, viewModel.state.value.loadStatus)
        assertFalse(viewModel.state.value.hasLoaded)
    }

    @Test
    fun `month comparison maps network failure to offline`() = runTest {
        fake.failure = ApiError.Network(IOException("offline"))
        val viewModel = MonthComparisonViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.OFFLINE, viewModel.state.value.loadStatus)
    }
}

private class FakeComparisonReportsSource : ReportsDataSource {
    var failure: Throwable? = null
    var currentRows: List<DailyAggregate> = emptyList()
    var previousRows: List<DailyAggregate> = emptyList()
    var requestCount = 0

    override suspend fun getDashboard(startDate: LocalDate, endDate: LocalDate): DashboardData = throw AssertionError("unexpected dashboard call")

    override suspend fun getDailyAggregates(startDate: LocalDate, endDate: LocalDate): List<DailyAggregate> {
        requestCount++
        failure?.let { throw it }
        return if (startDate.month == LocalDate.now().month && startDate.year == LocalDate.now().year) currentRows else previousRows
    }

    override suspend fun getPaycheckPeriods(startDate: LocalDate, endDate: LocalDate): PaycheckPeriodsData =
        PaycheckPeriodsData(ReportDateRange(startDate, endDate), emptyList())

    override fun currency(): Flow<String> = flowOf("CAD")
}
