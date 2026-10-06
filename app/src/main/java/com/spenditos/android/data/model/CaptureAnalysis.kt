package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** Handler-only audio/text capture contracts from packages/functions/src/api/audio-analysis.ts:30-42. */
@Serializable
data class CaptureAnalyzeRequest(
    val documentKey: String? = null,
    val mode: String? = null,
    val transcript: String? = null,
    val mimeType: String? = null,
    val inputType: String? = null,
    val locale: String? = null,
    val referenceDate: String? = null,
    val userCurrency: String? = null,
    val parentAnalysisId: String? = null,
    val action: String? = null,
    val analysisId: String? = null,
    val outcome: String? = null,
)

/** Handler-only upload URL shape from packages/functions/src/api/document-analysis.ts:49-55, 112-190. */
@Serializable
data class CaptureUploadRequest(val mimeType: String, val documentType: String = "audio")

/** Handler-only upload response from packages/functions/src/api/document-analysis.ts:173-190. */
@Serializable
data class CaptureUploadData(val uploadUrl: String, val fields: Map<String, String>, val documentKey: String)

/** Handler-only response from packages/functions/src/api/audio-analysis.ts:325-337. */
@Serializable
data class CaptureAnalyzeData(val analysisId: String)

/** Analysis item from GET /transactions/analysis/{id}, packages/functions/src/api/document-analysis.ts:527-550. */
@Serializable
data class CaptureAnalysisItem(
    val amount: Double? = null,
    val currency: String? = null,
    val date: String? = null,
    val categoryId: String? = null,
    val categoryHint: String? = null,
    val merchantName: String? = null,
    val description: String? = null,
    val confidence: Double? = null,
    val transactionType: String? = null,
)

/** Handler-only poll response from packages/functions/src/api/document-analysis.ts:474-550. */
@Serializable
data class CaptureAnalysisStatus(
    val analysisId: String? = null,
    val status: String,
    val items: List<CaptureAnalysisItem> = emptyList(),
    val result: CaptureAnalysisItem? = null,
    val transcript: String? = null,
    val captureSessionId: String? = null,
    val parentAnalysisId: String? = null,
    val error: String? = null,
    val errorDetail: String? = null,
)

/** Per-item review write shape from packages/core/src/schemas/transaction.ts:282-296. */
@Serializable
data class CaptureBulkTransaction(
    val description: String,
    val amount: Double,
    val date: String,
    val categoryId: String? = null,
    val mood: TransactionMood? = null,
    val idempotencyKeyId: String,
)

/** Bulk handler request from packages/functions/src/api/transactions.ts:1030-1066. */
@Serializable
data class CaptureBulkRequest(
    val transactions: List<CaptureBulkTransaction>,
    val source: String,
    val analysisId: String? = null,
)

/** Partial result from packages/functions/src/api/transactions.ts:1135-1150. */
@Serializable
data class CaptureBulkResponse(
    val results: List<CaptureBulkCreated> = emptyList(),
    val failed: List<CaptureBulkFailure> = emptyList(),
    val duplicates: Int = 0,
)

/** Created transaction result from packages/functions/src/api/transactions.ts:1135-1142. */
@Serializable
data class CaptureBulkCreated(val transaction: Transaction? = null, val index: Int? = null)

/** Per-item rejection from packages/functions/src/api/transactions.ts:1135-1150. */
@Serializable
data class CaptureBulkFailure(val index: Int, val reason: String)

/** Capture-session close response from packages/functions/src/api/capture-session.ts:56-66. */
@Serializable
data class CaptureCloseData(val captureSessionId: String, val outcome: String)
