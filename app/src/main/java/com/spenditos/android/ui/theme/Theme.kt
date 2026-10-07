package com.spenditos.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.spenditos.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    // iOS secondaryLabel: #3C3C43 at 60%.
    onSurfaceVariant = Color(0x993C3C43),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    // iOS separator: #3C3C43 at 29%.
    outlineVariant = Color(0x4A3C3C43),
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
    onBackground = Color.White,
    surface = Color(0xFF000000),
    onSurface = Color.White,
    // iOS dark secondaryLabel: #EBEBF5 at 60%; separator #545458 at 60%.
    onSurfaceVariant = Color(0x99EBEBF5),
    outlineVariant = Color(0x99545458),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
)

/** iOS `AppTheme` corner radii (10 / 16 / 24 / 32) so every Material surface rounds like the iOS app. */
object Radius {
    val s = 10.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.s),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/**
 * iOS text styles (Typography.swift): largeTitle black 34, title2 bold 22, title3 semibold 20, headline
 * semibold 17, body 17, callout 16, subheadline 15, footnote 13. System sans, tight tracking like SF.
 */
/** Inter (SIL OFL, docs/licenses/Inter-OFL.txt) as the closest free match to SF Pro, as one variable font. */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold, FontWeight.Black).map { weight ->
        Font(R.font.inter, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private val IosTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

private fun Typography.withFont(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
fun SpenditosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalFinanceColors provides if (darkTheme) DarkFinance else LightFinance) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = IosShapes,
            typography = IosTypography.withFont(Inter),
            content = content,
        )
    }
}

/**
 * SwiftUI's text styles at the default Dynamic Type size, by their iOS names. Screens ported from iOS use
 * these 1:1: `.font(.caption.weight(.semibold))` becomes `IosText.caption.w(FontWeight.SemiBold)`.
 * Tracking follows Inter's dynamic metrics, which keep Inter as tight as SF Pro at each size.
 */
object IosText {
    val largeTitle = ios(34, 41)
    val title = ios(28, 34)
    val title2 = ios(22, 28)
    val title3 = ios(20, 25)
    val headline = ios(17, 22, FontWeight.SemiBold)
    val body = ios(17, 22)
    val callout = ios(16, 21)
    val subheadline = ios(15, 20)
    val footnote = ios(13, 18)
    val caption = ios(12, 16)
    val caption2 = ios(11, 13)

    /** `.system(size:weight:design: .rounded)` and `.monospacedDigit()`: tabular figures for amounts. */
    fun rounded(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle =
        ios(size, (size * 1.2f).toInt(), weight).copy(fontFeatureSettings = "tnum")

    /** `.system(size:weight:)`. */
    fun system(size: Int, weight: FontWeight = FontWeight.Normal): TextStyle = ios(size, (size * 1.2f).toInt(), weight)

    /** Inter draws ~6% larger than SF Pro at the same size (taller x-height, wider glyphs); match SF's apparent size. */
    private const val INTER_TO_SF = 0.94f

    private fun ios(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = Inter,
        fontSize = (size * INTER_TO_SF).sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = interTracking(size).em,
    )

    private fun interTracking(size: Int): Float = (-0.0223 + 0.185 * kotlin.math.exp(-0.1745 * size)).toFloat()
}

fun TextStyle.w(weight: FontWeight): TextStyle = copy(fontWeight = weight)

/** iOS label colors (`textPrimary`/`textSecondary`/`textTertiary` in Color+Theme.swift) and fixed accents. */
object IosColors {
    val textPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val textSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val textTertiary: Color @Composable get() = if (isSystemInDarkTheme()) Color(0x4DEBEBF5) else Color(0x4D3C3C43)
    val separator: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val spendOrange = Color(0xFFB85F42)
    val spendGold = Color(0xFFD6B27A)
    val info = Color(0xFF3B82F6)
    val warning = Color(0xFFF59E0B)
    val neutral = Color(0xFF9E9E9E)
    val income: Color @Composable get() = LocalFinanceColors.current.income
    val expense: Color @Composable get() = LocalFinanceColors.current.expense
}

object Spacing {
    val xs = androidx.compose.ui.unit.Dp(4f)
    val s = androidx.compose.ui.unit.Dp(8f)
    val m = androidx.compose.ui.unit.Dp(16f)
    val l = androidx.compose.ui.unit.Dp(24f)
    val xl = androidx.compose.ui.unit.Dp(32f)
    val screen = androidx.compose.ui.unit.Dp(20f)
}
