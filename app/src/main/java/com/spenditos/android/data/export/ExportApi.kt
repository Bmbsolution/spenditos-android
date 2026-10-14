package com.spenditos.android.data.export

import com.spenditos.android.core.auth.SessionBinding
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Tag

interface ExportApi {
    /**
     * `GET /transactions/export` (packages/functions/src/api/export.ts): raw CSV text, not the `{data}` envelope.
     * [sessionBinding] tags the request with the account that asked for the file, so the auth interceptor
     * refuses to send it (or its 401 retry) with another account's token.
     */
    @Streaming
    @GET("transactions/export")
    suspend fun export(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
        @Query("categoryIds") categoryIds: String? = null,
        @Query("format") format: String = "csv",
        @Tag sessionBinding: SessionBinding? = null,
    ): Response<ResponseBody>

    /**
     * `GET /user/export` ("Download my data"): one JSON document, no `{data}` envelope. The server gzips it
     * when the request advertises gzip; OkHttp adds that header itself and gunzips transparently, so
     * never set `Accept-Encoding` by hand. Errors are `{error: "ExportFailed", message}`.
     */
    @Streaming
    @GET("user/export")
    suspend fun fullCopy(@Tag sessionBinding: SessionBinding? = null): Response<ResponseBody>
}
