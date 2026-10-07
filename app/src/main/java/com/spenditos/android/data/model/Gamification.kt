@file:kotlinx.serialization.UseSerializers(com.spenditos.android.data.model.LocalDateIsoSerializer::class)

package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.LocalDate

/** `StreakInfo`, packages/types/src/domain/gamification/streak.ts:74; response fallback in packages/functions/src/api/gamification/streak.ts:54. */
@Serializable
data class StreakInfo(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastEntryDate: LocalDate? = null,
    val streakFreezes: Int = 0,
)

/** `CalendarDay`, packages/types/src/domain/gamification/streak.ts:49; returned as an array by packages/core/src/services/gamification/StreakService.ts:173. */
@Serializable
data class StreakCalendarDay(
    val date: LocalDate,
    val hasEntry: Boolean = false,
    val isStreakDay: Boolean = false,
    val freezeUsed: Boolean = false,
)

/** `PointsBalance`, packages/types/src/domain/gamification/points.ts:109; empty response in packages/functions/src/api/gamification/points.ts:56. */
@Serializable
data class GamificationPointsBalance(
    val totalEarned: Int = 0,
    val totalSpent: Int = 0,
    val available: Int = 0,
    val level: Int = 1,
    val pointsToNextLevel: Int = 100,
    val progressToNextLevel: Double = 0.0,
)

/** `PointsTransaction`, packages/types/src/domain/gamification/points.ts:69. */
@Serializable
data class PointsTransaction(
    val id: String,
    val amount: Double,
    val action: String,
    val balanceAfter: Int,
    val timestamp: String,
    val metadata: Map<String, JsonElement> = emptyMap(),
)

/** The handler's level response is assembled in packages/functions/src/api/gamification/points.ts:137. */
@Serializable
data class GamificationLevelInfo(
    val level: Int,
    val title: String,
    val totalPoints: Int,
    val pointsAtCurrentLevel: Int,
    val pointsForNextLevel: Int,
    val progressPercentage: Double,
    val unlocks: List<String> = emptyList(),
)

/** `Challenge`, packages/types/src/domain/gamification/challenge.ts:92; today's handler calls `generateWeeklyChallenge` in packages/functions/src/api/gamification/challenges.ts:72. */
@Serializable
data class GamificationChallenge(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val difficulty: String? = null,
    val target: Int,
    val current: Int = 0,
    val completed: Boolean = false,
    val date: LocalDate? = null,
    val reward: Int = 0,
    val completedAt: String? = null,
    val userId: String? = null,
    val expiresAt: String? = null,
    val period: String? = null,
    val family: String? = null,
    val personalized: Boolean? = null,
    val categoryId: String? = null,
)

/** `Achievement` definitions, packages/types/src/domain/gamification/achievement.ts:132. */
@Serializable
data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val points: Int,
    val category: String,
    val requirement: AchievementRequirement,
    val repeatable: Boolean? = null,
    val minimumLevel: Int? = null,
)

/** `AchievementRequirement`, packages/types/src/domain/gamification/achievement.ts:110. */
@Serializable
data class AchievementRequirement(
    val type: String,
    val value: Int,
    val period: String? = null,
)

/** Handler-enriched locked and unlocked shape from packages/functions/src/api/gamification/achievements.ts:66-80. */
@Serializable
data class UserAchievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: String,
    val target: Int,
    val progress: Int = 0,
    val unlockedAt: String? = null,
)

/** Handler response from packages/functions/src/api/gamification/achievements.ts:128-132. */
@Serializable
data class AchievementProgressDetail(
    val achievementId: String,
    val isUnlocked: Boolean,
    val progress: Int = 0,
    val unlockedAt: String? = null,
)

/** `UserProgressSummary`, packages/types/src/domain/gamification/progress.ts:69. */
@Serializable
data class GamificationProgressSummary(
    val userId: String,
    val points: ProgressPoints,
    val streak: ProgressStreak,
    val challenge: ProgressChallenge? = null,
    val goals: List<ProgressGoal> = emptyList(),
    val achievements: ProgressAchievementCounts,
    val generatedAt: String,
)

/** The `points` member of `UserProgressSummary`, packages/types/src/domain/gamification/progress.ts:78-84. */
@Serializable
data class ProgressPoints(
    val available: Int,
    val totalEarned: Int,
    val level: Int,
    val pointsToNextLevel: Int,
    val progressToNextLevel: Double,
)

/** The `streak` member of `UserProgressSummary`, packages/types/src/domain/gamification/progress.ts:89-94. */
@Serializable
data class ProgressStreak(
    val currentStreak: Int,
    val longestStreak: Int,
    val hasEntryToday: Boolean,
    val streakFreezes: Int,
)

/** The `challenge` member of `UserProgressSummary`, packages/types/src/domain/gamification/progress.ts:99-110. */
@Serializable
data class ProgressChallenge(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val difficulty: String,
    val current: Int,
    val target: Int,
    val completed: Boolean,
    val reward: Int,
    val expiresAt: String,
)

/** `ProgressGoal`, packages/types/src/domain/gamification/progress.ts:14. */
@Serializable
data class ProgressGoal(
    val id: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
    val percentage: Int,
    val type: String,
    val entityId: String? = null,
    val pointsReward: Int? = null,
)

/** The `achievements` counts member of `UserProgressSummary`, packages/types/src/domain/gamification/progress.ts:120-124. */
@Serializable
data class ProgressAchievementCounts(
    val unlocked: Int,
    val total: Int,
    val recentUnlocks: List<String> = emptyList(),
)

/** Request body from the iOS update call; the backend currently rejects client-managed progress in packages/functions/src/api/gamification/challenges.ts:105-117. */
@Serializable
data class UpdateChallengeProgressRequest(val progress: Int)
