package com.bmbsolution.spenditos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val amount: Double,
    val period: String, // "weekly", "monthly", "yearly"
    val categoryIds: String, // JSON array
    val startDate: String?,
    val endDate: String?,
    val groupId: String?,
    val rollover: Boolean,
    val isSynced: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
