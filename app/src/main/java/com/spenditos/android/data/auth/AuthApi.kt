package com.spenditos.android.data.auth

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.LoginRequest
import com.spenditos.android.data.model.LoginResponse
import com.spenditos.android.data.model.LogoutRequest
import com.spenditos.android.data.model.RefreshRequest
import com.spenditos.android.data.model.RefreshResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** Public auth routes under `/auth/` (no bearer token). */
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<LoginResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest)
}
