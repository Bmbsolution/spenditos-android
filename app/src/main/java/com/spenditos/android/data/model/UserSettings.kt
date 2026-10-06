package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** `UserSettings` packages/types/src/domain/user.ts:126 (fields the app uses so far). */
@Serializable
data class UserSettings(
    val userId: String,
    val currency: String = "USD",
    val mainIncomeTemplateId: String? = null,
    val country: String? = null,
    val timezone: String = "UTC",
    val state: String? = null,
    val city: String? = null,
    val setupCompleted: Boolean = false,
    val displayName: String? = null,
    val premium: PremiumAccess? = null,
    val language: String? = null,
)

/** `PremiumAccess` domain/user.ts:52. Server truth for Pro (webhook-driven). */
@Serializable
data class PremiumAccess(
    val tier: String? = null,
    val active: Boolean = false,
    val expiresAt: String? = null,
)
