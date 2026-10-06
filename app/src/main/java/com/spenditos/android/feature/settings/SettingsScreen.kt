package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Phase 1 subset: account, sync queue, environment, sign out. The rest lands in Wave A. */
@Composable
fun SettingsScreen(onOpenNotSynced: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { ListItem(headlineContent = { Text("Account") }, supportingContent = { Text(state.email ?: "Signed in") }) }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Not synced") },
                    supportingContent = { Text(if (state.unsyncedCount == 0) "Everything is synced" else "${state.unsyncedCount} waiting") },
                    modifier = Modifier.clickable(onClick = onOpenNotSynced).testTag("settings.notSynced"),
                )
            }
            item { HorizontalDivider() }
            item { ListItem(headlineContent = { Text("Environment") }, supportingContent = { Text("${state.envName} (${state.stage})") }) }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { confirmSignOut = true }.testTag("settings.signOut"),
                )
            }
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out") },
            text = { Text(viewModel.signOutMessage()) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
