package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Gamification Models

@Serializable
data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val type: String, // "daily", "weekly", "special"
    val points: Int,
    val progress: Int,
    val target: Int,
    val completed: Boolean,
    val expiresAt: String? = null,
    val categoryId: String? = null
)

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val points: Int,
    val unlockedAt: String? = null,
    val progress: AchievementProgress? = null
)

@Serializable
data class AchievementProgress(
    val current: Int,
    val target: Int,
    val percentage: Double
)

@Serializable
data class PointsHistoryEntry(
    val id: String,
    val points: Int,
    val balance: Int,
    val reason: String,
    val createdAt: String
)

@Serializable
data class UserLevel(
    val level: Int,
    val title: String,
    val pointsToNextLevel: Int,
    val currentPoints: Int,
    val totalPoints: Int
)

@Serializable
data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivityDate: String? = null,
    val nextMilestone: Int? = null,
    val daysUntilNextMilestone: Int? = null
)

@Serializable
data class StreakCalendarResponse(
    val year: Int,
    val month: Int,
    val days: List<StreakDay>
)

@Serializable
data class StreakDay(
    val day: Int,
    val hasActivity: Boolean,
    val isStreakDay: Boolean
)

@Serializable
data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDays: Int,
    val calendar: StreakCalendarResponse
)

@Serializable
data class AllAchievementsResponse(
    val achievements: List<AchievementDefinition>,
    val userAchievements: List<Achievement>
)

@Serializable
data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val points: Int,
    val category: String,
    val rarity: String, // "common", "rare", "epic", "legendary"
    val condition: AchievementCondition
)

@Serializable
data class AchievementCondition(
    val type: String, // "transaction_count", "streak_days", "category_spend", "points_earned"
    val target: Int,
    val categoryId: String? = null
)
