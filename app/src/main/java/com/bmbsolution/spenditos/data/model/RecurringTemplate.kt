package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Recurring Template Models

@Serializable
data class RecurringTemplate(
    val id: String,
    val description: String,
    val amount: Double,
    val type: String, // "expense" | "income"
    val categoryId: String,
    val frequency: String, // "weekly", "monthly", "yearly"
    val dayOfMonth: Int? = null, // For monthly/yearly
    val dayOfWeek: Int? = null, // For weekly (1-7, Monday=1)
    val startDate: String,
    val endDate: String? = null,
    val groupId: String? = null,
    val isActive: Boolean = true,
    val lastGenerated: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class RecurringTemplateCreateRequest(
    val description: String,
    val amount: Double,
    val type: String,
    val categoryId: String,
    val frequency: String,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val startDate: String,
    val endDate: String? = null,
    val groupId: String? = null
)

@Serializable
data class RecurringTemplateUpdateRequest(
    val description: String? = null,
    val amount: Double? = null,
    val categoryId: String? = null,
    val frequency: String? = null,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val endDate: String? = null,
    val isActive: Boolean? = null
)
