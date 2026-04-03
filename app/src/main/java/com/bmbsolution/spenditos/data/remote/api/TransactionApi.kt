package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface TransactionApi {
    @GET("transactions")
    suspend fun list(
        @QueryMap filters: Map<String, String> = emptyMap()
    ): TransactionListResponse

    @POST("transactions")
    suspend fun create(
        @Body request: TransactionCreateRequest
    ): ApiResponse<Transaction>

    @PATCH("transactions/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: TransactionUpdateRequest
    ): Transaction

    @DELETE("transactions/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("transactions/bulk")
    suspend fun bulkCreate(
        @Body requests: List<TransactionCreateRequest>
    ): List<Transaction>

    @POST("transactions/bulk-delete")
    suspend fun bulkDelete(@Body request: BulkDeleteRequest): ApiResponse<Unit>

    @POST("transactions/generate")
    suspend fun generateMonthly(
        @Body request: GenerateTransactionsRequest
    ): GenerateTransactionsResult
}
