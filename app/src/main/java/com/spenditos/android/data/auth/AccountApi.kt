package com.spenditos.android.data.auth

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.data.model.ConfirmPasswordRequest
import com.spenditos.android.data.model.ConfirmRegistrationRequest
import com.spenditos.android.data.model.ForgotPasswordRequest
import com.spenditos.android.data.model.RegisterAccountRequest
import com.spenditos.android.data.model.RegisterAccountResponse
import com.spenditos.android.data.model.ResendConfirmationRequest
import retrofit2.http.Body
import retrofit2.http.POST

/** Public account routes declared in `packages/functions/src/auth.ts:59-109`. */
interface AccountApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterAccountRequest): ApiEnvelope<RegisterAccountResponse>

    /** Body is intentionally ignored: already-confirmed responses may have no `data`. */
    @POST("auth/confirm-registration")
    suspend fun confirmRegistration(@Body body: ConfirmRegistrationRequest)

    @POST("auth/resend-code")
    suspend fun resendCode(@Body body: ResendConfirmationRequest)

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest)

    @POST("auth/confirm-password")
    suspend fun confirmPassword(@Body body: ConfirmPasswordRequest)
}
