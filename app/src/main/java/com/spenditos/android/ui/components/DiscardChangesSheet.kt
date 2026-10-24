package com.spenditos.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

/**
 * Small bottom card shown when a filled-in form is closed (iOS `DiscardChangesSheet`).
 * Keeping the work is the primary action; discarding is a quiet destructive text button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscardChangesSheet(
    title: String,
    message: String,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    keepText: String = "Keep editing",
    discardText: String = "Discard",
) {
    ModalBottomSheet(
        onDismissRequest = onKeep,
        shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier.testTag("discard.sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = Spacing.screen).padding(top = Spacing.s, bottom = Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(IosColors.expense.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SfSymbols.icon("trash"), contentDescription = null, tint = IosColors.expense, modifier = Modifier.size(24.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = IosText.title3, color = IosColors.textPrimary, textAlign = TextAlign.Center)
                Text(message, style = IosText.body, color = IosColors.textSecondary, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PillButton(keepText, onClick = onKeep, modifier = Modifier.fillMaxWidth().testTag("discard.keep"))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onDiscard).testTag("discard.confirm"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(discardText, style = IosText.headline, color = IosColors.expense)
                }
            }
        }
    }
}
