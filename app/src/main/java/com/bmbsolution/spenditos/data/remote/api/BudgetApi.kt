package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface BudgetApi {
    @GET("budgets")
    suspend fun list(): ApiResponse<List<Budget>>

    @POST("budgets")
    suspend fun create(@Body request: BudgetCreateRequest): ApiResponse<Budget>

    @PATCH("budgets/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: BudgetUpdateRequest
    ): ApiResponse<Budget>

    @DELETE("budgets/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("budgets/{id}/history")
    suspend fun getHistory(@Path("id") id: String): ApiResponse<BudgetHistoryResponse>

    @GET("budgets/orphans")
    suspend fun getOrphans(): ApiResponse<List<OrphanBudget>>
}
