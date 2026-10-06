package com.spenditos.android.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.Money
import com.spenditos.android.ui.theme.Spacing

@Composable
fun NotSyncedScreen(onBack: () -> Unit, viewModel: NotSyncedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Not synced") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("notSynced.list"),
            contentPadding = PaddingValues(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.items.isEmpty()) {
                item { Text("Everything is synced.", style = MaterialTheme.typography.bodyLarge) }
            }
            items(state.items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.m)) {
                        Text(item.description.ifBlank { "Transaction" }, style = MaterialTheme.typography.titleMedium)
                        Text("${Money.format(item.amount, state.currency)} · ${item.date}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            item.message ?: if (item.isFailed) "Not synced." else "Waiting to sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (item.canRetry) TextButton(onClick = { viewModel.retry(item.id) }) { Text("Retry") }
                            TextButton(onClick = { confirmDiscard = item.id }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
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
