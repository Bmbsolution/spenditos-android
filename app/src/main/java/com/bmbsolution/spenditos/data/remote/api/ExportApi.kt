package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*

interface ExportApi {
    @GET("export/formats")
    suspend fun getFormats(): ApiResponse<List<ExportFormat>>

    @POST("export")
    suspend fun export(@Body request: ExportRequest): ApiResponse<ExportResult>
}
