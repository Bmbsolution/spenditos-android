package com.spenditos.android.data.receipt

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.ReceiptAnalysisStatus
import com.spenditos.android.data.model.ReceiptAnalyzeData
import com.spenditos.android.data.model.ReceiptAnalyzeRequest
import com.spenditos.android.data.model.ReceiptUploadData
import com.spenditos.android.data.model.ReceiptUploadRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ReceiptApi {
    @POST("transactions/upload-url")
    suspend fun uploadUrl(
        @Header("X-Spenditos-Trace-Id") traceId: String,
        @Body body: ReceiptUploadRequest,
    ): ApiEnvelope<ReceiptUploadData>

    @POST("transactions/analyze-image")
    suspend fun analyzeImage(
        @Header("X-Spenditos-Trace-Id") traceId: String,
        @Body body: ReceiptAnalyzeRequest,
    ): ApiEnvelope<ReceiptAnalyzeData>

    @GET("transactions/analysis/{id}")
    suspend fun analysisStatus(
        @Header("X-Spenditos-Trace-Id") traceId: String,
        @Path("id") analysisId: String,
    ): ApiEnvelope<ReceiptAnalysisStatus>
}
