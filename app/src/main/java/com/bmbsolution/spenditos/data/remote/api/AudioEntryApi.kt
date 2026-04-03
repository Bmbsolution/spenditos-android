package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AudioEntryApi {
    @Multipart
    @POST("audio-entry/upload")
    suspend fun uploadAudio(@Part file: MultipartBody.Part): ApiResponse<AudioAnalysisResponse>

    @GET("audio-entry/status/{analysisId}")
    suspend fun getAnalysisStatus(@Path("analysisId") analysisId: String): ApiResponse<AudioAnalysisResult>
}
