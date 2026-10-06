@file:kotlinx.serialization.UseSerializers(com.spenditos.android.data.model.LocalDateIsoSerializer::class)

package com.spenditos.android.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import kotlin.math.abs

/** `getUploadUrlHandler` document response, packages/functions/src/api/document-analysis.ts:198-205. */
@Serializable
data class StatementUploadUrlRequest(
    val mimeType: String,
    val documentType: String = "bank_statement",
)

/** `getUploadUrlHandler` response payload, packages/functions/src/api/document-analysis.ts:198-205. */
@Serializable
data class StatementUploadUrlResponse(
    val uploadUrl: String,
    val fields: Map<String, String> = emptyMap(),
    val documentKey: String,
)

/** `PreviewSpreadsheetSchema.mapping`, packages/functions/src/api/statement-import.ts:28-41. */
@Serializable
data class StatementColumnMapping(
    val dateColumn: String? = null,
    val dateFormat: String? = null,
    val descriptionColumn: String? = null,
    val amountColumn: String? = null,
    val debitColumn: String? = null,
    val creditColumn: String? = null,
    val amountIsNegativeForDebit: Boolean? = null,
    val confidence: Double = 0.0,
) {
    /** Mirrors iOS `StatementColumnMapping.isProcessable`: date, description and amount or debit/credit. */
    val isProcessable: Boolean
        get() = dateColumn != null && descriptionColumn != null &&
            (amountColumn != null || (debitColumn != null && creditColumn != null))
}

/** A scalar from `SpreadsheetAnalyzer.analyze()`, packages/functions/src/api/statement-import.ts:127. */
@Serializable(with = StatementCellValueSerializer::class)
data class StatementCellValue(val text: String)

private object StatementCellValueSerializer : KSerializer<StatementCellValue> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StatementCellValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StatementCellValue) = encoder.encodeString(value.text)

    override fun deserialize(decoder: Decoder): StatementCellValue {
        val jsonDecoder = decoder as? JsonDecoder ?: return StatementCellValue(decoder.decodeString())
        val element = jsonDecoder.decodeJsonElement()
        val text = when {
            element == JsonNull -> ""
            element !is JsonPrimitive -> ""
            element.isString -> element.content
            element.booleanOrNull != null -> element.content
            element.doubleOrNull != null -> {
                val value = element.doubleOrNull!!
                if (value.isFinite() && abs(value) < 1e15 && value == value.toLong().toDouble()) value.toLong().toString()
                else value.toString()
            }
            else -> ""
        }
        return StatementCellValue(text)
    }
}

/** Analyze response builder, packages/functions/src/api/statement-import.ts:127. */
@Serializable
data class StatementAnalysisResult(
    val mapping: StatementColumnMapping,
    val headers: List<String> = emptyList(),
    val preview: List<List<StatementCellValue>> = emptyList(),
)

/** `AnalyzeSpreadsheetSchema`, packages/functions/src/api/statement-import.ts:23-26. */
@Serializable
data class StatementAnalyzeRequest(
    val filename: String,
    val documentKey: String,
)

/** `PreviewSpreadsheetSchema`, packages/functions/src/api/statement-import.ts:28-41. */
@Serializable
data class StatementPreviewRequest(
    val filename: String,
    val documentKey: String,
    val mapping: StatementColumnMapping,
)

/** Preview response builder, packages/functions/src/api/statement-import.ts:202-215. */
@Serializable
data class StatementPreviewTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Double,
    val merchant: String? = null,
    /** The handler returns an AI category NAME in this field, despite its name. */
    val categoryId: String? = null,
) {
    val importDescription: String get() = merchant ?: description
}

/** Preview response builder, packages/functions/src/api/statement-import.ts:215. */
@Serializable
data class StatementPreviewResult(
    val transactions: List<StatementPreviewTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
)

/** `ImportSpreadsheetSchema` row, packages/functions/src/api/statement-import.ts:43-55. */
@Serializable
data class StatementImportTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Double,
    val categoryId: String? = null,
    val newCategoryName: String? = null,
)

/** `ImportSpreadsheetSchema`, packages/functions/src/api/statement-import.ts:43-55. */
@Serializable
data class StatementImportRequest(val transactions: List<StatementImportTransaction>)

/** Import response builder, packages/functions/src/api/statement-import.ts:455-463. */
@Serializable
data class StatementImportResult(
    val message: String,
    val count: Int,
    val stats: StatementImportStats? = null,
)

/** Import response stats, packages/functions/src/api/statement-import.ts:458-462. */
@Serializable
data class StatementImportStats(
    val created: Int,
    val updated: Int,
    val skipped: Int,
)

/** Resolves the preview's category name case-insensitively; an unknown name stays unassigned. */
fun resolveStatementCategoryId(suggestion: String?, categories: List<Category>): String? =
    suggestion?.takeIf { it.isNotEmpty() }?.let { name ->
        categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    }

/** Port of iOS `StatementPreviewResult.importTransactions()`. */
fun StatementPreviewResult.toImportTransactions(): List<StatementImportTransaction> =
    transactions.map { row ->
        StatementImportTransaction(
            date = row.date,
            description = row.importDescription,
            amount = row.amount,
            categoryId = resolveStatementCategoryId(row.categoryId, categories),
        )
    }

fun StatementPreviewResult.categoryNameFor(row: StatementPreviewTransaction): String =
    resolveStatementCategoryId(row.categoryId, categories)
        ?.let { id -> categories.firstOrNull { it.id == id }?.name }
        ?: "Uncategorized"
