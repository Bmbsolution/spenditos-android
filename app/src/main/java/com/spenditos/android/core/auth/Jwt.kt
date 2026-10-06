package com.spenditos.android.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** Reads unverified JWT claims (the server verifies; the client only needs exp/sub/email). */
object Jwt {
    fun claims(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val payload = Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))
            Json.parseToJsonElement(payload.decodeToString()).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    fun subject(token: String): String? = claims(token)?.get("sub")?.jsonPrimitive?.contentOrNull

    fun email(token: String): String? = claims(token)?.get("email")?.jsonPrimitive?.contentOrNull

    /** True when the token expires within [skewSeconds] (iOS uses 300 s) or can't be read. */
    fun isExpired(token: String, nowEpochSeconds: Long, skewSeconds: Long = 300): Boolean {
        val exp = claims(token)?.get("exp")?.jsonPrimitive?.longOrNull ?: return true
        return nowEpochSeconds > exp - skewSeconds
    }
}
