package com.bmbsolution.spenditos.data.remote.api

import com.bmbsolution.spenditos.data.model.*
import retrofit2.http.*
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ImportApi {
    @Multipart
    @POST("import/csv")
    suspend fun uploadCSV(@Part file: MultipartBody.Part): ApiResponse<CSVImport>

    @GET("import/{importId}/items")
    suspend fun getImportItems(
        @Path("importId") importId: String
    ): ApiResponse<List<ImportItem>>

    @PATCH("import/{importId}/mapping")
    suspend fun updateMapping(
        @Path("importId") importId: String,
        @Body request: ImportMappingUpdate
    ): ApiResponse<CSVImport>

    @POST("import/{importId}/finalize")
    suspend fun finalizeImport(
        @Path("importId") importId: String,
        @Body request: ImportFinalizeRequest
    ): ApiResponse<ImportFinalizeResponse>
}
