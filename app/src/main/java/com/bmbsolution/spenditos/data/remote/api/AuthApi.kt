package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthTokens>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthTokens>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): RefreshTokenResponse

    @POST("auth/password-change")
    suspend fun changePassword(@Body request: PasswordChangeRequest): ApiResponse<Unit>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiResponse<Unit>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiResponse<Unit>

    @GET("auth/me")
    suspend fun getCurrentUser(): ApiResponse<User>
}
