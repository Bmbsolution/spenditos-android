package com.spenditos.android.feature.reports

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.PaycheckPeriod
import com.spenditos.android.data.model.PaycheckPeriodTotal
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

class PaycheckPeriodsViewModelTest {
    private lateinit var fake: FakePaycheckReportsSource

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        fake = FakePaycheckReportsSource()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `summary filters overlapping periods and uses the total fields`() = runTest {
        val month = LocalDate.now().withDayOfMonth(1)
        val start = month.withDayOfMonth(minOf(10, month.lengthOfMonth()))
        val end = month.withDayOfMonth(minOf(24, month.lengthOfMonth()))
        fake.periods = listOf(
            period("overlap", start, end, 1800.0, 1050.0),
            period("outside", month.minusMonths(2).withDayOfMonth(1), month.minusMonths(2).withDayOfMonth(3), 999.0, 111.0),
        )
        val viewModel = PaycheckPeriodsViewModel(fake)
        viewModel.load()
        advanceUntilIdle()

        assertEquals("CAD", viewModel.state.value.currency)
        assertEquals(1, viewModel.state.value.periods.size)
        assertEquals(BigDecimal("1800.0"), viewModel.state.value.summary!!.totalIncome)
        assertEquals(BigDecimal("1050.0"), viewModel.state.value.summary!!.totalExpenses)
        assertEquals(58.3333333333, viewModel.state.value.summary!!.averageCoverage!!.toDouble(), 0.0001)
        assertEquals(month.minusMonths(1).withDayOfMonth(1), fake.requestStart)
        val nextMonth = month.plusMonths(1).withDayOfMonth(1)
        assertEquals(nextMonth.withDayOfMonth(nextMonth.lengthOfMonth()), fake.requestEnd)
    }

    @Test
    fun `remaining and coverage classify gap covered and exact periods`() = runTest {
        val month = LocalDate.now().withDayOfMonth(1)
        val vm = PaycheckPeriodsViewModel(fake)
        advanceUntilIdle()
        val gap = period("gap", month, month.plusDays(1), 1000.0, 1200.0)
        val covered = period("covered", month, month.plusDays(1), 600.0, 200.0)
        val exact = period("exact", month, month.plusDays(1), 600.0, 600.0)

        assertEquals(BigDecimal("-200.0"), vm.remainingAmount(gap))
        assertFalse(vm.isCovered(gap))
        assertEquals(BigDecimal("400.0"), vm.remainingAmount(covered))
        assertTrue(vm.isCovered(covered))
        assertTrue(vm.isCovered(exact))
    }

    @Test
    fun `ProRequired maps to a calm Pro feature state`() = runTest {
        fake.failure = ApiError.Server(403, "ProRequired")
        val viewModel = PaycheckPeriodsViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.PRO_REQUIRED, viewModel.state.value.loadStatus)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `paycheck client error is shown as an error state`() = runTest {
        fake.failure = ApiError.Server(400, "InvalidQuery")
        val viewModel = PaycheckPeriodsViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.ERROR, viewModel.state.value.loadStatus)
    }

    @Test
    fun `paycheck network error is shown as offline`() = runTest {
        fake.failure = ApiError.Network(IOException("offline"))
        val viewModel = PaycheckPeriodsViewModel(fake)
        viewModel.load()
        advanceUntilIdle()
        assertEquals(ReportLoadStatus.OFFLINE, viewModel.state.value.loadStatus)
    }
}

private class FakePaycheckReportsSource : ReportsDataSource {
    var failure: Throwable? = null
    var periods: List<PaycheckPeriod> = emptyList()
    var requestStart: LocalDate? = null
    var requestEnd: LocalDate? = null

    override suspend fun getDashboard(startDate: LocalDate, endDate: LocalDate): DashboardData = throw AssertionError("unexpected dashboard call")

    override suspend fun getDailyAggregates(startDate: LocalDate, endDate: LocalDate): List<com.spenditos.android.data.model.DailyAggregate> =
        throw AssertionError("unexpected daily aggregate call")

    override suspend fun getPaycheckPeriods(startDate: LocalDate, endDate: LocalDate): PaycheckPeriodsData {
        requestStart = startDate
        requestEnd = endDate
        failure?.let { throw it }
        return PaycheckPeriodsData(ReportDateRange(startDate, endDate), periods)
    }

    override fun currency(): Flow<String> = flowOf("CAD")
}

private fun period(id: String, start: LocalDate, end: LocalDate, income: Double, expenses: Double) = PaycheckPeriod(
    id = id,
    startDate = start,
    endDate = end,
    label = id,
    income = PaycheckPeriodTotal(income),
    expenses = PaycheckPeriodTotal(expenses),
)
