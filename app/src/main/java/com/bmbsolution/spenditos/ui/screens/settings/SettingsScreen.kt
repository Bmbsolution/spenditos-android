package com.bmbsolution.spenditos.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bmbsolution.spenditos.data.model.User
import com.bmbsolution.spenditos.data.model.UserSettings
import com.bmbsolution.spenditos.data.model.UserSettingsUpdateRequest
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onLogoutComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadUser()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Profile Section
                    item {
                        ProfileSection(user = uiState.user)
                    }

                    item { SectionDivider() }

                    // Subscription Section
                    item {
                        SubscriptionSection(onNavigateToPaywall = onNavigateToPaywall)
                    }

                    item { SectionDivider() }

                    // Preferences Section
                    item {
                        PreferencesSection(
                            settings = uiState.user?.settings,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            isSaving = uiState.isSaving
                        )
                    }

                    item { SectionDivider() }

                    // Import Data Section
                    item {
                        ImportDataSection(
                            onClick = { viewModel.showImportData() }
                        )
                    }

                    item { SectionDivider() }

                    // Income Settings
                    item {
                        IncomeSection(
                            mainIncome = uiState.user?.mainIncome,
                            onEditClick = { viewModel.showEditIncome() }
                        )
                    }

                    item { SectionDivider() }

                    // About Section
                    item {
                        AboutSection()
                    }

                    item { SectionDivider() }

                    // Logout
                    item {
                        LogoutButton(
                            onClick = { showLogoutConfirm = true },
                            isLoading = uiState.isLoggingOut
                        )
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to logout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout(onLogoutComplete)
                    }
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Data Sheet
    if (uiState.showImportData) {
        ImportDataSheet(
            onDismiss = { viewModel.hideImportData() },
            isPro = uiState.user?.settings?.let { false } ?: false // TODO: Check Pro status
        )
    }

    // Edit Income Dialog
    if (uiState.showEditIncome) {
        EditIncomeDialog(
            currentIncome = uiState.user?.mainIncome,
            onDismiss = { viewModel.hideEditIncome() },
            onSave = { viewModel.updateMainIncome(it) }
        )
    }
}

@Composable
private fun ProfileSection(user: User?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.name ?: user?.email ?: "User",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (user?.country != null) {
                    Text(
                        text = "${user.country} • ${user.currency ?: "USD"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSection(onNavigateToPaywall: () -> Unit) {
    Column {
        Text(
            text = "Subscription",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("Upgrade to Pro") },
                supportingContent = { Text("Unlock AI receipt scanning, CSV import, and more") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onNavigateToPaywall() }
            )
        }
    }
}

@Composable
private fun PreferencesSection(
    settings: UserSettings?,
    onUpdateSettings: (UserSettingsUpdateRequest) -> Unit,
    isSaving: Boolean
) {
    Column {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            // Notifications Toggle
            ListItem(
                headlineContent = { Text("Push Notifications") },
                supportingContent = { Text("Get notified about budgets, streaks, and insights") },
                leadingContent = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = settings?.notificationsEnabled ?: true,
                        onCheckedChange = {
                            onUpdateSettings(UserSettingsUpdateRequest(notificationsEnabled = it))
                        },
                        enabled = !isSaving
                    )
                }
            )

            HorizontalDivider()

            // Email Notifications Toggle
            ListItem(
                headlineContent = { Text("Email Notifications") },
                supportingContent = { Text("Weekly summaries and important updates") },
                leadingContent = {
                    Icon(Icons.Default.Email, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = settings?.emailNotifications ?: true,
                        onCheckedChange = {
                            onUpdateSettings(UserSettingsUpdateRequest(emailNotifications = it))
                        },
                        enabled = !isSaving
                    )
                }
            )

            HorizontalDivider()

            // Dark Mode Toggle
            var expanded by remember { mutableStateOf(false) }
            val darkModeOptions = listOf(
                null to "System Default",
                true to "Dark",
                false to "Light"
            )
            val currentDarkModeLabel = darkModeOptions.find { it.first == settings?.darkMode }?.second ?: "System Default"

            ListItem(
                headlineContent = { Text("Appearance") },
                supportingContent = { Text(currentDarkModeLabel) },
                leadingContent = {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                },
                trailingContent = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select theme")
                    }
                },
                modifier = Modifier.clickable { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                darkModeOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdateSettings(UserSettingsUpdateRequest(darkMode = value))
                            expanded = false
                        }
                    )
                }
            }

            HorizontalDivider()

            // Default View
            ListItem(
                headlineContent = { Text("Default View") },
                supportingContent = { Text(settings?.defaultView?.capitalize() ?: "Dashboard") },
                leadingContent = {
                    Icon(Icons.Default.ViewModule, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun ImportDataSection(onClick: () -> Unit) {
    Column {
        Text(
            text = "Data Import",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("Import Data") },
                supportingContent = { Text("Import CSV files or bank statements") },
                leadingContent = {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                },
                modifier = Modifier.clickable { onClick() }
            )
        }
    }
}

@Composable
private fun IncomeSection(
    mainIncome: Double?,
    onEditClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.getDefault()) }
    currencyFormat.currency = Currency.getInstance("USD")

    Column {
        Text(
            text = "Income",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("Main Income") },
                supportingContent = {
                    Text(
                        mainIncome?.let { currencyFormat.format(it) } ?: "Not set"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                },
                modifier = Modifier.clickable { onEditClick() }
            )
        }
    }
}

@Composable
private fun AboutSection() {
    Column {
        Text(
            text = "About",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0 (Build 100)") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Privacy Policy") },
                leadingContent = {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Terms of Service") },
                leadingContent = {
                    Icon(Icons.Default.Description, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                }
            )
        }
    }
}

@Composable
private fun LogoutButton(onClick: () -> Unit, isLoading: Boolean) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(Icons.Default.Logout, contentDescription = null)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Logout")
    }
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportDataSheet(
    onDismiss: () -> Unit,
    isPro: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Import Data",
                style = MaterialTheme.typography.titleLarge
            )

            if (!isPro) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Upgrade to Pro to unlock data import features",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                enabled = isPro
            ) {
                ListItem(
                    headlineContent = { Text("CSV Import") },
                    supportingContent = { Text("Import transactions from CSV files") },
                    leadingContent = {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                    },
                    trailingContent = {
                        if (!isPro) {
                            Icon(Icons.Default.Lock, contentDescription = "Pro feature")
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                enabled = isPro
            ) {
                ListItem(
                    headlineContent = { Text("Bank Statement Import") },
                    supportingContent = { Text("AI-powered bank statement analysis") },
                    leadingContent = {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                    },
                    trailingContent = {
                        if (!isPro) {
                            Icon(Icons.Default.Lock, contentDescription = "Pro feature")
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditIncomeDialog(
    currentIncome: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var incomeText by remember { mutableStateOf(currentIncome?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Main Income") },
        text = {
            OutlinedTextField(
                value = incomeText,
                onValueChange = { incomeText = it },
                label = { Text("Monthly Income") },
                prefix = { Text("$") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.DecimalNumber
                ),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    incomeText.toDoubleOrNull()?.let { onSave(it) }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
