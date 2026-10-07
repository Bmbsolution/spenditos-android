package com.spenditos.android.data.gamification

import com.spenditos.android.core.network.apiCall
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
import javax.inject.Inject
import javax.inject.Singleton

interface GamificationDataSource {
    suspend fun getStreak(): StreakInfo
    suspend fun getStreakCalendar(): List<StreakCalendarDay>
    suspend fun useStreakFreeze(): StreakInfo
    suspend fun getPoints(): GamificationPointsBalance
    suspend fun getPointsHistory(limit: Int = 10): List<PointsTransaction>
    suspend fun getLevel(): GamificationLevelInfo
    suspend fun getTodayChallenge(): GamificationChallenge
    suspend fun getChallengeHistory(limit: Int = 10): List<GamificationChallenge>
    suspend fun updateChallengeProgress(challengeId: String, progress: Int): GamificationChallenge
    suspend fun completeChallenge(challengeId: String): GamificationChallenge
    suspend fun completeCurrentChallenge(): GamificationChallenge
    suspend fun getAchievementDefinitions(): List<AchievementDefinition>
    suspend fun getUserAchievements(): List<UserAchievement>
    suspend fun getAchievementProgress(achievementId: String): AchievementProgressDetail
    suspend fun getProgressSummary(): GamificationProgressSummary
}

@Singleton
class GamificationRepository @Inject constructor(
    private val api: GamificationApi,
    private val streakCache: StreakCache? = null,
) : GamificationDataSource {
    override suspend fun getStreak(): StreakInfo = apiCall { api.streak() }.also { remember(it) }

    override suspend fun getStreakCalendar(): List<StreakCalendarDay> = apiCall { api.streakCalendar() }

    override suspend fun useStreakFreeze(): StreakInfo = apiCall { api.useStreakFreeze() }.also { remember(it) }

    /** Best effort: the widget's streak; a storage failure never fails the fetch. */
    private suspend fun remember(info: StreakInfo) {
        runCatching { streakCache?.set(info.currentStreak) }
    }

    override suspend fun getPoints(): GamificationPointsBalance = apiCall { api.points() }

    override suspend fun getPointsHistory(limit: Int): List<PointsTransaction> = apiCall { api.pointsHistory(limit) }

    override suspend fun getLevel(): GamificationLevelInfo = apiCall { api.level() }

    override suspend fun getTodayChallenge(): GamificationChallenge = apiCall { api.todayChallenge() }

    override suspend fun getChallengeHistory(limit: Int): List<GamificationChallenge> = apiCall { api.challengeHistory(limit) }

    override suspend fun updateChallengeProgress(challengeId: String, progress: Int): GamificationChallenge =
        apiCall { api.updateChallengeProgress(challengeId, UpdateChallengeProgressRequest(progress)) }

    override suspend fun completeChallenge(challengeId: String): GamificationChallenge =
        apiCall { api.completeChallenge(challengeId) }

    override suspend fun completeCurrentChallenge(): GamificationChallenge = apiCall { api.completeCurrentChallenge() }

    override suspend fun getAchievementDefinitions(): List<AchievementDefinition> = apiCall { api.achievements() }

    override suspend fun getUserAchievements(): List<UserAchievement> = apiCall { api.userAchievements() }

    override suspend fun getAchievementProgress(achievementId: String): AchievementProgressDetail =
        apiCall { api.achievementProgress(achievementId) }

    override suspend fun getProgressSummary(): GamificationProgressSummary = apiCall { api.progressSummary() }
}
