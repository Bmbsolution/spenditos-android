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
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onOpenNotSynced: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenStatementImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text("Account") },
                    supportingContent = { Text(state.email ?: "Signed in") },
                    modifier = Modifier.testTag("settings.account"),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Profile") },
                    supportingContent = { Text("Name and currency") },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenProfile).testTag("settings.profile"),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Categories") },
                    supportingContent = { Text("Manage income and expense categories") },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenCategories).testTag("settings.categories"),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Import statement") },
                    supportingContent = { Text("Bring in transactions from a bank CSV or Excel file") },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenStatementImport).testTag("settings.importStatement"),
                )
            }
            item { HorizontalDivider() }
            if (state.lockAvailable) {
                item {
                    val activity = androidx.compose.ui.platform.LocalContext.current.let { generateSequence(it) { c -> (c as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<androidx.fragment.app.FragmentActivity>().firstOrNull() }
                    ListItem(
                        headlineContent = { Text("App lock") },
                        supportingContent = { Text(state.lockMessage ?: "Ask for your fingerprint, face or screen lock when you return") },
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = state.lockEnabled,
                                onCheckedChange = { wanted -> activity?.let { viewModel.setLock(it, wanted) } },
                                modifier = Modifier.testTag("settings.appLock.switch"),
                            )
                        },
                        modifier = Modifier.testTag("settings.appLock"),
                    )
                }
                item { HorizontalDivider() }
            }
            item {
                ListItem(
                    headlineContent = { Text("Change password") },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenChangePassword).testTag("settings.changePassword"),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Not synced") },
                    supportingContent = { Text(if (state.unsyncedCount == 0) "Everything is synced" else "${state.unsyncedCount} waiting") },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenNotSynced).testTag("settings.notSynced"),
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    headlineContent = { Text("Environment") },
                    supportingContent = { Text("${state.envName} (${state.stage})") },
                    modifier = Modifier.testTag("settings.environment"),
                )
            }
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
            confirmButton = {
                TextButton(
                    onClick = { confirmSignOut = false; viewModel.signOut() },
                    modifier = Modifier.testTag("settings.signOut.confirm"),
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }, modifier = Modifier.testTag("settings.signOut.cancel")) { Text("Cancel") }
            },
        )
    }
}
