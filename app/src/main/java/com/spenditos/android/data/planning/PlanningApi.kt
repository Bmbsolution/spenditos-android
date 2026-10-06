package com.spenditos.android.data.planning

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.BulkCreateRequest
import com.spenditos.android.data.model.BulkCreateResult
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.DeleteMonthlyPlanDraftResult
import com.spenditos.android.data.model.MonthlyPlanData
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanDraftLookup
import com.spenditos.android.data.model.MonthlyPlanTransactionUpdate
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.SkipMonthlyPlanOccurrenceRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.SaveMonthlyPlanDraftRequest
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.TransactionsPage
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface PlanningApi {
    @GET("planning/month")
    suspend fun monthlyPlan(@Query("month") month: String): ApiEnvelope<MonthlyPlanData>

    @GET("planning/draft")
    suspend fun draft(@Query("month") month: String): ApiEnvelope<MonthlyPlanDraftLookup>

    @PUT("planning/draft")
    suspend fun saveDraft(@Body body: SaveMonthlyPlanDraftRequest): ApiEnvelope<MonthlyPlanDraftData>

    @DELETE("planning/draft")
    suspend fun deleteDraft(@Query("month") month: String): ApiEnvelope<DeleteMonthlyPlanDraftResult>

    @GET("categories")
    suspend fun categories(): ApiEnvelope<List<Category>>

    @GET("recurring")
    suspend fun recurring(): ApiEnvelope<List<RecurringTemplate>>

    @GET("transactions")
    suspend fun transactions(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("sortBy") sortBy: String = "date",
        @Query("sortOrder") sortOrder: String = "asc",
        @Query("limit") limit: Int = 200,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<TransactionsPage>

    @POST("recurring/{id}/skip")
    suspend fun skipOccurrence(
        @Path("id") templateId: String,
        @Body body: SkipMonthlyPlanOccurrenceRequest,
    ): ApiEnvelope<RecurringTemplate>

    @POST("transactions/generate")
    suspend fun generate(@Body body: GenerateTransactionsRequest): ApiEnvelope<GenerateTransactionsResult>

    @PUT("transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") transactionId: String,
        @Body body: MonthlyPlanTransactionUpdate,
    ): ApiEnvelope<Transaction>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") transactionId: String)

    @POST("transactions/bulk")
    suspend fun createBulk(@Body body: BulkCreateRequest): ApiEnvelope<BulkCreateResult>
}
