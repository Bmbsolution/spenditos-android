package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Receipt Scan Models

@Serializable
data class AnalyzedReceipt(
    val merchant: String? = null,
    val total: Double? = null,
    val date: String? = null,
    val items: List<ReceiptItem>? = null,
    val tax: Double? = null,
    val tip: Double? = null,
    val currency: String? = null,
    val confidence: Double
)

@Serializable
data class ReceiptItem(
    val name: String,
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val totalPrice: Double,
    val categoryId: String? = null
)

@Serializable
data class UploadUrlResponse(
    val uploadUrl: String,
    val fileId: String,
    val expiresAt: String
)

@Serializable
data class AnalysisResponse(
    val analysisId: String,
    val status: String // "pending", "processing", "completed", "failed"
)

@Serializable
data class AnalysisStatus(
    val status: String, // "pending", "processing", "completed", "failed"
    val progress: Double? = null,
    val result: AnalyzedReceipt? = null,
    val error: String? = null
)

@Serializable
data class ReceiptAnalysisRequest(
    val fileId: String,
    val mimeType: String
)

// MARK: - Merchant Mapping Models

@Serializable
data class MerchantMapping(
    val id: String,
    val merchantName: String,
    val normalizedName: String? = null,
    val defaultCategoryId: String,
    val matchPattern: String? = null,
    val isRegex: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MerchantMappingCreateRequest(
    val merchantName: String,
    val defaultCategoryId: String,
    val matchPattern: String? = null,
    val isRegex: Boolean = false
)

@Serializable
data class MerchantMappingUpdateRequest(
    val defaultCategoryId: String? = null,
    val matchPattern: String? = null,
    val isRegex: Boolean? = null
)

// MARK: - Export Models

@Serializable
data class ExportFormat(
    val id: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val description: String
)

@Serializable
data class ExportRequest(
    val format: String, // "csv", "json", "pdf", "xlsx"
    val startDate: String? = null,
    val endDate: String? = null,
    val categoryIds: List<String>? = null,
    val includeSplits: Boolean = true
)

@Serializable
data class ExportResult(
    val downloadUrl: String,
    val filename: String,
    val fileSize: Long,
    val expiresAt: String
)

// MARK: - Audio Entry Models

@Serializable
data class AudioUploadRequest(
    val fileId: String,
    val mimeType: String, // "audio/m4a", "audio/wav", "audio/mp3"
    val language: String? = null // e.g., "en-US", "es-ES"
)

@Serializable
data class AudioAnalysisResponse(
    val analysisId: String,
    val status: String // "pending", "processing", "completed", "failed"
)

@Serializable
data class AudioAnalysisResult(
    val status: String,
    val transcript: String? = null,
    val transaction: ParsedAudioTransaction? = null,
    val error: String? = null
)

@Serializable
data class ParsedAudioTransaction(
    val description: String,
    val amount: Double,
    val type: String, // "expense" | "income"
    val categoryId: String? = null,
    val date: String? = null,
    val confidence: Double
)
