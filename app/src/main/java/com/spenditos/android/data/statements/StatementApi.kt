package com.spenditos.android.data.statements

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementAnalyzeRequest
import com.spenditos.android.data.model.StatementImportRequest
import com.spenditos.android.data.model.StatementImportResult
import com.spenditos.android.data.model.StatementPreviewRequest
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.StatementUploadUrlRequest
import com.spenditos.android.data.model.StatementUploadUrlResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface StatementApi {
    @POST("transactions/upload-url")
    suspend fun uploadUrl(@Body body: StatementUploadUrlRequest): ApiEnvelope<StatementUploadUrlResponse>

    @POST("statements/analyze-spreadsheet")
    suspend fun analyze(@Body body: StatementAnalyzeRequest): ApiEnvelope<StatementAnalysisResult>

    @POST("statements/preview-spreadsheet")
    suspend fun preview(@Body body: StatementPreviewRequest): ApiEnvelope<StatementPreviewResult>

    @POST("statements/import-spreadsheet")
    suspend fun importSpreadsheet(
        @Body body: StatementImportRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<StatementImportResult>
}
