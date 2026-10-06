package com.spenditos.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

/** Parses the server's `#RRGGBB` category color; falls back to neutral grey. */
fun categoryColor(hex: String?): Color = runCatching {
    Color((hex?.takeIf { it.startsWith("#") } ?: "#$hex").toColorInt())
}.getOrDefault(Color(0xFF9E9E9E))
