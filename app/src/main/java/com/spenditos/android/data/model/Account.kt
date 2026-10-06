package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** Registration request and response from `packages/functions/src/auth.ts:59-63,126-129`. */
@Serializable
data class RegisterAccountRequest(val email: String, val password: String, val timezone: String? = null)

/** `RegisterResponse` from `packages/functions/src/auth.ts:126-129`. */
@Serializable
data class RegisterAccountResponse(val userId: String, val needsConfirmation: Boolean)

/** `ConfirmRegistrationRequestSchema` from `packages/functions/src/auth.ts:99-102`. */
@Serializable
data class ConfirmRegistrationRequest(val email: String, val code: String)

/** `ResendConfirmationCodeRequestSchema` from `packages/functions/src/auth.ts:107-109`. */
@Serializable
data class ResendConfirmationRequest(val email: String)

/** `ForgotPasswordRequestSchema` from `packages/functions/src/auth.ts:83-85`. */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** `ConfirmForgotPasswordRequestSchema` from `packages/functions/src/auth.ts:90-94`. */
@Serializable
data class ConfirmPasswordRequest(val email: String, val code: String, val password: String)
