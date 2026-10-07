package com.spenditos.android.data.export

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ExportApi {
    /** `GET /transactions/export` (packages/functions/src/api/export.ts): raw CSV text, not the `{data}` envelope. */
    @Streaming
    @GET("transactions/export")
    suspend fun export(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("categoryIds") categoryIds: String? = null,
        @Query("format") format: String = "csv",
    ): Response<ResponseBody>
}
