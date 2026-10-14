package com.spenditos.android.feature.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spenditos.android.data.merchants.RuleAsk
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.glassCardModifier
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

/**
 * iOS `RuleAskSheet`: "Always file {merchant} under {category}?". Shown once after the user picks a category for a row
 * or group in a scan review. The past-transactions checkbox appears only when the server says there is something to
 * update.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleAskSheet(
    ask: RuleAsk,
    pastCount: Int?,
    alsoUpdatePast: Boolean,
    isSaving: Boolean,
    onToggleUpdatePast: () -> Unit,
    onAlways: () -> Unit,
    onOnce: () -> Unit,
) {
    // Like iOS `interactiveDismissDisabled(isSavingRule)`: no swiping the sheet away mid-save.
    val sheetState = rememberModalBottomSheetState(confirmValueChange = { !isSaving })
    ModalBottomSheet(onDismissRequest = { if (!isSaving) onOnce() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.l).testTag("rule-ask"),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                "Always file ${ask.merchant} under ${ask.categoryName}?",
                style = IosText.headline,
                color = IosColors.textPrimary,
                modifier = Modifier.testTag("rule-ask-title"),
            )
            Text(
                "Next time Spenditos sees ${ask.merchant} in a scan or import, it files it under ${ask.categoryName} for you.",
                style = IosText.callout,
                color = IosColors.textSecondary,
            )
            if (pastCount != null && pastCount > 0) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = alsoUpdatePast, role = Role.Checkbox, onValueChange = { onToggleUpdatePast() })
                        .semantics { stateDescription = if (alsoUpdatePast) "On" else "Off" }
                        .testTag("rule-ask-past"),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Icon(
                        SfSymbols.icon(if (alsoUpdatePast) "checkmark.square.fill" else "square"),
                        contentDescription = null,
                        tint = if (alsoUpdatePast) IosColors.spendOrange else IosColors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Also update $pastCount past ${ask.merchant} ${if (pastCount == 1) "transaction" else "transactions"}",
                        style = IosText.callout,
                        color = IosColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                // iOS `LoadingButton`: 52pt tall, radius M; a spinner replaces the title while the rule saves.
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !isSaving, role = Role.Button, onClick = onAlways)
                        .testTag("rule-ask-always"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Always", style = IosText.headline, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(52.dp).then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                        .clickable(enabled = !isSaving, role = Role.Button, onClick = onOnce)
                        .testTag("rule-ask-once"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Just this once", style = IosText.headline, color = IosColors.textPrimary)
                }
            }
        }
    }
}

/** iOS `ruleBanner`: what "Always" did (or that it could not be saved), above the Save footer; tap to dismiss. */
@Composable
internal fun RuleBanner(message: RuleMessage?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        // Keep the last text while the exit animation runs.
        val shown = message ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).fillMaxWidth()
                .then(glassCardModifier(RoundedCornerShape(Radius.m), shadowAlpha = 0.1f))
                .clickable(onClick = onDismiss)
                .padding(Spacing.m)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("review-rule-toast"),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                SfSymbols.icon(if (shown.isError) "exclamationmark.circle.fill" else "checkmark.circle.fill"),
                contentDescription = null,
                tint = if (shown.isError) IosColors.warning else IosColors.income,
                modifier = Modifier.size(18.dp),
            )
            Text(shown.text, style = IosText.subheadline, color = IosColors.textPrimary, modifier = Modifier.weight(1f), overflow = TextOverflow.Clip)
        }
    }
}
