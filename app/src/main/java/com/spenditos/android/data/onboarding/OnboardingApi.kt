package com.spenditos.android.data.onboarding

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CountryDetectionResponse
import com.spenditos.android.data.model.OnboardingCategoryRequest
import com.spenditos.android.data.model.UpdateOnboardingSettingsRequest
import com.spenditos.android.data.model.UserSettings
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface OnboardingApi {
    @GET("user/settings")
    suspend fun settings(): ApiEnvelope<UserSettings>

    @PUT("user/settings")
    suspend fun updateSettings(@Body body: UpdateOnboardingSettingsRequest): ApiEnvelope<UserSettings>

    @GET("user/country-detection")
    suspend fun detectCountry(): ApiEnvelope<CountryDetectionResponse>

    @GET("categories")
    suspend fun categories(): ApiEnvelope<List<Category>>

    @POST("categories/batch")
    suspend fun createCategories(@Body body: List<OnboardingCategoryRequest>): ApiEnvelope<List<Category>>
}
