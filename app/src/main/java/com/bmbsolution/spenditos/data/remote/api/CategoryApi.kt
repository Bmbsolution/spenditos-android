package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface CategoryApi {
    @GET("categories")
    suspend fun list(): ApiResponse<List<Category>>

    @POST("categories")
    suspend fun create(@Body category: Map<String, String>): ApiResponse<Category>

    @PATCH("categories/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body updates: Map<String, String>
    ): ApiResponse<Category>

    @DELETE("categories/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("categories/reorder")
    suspend fun reorder(@Body categoryIds: List<String>): ApiResponse<Unit>
}
