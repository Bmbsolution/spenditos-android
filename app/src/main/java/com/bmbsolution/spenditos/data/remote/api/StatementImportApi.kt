package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface StatementImportApi {
    @Multipart
    @POST("statement-import/upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): ApiResponse<Map<String, String>>

    @POST("statement-import/analyze")
    suspend fun analyzeStatement(
        @Body request: StatementAnalysisRequest
    ): ApiResponse<StatementAnalysisResult>

    @POST("statement-import/preview")
    suspend fun getPreview(
        @Body request: StatementPreviewRequest
    ): ApiResponse<StatementPreviewResult>

    @POST("statement-import/import")
    suspend fun importStatement(
        @Body request: StatementImportRequest
    ): ApiResponse<StatementImportResult>

    @POST("statement-import/detect-recurring")
    suspend fun detectRecurring(
        @Body request: RecurringDetectionRequest
    ): ApiResponse<RecurringDetectionResult>
}
