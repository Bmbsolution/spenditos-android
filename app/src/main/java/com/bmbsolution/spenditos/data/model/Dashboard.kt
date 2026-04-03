package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Dashboard Models

@Serializable
data class DashboardReport(
    val dateRange: DashboardDateRange,
    val summary: DashboardSummary,
    val spendingByCategory: List<CategorySpending>,
    val game: GameSummary,
    val recentTransactions: List<Transaction>
)

@Serializable
data class DashboardDateRange(
    val start: String,
    val end: String,
    val label: String
)

@Serializable
data class DashboardSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val netAmount: Double,
    val transactionCount: Int,
    val averageTransaction: Double
)

@Serializable
data class CategorySpending(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val icon: String? = null,
    val color: String? = null,
    val amount: Double,
    val percentage: Double,
    val transactionCount: Int
)

@Serializable
data class GameSummary(
    val level: Int,
    val levelTitle: String,
    val currentPoints: Int,
    val totalPoints: Int,
    val pointsToNextLevel: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val recentAchievements: List<UserAchievementEntry>
)

@Serializable
data class UserAchievementEntry(
    val id: String,
    val name: String,
    val icon: String,
    val points: Int,
    val unlockedAt: String
)

// MARK: - Reports Models

@Serializable
data class DailyAggregatesResponse(
    val aggregates: List<DailyAggregate>,
    val summary: DailyAggregatesSummary
)

@Serializable
data class DailyAggregate(
    val id: String,
    val date: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val netAmount: Double,
    val transactionCount: Int,
    val categories: List<DailyCategoryAmount>
)

@Serializable
data class DailyCategoryAmount(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val amount: Double,
    val transactionCount: Int
)

@Serializable
data class DailyAggregatesSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val averageDailyIncome: Double,
    val averageDailyExpense: Double,
    val transactionCount: Int,
    val dayCount: Int
)

@Serializable
data class PaycheckPeriodsResponse(
    val periods: List<PaycheckPeriod>,
    val summary: PaycheckSummary
)

@Serializable
data class PaycheckPeriod(
    val id: String,
    val startDate: String,
    val endDate: String,
    val income: Double,
    val expense: Double,
    val savings: Double,
    val savingsRate: Double,
    val categories: List<PaycheckCategoryAmount>
)

@Serializable
data class PaycheckCategoryAmount(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val amount: Double,
    val percentage: Double
)

@Serializable
data class PaycheckSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val totalSavings: Double,
    val averageSavingsRate: Double,
    val periodCount: Int
)
