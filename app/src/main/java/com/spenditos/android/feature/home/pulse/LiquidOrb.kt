package com.spenditos.android.feature.home.pulse

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** True unless the user turned animations off (system "remove animations" / animator scale 0). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }.getOrDefault(true)
    }
}

/**
 * The spending orb (iOS `LiquidOrb`): a glass sphere whose liquid is the share of this month's income
 * still left. It fills on appear, the surface waves and bubbles rise; still when animations are off.
 */
@Composable
fun LiquidOrb(
    level: Float,
    tint: Color,
    label: String,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 124.dp,
) {
    val animate = rememberAnimationsEnabled()
    val target = level.coerceIn(0f, 1f)
    val fill = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) { if (animate) fill.animateTo(1f, tween(1600)) else fill.snapTo(1f) }
    val time by if (animate) {
        rememberInfiniteTransition(label = "orb").animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(tween(1_000_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orbTime",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    val eased = 1f - (1f - fill.value).pow(3)

    Box(
        modifier = modifier.size(size)
            .dropShadow(CircleShape, Shadow(radius = 44.dp, color = tint, offset = DpOffset(0.dp, 8.dp), alpha = 0.55f))
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.15f))), CircleShape)
            .clearAndSetSemantics { contentDescription = "$label $caption" }
            .testTag("home.orb"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) { drawLiquid(time, target * eased, tint) }
        // Glass highlight.
        Box(
            Modifier.align(Alignment.TopStart)
                .size(width = size * 0.42f, height = size * 0.2f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)), CircleShape),
        )
        // The orb has a fixed size, so its text must not grow with the system font scale.
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.19f / fontScale).sp, maxLines = 1)
            Text(caption, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.09f / fontScale).sp, maxLines = 1)
        }
    }
}

private fun DrawScope.drawLiquid(time: Float, level: Float, tint: Color) {
    val w = size.width
    val h = size.height
    val surface = h * (1f - level)
    val amplitude = if (level > 0.02f && level < 0.98f) h * 0.035f else 0f

    fun wave(phase: Float, frequency: Float): Path = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, surface + (sin(x / w * PI.toFloat() * 2f * frequency + phase) * amplitude))
            x += 2f
        }
        lineTo(w, h)
        close()
    }

    // Back wave, lighter and out of phase.
    drawPath(wave(time * 1.3f + 1.6f, 1.4f), Color.White.copy(alpha = 0.28f))
    // Front liquid.
    drawPath(
        wave(time * 1.9f, 1.1f),
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.98f), tint.copy(alpha = 0.7f)), startY = surface, endY = h),
    )
    if (level <= 0.08f) return
    // Bubbles rising through the liquid.
    for (index in 0 until 7) {
        val seed = index * 0.37f
        val rise = (time * (0.12f + 0.03f * (index % 3)) + seed) % 1f
        val y = h - rise * (h - surface)
        if (y <= surface + 4f) continue
        val x = w * (0.2f + 0.6f * abs(sin(seed * 7.3f))) + sin(time * 2f + seed * 9f) * 3f
        drawCircle(Color.White.copy(alpha = 0.45f), radius = 1.6f + (index % 3), center = Offset(x, y))
    }
}
