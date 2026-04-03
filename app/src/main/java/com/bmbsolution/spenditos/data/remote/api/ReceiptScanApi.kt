package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ReceiptScanApi {
    @POST("receipt-scan/upload-url")
    suspend fun getUploadUrl(): ApiResponse<UploadUrlResponse>

    @Multipart
    @POST("receipt-scan/upload")
    suspend fun uploadReceipt(@Part file: MultipartBody.Part): ApiResponse<AnalysisResponse>

    @POST("receipt-scan/analyze")
    suspend fun analyzeReceipt(@Body request: ReceiptAnalysisRequest): ApiResponse<AnalysisResponse>

    @GET("receipt-scan/status/{analysisId}")
    suspend fun getAnalysisStatus(@Path("analysisId") analysisId: String): ApiResponse<AnalysisStatus>
}
