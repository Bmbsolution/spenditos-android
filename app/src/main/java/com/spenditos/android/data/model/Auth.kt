package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

// /auth/* shapes are handler-local zod in spenditos/packages/functions/src/auth.ts
// (no packages/types export). Lines refer to backend origin/develop 4acac33.

/** `LoginRequestSchema` auth.ts:68. */
@Serializable
data class LoginRequest(val email: String, val password: String)

/** `LoginResponse` auth.ts:140. */
@Serializable
data class LoginResponse(val accessToken: String, val idToken: String, val refreshToken: String)

/** auth.ts:764 refresh request; response auth.ts:777 (refresh token is not rotated). */
@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val accessToken: String, val idToken: String)

/** `LogoutRequestSchema` auth.ts:76. */
@Serializable
data class LogoutRequest(val refreshToken: String)
