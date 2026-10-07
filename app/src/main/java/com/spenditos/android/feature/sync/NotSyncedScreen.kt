package com.spenditos.android.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

@Composable
fun NotSyncedScreen(onBack: () -> Unit, viewModel: NotSyncedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    val pending = state.items.filterNot { it.isFailed }
    val failed = state.items.filter { it.isFailed }

    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize().testTag("notSynced.list"),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IosColors.spendOrange)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { failed.filter { it.canRetry }.forEach { viewModel.retry(it.id) } },
                        enabled = failed.any { it.canRetry },
                        modifier = Modifier.padding(end = Spacing.s).testTag("notSynced.retryAll"),
                    ) { Text("Retry", style = IosText.body, color = IosColors.spendOrange) }
                }
            }
            item { LargeTitle("Offline Changes") }

            if (pending.isNotEmpty()) {
                item { SectionHeader("Waiting to sync") }
                item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                        pending.forEachIndexed { index, item ->
                            QueuedTransactionRow(
                                item = item,
                                currency = state.currency,
                                onRetry = { viewModel.retry(item.id) },
                                onDiscard = { confirmDiscard = item.id },
                            )
                            if (index < pending.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                        }
                    }
                }
                item {
                    Text(
                        "These transactions are stored on this device and keep their idempotency key across retries.",
                        style = IosText.footnote,
                        color = IosColors.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }

            if (failed.isNotEmpty()) {
                item { SectionHeader("Needs attention") }
                item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                        failed.forEachIndexed { index, item ->
                            QueuedTransactionRow(
                                item = item,
                                currency = state.currency,
                                onRetry = { viewModel.retry(item.id) },
                                onDiscard = { confirmDiscard = item.id },
                            )
                            if (index < failed.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Spacing.m), color = IosColors.separator)
                        }
                    }
                }
                item {
                    Text(
                        "Retryable connection failures also retry automatically when you reconnect. Validation and category errors stay here until you fix or discard them.",
                        style = IosText.footnote,
                        color = IosColors.textSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }

            if (pending.isEmpty() && failed.isEmpty()) {
                item {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                        Column(
                            Modifier.fillMaxWidth().padding(Spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Icon(SfSymbols.icon("checkmark.circle"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(42.dp))
                            Text("All Changes Synced", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                            Text("New offline transactions will appear here until they reach the server.", style = IosText.subheadline, color = IosColors.textSecondary)
                        }
                    }
                }
            }
        }
    }

    confirmDiscard?.let { id ->
        val item = state.items.firstOrNull { it.id == id }
        val description = item?.description?.takeIf(String::isNotEmpty) ?: "Transaction"
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("Discard this saved transaction?", style = IosText.headline) },
            text = { Text("$description will be removed from this device and cannot be retried.", style = IosText.body) },
            confirmButton = { TextButton(onClick = { viewModel.discard(id); confirmDiscard = null }) { Text("Discard", color = IosColors.expense) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueuedTransactionRow(item: QueuedItemUi, currency: String, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val finance = LocalFinanceColors.current
    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(item.description.ifBlank { "Transaction" }, style = IosText.body.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                Text(if (item.isFailed) "Sync failed" else "Saved locally", style = IosText.subheadline.w(FontWeight.SemiBold), color = if (item.isFailed) finance.expense else IosColors.spendOrange)
            }
            Text(Money.format(item.amount, currency), style = IosText.rounded(17, FontWeight.Medium), color = IosColors.textPrimary)
        }

        Text(
            item.message ?: if (item.isFailed) "This change needs attention before it can sync." else "Waiting for a connection to sync.",
            style = IosText.subheadline,
            color = IosColors.textSecondary,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(SfSymbols.icon("calendar"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(16.dp))
            Text(item.date, style = IosText.subheadline, color = IosColors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (item.isFailed && item.canRetry) {
                TextButton(onClick = onRetry) { Text("Retry", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.spendOrange) }
            }
            TextButton(onClick = onDiscard) { Text("Discard", style = IosText.subheadline.w(FontWeight.SemiBold), color = finance.expense) }
        }
    }
}
