package com.spenditos.android.feature.pro

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the Pro screen from any locked-feature card; null where purchases are not available (no store key). */
val LocalOpenPro = staticCompositionLocalOf<(() -> Unit)?> { null }
