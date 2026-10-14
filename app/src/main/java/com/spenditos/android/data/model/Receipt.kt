package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** Handler-only upload response from packages/functions/src/api/document-analysis.ts:112-190. */
@Serializable
data class ReceiptUploadData(
    val uploadUrl: String,
    val fields: Map<String, String>,
    val documentKey: String,
)

/** Upload request accepted by packages/functions/src/api/document-analysis.ts:49-55. */
@Serializable
data class ReceiptUploadRequest(val mimeType: String, val documentType: String)

/** Receipt result fields returned by the document-analysis handler at document-analysis.ts:502-562. */
@Serializable
data class ReceiptResult(
    val amount: Double? = null,
    val description: String? = null,
    val date: String? = null,
    val merchantName: String? = null,
    val currency: String? = null,
    val categoryId: String? = null,
    val categoryHint: String? = null,
)
