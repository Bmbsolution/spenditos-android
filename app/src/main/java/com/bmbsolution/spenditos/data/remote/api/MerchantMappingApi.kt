package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface MerchantMappingApi {
    @GET("merchant-mappings")
    suspend fun list(): ApiResponse<List<MerchantMapping>>

    @POST("merchant-mappings")
    suspend fun create(
        @Body request: MerchantMappingCreateRequest
    ): ApiResponse<MerchantMapping>

    @PATCH("merchant-mappings/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: MerchantMappingUpdateRequest
    ): ApiResponse<MerchantMapping>

    @DELETE("merchant-mappings/{id}")
    suspend fun delete(@Path("id") id: String)
}
