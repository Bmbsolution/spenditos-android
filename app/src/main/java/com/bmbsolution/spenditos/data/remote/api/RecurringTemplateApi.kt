package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface RecurringTemplateApi {
    @GET("recurring-templates")
    suspend fun list(): ApiResponse<List<RecurringTemplate>>

    @POST("recurring-templates")
    suspend fun create(
        @Body request: RecurringTemplateCreateRequest
    ): ApiResponse<RecurringTemplate>

    @PATCH("recurring-templates/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: RecurringTemplateUpdateRequest
    ): ApiResponse<RecurringTemplate>

    @DELETE("recurring-templates/{id}")
    suspend fun delete(@Path("id") id: String)
}
