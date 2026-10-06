package com.spenditos.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand and semantic colors from iOS Shared/Extensions/Color+Theme.swift.
private val Terracotta = Color(0xFFB85F42)
private val TerracottaDark = Color(0xFFE08A6C)
private val Gold = Color(0xFFD6B27A)

/** Finance semantics: never use the brand color to mean "money in/out". */
@Immutable
data class FinanceColors(val income: Color, val expense: Color, val warning: Color, val neutral: Color)

private val LightFinance = FinanceColors(Color(0xFF1F7F47), Color(0xFFC0392B), Color(0xFFF59E0B), Color(0xFF9E9E9E))
private val DarkFinance = FinanceColors(Color(0xFF3DC47D), Color(0xFFFF6B5E), Color(0xFFF59E0B), Color(0xFF9E9E9E))

val LocalFinanceColors = staticCompositionLocalOf { LightFinance }

// Neutral surfaces like iOS grouped backgrounds; the brand color stays an accent.
private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3DB),
    onPrimaryContainer = Color(0xFF4A1F12),
    secondary = Gold,
    secondaryContainer = Color(0xFFF3E4DE),
    onSecondaryContainer = Color(0xFF4A1F12),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outlineVariant = Color(0xFFE0E0E5),
)

private val DarkColors = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = Color(0xFF2B140C),
    primaryContainer = Color(0xFF5A2E20),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Gold,
    secondaryContainer = Color(0xFF3D2A22),
    onSecondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
)

@Composable
fun SpenditosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides if (darkTheme) DarkFinance else LightFinance) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

object Spacing {
    val xs = androidx.compose.ui.unit.Dp(4f)
    val s = androidx.compose.ui.unit.Dp(8f)
    val m = androidx.compose.ui.unit.Dp(16f)
    val l = androidx.compose.ui.unit.Dp(24f)
    val screen = androidx.compose.ui.unit.Dp(20f)
}
