package com.spenditos.android.data.reports

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.DailyAggregatesPayload
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.PaycheckPeriodsData
import retrofit2.http.GET
import retrofit2.http.Query

interface ReportsApi {
    /** `GetDashboardQuery` / `GetDashboardResponse` api/index.ts:717, 792. */
    @GET("reports/dashboard")
    suspend fun dashboard(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): ApiEnvelope<DashboardData>

    /** `GetDailyAggregatesResponse` api/index.ts:921; handler returns a nested `data` at reports.ts:450. */
    @GET("reports/daily-aggregates")
    suspend fun dailyAggregates(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): ApiEnvelope<DailyAggregatesPayload>

    /** `GetPaycheckPeriodsQuery` / `GetPaycheckPeriodsResponse` api/index.ts:799, 886. */
    @GET("reports/paycheck-periods")
    suspend fun paycheckPeriods(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): ApiEnvelope<PaycheckPeriodsData>
}
