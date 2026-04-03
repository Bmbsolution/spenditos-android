package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: String,
    val description: String,
    val amount: Double,
    val type: String, // "expense" | "income"
    val category: Category?,
    val date: String, // ISO 8601
    val groupId: String?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val splits: List<TransactionSplit>? = null,
    val isRecurring: Boolean = false,
    val recurringTemplateId: String? = null
)

@Serializable
data class TransactionSplit(
    val userId: String,
    val amount: Double,
    val percentage: Double? = null
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val type: String? = null, // "expense" | "income"
    val isSystem: Boolean = false
)

@Serializable
data class TransactionListResponse(
    val transactions: List<Transaction>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val nextCursor: String? = null,
    val hasMore: Boolean
)

@Serializable
data class TransactionCreateRequest(
    val description: String,
    val amount: Double,
    val type: String,
    val categoryId: String? = null,
    val date: String,
    val groupId: String? = null,
    val splits: List<TransactionSplit>? = null,
    val isRecurring: Boolean = false,
    val recurringTemplateId: String? = null
)

@Serializable
data class TransactionUpdateRequest(
    val description: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val categoryId: String? = null,
    val date: String? = null,
    val groupId: String? = null,
    val splits: List<TransactionSplit>? = null
)

@Serializable
data class BulkDeleteRequest(
    val ids: List<String>
)

@Serializable
data class GenerateTransactionsRequest(
    val targetDate: String? = null,
    val templateIds: List<String>? = null
)

@Serializable
data class GenerateTransactionsResult(
    val message: String? = null,
    val generated: Int,
    val skipped: Int,
    val errors: Int
)

@Serializable
data class ApiResponse<T>(
    val data: T,
    val details: Map<String, kotlinx.serialization.json.JsonElement>? = null
)

// Gamification details from transaction response
@Serializable
data class TransactionGamificationDetails(
    val pointsAwarded: Int = 0,
    val newBalance: Int = 0,
    val leveledUp: Boolean = false,
    val newLevel: Int? = null,
    val newLevelTitle: String? = null,
    val streakUpdated: Boolean = false,
    val currentStreak: Int? = null,
    val achievementsUnlocked: List<UnlockedAchievementInfo> = emptyList()
)

@Serializable
data class UnlockedAchievementInfo(
    val id: String,
    val name: String,
    val icon: String,
    val points: Int
)
