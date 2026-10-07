package com.spenditos.android.data.realtime

import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.DataChangedEvent
import com.spenditos.android.data.model.GamificationUpdate
import com.spenditos.android.data.model.RealtimeEnvelope
import com.spenditos.android.core.network.SpenditosJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject
import javax.inject.Singleton

/** Parses known server event envelopes and forwards finance invalidations. */
@Singleton
class RealtimeEvents @Inject constructor(
    private val invalidations: FinanceInvalidationBus,
) {
    private val processedEventIds = LinkedHashSet<String>(EVENT_ID_LIMIT)
    private val _gamificationUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    val gamificationUpdates: SharedFlow<Unit> = _gamificationUpdates.asSharedFlow()

    /** Unknown and malformed messages are ignored so they cannot end the socket's receive loop. */
    fun accept(message: String) {
        val raw = try {
            SpenditosJson.parseToJsonElement(message) as? JsonObject ?: return
        } catch (_: Exception) {
            return
        }
        val type = (raw["type"] as? JsonPrimitive)?.contentOrNull ?: return
        if (type != USER_DATA_CHANGED && type != GAMIFICATION_UPDATE) return

        val envelope = try {
            SpenditosJson.decodeFromJsonElement(RealtimeEnvelope.serializer(), raw)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }

        when (envelope.type) {
            USER_DATA_CHANGED -> acceptDataChanged(envelope.data)
            GAMIFICATION_UPDATE -> {
                try {
                    SpenditosJson.decodeFromJsonElement(GamificationUpdate.serializer(), envelope.data)
                } catch (_: SerializationException) {
                    return
                } catch (_: IllegalArgumentException) {
                    return
                }
                _gamificationUpdates.tryEmit(Unit)
            }
        }
    }

    private fun acceptDataChanged(data: JsonObject) {
        val event = try {
            SpenditosJson.decodeFromJsonElement(DataChangedEvent.serializer(), data)
        } catch (_: SerializationException) {
            return
        } catch (_: IllegalArgumentException) {
            return
        }
        if (event.eventId.isBlank() || event.eventId in processedEventIds) return
        val aggregate = event.toAggregate() ?: return

        processedEventIds.add(event.eventId)
        if (processedEventIds.size > EVENT_ID_LIMIT) {
            val oldest = processedEventIds.iterator()
            oldest.next()
            oldest.remove()
        }
        invalidations.invalidate(aggregate)
    }

    private fun DataChangedEvent.toAggregate(): Aggregate? {
        if (mutationType !in SUPPORTED_MUTATIONS) return null
        return when (aggregateType) {
            "transaction" -> Aggregate.TRANSACTION
            "category" -> Aggregate.CATEGORY
            "recurring" -> Aggregate.RECURRING
            "import" -> Aggregate.IMPORT
            "settings" -> Aggregate.SETTINGS
            "entitlement" -> Aggregate.ENTITLEMENT
            "account" -> Aggregate.ACCOUNT
            else -> null
        }
    }

    private companion object {
        const val EVENT_ID_LIMIT = 512
        const val USER_DATA_CHANGED = "user.data_changed"
        const val GAMIFICATION_UPDATE = "gamification.update"
        val SUPPORTED_MUTATIONS = setOf("created", "updated", "deleted")
    }
}
