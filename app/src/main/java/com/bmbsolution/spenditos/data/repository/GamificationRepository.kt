package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.DashboardApi
import com.bmbsolution.spenditos.data.remote.api.GamificationApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamificationRepository @Inject constructor(
    private val gamificationApi: GamificationApi,
    private val dashboardApi: DashboardApi
) {
    suspend fun getDailyChallenge(): Result<Challenge> {
        return try {
            val response = gamificationApi.getDailyChallenge()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChallenges(): Result<List<Challenge>> {
        return try {
            val response = gamificationApi.getChallenges()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMyAchievements(): Result<List<Achievement>> {
        return try {
            val response = gamificationApi.getMyAchievements()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllAchievements(): Result<AllAchievementsResponse> {
        return try {
            Result.success(gamificationApi.getAllAchievements())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPointsHistory(): Result<List<PointsHistoryEntry>> {
        return try {
            val response = gamificationApi.getPointsHistory()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStreak(): Result<StreakInfo> {
        return try {
            val response = gamificationApi.getStreak()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStreakCalendar(year: Int? = null, month: Int? = null): Result<StreakCalendarResponse> {
        return try {
            val response = gamificationApi.getStreakCalendar(year, month)
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLevel(): Result<UserLevel> {
        return try {
            val response = gamificationApi.getLevel()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeChallenge(challengeId: String): Result<Unit> {
        return try {
            gamificationApi.completeChallenge(challengeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDashboard(): Result<DashboardReport> {
        return try {
            val response = dashboardApi.getDashboard()
            Result.success(response.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
