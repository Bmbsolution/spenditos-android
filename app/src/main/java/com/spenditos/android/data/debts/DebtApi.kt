package com.spenditos.android.data.debts

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * The seven debts routes (docs/features/debts.md "API"; iOS `DebtEndpoints`). Bodies are JSON
 * with the standard `{ data }` envelope in the response. Every read sends `asOf` = the device's
 * local date. A 207 `EntriesNotSynced` (debt saved, upcoming entries not) is a 2xx, so Retrofit
 * decodes its body like any success.
 *
 * Writes carry a [SessionBinding] tag so the request is refused, never sent under another
 * account, if the signed-in account changes while it is being prepared or retried.
 */
interface DebtApi {
    /** `GET /debts?asOf=YYYY-MM-DD`. */
    @GET("debts")
    suspend fun list(@Query("asOf") asOf: String): ApiEnvelope<ListDebtsResponse>

    /** `GET /debts/{debtId}?asOf=YYYY-MM-DD`. */
    @GET("debts/{debtId}")
    suspend fun get(@Path("debtId") debtId: String, @Query("asOf") asOf: String): ApiEnvelope<DebtDetail>

    /**
     * `POST /debts` (201). With an `Idempotency-Key` (1 to 128 characters, one per "Add debt"
     * attempt) a repeated request returns the same debt instead of creating a second one.
     */
    @POST("debts")
    suspend fun create(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateDebtRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<DebtDetail>

    /** `PATCH /debts/{debtId}`; `payment: null` stops the bill (see [UpdateDebtRequest]). */
    @PATCH("debts/{debtId}")
    suspend fun update(
        @Path("debtId") debtId: String,
        @Body body: UpdateDebtRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<DebtDetail>

    /** `DELETE /debts/{debtId}` (204, no body). */
    @DELETE("debts/{debtId}")
    suspend fun delete(@Path("debtId") debtId: String, @Tag sessionBinding: SessionBinding? = null)

    /** `POST /debts/{debtId}/adjustments` ("Correct balance", 201). */
    @POST("debts/{debtId}/adjustments")
    suspend fun createAdjustment(
        @Path("debtId") debtId: String,
        @Body body: CreateDebtAdjustmentRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<DebtDetail>

    /** `POST /debts/preview` (stateless: nothing is saved). */
    @POST("debts/preview")
    suspend fun preview(@Body body: DebtPreviewRequest): ApiEnvelope<DebtPreviewResponse>
}
