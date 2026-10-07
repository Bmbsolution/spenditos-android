package com.spenditos.android.data.gamification

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.AchievementDefinition
import com.spenditos.android.data.model.AchievementProgressDetail
import com.spenditos.android.data.model.GamificationChallenge
import com.spenditos.android.data.model.GamificationLevelInfo
import com.spenditos.android.data.model.GamificationPointsBalance
import com.spenditos.android.data.model.GamificationProgressSummary
import com.spenditos.android.data.model.PointsTransaction
import com.spenditos.android.data.model.StreakCalendarDay
import com.spenditos.android.data.model.StreakInfo
import com.spenditos.android.data.model.UpdateChallengeProgressRequest
import com.spenditos.android.data.model.UserAchievement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GamificationApi {
    @GET("user/streak")
    suspend fun streak(): ApiEnvelope<StreakInfo>

    @GET("user/streak/calendar")
    suspend fun streakCalendar(@Query("limit") limit: Int = 30): ApiEnvelope<List<StreakCalendarDay>>

    @POST("user/streak/freeze")
    suspend fun useStreakFreeze(): ApiEnvelope<StreakInfo>

    @GET("user/points")
    suspend fun points(): ApiEnvelope<GamificationPointsBalance>

    @GET("user/points/history")
    suspend fun pointsHistory(@Query("limit") limit: Int = 10): ApiEnvelope<List<PointsTransaction>>

    @GET("user/level")
    suspend fun level(): ApiEnvelope<GamificationLevelInfo>

    @GET("user/challenges/today")
    suspend fun todayChallenge(): ApiEnvelope<GamificationChallenge>

    @GET("user/challenges/history")
    suspend fun challengeHistory(@Query("limit") limit: Int = 10): ApiEnvelope<List<GamificationChallenge>>

    @POST("user/challenges/{challengeId}/progress")
    suspend fun updateChallengeProgress(
        @Path("challengeId") challengeId: String,
        @Body request: UpdateChallengeProgressRequest,
    ): ApiEnvelope<GamificationChallenge>

    @POST("user/challenges/{challengeId}/complete")
    suspend fun completeChallenge(@Path("challengeId") challengeId: String): ApiEnvelope<GamificationChallenge>

    @POST("user/challenges/complete")
    suspend fun completeCurrentChallenge(): ApiEnvelope<GamificationChallenge>

    @GET("achievements")
    suspend fun achievements(): ApiEnvelope<List<AchievementDefinition>>

    @GET("user/achievements")
    suspend fun userAchievements(): ApiEnvelope<List<UserAchievement>>

    @GET("user/achievements/{achievementId}")
    suspend fun achievementProgress(@Path("achievementId") achievementId: String): ApiEnvelope<AchievementProgressDetail>

    @GET("user/progress-summary")
    suspend fun progressSummary(): ApiEnvelope<GamificationProgressSummary>
}
