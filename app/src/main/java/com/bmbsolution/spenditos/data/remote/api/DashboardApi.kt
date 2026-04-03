package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface DashboardApi {
    @GET("dashboard")
    suspend fun getDashboard(
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): ApiResponse<DashboardReport>
}
