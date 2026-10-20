package com.spenditos.android.data.debts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Debts (repayment planning) wire contract: docs/features/debts.md ("Data model", "Ledger and
 * projection", "API") in the spenditos-debts repo; iOS twin: Domain/Models/Debt.swift.
 *
 * Responses use the standard `{ "data": ... }` envelope (unwrapped by `apiCall`), so these types
 * describe the inner `data`. Money is a JSON number, dates are `YYYY-MM-DD` strings.
 * Server-owned enums decode leniently: an unknown value becomes UNKNOWN instead of failing the
 * whole response. `frequency` stays a String like the recurring models, so a frequency this
 * client does not know yet is preserved rather than dropped.
 */

// region Enums

/** String enum whose unknown wire values decode to [fallback] whatever the Json configuration. */
open class LenientEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val entries: List<E>,
    private val wire: (E) -> String,
    private val fallback: E,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(wire(value))
    override fun deserialize(decoder: Decoder): E {
        val raw = decoder.decodeString()
        return entries.firstOrNull { wire(it) == raw } ?: fallback
    }
}

/** `DebtStatus` (docs/features/debts.md): `active | paid_off`. */
@Serializable(with = DebtStatusSerializer::class)
enum class DebtStatus(val wire: String) {
    ACTIVE("active"),
    PAID_OFF("paid_off"),

    /** A status this client does not know yet. */
    UNKNOWN("unknown"),
}

object DebtStatusSerializer :
    LenientEnumSerializer<DebtStatus>("DebtStatus", DebtStatus.entries, { it.wire }, DebtStatus.UNKNOWN)

/** `DebtHistoryEntry.kind`: `payment | extra_payment | adjustment`. */
@Serializable(with = DebtHistoryKindSerializer::class)
enum class DebtHistoryKind(val wire: String) {
    PAYMENT("payment"),
    EXTRA_PAYMENT("extra_payment"),
    ADJUSTMENT("adjustment"),

    /** A kind this client does not know yet. */
    UNKNOWN("unknown"),
}

object DebtHistoryKindSerializer :
    LenientEnumSerializer<DebtHistoryKind>("DebtHistoryKind", DebtHistoryKind.entries, { it.wire }, DebtHistoryKind.UNKNOWN)

// endregion

// region Entities and responses

/** `Debt` (docs/features/debts.md "Debt"). */
@Serializable
data class Debt(
    val id: String,
    val userId: String,
    /** 1..80 characters. */
    val name: String,
    /** User currency, > 0. */
    val startingBalance: Double,
    /** `YYYY-MM-DD`; the balance is [startingBalance] on this day. */
    val startDate: String,
    /** Yearly percent (0 < apr < 100). Null means no interest. */
    val apr: Double? = null,
    /** The payment bill (`RecurringTemplate`). Null means "pay when I can". */
    val billId: String? = null,
    val categoryId: String,
    val createdAt: String,
    val updatedAt: String,
)

/** `DebtAdjustment` ("Correct balance"). Not returned by any route; history carries adjustments. */
@Serializable
data class DebtAdjustment(
    val id: String,
    val debtId: String,
    val userId: String,
    val date: String,
    val balance: Double,
    val createdAt: String,
)

/** `DebtSummary`. */
@Serializable
data class DebtSummary(
    /** PAID_OFF once the balance is at or below 0.005. */
    val status: DebtStatus = DebtStatus.UNKNOWN,
    val balance: Double,
    /** Sum of the amounts applied to the balance so far. */
    val paidTotal: Double,
    /** Estimated interest so far (0 when `apr` is null). */
    val interestTotal: Double,
    /** clamp(1 - balance / startingBalance, 0, 1). */
    val progress: Double,
    val lastPaymentDate: String? = null,
    /** Date of the event that took the balance to zero. */
    val paidOffDate: String? = null,
    /** First bill occurrence after `asOf`; null without a bill, when paid off, or when the bill is inactive. */
    val nextPaymentDate: String? = null,
    /** The bill amount. */
    val payment: Double? = null,
    val frequency: String? = null,
    /** "Every N" frequency periods: 1 when unset on the bill, null without a bill. Absent on older servers. */
    val repeatInterval: Int? = null,
) {
    val isPaidOff: Boolean get() = status == DebtStatus.PAID_OFF
}

@Serializable
data class DebtScheduleEntry(val date: String, val amount: Double)

/** `DebtProjection`. */
@Serializable
data class DebtProjection(
    val paymentsLeft: Int,
    val payoffDate: String? = null,
    val lastPaymentAmount: Double? = null,
    val interestRemaining: Double,
    /** True when the first projected payment does not cover its interest. */
    val neverPaysOff: Boolean,
    /** The first 120 projected payments. */
    val schedule: List<DebtScheduleEntry>,
)

/** One row of `GET /debts`. */
@Serializable
data class DebtWithPlan(
    val debt: Debt,
    val summary: DebtSummary,
    val projection: DebtProjection,
) {
    val id: String get() = debt.id
}

/** `DebtHistoryEntry`. */
@Serializable
data class DebtHistoryEntry(
    val kind: DebtHistoryKind = DebtHistoryKind.UNKNOWN,
    /** `YYYY-MM-DD`. */
    val date: String,
    /** Payment amount, extra payment amount, or the adjustment's new balance. */
    val amount: Double,
    /** Interest added before this payment (0 otherwise). */
    val interest: Double,
    val balanceAfter: Double,
    val transactionId: String? = null,
    val adjustmentId: String? = null,
)

/**
 * `data` of `GET /debts/{id}` and of create, update and adjustment. Flattened on the wire:
 * `debt`, `summary`, `projection` and `history` sit at the top of `data`. [history] is newest first.
 */
@Serializable
data class DebtDetail(
    val debt: Debt,
    val summary: DebtSummary,
    val projection: DebtProjection,
    val history: List<DebtHistoryEntry>,
) {
    val id: String get() = debt.id
}

/** `data` of `GET /debts`. */
@Serializable
data class ListDebtsResponse(val debts: List<DebtWithPlan> = emptyList())

/** `data` of `POST /debts/preview`. */
@Serializable
data class DebtPreviewResponse(
    /** Given, or computed from `targetMonth` and rounded up to a whole currency unit. */
    val payment: Double,
    val paymentsCount: Int,
    val payoffDate: String? = null,
    val lastPaymentAmount: Double? = null,
    val totalInterest: Double,
    val neverPaysOff: Boolean,
)

// endregion

// region Requests

/** Bill fields for `POST /debts` (`payment`). */
@Serializable
data class CreateDebtPayment(
    val amount: Double,
    val frequency: String,
    /** `YYYY-MM-DD`. */
    val firstPaymentDate: String,
    val repeatInterval: Int? = null,
    val secondOccurrenceDay: Int? = null,
)

/** Body of `POST /debts`. Null optionals are omitted from the JSON. */
@Serializable
data class CreateDebtRequest(
    val name: String,
    val startingBalance: Double,
    /** Defaults to the server's today when omitted. */
    val startDate: String? = null,
    /** Omitted means no interest. */
    val apr: Double? = null,
    val categoryId: String? = null,
    /** Omitted creates a debt without a bill ("pay when I can"). */
    val payment: CreateDebtPayment? = null,
)

/** Bill fields changed by `PATCH /debts/{id}` (`payment`). Null fields are not sent. */
@Serializable
data class UpdateDebtPayment(
    val amount: Double? = null,
    val frequency: String? = null,
    val firstPaymentDate: String? = null,
    val repeatInterval: Int? = null,
)

/**
 * A PATCH field that is set to a value or to an explicit JSON `null`. Omitting the field is
 * expressed by a Kotlin `null` on the owning property (Json has `explicitNulls = false`).
 */
sealed interface DebtPatch<out T> {
    data class Value<T>(val value: T) : DebtPatch<T>
    data object Null : DebtPatch<Nothing>
}

/**
 * Body of `PATCH /debts/{id}`. A null property is omitted; [DebtPatch.Null] sends JSON `null`.
 * For [payment], `Null` means "stop the payments (the bill is deactivated, the debt keeps its
 * `billId`)" and an omitted key leaves the bill alone; for [apr], `Null` removes interest.
 */
@Serializable(with = UpdateDebtRequestSerializer::class)
data class UpdateDebtRequest(
    val name: String? = null,
    val startingBalance: Double? = null,
    val categoryId: String? = null,
    val apr: DebtPatch<Double>? = null,
    val payment: DebtPatch<UpdateDebtPayment>? = null,
)

/** Body of `POST /debts/{id}/adjustments` ("Correct balance"). */
@Serializable
data class CreateDebtAdjustmentRequest(
    /** `YYYY-MM-DD`, at most today + 1. */
    val date: String,
    /** Balance on [date], >= 0. */
    val balance: Double,
)

/**
 * Body of `POST /debts/preview`. Stateless; powers the add sheet and the what-if slider.
 * Exactly one of [payment] ("What I pay") or [targetMonth] ("When I'm done", `YYYY-MM`) is set.
 * `apr` is always sent, as an explicit `null` when the debt has no interest.
 */
@Serializable(with = DebtPreviewRequestSerializer::class)
data class DebtPreviewRequest(
    val balance: Double,
    val apr: Double?,
    val frequency: String,
    val repeatInterval: Int? = null,
    /** `YYYY-MM-DD`. */
    val firstPaymentDate: String,
    val payment: Double? = null,
    val targetMonth: String? = null,
)

// endregion

// region Serializers for explicit nulls

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toIntOrNull()

private fun Encoder.jsonEncoder(): JsonEncoder = this as? JsonEncoder ?: throw SerializationException("JSON only")
private fun Decoder.jsonDecoder(): JsonDecoder = this as? JsonDecoder ?: throw SerializationException("JSON only")

/** Writes only the keys that are present, with `DebtPatch.Null` as a real JSON `null`. */
object UpdateDebtRequestSerializer : KSerializer<UpdateDebtRequest> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UpdateDebtRequest", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UpdateDebtRequest) {
        val jsonEncoder = encoder.jsonEncoder()
        val body = buildJsonObject {
            value.name?.let { put("name", it) }
            value.startingBalance?.let { put("startingBalance", it) }
            value.categoryId?.let { put("categoryId", it) }
            when (val apr = value.apr) {
                null -> Unit
                DebtPatch.Null -> put("apr", JsonNull)
                is DebtPatch.Value -> put("apr", apr.value)
            }
            when (val payment = value.payment) {
                null -> Unit
                DebtPatch.Null -> put("payment", JsonNull)
                is DebtPatch.Value -> put("payment", jsonEncoder.json.encodeToJsonElement(UpdateDebtPayment.serializer(), payment.value))
            }
        }
        jsonEncoder.encodeJsonElement(body)
    }

    override fun deserialize(decoder: Decoder): UpdateDebtRequest {
        val jsonDecoder = decoder.jsonDecoder()
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject ?: throw SerializationException("Expected an object")
        return UpdateDebtRequest(
            name = obj.str("name"),
            startingBalance = obj.dbl("startingBalance"),
            categoryId = obj.str("categoryId"),
            apr = obj["apr"]?.let { if (it is JsonNull) DebtPatch.Null else DebtPatch.Value((it as JsonPrimitive).content.toDouble()) },
            payment = obj["payment"]?.let {
                if (it is JsonNull) DebtPatch.Null else DebtPatch.Value(jsonDecoder.json.decodeFromJsonElement(UpdateDebtPayment.serializer(), it))
            },
        )
    }
}

/** Always writes `apr` (null when the debt has no interest); other nulls are omitted. */
object DebtPreviewRequestSerializer : KSerializer<DebtPreviewRequest> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DebtPreviewRequest", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: DebtPreviewRequest) {
        val body = buildJsonObject {
            put("balance", value.balance)
            if (value.apr == null) put("apr", JsonNull) else put("apr", value.apr)
            put("frequency", value.frequency)
            value.repeatInterval?.let { put("repeatInterval", it) }
            put("firstPaymentDate", value.firstPaymentDate)
            value.payment?.let { put("payment", it) }
            value.targetMonth?.let { put("targetMonth", it) }
        }
        encoder.jsonEncoder().encodeJsonElement(body)
    }

    override fun deserialize(decoder: Decoder): DebtPreviewRequest {
        val obj = decoder.jsonDecoder().decodeJsonElement() as? JsonObject ?: throw SerializationException("Expected an object")
        return DebtPreviewRequest(
            balance = obj.dbl("balance") ?: throw SerializationException("balance is required"),
            apr = obj.dbl("apr"),
            frequency = obj.str("frequency") ?: throw SerializationException("frequency is required"),
            repeatInterval = obj.int("repeatInterval"),
            firstPaymentDate = obj.str("firstPaymentDate") ?: throw SerializationException("firstPaymentDate is required"),
            payment = obj.dbl("payment"),
            targetMonth = obj.str("targetMonth"),
        )
    }
}

// endregion
