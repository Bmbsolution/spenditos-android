package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface UserApi {
    @GET("user")
    suspend fun getUser(): ApiResponse<User>

    @PATCH("user")
    suspend fun updateUser(@Body updates: Map<String, String>): ApiResponse<User>

    @GET("user/country-detection")
    suspend fun detectCountry(): CountryDetectionResponse

    @PATCH("user/settings")
    suspend fun updateSettings(@Body request: UserSettingsUpdateRequest): ApiResponse<UserSettings>

    @PATCH("user/main-income")
    suspend fun updateMainIncome(@Body request: MainIncomePatchRequest): ApiResponse<User>
}
