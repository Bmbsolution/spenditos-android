package com.spenditos.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/**
 * iOS `ErrorBanner` (Shared/Components/ErrorBanner.swift): expense-red circle icon, callout message in the
 * primary label color, optional "Retry" and dismiss ✕, on a 12% expense fill with a 45% 1dp stroke, radius 12.
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val expense = IosColors.expense
    Row(
        modifier.fillMaxWidth()
            .background(expense.copy(alpha = 0.12f), shape)
            .border(1.dp, expense.copy(alpha = 0.45f), shape)
            .padding(Spacing.m)
            .semantics { contentDescription = "Error: $message" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(SfSymbols.icon("exclamationmark.circle.fill"), contentDescription = null, tint = expense, modifier = Modifier.size(20.dp))
        Text(message, style = IosText.callout, color = IosColors.textPrimary, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                "Retry",
                style = IosText.subheadline.w(FontWeight.SemiBold),
                color = expense,
                modifier = Modifier.clickable(role = Role.Button, onClick = onRetry),
            )
        }
        if (onDismiss != null) {
            Box(
                // 28dp visual like iOS; the 48dp touch target overflows it without changing the layout.
                Modifier.size(28.dp).wrapContentSize(Alignment.Center, unbounded = true).requiredSize(48.dp)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "Dismiss error" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfSymbols.icon("xmark"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
