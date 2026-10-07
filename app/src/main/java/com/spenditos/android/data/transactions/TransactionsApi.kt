package com.spenditos.android.data.transactions

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateTransactionRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TransactionsApi {
    /** `ListTransactionsQuery` api/index.ts:316; `limit` 1…200 (transactions.ts:327). */
    @GET("transactions")
    suspend fun list(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("sortBy") sortBy: String = "date",
        @Query("sortOrder") sortOrder: String = "desc",
        @Query("limit") limit: Int = 200,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<TransactionsPage>

    @POST("transactions/bulk")
    suspend fun createBulk(@Body body: BulkCreateRequest): ApiEnvelope<BulkCreateResult>

    /** `PUT /transactions/{id}` (sst.config.ts:143); `UpdateTransactionRequest` api/index.ts:422. */
    @PUT("transactions/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateTransactionRequest): ApiEnvelope<Transaction>

    /** `DELETE /transactions/{id}` (sst.config.ts:147); 204 with no body. */
    @DELETE("transactions/{id}")
    suspend fun delete(@Path("id") id: String)
}
