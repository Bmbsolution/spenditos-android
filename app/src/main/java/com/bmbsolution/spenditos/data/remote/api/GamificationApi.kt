package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface GamificationApi {
    @GET("user/challenges/today")
    suspend fun getDailyChallenge(): ApiResponse<Challenge>

    @GET("user/challenges")
    suspend fun getChallenges(): ApiResponse<List<Challenge>>

    @GET("user/achievements")
    suspend fun getMyAchievements(): ApiResponse<List<Achievement>>

    @GET("achievements")
    suspend fun getAllAchievements(): AllAchievementsResponse

    @GET("user/points-history")
    suspend fun getPointsHistory(): ApiResponse<List<PointsHistoryEntry>>

    @GET("user/streak")
    suspend fun getStreak(): ApiResponse<StreakInfo>

    @GET("user/streak/calendar")
    suspend fun getStreakCalendar(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): ApiResponse<StreakCalendarResponse>

    @GET("user/level")
    suspend fun getLevel(): ApiResponse<UserLevel>

    @POST("challenges/{id}/complete")
    suspend fun completeChallenge(@Path("id") challengeId: String): ApiResponse<Unit>
}
