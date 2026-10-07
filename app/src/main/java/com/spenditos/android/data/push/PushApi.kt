package com.spenditos.android.data.push

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/** `RegisterDeviceTokenSchema`, packages/core/src/schemas/device-token.ts:22. */
@Serializable
data class DeviceTokenRequest(
    val token: String,
    val platform: String = "android",
    val bundleId: String? = null,
    val appVersion: String? = null,
)

/** `UnregisterDeviceTokenSchema`, packages/core/src/schemas/device-token.ts. */
@Serializable
data class UnregisterDeviceTokenRequest(val token: String)

interface PushApi {
    @POST("user/device-token")
    suspend fun register(@Body body: DeviceTokenRequest)

    @HTTP(method = "DELETE", path = "user/device-token", hasBody = true)
    suspend fun unregister(@Body body: UnregisterDeviceTokenRequest)
}
