package com.spenditos.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * `PremiumStatus` packages/types/src/domain/user.ts:111: the server-computed effective Premium
 * (store, admin grant or welcome), expiry already enforced. Optional on every settings response:
 * old servers omit it. [reason] stays a string so a value this version does not know decodes.
 */
@Serializable
data class PremiumStatus(
    val isPremium: Boolean,
    val expiresAt: String? = null,
    /** `store` | `grant` | `welcome` | `none` (anything else is treated as unknown). */
    val reason: String? = null,
) {
    val isWelcome: Boolean get() = isPremium && reason == REASON_WELCOME

    /** [expiresAt] parsed (with or without fractional seconds); null when absent or malformed. */
    val expiresAtInstant: Instant? get() = parseInstant(expiresAt)

    companion object {
        const val REASON_WELCOME = "welcome"

        fun parseInstant(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            return try {
                Instant.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/** `WelcomePremium` domain/user.ts:93: the one-time welcome period granted to a new account. */
@Serializable
data class WelcomePremium(
    val grantedAt: String? = null,
    val expiresAt: String? = null,
    val days: Int? = null,
)

/**
 * Decodes [T] or null: a malformed value must never break the whole settings response
 * (iOS decodes these with `try?`). Only valid with [Json] decoders.
 */
open class NullOnErrorSerializer<T : Any>(private val delegate: KSerializer<T>) : KSerializer<T?> {
    private val nullable = delegate.nullable
    override val descriptor: SerialDescriptor = nullable.descriptor

    override fun deserialize(decoder: Decoder): T? {
        val jsonDecoder = decoder as? JsonDecoder ?: return nullable.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        return try {
            jsonDecoder.json.decodeFromJsonElement(nullable, element)
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: T?) = nullable.serialize(encoder, value)
}

object TolerantPremiumStatusSerializer : NullOnErrorSerializer<PremiumStatus>(PremiumStatus.serializer())
object TolerantWelcomePremiumSerializer : NullOnErrorSerializer<WelcomePremium>(WelcomePremium.serializer())
