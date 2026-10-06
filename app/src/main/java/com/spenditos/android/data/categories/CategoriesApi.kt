package com.spenditos.android.data.categories

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.Category
import retrofit2.http.GET

interface CategoriesApi {
    @GET("categories")
    suspend fun list(): ApiEnvelope<List<Category>>
}
