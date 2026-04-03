package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface ReportsApi {
    @GET("reports/daily-aggregates")
    suspend fun getDailyAggregates(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<DailyAggregatesResponse>

    @GET("reports/paycheck-periods")
    suspend fun getPaycheckPeriods(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<PaycheckPeriodsResponse>

    @GET("reports/budget-adherence")
    suspend fun getBudgetAdherence(): ApiResponse<BudgetAdherenceReport>
}
