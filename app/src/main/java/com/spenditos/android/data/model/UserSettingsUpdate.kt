package com.spenditos.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `UpdateUserSettingsSchema`, packages/core/src/schemas/user.ts:160-176 and
 * its handler in packages/functions/src/user.ts:166-202. `displayName` is a
 * JsonElement so an explicit JSON null can clear the nullable server field.
 */
@Serializable
data class UserSettingsUpdate(
    val currency: String,
    val displayName: JsonElement,
    /** `en` | `fr`: the app language mirrored to the server (omitted when null, so older servers are unaffected). */
    val language: String? = null,
)

/** `ChangePasswordRequestSchema`, packages/functions/src/user.ts:736-739. */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
