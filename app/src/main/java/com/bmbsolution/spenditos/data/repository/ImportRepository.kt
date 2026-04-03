package com.bmbsolution.spenditos.data.repository

import com.bmbsolution.spenditos.data.model.*
import com.bmbsolution.spenditos.data.remote.api.ImportApi
import com.bmbsolution.spenditos.data.remote.api.StatementImportApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for CSV and Statement import operations
 */
@Singleton
class ImportRepository @Inject constructor(
    private val importApi: ImportApi,
    private val statementImportApi: StatementImportApi
) {

    // MARK: - CSV Import

    suspend fun uploadCSV(file: File): Result<CSVImport> {
        return try {
            val requestFile = file.asRequestBody("text/csv".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = importApi.uploadCSV(filePart)
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getImportItems(importId: String): Result<List<ImportItem>> {
        return try {
            val response = importApi.getImportItems(importId)
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMapping(importId: String, mapping: CSVMapping): Result<CSVImport> {
        return try {
            val response = importApi.updateMapping(importId, ImportMappingUpdate(mapping))
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finalizeImport(
        importId: String,
        itemIds: List<String>? = null,
        categoryMappings: Map<String, String>? = null
    ): Result<ImportFinalizeResponse> {
        return try {
            val response = importApi.finalizeImport(
                importId,
                ImportFinalizeRequest(itemIds, categoryMappings)
            )
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // MARK: - Statement Import

    suspend fun uploadStatementFile(file: File): Result<String> {
        return try {
            val mimeType = when (file.extension.lowercase()) {
                "csv" -> "text/csv"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = statementImportApi.uploadFile(filePart)
            Result.success(response.data!!["fileId"] ?: throw IllegalStateException("No fileId in response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeStatement(fileId: String, fileType: String): Result<StatementAnalysisResult> {
        return try {
            val response = statementImportApi.analyzeStatement(
                StatementAnalysisRequest(fileId, fileType)
            )
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStatementPreview(
        fileId: String,
        mapping: ColumnMapping
    ): Result<StatementPreviewResult> {
        return try {
            val response = statementImportApi.getPreview(
                StatementPreviewRequest(fileId, mapping)
            )
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importStatement(
        fileId: String,
        mapping: ColumnMapping,
        transactionIds: List<String>? = null
    ): Result<StatementImportResult> {
        return try {
            val response = statementImportApi.importStatement(
                StatementImportRequest(fileId, mapping, transactionIds)
            )
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detectRecurring(
        fileId: String,
        mapping: ColumnMapping
    ): Result<RecurringDetectionResult> {
        return try {
            val response = statementImportApi.detectRecurring(
                RecurringDetectionRequest(fileId, mapping)
            )
            Result.success(response.data!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
