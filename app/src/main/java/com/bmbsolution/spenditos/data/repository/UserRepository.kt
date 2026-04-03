package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.local.preferences.AuthPreferences
import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.AuthApi
import com.bmbsolution.spenditos.data.remote.api.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userApi: UserApi,
    private val authApi: AuthApi,
    private val authPreferences: AuthPreferences
) {
    fun getUser(): Flow<Result<User>> = flow {
        try {
            val response = userApi.getUser()
            emit(Result.success(response.data))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun updateSettings(request: UserSettingsUpdateRequest): Result<UserSettings> {
        return try {
            val response = userApi.updateSettings(request)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMainIncome(income: Double): Result<User> {
        return try {
            val response = userApi.updateMainIncome(MainIncomePatchRequest(income))
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detectCountry(): Result<CountryDetectionResponse> {
        return try {
            val response = userApi.detectCountry()
            Result.success(response)
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
            // Even if API call fails, clear local data
            authPreferences.clear()
            Result.success(Unit)
        }
    }
}
