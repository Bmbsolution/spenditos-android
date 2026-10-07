package com.spenditos.android.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

@Composable
fun NotSyncedScreen(onBack: () -> Unit, viewModel: NotSyncedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize().testTag("notSynced.list"),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { LargeTitle("Not synced") }
            if (state.items.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                        Column(
                            Modifier.fillMaxWidth().padding(Spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                            Text("Everything is synced.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            items(state.items, key = { it.id }) { item ->
                val finance = LocalFinanceColors.current
                GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Icon(
                                if (item.isFailed) Icons.Filled.ErrorOutline else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = if (item.isFailed) finance.expense else MaterialTheme.colorScheme.primary,
                            )
                            Text(item.description.ifBlank { "Transaction" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            "${Money.format(item.amount, state.currency)} · ${item.date}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            item.message ?: if (item.isFailed) "Not synced." else "Waiting to sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isFailed) finance.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Row(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End)) {
                            if (item.canRetry) {
                                PillButton(text = "Retry", onClick = { viewModel.retry(item.id) }, filled = true)
                            }
                            PillButton(text = "Discard", onClick = { confirmDiscard = item.id }, filled = false)
                        }
                    }
                }
            }
        }
    }
    confirmDiscard?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text("Discard this transaction?") },
            text = { Text("It hasn't reached your account yet, so it will be gone for good.") },
            confirmButton = { TextButton(onClick = { viewModel.discard(id); confirmDiscard = null }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text("Keep") } },
        )
    }
}
