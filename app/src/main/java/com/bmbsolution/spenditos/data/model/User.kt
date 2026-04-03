package com.bmbsolution.spenditos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null,
    val country: String? = null,
    val currency: String? = null,
    val mainIncome: Double? = null,
    val settings: UserSettings? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UserSettings(
    val notificationsEnabled: Boolean = true,
    val emailNotifications: Boolean = true,
    val darkMode: Boolean? = null, // null = system default
    val defaultCurrency: String = "USD",
    val weekStartsOn: Int = 0, // 0 = Sunday
    val defaultView: String = "dashboard" // "dashboard", "transactions", "budgets"
)

@Serializable
data class UserSettingsUpdateRequest(
    val notificationsEnabled: Boolean? = null,
    val emailNotifications: Boolean? = null,
    val darkMode: Boolean? = null,
    val defaultCurrency: String? = null,
    val weekStartsOn: Int? = null,
    val defaultView: String? = null
)

@Serializable
data class CountryDetectionResponse(
    val country: String,
    val currency: String,
    val detectedFrom: String // "ip", "locale", "default"
)

@Serializable
data class MainIncomePatchRequest(
    val mainIncome: Double
)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Int? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Int
)

@Serializable
data class PasswordChangeRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)
