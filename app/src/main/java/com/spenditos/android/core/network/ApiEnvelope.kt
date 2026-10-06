package com.spenditos.android.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Every Spenditos response body: `HttpJsonBody<T>` in
 * spenditos/packages/types/src/api/index.ts (`{ message?, data?, error?, details? }`).
 * `details` carries extras such as gamification info and stays untyped.
 */
@Serializable
data class ApiEnvelope<T>(
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
    val details: JsonElement? = null,
)
