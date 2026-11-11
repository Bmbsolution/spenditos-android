package com.spenditos.android.data.restore

import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.network.ApiEnvelope
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Tag

/**
 * The four restore routes (packages/functions/src/api/account-restore.ts). Every answer is
 * `{ data: RestoreSession }`; errors are `{ message, error }` where `error` is the code
 * (`BackupTooLarge`, `TooManyRestores`, `RestoreInProgress`, `RestoreExpired`, ...).
 * Each call carries the [SessionBinding] of the account that started the restore, so nothing
 * is ever sent under another account's token.
 */
interface RestoreApi {
    /** `POST /user/restore` (201). */
    @POST("user/restore")
    suspend fun start(@Body body: StartRestoreRequest, @Tag binding: SessionBinding?): ApiEnvelope<RestoreSession>

    /** `PUT /user/restore/{id}/parts/{index}`: raw `application/octet-stream` bytes. Re-sending overwrites, so retries are safe. */
    @PUT("user/restore/{id}/parts/{index}")
    suspend fun putPart(
        @Path("id") id: String,
        @Path("index") index: Int,
        @Body bytes: RequestBody,
        @Tag binding: SessionBinding?,
    ): ApiEnvelope<RestoreSession>

    /** `GET /user/restore/{id}`. */
    @GET("user/restore/{id}")
    suspend fun get(@Path("id") id: String, @Tag binding: SessionBinding?): ApiEnvelope<RestoreSession>

    /** `POST /user/restore/{id}/complete` (202); safe to repeat. */
    @POST("user/restore/{id}/complete")
    suspend fun complete(@Path("id") id: String, @Tag binding: SessionBinding?): ApiEnvelope<RestoreSession>
}
