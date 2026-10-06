package com.spenditos.android.data.recurring

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.PatchMainIncomeTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path

interface RecurringApi {
    @GET("recurring")
    suspend fun list(): ApiEnvelope<List<RecurringTemplate>>

    @POST("recurring")
    suspend fun create(@Body body: CreateRecurringTemplateRequest): ApiEnvelope<RecurringTemplate>

    @PUT("recurring/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateRecurringTemplateRequest): ApiEnvelope<RecurringTemplate>

    @DELETE("recurring/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("user/settings")
    suspend fun settings(): ApiEnvelope<UserSettings>

    @PATCH("user/settings/main-income")
    suspend fun setMainIncome(@Body body: PatchMainIncomeTemplateRequest): ApiEnvelope<UserSettings>

    @POST("transactions/generate")
    suspend fun generate(@Body body: GenerateTransactionsRequest): ApiEnvelope<GenerateTransactionsResult>
}
