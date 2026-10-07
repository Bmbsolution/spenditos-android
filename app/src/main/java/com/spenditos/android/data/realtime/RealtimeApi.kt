package com.spenditos.android.data.realtime

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.WsTicketResponse
import retrofit2.http.POST

/** Authenticated endpoint used to mint a single-use websocket connection ticket. */
interface RealtimeApi {
    @POST("ws-ticket")
    suspend fun createTicket(): ApiEnvelope<WsTicketResponse>
}
