package com.spenditos.android.data.model

import kotlinx.serialization.Serializable

/** `MerchantMappingSchema`, packages/core/src/schemas/merchant-mapping.ts:6 (response of the list/create/update handlers). */
@Serializable
data class MerchantMapping(
    val id: String,
    val userId: String,
    val rawPattern: String,
    val cleanMerchantName: String,
    val categoryId: String? = null,
    val confidence: Double = 1.0,
    val source: String = "user",
    val isRegex: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** Body of `POST /settings/merchant-mappings`; the server sets `userId` and `source: "user"`. */
@Serializable
data class CreateMerchantMappingRequest(
    val rawPattern: String,
    val cleanMerchantName: String,
    val categoryId: String? = null,
    val isRegex: Boolean = false,
)

/** Body of `PUT /settings/merchant-mappings/{id}` (every field optional on the server; the app sends all). */
@Serializable
data class UpdateMerchantMappingRequest(
    val rawPattern: String,
    val cleanMerchantName: String,
    val categoryId: String? = null,
    val isRegex: Boolean = false,
)
