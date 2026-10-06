package com.spenditos.android.data.capture

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.CaptureAnalysisStatus
import com.spenditos.android.data.model.CaptureAnalyzeData
import com.spenditos.android.data.model.CaptureAnalyzeRequest
import com.spenditos.android.data.model.CaptureBulkRequest
import com.spenditos.android.data.model.CaptureBulkResponse
import com.spenditos.android.data.model.CaptureCloseData
import com.spenditos.android.data.model.CaptureUploadData
import com.spenditos.android.data.model.CaptureUploadRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST

interface CaptureApi {
    @POST("transactions/upload-url")
    suspend fun uploadUrl(@Header("X-Spenditos-Trace-Id") traceId: String, @Body body: CaptureUploadRequest): ApiEnvelope<CaptureUploadData>

    @POST("transactions/analyze-audio")
    suspend fun analyze(@Header("X-Spenditos-Trace-Id") traceId: String, @Body body: CaptureAnalyzeRequest): ApiEnvelope<CaptureAnalyzeData>

    @GET("transactions/analysis/{id}")
    suspend fun analysisStatus(@Header("X-Spenditos-Trace-Id") traceId: String, @Path("id") analysisId: String): ApiEnvelope<CaptureAnalysisStatus>

    @POST("transactions/analyze-audio")
    suspend fun closeSession(@Header("X-Spenditos-Trace-Id") traceId: String, @Body body: CaptureAnalyzeRequest): ApiEnvelope<CaptureCloseData>

    @POST("transactions/bulk")
    suspend fun createBulk(@Body body: CaptureBulkRequest): ApiEnvelope<CaptureBulkResponse>
}
