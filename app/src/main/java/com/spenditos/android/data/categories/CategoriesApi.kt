package com.spenditos.android.data.categories

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CategoryCreateRequest
import com.spenditos.android.data.model.CategoryUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path

interface CategoriesApi {
    @GET("categories")
    suspend fun list(): ApiEnvelope<List<Category>>

    @POST("categories")
    suspend fun create(@Body body: CategoryCreateRequest): ApiEnvelope<Category>

    @PUT("categories/{id}")
    suspend fun update(@Path("id") id: String, @Body body: CategoryUpdateRequest): ApiEnvelope<Category>

    @DELETE("categories/{id}")
    suspend fun delete(@Path("id") id: String)
}
