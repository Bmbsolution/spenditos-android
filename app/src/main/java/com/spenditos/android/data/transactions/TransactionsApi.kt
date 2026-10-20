package com.spenditos.android.data.transactions

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateTransactionRequest
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

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

    /**
     * `POST /transactions/bulk` with the queued item. [sessionBinding] tags the request with the
     * account and session generation that owns the write, so the auth interceptor can refuse the
     * request (and its 401 retry) after an account switch. Null on non-queued calls.
     */
    @POST("transactions/bulk")
    suspend fun createBulk(
        @Body body: BulkCreateRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<BulkCreateResult>

    /**
     * `POST /transactions` (handler `createTransactionHandlerImpl`; schema `CreateTransactionSchema`):
     * required `amount`, `date`, `categoryId`; this client also sends `description` and `debtId`.
     * ONLY for a debt's extra payment (docs/DECISIONS.md D22): the bulk endpoint cannot carry `debtId`.
     * Unlike bulk this route has no retry protection, so a repeated request creates a second
     * transaction; callers send it once per user tap and never resend it automatically.
     * [sessionBinding] works as on [createBulk].
     */
    @POST("transactions")
    suspend fun createSingle(
        @Body body: CreateTransactionRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<Transaction>

    /** `PUT /transactions/{id}` (sst.config.ts:143); `UpdateTransactionRequest` api/index.ts:422. */
    @PUT("transactions/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateTransactionRequest): ApiEnvelope<Transaction>

    /** `DELETE /transactions/{id}` (sst.config.ts:147); 204 with no body. */
    @DELETE("transactions/{id}")
    suspend fun delete(@Path("id") id: String)

    /**
     * `POST /transactions/bulk-delete` (sst.config.ts:145), at most 100 ids. 204 when all were deleted;
     * 207 with `details.failedTransactionIds` on a partial failure, so the body is read raw.
     */
    @POST("transactions/bulk-delete")
    suspend fun bulkDelete(@Body body: BulkDeleteRequest): retrofit2.Response<okhttp3.ResponseBody>
}

@kotlinx.serialization.Serializable
data class BulkDeleteRequest(val transactionIds: List<String>)

/**
 * Body of `POST /transactions` (`CreateTransactionSchema` without `userId`, which the server takes
 * from the token). Null fields are omitted from the JSON.
 */
@Serializable
data class CreateTransactionRequest(
    val amount: Double,
    /** `YYYY-MM-DD`. */
    val date: String,
    val categoryId: String,
    val description: String? = null,
    /** The debt this expense pays toward (docs/features/debts.md). */
    val debtId: String? = null,
)
