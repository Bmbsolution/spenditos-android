package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** `CreateCategoryRequest` and `UpdateCategoryRequest`, packages/types/src/api/index.ts:571-601. */
@Serializable
data class CategoryCreateRequest(
    val name: String,
    val color: String,
    val isIncome: Boolean,
    val icon: String? = null,
)

/** `UpdateCategoryRequest`, validated by packages/core/src/schemas/category.ts:53-63. */
@Serializable
data class CategoryUpdateRequest(
    val name: String? = null,
    val color: String? = null,
    val isIncome: Boolean? = null,
    val icon: String? = null,
)
