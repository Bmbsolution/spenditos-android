package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `POST /ws-ticket` response data (`packages/functions/src/websocket/ws-ticket.ts:155-160`). */
@Serializable
data class WsTicketResponse(
    val ticket: String,
    val expiresIn: Int,
)

/** WebSocket envelope shape (`packages/types/src/events/data-changed.ts:132-138`). */
@Serializable
data class RealtimeEnvelope(
    val type: String,
    val data: JsonObject,
)

/** Broadcast payload (`DataChangedEventPayload`, `packages/types/src/events/data-changed.ts:115-130`). */
@Serializable
data class DataChangedEvent(
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String? = null,
    val mutationType: String,
    val occurredAt: String,
    val version: Int,
)

/** WebSocket gamification payload (`packages/functions/src/workers/gamification-worker.ts:217-227`). */
@Serializable
data class GamificationUpdate(
    val points: GamificationPoints? = null,
    val streak: GamificationStreak? = null,
    val level: GamificationLevel? = null,
    val newAchievements: List<GamificationAchievement>? = null,
    val challengeProgress: List<JsonElement>? = null,
)

/** Points values from `packages/functions/src/workers/gamification-worker.ts:220`. */
@Serializable
data class GamificationPoints(
    val total: Int,
    val change: Int,
)

/** Streak values from `packages/functions/src/workers/gamification-worker.ts:221-224`. */
@Serializable
data class GamificationStreak(
    val current: Int,
    val frozen: Boolean,
)

/** Level values from `packages/functions/src/workers/gamification-worker.ts:225`. */
@Serializable
data class GamificationLevel(
    val current: Int,
    val progress: Double,
)

/** Achievement fields (`packages/functions/src/workers/gamification-worker.ts:136-141`). */
@Serializable
data class GamificationAchievement(
    val id: String,
    val name: String,
    val description: String? = null,
    val points: Int? = null,
)
