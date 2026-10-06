package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** `UpdateUserSettingsSchema`, packages/core/src/schemas/user.ts:160. */
@Serializable
data class UpdateOnboardingSettingsRequest(
    val displayName: String,
    val currency: String,
    val country: String,
    val language: String,
    val setupCompleted: Boolean,
)

/** Handler-only `GET /user/country-detection` response, packages/functions/src/user.ts:482. */
@Serializable
data class CountryDetectionResponse(
    val country: String? = null,
)

/** `CreateBulkCategoriesRequest`, packages/types/src/api/index.ts:620. */
@Serializable
data class OnboardingCategoryRequest(
    val name: String,
    val color: String,
    val isIncome: Boolean,
    val icon: String,
    val sortOrder: Int,
    val isSystemSuggestion: Boolean,
)
