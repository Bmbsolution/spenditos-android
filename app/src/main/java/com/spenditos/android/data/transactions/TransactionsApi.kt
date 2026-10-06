package com.spenditos.android.data.transactions

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.TransactionsPage
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}
