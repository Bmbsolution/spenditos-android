package com.spenditos.android.core.network

import kotlinx.serialization.json.Json

/**
 * The one Json instance for wire models.
 * - ignoreUnknownKeys: new server fields never break old clients.
 * - coerceInputValues: an unknown enum value decodes to the property's default
 *   (declare enums as `val x: MyEnum? = null` or with an explicit default).
 * - explicitNulls = false: omit nulls when encoding; missing keys decode to defaults.
 * - encodeDefaults = true: request defaults (e.g. `source = "manual"`) are sent.
 */
val SpenditosJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
