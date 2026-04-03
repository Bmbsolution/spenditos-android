package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Budget(
    val id: String,
    val name: String,
    val amount: Double,
    val period: String, // "weekly", "monthly", "yearly"
    val categoryIds: List<String>,
    val startDate: String? = null,
    val endDate: String? = null,
    val groupId: String? = null,
    val rollover: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class BudgetHistoryEntry(
    val id: String,
    val budgetId: String,
    val periodStart: String,
    val periodEnd: String,
    val budgetedAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val percentageUsed: Double,
    val status: String, // "under", "near", "over"
    val categoryBreakdown: List<BudgetHistoryCategory>
)

@Serializable
data class BudgetHistoryResponse(
    val history: List<BudgetHistoryEntry>
)

@Serializable
data class BudgetHistoryCategory(
    val categoryId: String,
    val categoryName: String,
    val budgetedAmount: Double,
    val spentAmount: Double
)

@Serializable
data class OrphanBudget(
    val id: String,
    val name: String,
    val amount: Double,
    val categoryIds: List<String>,
    val reason: String // "no_transactions", "no_matching_categories"
)

@Serializable
data class BudgetCreateRequest(
    val name: String,
    val amount: Double,
    val period: String,
    val categoryIds: List<String>,
    val startDate: String? = null,
    val endDate: String? = null,
    val groupId: String? = null,
    val rollover: Boolean = false
)

@Serializable
data class BudgetUpdateRequest(
    val name: String? = null,
    val amount: Double? = null,
    val period: String? = null,
    val categoryIds: List<String>? = null,
    val rollover: Boolean? = null
)

@Serializable
data class BudgetAdherenceReport(
    val overallScore: Double,
    val totalBudgeted: Double,
    val totalSpent: Double,
    val periods: List<BudgetPeriod>,
    val categoryAdherence: List<CategoryAdherence>,
    val insights: List<BudgetInsight>
)

@Serializable
data class BudgetPeriod(
    val period: String,
    val budgeted: Double,
    val spent: Double,
    val adherence: Double
)

@Serializable
data class CategoryAdherence(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val budgetedAmount: Double,
    val spentAmount: Double,
    val adherencePercentage: Double,
    val status: String // "under", "on_track", "over"
)

@Serializable
data class BudgetInsight(
    val id: String,
    val type: String, // "overspending", "underspending", "trend", "suggestion"
    val title: String,
    val description: String,
    val severity: String, // "info", "warning", "critical"
    val relatedCategoryId: String? = null
)
