package com.spenditos.android.data.recurring

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.PatchMainIncomeTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.TransactionsPage
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RecurringApi {
    @GET("recurring")
    suspend fun list(): ApiEnvelope<List<RecurringTemplate>>

    @POST("recurring")
    suspend fun create(@Body body: CreateRecurringTemplateRequest): ApiEnvelope<RecurringTemplate>

    @PUT("recurring/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateRecurringTemplateRequest): ApiEnvelope<RecurringTemplate>

    @DELETE("recurring/{id}")
    suspend fun delete(@Path("id") id: String)

    /**
     * `GET /recurring/history?ids=a,b[&includeEntries=true]` (#403): one summary per requested bill
     * the user owns, keyed by id. At most 50 ids; `entries` only with a single id and the flag.
     * The commas stay literal, like iOS.
     */
    @GET("recurring/history")
    suspend fun history(
        @Query("ids", encoded = true) ids: String,
        @Query("includeEntries") includeEntries: Boolean? = null,
    ): ApiEnvelope<Map<String, BillHistorySummary>>

    /** The month's logged entries for the Income & bills summary strip and the Generate sheet. */
    @GET("transactions")
    suspend fun entries(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("limit") limit: Int = 200,
        @Query("cursor") cursor: String? = null,
    ): ApiEnvelope<TransactionsPage>

    @GET("user/settings")
    suspend fun settings(): ApiEnvelope<UserSettings>

    @PATCH("user/settings/main-income")
    suspend fun setMainIncome(@Body body: PatchMainIncomeTemplateRequest): ApiEnvelope<UserSettings>

    @POST("transactions/generate")
    suspend fun generate(@Body body: GenerateTransactionsRequest): ApiEnvelope<GenerateTransactionsResult>
}
