package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Import Models

@Serializable
data class CSVImport(
    val id: String,
    val filename: String,
    val status: String, // "uploaded", "processing", "review", "importing", "completed", "failed"
    val totalRows: Int? = null,
    val processedRows: Int? = null,
    val importedCount: Int? = null,
    val errorCount: Int? = null,
    val mapping: CSVMapping? = null,
    val errorMessage: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CSVMapping(
    val dateColumn: String? = null,
    val descriptionColumn: String? = null,
    val amountColumn: String? = null,
    val typeColumn: String? = null,
    val categoryColumn: String? = null,
    val dateFormat: String? = null
)

@Serializable
data class ImportItem(
    val id: String,
    val importId: String,
    val rowData: Map<String, String>,
    val parsedData: ParsedImportData? = null,
    val status: String, // "pending", "mapped", "conflict", "imported", "skipped", "error"
    val suggestedCategoryId: String? = null,
    val categoryId: String? = null,
    val duplicateOfTransactionId: String? = null,
    val errorMessage: String? = null
)

@Serializable
data class ParsedImportData(
    val date: String? = null,
    val description: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val categoryId: String? = null
)

@Serializable
data class ImportMappingUpdate(
    val mapping: CSVMapping
)

@Serializable
data class ImportFinalizeRequest(
    val itemIds: List<String>? = null, // null = import all
    val categoryMappings: Map<String, String>? = null // rowId -> categoryId
)

@Serializable
data class ImportFinalizeResponse(
    val importId: String,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int
)

// MARK: - Statement Import Models

@Serializable
data class StatementAnalysisRequest(
    val fileId: String,
    val fileType: String // "csv", "xlsx", "pdf"
)

@Serializable
data class StatementAnalysisResult(
    val detectedColumns: List<DetectedColumn>,
    val sampleRows: List<List<String>>,
    val detectedDateFormat: String? = null,
    val detectedDelimiter: String? = null,
    val confidence: Double // 0.0 - 1.0
)

@Serializable
data class DetectedColumn(
    val columnName: String,
    val suggestedType: String, // "date", "description", "amount", "type", "ignore"
    val confidence: Double,
    val sampleValues: List<String>
)

@Serializable
data class ColumnMapping(
    val dateColumn: String,
    val descriptionColumn: String,
    val amountColumn: String,
    val typeColumn: String? = null,
    val dateFormat: String? = null
)

@Serializable
data class StatementPreviewRequest(
    val fileId: String,
    val mapping: ColumnMapping
)

@Serializable
data class StatementPreviewResult(
    val previewTransactions: List<PreviewTransaction>,
    val duplicateCount: Int,
    val totalAmount: Double
)

@Serializable
data class PreviewTransaction(
    val id: String,
    val date: String,
    val description: String,
    val amount: Double,
    val type: String,
    val suggestedCategoryId: String? = null,
    val confidence: Double,
    val isDuplicate: Boolean,
    val duplicateOfTransactionId: String? = null
)

@Serializable
data class StatementImportRequest(
    val fileId: String,
    val mapping: ColumnMapping,
    val transactionIds: List<String>? = null // null = import all
)

@Serializable
data class StatementImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
    val totalAmount: Double,
    val transactions: List<Transaction>
)

@Serializable
data class ImportedTransactionSummary(
    val id: String,
    val date: String,
    val description: String,
    val amount: Double,
    val category: Category? = null
)
