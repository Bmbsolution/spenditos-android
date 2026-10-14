package com.spenditos.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Rules = merchant mappings with `source == "user"` (Bmbsolution/spenditos#358). Routes of
// packages/functions/src/api/merchant-mapping.ts:
//   GET    /settings/merchant-mappings                 -> data: [MerchantMapping]
//   POST   /settings/merchant-mappings                 -> upsert a user rule by normalized rawPattern (201 created / 200 updated)
//   PUT    /settings/merchant-mappings/{id}            -> partial update; a skip rule clears the category
//   DELETE /settings/merchant-mappings/{id}            -> 204
//   GET    /settings/merchant-mappings/match-count     -> data: { count }
//   POST   /settings/merchant-mappings/{id}/apply      -> data: { updated }; 409 SkipRuleCannotApply
// Decoding is lenient like iOS `MerchantMapping.swift`: `action` defaults to categorize (older rows), a malformed
// mapping in a list is dropped instead of failing the list.

/** What a rule does with a matching row. */
enum class RuleAction(val wire: String) {
    /** File it under the rule's category. */
    CATEGORIZE("categorize"),

    /** Not spending (card payments, transfers): starts unchecked. */
    SKIP("skip"),
    ;

    companion object {
        /** An unknown or missing value is a categorize rule, like iOS. */
        fun fromWire(raw: String?): RuleAction = entries.firstOrNull { it.wire == raw } ?: CATEGORIZE
    }
}

/** `MerchantMappingSchema`, packages/core/src/schemas/merchant-mapping.ts:6 (response of the list/create/update handlers). */
@Serializable
data class MerchantMapping(
    val id: String,
    val userId: String = "",
    /** For rules created from review, the clean merchant name ("Tim Hortons"). */
    val rawPattern: String,
    /** Display name applied to matching rows. */
    val cleanMerchantName: String = "",
    /** Required when the action is categorize; absent for skip rules. */
    val categoryId: String? = null,
    val confidence: Double = 1.0,
    /** "user", "ai" or "system". */
    val source: String = USER_SOURCE,
    val isRegex: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    /** "categorize" (default for older rows) or "skip"; read [ruleAction]. */
    val action: String? = null,
) {
    val ruleAction: RuleAction get() = RuleAction.fromWire(action)

    /** Only these are Rules (Settings > Rules, scan review). */
    val isUserRule: Boolean get() = source == USER_SOURCE

    /** The name shown for the rule (older rows without one fall back to the pattern). */
    val displayName: String get() = cleanMerchantName.ifBlank { rawPattern }

    companion object {
        const val USER_SOURCE = "user"
    }
}

/**
 * Body of `POST /settings/merchant-mappings` (an upsert for user rules, `UserRuleBodySchema`). The app sends the
 * merchant as both `rawPattern` and `cleanMerchantName`; a skip rule carries no category. The server sets `userId`
 * and `source: "user"`.
 */
@Serializable
data class UpsertRuleRequest(
    val rawPattern: String,
    val cleanMerchantName: String,
    val action: String,
    val categoryId: String? = null,
) {
    companion object {
        fun of(merchant: String, action: RuleAction, categoryId: String?) = UpsertRuleRequest(
            rawPattern = merchant,
            cleanMerchantName = merchant,
            action = action.wire,
            categoryId = categoryId?.takeIf { action == RuleAction.CATEGORIZE && it.isNotEmpty() },
        )
    }
}

/** Body of `PUT /settings/merchant-mappings/{id}` (`UpdateUserRuleBodySchema`, every field optional). */
@Serializable
data class UpdateRuleRequest(
    val action: String,
    val categoryId: String? = null,
) {
    companion object {
        fun of(action: RuleAction, categoryId: String?) = UpdateRuleRequest(
            action = action.wire,
            categoryId = categoryId?.takeIf { action == RuleAction.CATEGORIZE && it.isNotEmpty() },
        )
    }
}

/** `data` of `GET .../match-count`: past transactions (24 months, capped) a rule would change. */
@Serializable
data class MerchantMatchCount(val count: Int = 0)

/** `data` of `POST .../{id}/apply`. */
@Serializable
data class MerchantApplyResult(val updated: Int = 0)

/** `data` of `GET /settings/merchant-mappings`: an array that drops malformed entries (iOS `MerchantMappingList`). */
@Serializable(with = MerchantMappingListSerializer::class)
data class MerchantMappingList(val mappings: List<MerchantMapping>)

object MerchantMappingListSerializer : KSerializer<MerchantMappingList> {
    private val lossy = LossyListSerializer(MerchantMapping.serializer())
    override val descriptor: SerialDescriptor = lossy.descriptor
    override fun deserialize(decoder: Decoder) = MerchantMappingList(lossy.deserialize(decoder))
    override fun serialize(encoder: Encoder, value: MerchantMappingList) = lossy.serialize(encoder, value.mappings)
}
