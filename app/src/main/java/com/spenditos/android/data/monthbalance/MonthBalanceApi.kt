package com.spenditos.android.data.monthbalance

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * `GET /month-balance?month=YYYY-MM` and `PUT /month-balance` (docs/features/month-balance.md; iOS
 * `MonthBalanceEndpoints`). `month` is the device's LOCAL month. JWT; the user comes from the token.
 * The PUT carries a [SessionBinding] tag so it is refused, never sent under another account, if the
 * signed-in account changes while it is being prepared.
 */
interface MonthBalanceApi {
    @GET("month-balance")
    suspend fun get(@Query("month") month: String): ApiEnvelope<MonthBalanceData>

    @PUT("month-balance")
    suspend fun set(
        @Body body: SetMonthBalanceChoiceRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<MonthBalanceData>

    @PUT("month-balance")
    suspend fun pay(
        @Body body: PayMonthBalanceRequest,
        @Tag sessionBinding: SessionBinding? = null,
    ): ApiEnvelope<MonthBalanceData>
}
