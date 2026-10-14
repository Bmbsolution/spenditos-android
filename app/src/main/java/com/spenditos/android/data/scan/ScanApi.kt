package com.spenditos.android.data.scan

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.CompleteScanRequest
import com.spenditos.android.data.model.CreateScanRequest
import com.spenditos.android.data.model.CreateScanResponse
import com.spenditos.android.data.model.ReceiptUploadData
import com.spenditos.android.data.model.ReceiptUploadRequest
import com.spenditos.android.data.model.ScanBulkRequest
import com.spenditos.android.data.model.ScanBulkResponse
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanListData
import com.spenditos.android.data.model.ScanSummary
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/** The unified Scan engine endpoints (packages/functions/src/api/scan.ts) plus the object-form bulk save. */
interface ScanApi {
    /** `POST /transactions/upload-url` with `documentType: "scan"`; PDFs are not accepted (pages are rendered to JPEG first). */
    @POST("transactions/upload-url")
    suspend fun uploadUrl(
        @Header("X-Spenditos-Trace-Id") traceId: String,
        @Body body: ReceiptUploadRequest,
    ): ApiEnvelope<ReceiptUploadData>

    /** 202 `{scanId, status: "processing", total}`; 403 `QuotaExhausted` when the credits are used up. */
    @POST("scans")
    suspend fun create(
        @Header("X-Spenditos-Trace-Id") traceId: String,
        @Body body: CreateScanRequest,
    ): ApiEnvelope<CreateScanResponse>

    /** Summary while processing / failed / discarded; summary plus `items` once ready or saved. */
    @GET("scans/{id}")
    suspend fun get(@Path("id") scanId: String): ApiEnvelope<ScanDetail>

    /** Newest first, last 7 days, at most 20. [statuses] is a comma list; the comma stays unescaped. */
    @GET("scans")
    suspend fun list(@Query(value = "status", encoded = true) statuses: String): ApiEnvelope<ScanListData>

    /** Idempotent. 409 `ScanNotReady` while the scan is still processing. */
    @POST("scans/{id}/complete")
    suspend fun complete(@Path("id") scanId: String, @Body body: CompleteScanRequest): ApiEnvelope<ScanSummary>

    /** `POST /transactions/bulk`, object form `{transactions, source: "scan", scanId}`, at most 100 rows. */
    @POST("transactions/bulk")
    suspend fun bulk(
        @Body body: ScanBulkRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<ScanBulkResponse>
}
