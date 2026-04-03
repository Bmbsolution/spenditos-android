package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.AuthApi
import com.bmbsolution.spenditos.data.local.preferences.AuthPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences
) {
    val isAuthenticated: Flow<Boolean> = flow {
        emit(authPreferences.getAccessToken() != null)
    }

    suspend fun login(email: String, password: String): Result<AuthTokens> {
        return try {
            val response = authApi.login(LoginRequest(email, password))
            val tokens = response.data
            authPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
            Result.success(tokens)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, name: String? = null): Result<AuthTokens> {
        return try {
            val response = authApi.register(RegisterRequest(email, password, name))
            val tokens = response.data
            authPreferences.saveTokens(tokens.accessToken, tokens.refreshToken)
            Result.success(tokens)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            authApi.logout()
            authPreferences.clear()
            Result.success(Unit)
        } catch (e: Exception) {
            authPreferences.clear()
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<String> {
        return try {
            val refreshToken = authPreferences.getRefreshToken()
                ?: return Result.failure(Exception("No refresh token"))
            val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))
            authPreferences.saveTokens(response.accessToken, response.refreshToken)
            Result.success(response.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = authApi.getCurrentUser()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            authApi.forgotPassword(ForgotPasswordRequest(email))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            authApi.resetPassword(ResetPasswordRequest(token, newPassword))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            authApi.changePassword(PasswordChangeRequest(currentPassword, newPassword))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
