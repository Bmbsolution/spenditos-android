package com.spenditos.android.data.user

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.UserSettings
import retrofit2.http.GET

interface UserApi {
    @GET("user/settings")
    suspend fun settings(): ApiEnvelope<UserSettings>
}
