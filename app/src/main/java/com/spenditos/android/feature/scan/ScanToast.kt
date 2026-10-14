package com.spenditos.android.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import kotlinx.coroutines.delay

enum class ScanToastStyle { SUCCESS, ERROR, INFO }

/**
 * iOS `ToastModifier` (Shared/Components/ToastModifier.swift): a bodyM line with a style icon on a 15% tinted
 * surface, radiusM, 1pt 30% stroke, anchored 32pt above the bottom; it dismisses itself after 3 s.
 */
@Composable
fun ScanToast(text: String?, style: ScanToastStyle, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (text == null) return
    LaunchedEffect(text) {
        delay(3_000)
        onDismiss()
    }
    val tint = when (style) {
        ScanToastStyle.SUCCESS -> IosColors.income
        ScanToastStyle.ERROR -> IosColors.expense
        ScanToastStyle.INFO -> Color(0xFF2196F3)
    }
    val icon = when (style) {
        ScanToastStyle.SUCCESS -> "checkmark.circle.fill"
        ScanToastStyle.ERROR -> "xmark.circle.fill"
        ScanToastStyle.INFO -> "info.circle.fill"
    }
    val shape = RoundedCornerShape(Radius.m)
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.xl), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, shape)
                .background(tint.copy(alpha = 0.15f), shape)
                .border(1.dp, tint.copy(alpha = 0.3f), shape)
                .padding(horizontal = Spacing.m, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("scan.toast"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SfSymbols.icon(icon), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Text(text, style = IosText.callout, color = IosColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
