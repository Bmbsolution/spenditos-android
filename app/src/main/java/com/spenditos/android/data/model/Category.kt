package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** `Category` packages/types/src/domain/category.ts. */
@Serializable
data class Category(
    val id: String,
    val userId: String,
    val name: String,
    val color: String,
    val icon: String? = null,
    val isIncome: Boolean,
    val sortOrder: Int = 0,
    val isSystemSuggestion: Boolean = false,
)
