package com.spenditos.android.data.reports

import com.spenditos.android.core.network.apiCall
import com.spenditos.android.data.model.DailyAggregate
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.PaycheckPeriodsData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReportsDataSource {
    suspend fun getDashboard(startDate: LocalDate, endDate: LocalDate): DashboardData
    suspend fun getDailyAggregates(startDate: LocalDate, endDate: LocalDate): List<DailyAggregate>
    suspend fun getPaycheckPeriods(startDate: LocalDate, endDate: LocalDate): PaycheckPeriodsData
    fun currency(): Flow<String>
}

/** Network-only reports. These results are held in screen state and are not written to Room. */
class ReportsRepository(
    private val api: ReportsApi,
    private val currencyFlow: () -> Flow<String>,
) : ReportsDataSource {
    override suspend fun getDashboard(startDate: LocalDate, endDate: LocalDate): DashboardData =
        apiCall { api.dashboard(startDate.toString(), endDate.toString()) }

    override suspend fun getDailyAggregates(startDate: LocalDate, endDate: LocalDate): List<DailyAggregate> =
        apiCall { api.dailyAggregates(startDate.toString(), endDate.toString()) }.data

    override suspend fun getPaycheckPeriods(startDate: LocalDate, endDate: LocalDate): PaycheckPeriodsData =
        apiCall { api.paycheckPeriods(startDate.toString(), endDate.toString()) }

    override fun currency(): Flow<String> = currencyFlow()
}
