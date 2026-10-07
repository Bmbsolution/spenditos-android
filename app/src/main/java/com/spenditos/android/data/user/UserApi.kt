package com.spenditos.android.data.user

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.ChangePasswordRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface UserApi {
    @GET("user/settings")
    suspend fun settings(): ApiEnvelope<UserSettings>

    @PUT("user/settings")
    suspend fun updateSettings(@Body body: UserSettingsUpdate): ApiEnvelope<UserSettings>

    @POST("user/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    /** `DELETE /user/account` (sst.config.ts:128): marks the account PENDING_DELETION and disables login; 200. */
    @DELETE("user/account")
    suspend fun deleteAccount()
}
