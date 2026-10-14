package com.spenditos.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

// Unified "Scan" contract (Bmbsolution/spenditos#358). Shapes mirror the backend:
//   POST /scans               -> CreateScanResponse (202)      packages/types/src/api/index.ts (CreateScanRequest...)
//   GET  /scans/{id}          -> ScanDetail (items when ready/saved)
//   GET  /scans?status=...    -> { scans: [ScanSummary] }
//   POST /scans/{id}/complete -> ScanSummary
// Golden bodies: packages/types/src/__fixtures__/api-fixtures.ts (scn_0001..3), copied to
// app/src/test/resources/contract/. Decoding is deliberately lenient like iOS `Scan.swift`: unknown
// enum values map to a fallback, optional fields default, and one malformed item or summary is
// dropped instead of failing the whole response, so a newer backend never breaks an older client.

/** Server lifecycle of a scan. An unknown value is [UNKNOWN] and never shown on Home. */
enum class ScanStatus {
    PROCESSING,
    READY,
    FAILED,
    SAVED,
    DISCARDED,
    UNKNOWN,
    ;

    /** The value `GET /scans?status=` takes. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(raw: String?): ScanStatus = when (raw) {
            "processing" -> PROCESSING
            "ready" -> READY
            "failed" -> FAILED
            "saved" -> SAVED
            "discarded" -> DISCARDED
            else -> UNKNOWN
        }
    }
}

/** What a finished scan turned out to contain. `null` while processing, or for a value this client does not know (a list). */
enum class ScanKind {
    RECEIPT,
    LIST,
    ;

    companion object {
        fun fromWire(raw: String?): ScanKind? = when (raw) {
            "receipt" -> RECEIPT
            "list" -> LIST
            else -> null
        }
    }
}

/** How a scan was closed (`POST /scans/{id}/complete`). */
enum class ScanOutcome(val wire: String) {
    SAVED("saved"),
    DISCARDED("discarded"),
    ;

    companion object {
        fun fromWire(raw: String?): ScanOutcome? = entries.firstOrNull { it.wire == raw }
    }
}

/** Why the backend thinks a row should not be saved by default (`ScanItem.skipReason`). Unknown values decode to null. */
enum class ScanSkipReason {
    DUPLICATE,
    PAYMENT,
    TRANSFER,

    /** A skip rule of the user's (`ruleId` says which). */
    RULE,
    ;

    companion object {
        fun fromWire(raw: String?): ScanSkipReason? = when (raw) {
            "duplicate" -> DUPLICATE
            "payment" -> PAYMENT
            "transfer" -> TRANSFER
            "rule" -> RULE
            else -> null
        }
    }
}

@Serializable
data class ScanProgress(val done: Int = 0, val total: Int = 0)

/** `ScanSummary` (api/index.ts). [status] and [kind] stay raw strings; use [state] / [scanKind]. */
@Serializable
data class ScanSummary(
    val scanId: String,
    val status: String? = null,
    val progress: ScanProgress = ScanProgress(),
    val kind: String? = null,
    val itemCount: Int = 0,
    val flaggedCount: Int = 0,
    /** Items a user rule handled (carry a `ruleId`); 0 on old scans. */
    val ruleCount: Int = 0,
    val failedDocuments: Int = 0,
    val sourceLabel: String = "",
    val error: String? = null,
    val createdAt: String = "",
    val updatedAt: String? = null,
) {
    val state: ScanStatus get() = ScanStatus.fromWire(status)
    val scanKind: ScanKind? get() = ScanKind.fromWire(kind)
}

/** The existing transaction a scanned row appears to repeat (`ScanItem.possibleDuplicateOf`). */
@Serializable
data class ScanPossibleDuplicate(
    val transactionId: String,
    val date: String,
    val amount: Double,
    val description: String? = null,
)

/** `ScanItem` (api/index.ts): one extracted row. [id] is stable per scan and is the bulk-save idempotency key. */
@Serializable
data class ScanItem(
    val id: String,
    val documentIndex: Int = 0,
    val amount: Double? = null,
    val currency: String? = null,
    val date: String? = null,
    val categoryId: String? = null,
    val categoryHint: String? = null,
    val merchantName: String? = null,
    val description: String? = null,
    val confidence: Double = 0.0,
    val transactionType: String = "expense",
    val possibleDuplicateOf: ScanPossibleDuplicate? = null,
    val skipReason: String? = null,
    val isPending: Boolean? = null,
    /** The user rule that categorized or skipped this row. */
    val ruleId: String? = null,
) {
    val skip: ScanSkipReason? get() = ScanSkipReason.fromWire(skipReason)
    val isIncome: Boolean get() = transactionType.equals("income", ignoreCase = true)
}

/** `ScanDetail`: the summary fields plus [items] (present when the scan is ready or saved). */
@Serializable(with = ScanDetailSerializer::class)
data class ScanDetail(val summary: ScanSummary, val items: List<ScanItem>?) {
    val scanId: String get() = summary.scanId
    val state: ScanStatus get() = summary.state
    val kind: ScanKind? get() = summary.scanKind
}

@Serializable
data class ScanListData(
    @Serializable(with = ScanSummaryListSerializer::class) val scans: List<ScanSummary> = emptyList(),
)

/** One document in `POST /scans` (`pdfGroupId` only for kind=image: all pages of one PDF share it). */
@Serializable
data class CreateScanDocument(
    val documentKey: String,
    val kind: String,
    val filename: String? = null,
    val pdfGroupId: String? = null,
)

/** `POST /scans` body (api/index.ts `CreateScanRequest`): 1..60 documents. */
@Serializable
data class CreateScanRequest(
    val documents: List<CreateScanDocument>,
    val referenceDate: String? = null,
    val locale: String? = null,
    val userCurrency: String? = null,
    val sourceLabel: String? = null,
)

/** 202 body of `POST /scans`. */
@Serializable
data class CreateScanResponse(val scanId: String, val status: String? = null, val total: Int = 0)

/** `POST /scans/{id}/complete` body. */
@Serializable
data class CompleteScanRequest(val outcome: String)

/** One row of the object-form bulk save (`packages/functions/src/api/transactions.ts:1042-1066`). */
@Serializable
data class ScanBulkTransaction(
    val description: String,
    val amount: Double,
    val date: String,
    val mood: TransactionMood? = null,
    val idempotencyKeyId: String,
    /** Steers the server's fallback category for rows without one: income must not land in the expense "Uncategorized". */
    val transactionType: String,
    val categoryId: String? = null,
)

/** `POST /transactions/bulk` object form for scans: `{transactions, source: "scan", scanId}`, at most 100 rows. */
@Serializable
data class ScanBulkRequest(
    val transactions: List<ScanBulkTransaction>,
    val source: String = "scan",
    val scanId: String,
)

/** `data` of `POST /transactions/bulk` (api/transactions.ts:1167-1190): created rows are not index-aligned; failures carry the request index. */
@Serializable
data class ScanBulkResponse(
    val results: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val failed: List<ScanBulkFailure> = emptyList(),
    /** Rows the server already had under the same idempotency key (a retried chunk): still "added". */
    val duplicates: Int = 0,
)

@Serializable
data class ScanBulkFailure(val index: Int, val reason: String = "")

/** Decodes a JSON array element by element, dropping the ones that do not decode. */
open class LossyListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(element).descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val array = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            try {
                input.json.decodeFromJsonElement(element, entry)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = encoder.encodeSerializableValue(ListSerializer(element), value)
}

object ScanSummaryListSerializer : LossyListSerializer<ScanSummary>(ScanSummary.serializer())

/** Decode-only: a scan response whose [ScanSummary] is strict (needs `scanId`) and whose items are lossy. */
object ScanDetailSerializer : KSerializer<ScanDetail> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ScanDetail")

    override fun deserialize(decoder: Decoder): ScanDetail {
        val input = decoder as? JsonDecoder ?: throw SerializationException("JSON only")
        val body = input.decodeJsonElement() as? JsonObject ?: throw SerializationException("Scan detail must be an object")
        val summary = input.json.decodeFromJsonElement(ScanSummary.serializer(), body)
        val items = (body["items"] as? JsonArray)?.let { array ->
            input.json.decodeFromJsonElement(ScanItemListSerializer, array)
        }
        return ScanDetail(summary, items)
    }

    override fun serialize(encoder: Encoder, value: ScanDetail) = throw SerializationException("ScanDetail is decode-only")
}

object ScanItemListSerializer : LossyListSerializer<ScanItem>(ScanItem.serializer())
