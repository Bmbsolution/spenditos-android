package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing

/** iOS SettingsView: profile card, then Money / Notifications / Preferences / Your data / Subscription / About. */
@Composable
fun SettingsScreen(
    onOpenNotSynced: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenStatementImport: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    val finance = LocalFinanceColors.current
    val brand = MaterialTheme.colorScheme.primary
    val mint = Color(0xFF1BAF84)
    val slate = Color(0xFF6B7A90)

    IosScreen {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
            item { LargeTitle("Settings") }
            item {
                // Profile card.
                GlassCard(
                    Modifier.padding(horizontal = Spacing.screen).testTag("settings.profile"),
                    onClick = onOpenProfile,
                ) {
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(64.dp).clip64().background(brand.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Text(initials(state.email), style = MaterialTheme.typography.titleLarge, color = brand, fontWeight = FontWeight.SemiBold)
                        }
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f).testTag("settings.account")) {
                            Text(state.email ?: "Signed in", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("Name and currency", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            }

            item { SectionHeader("Money") }
            item {
                GroupedCard {
                    IosRow("Currency", icon = Icons.Filled.AttachMoney, iconTint = finance.income, value = state.currency.ifBlank { null }, chevron = false, onClick = onOpenProfile)
                    IosRow("Categories", icon = Icons.Filled.Sell, iconTint = mint, onClick = onOpenCategories, modifier = Modifier.testTag("settings.categories"))
                    IosRow("Merchant names", icon = Icons.Filled.Storefront, iconTint = brand, onClick = onOpenMerchants, divider = false, modifier = Modifier.testTag("settings.merchants"))
                }
            }

            item { SectionHeader("Notifications") }
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val permission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted -> if (granted) viewModel.setPush(true) }
                GroupedCard {
                    IosRow(
                        "Enable notifications",
                        subtitle = if (state.pushEnabled) "Reminders and progress updates" else "Notifications are turned off",
                        icon = Icons.Filled.Notifications,
                        iconTint = slate,
                        chevron = false,
                        divider = false,
                        modifier = Modifier.testTag("settings.notifications"),
                        trailing = {
                            Switch(
                                checked = state.pushEnabled,
                                onCheckedChange = { wanted ->
                                    val needsPermission = wanted && android.os.Build.VERSION.SDK_INT >= 33 &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                                        android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else viewModel.setPush(wanted)
                                },
                                modifier = Modifier.testTag("settings.notifications.switch"),
                            )
                        },
                    )
                }
            }

            item { SectionHeader("Preferences") }
            item {
                GroupedCard {
                    IosRow("Progress", subtitle = "Streak, level and achievements", icon = Icons.Filled.EmojiEvents, iconTint = Color(0xFFFF6B35), onClick = onOpenProgress, divider = state.lockAvailable, modifier = Modifier.testTag("settings.progress"))
                    if (state.lockAvailable) {
                        val activity = androidx.compose.ui.platform.LocalContext.current.let { generateSequence(it) { c -> (c as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<androidx.fragment.app.FragmentActivity>().firstOrNull() }
                        IosRow(
                            "App lock",
                            subtitle = state.lockMessage ?: "Ask for your fingerprint, face or screen lock",
                            icon = Icons.Filled.Fingerprint,
                            iconTint = slate,
                            chevron = false,
                            divider = false,
                            modifier = Modifier.testTag("settings.appLock"),
                            trailing = {
                                Switch(
                                    checked = state.lockEnabled,
                                    onCheckedChange = { wanted -> activity?.let { viewModel.setLock(it, wanted) } },
                                    modifier = Modifier.testTag("settings.appLock.switch"),
                                )
                            },
                        )
                    }
                }
            }

            item { SectionHeader("Your data") }
            item {
                GroupedCard {
                    IosRow("Import", subtitle = "Bank statement or CSV", icon = Icons.Filled.FileDownload, iconTint = brand, onClick = onOpenStatementImport, modifier = Modifier.testTag("settings.importStatement"))
                    IosRow("Export", subtitle = "Download your transactions", icon = Icons.Filled.FileUpload, iconTint = brand, onClick = onOpenExport, modifier = Modifier.testTag("settings.export"))
                    IosRow(
                        if (state.unsyncedCount == 0) "Everything is synced" else "Waiting to sync (${state.unsyncedCount})",
                        icon = Icons.Filled.CloudSync,
                        iconTint = if (state.unsyncedCount == 0) slate else brand,
                        onClick = onOpenNotSynced,
                        divider = false,
                        modifier = Modifier.testTag("settings.notSynced"),
                    )
                }
            }

            item { SectionHeader("Subscription") }
            item {
                GroupedCard {
                    IosRow("Spenditos Pro", subtitle = "Paycheck periods, statement import and more", icon = Icons.Filled.AutoAwesome, iconTint = Color(0xFFD6A215), onClick = onOpenPro, divider = false, modifier = Modifier.testTag("settings.pro"))
                }
            }

            item { SectionHeader("Account") }
            item {
                GroupedCard {
                    IosRow("Change password", icon = Icons.Filled.Lock, iconTint = slate, onClick = onOpenChangePassword, divider = false, modifier = Modifier.testTag("settings.changePassword"))
                }
            }

            item { SectionHeader("About") }
            item {
                GroupedCard {
                    IosRow("Version", icon = Icons.Filled.Info, iconTint = slate, value = com.spenditos.android.BuildConfig.VERSION_NAME, chevron = false)
                    IosRow("Environment", icon = Icons.Filled.Dns, iconTint = slate, value = "${state.envName} (${state.stage})", chevron = false, divider = false, modifier = Modifier.testTag("settings.environment"))
                }
            }

            item {
                GroupedCard(Modifier.padding(top = Spacing.l)) {
                    Box(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmSignOut = true }.padding(vertical = 16.dp).testTag("settings.signOut"),
                        contentAlignment = Alignment.Center,
                    ) { Text("Sign out", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out") },
            text = { Text(viewModel.signOutMessage()) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }, modifier = Modifier.testTag("settings.signOut.confirm")) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }, modifier = Modifier.testTag("settings.signOut.cancel")) { Text("Cancel") }
            },
        )
    }
}

private fun Modifier.clip64(): Modifier = this.then(Modifier.clip(CircleShape))

private fun initials(email: String?): String {
    val local = email?.substringBefore('@').orEmpty()
    val parts = local.split('.', '_', '-', ' ').filter { it.isNotBlank() }
    val letters = when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        local.length >= 2 -> local.take(2)
        else -> local.ifBlank { "?" }
    }
    return letters.uppercase()
}
