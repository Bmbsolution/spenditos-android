package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

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
    @Serializable(with = TolerantPremiumAccessSerializer::class)
    val premium: PremiumAccess? = null,
    val language: String? = null,
    /** Server-decided Premium (store, grant or welcome). Absent on old servers; a malformed value decodes as null. */
    @Serializable(with = TolerantPremiumStatusSerializer::class)
    val premiumStatus: PremiumStatus? = null,
    @Serializable(with = TolerantWelcomePremiumSerializer::class)
    val welcomePremium: WelcomePremium? = null,
)

/**
 * `PremiumAccess` domain/user.ts. Server truth for Pro, fed by Paykit; [active] is the only part that decides
 * access. [tier] is a free string (tenant-configured) and [expiresAt] is display-only: a revoked subscription
 * keeps both. Everything below [expiresAt] is additive and optional, so old servers still decode.
 */
@Serializable
data class PremiumAccess(
    val tier: String? = null,
    val active: Boolean = false,
    val expiresAt: String? = null,
    @Serializable(with = TolerantPremiumEntitlementsSerializer::class)
    val entitlements: Map<String, PremiumEntitlementAccess>? = null,
    /** `apple_appstore` | `google_play` | `stripe` | a provider this version doesn't know; null when manual only. */
    val activeProvider: String? = null,
    @Serializable(with = TolerantPremiumPlanSerializer::class)
    val plan: PremiumPlan? = null,
    @Serializable(with = TolerantPremiumPendingChangeSerializer::class)
    val pendingChange: PremiumPendingChange? = null,
    val environment: String? = null,
    val version: Long? = null,
)

/** `PremiumEntitlementAccess` domain/user.ts. */
@Serializable
data class PremiumEntitlementAccess(
    val active: Boolean = false,
    val accessUntil: String? = null,
    val source: String? = null,
)

/** `PremiumPlan` domain/user.ts. */
@Serializable
data class PremiumPlan(
    val provider: String? = null,
    val productId: String? = null,
    /** ISO 8601 duration (`P1M`, `P1Y`) or `lifetime`. */
    val period: String? = null,
    val renewsAt: String? = null,
)

/** `PremiumPendingChange` domain/user.ts: a scheduled plan change, display-only until [effectiveAt]. */
@Serializable
data class PremiumPendingChange(
    val productId: String? = null,
    val tier: String? = null,
    val period: String? = null,
    val effectiveAt: String? = null,
    val kind: String? = null,
)

object TolerantPremiumAccessSerializer : NullOnErrorSerializer<PremiumAccess>(PremiumAccess.serializer())
object TolerantPremiumPlanSerializer : NullOnErrorSerializer<PremiumPlan>(PremiumPlan.serializer())
object TolerantPremiumPendingChangeSerializer : NullOnErrorSerializer<PremiumPendingChange>(PremiumPendingChange.serializer())
object TolerantPremiumEntitlementsSerializer :
    NullOnErrorSerializer<Map<String, PremiumEntitlementAccess>>(MapSerializer(String.serializer(), PremiumEntitlementAccess.serializer()))
