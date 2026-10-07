package com.spenditos.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
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
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/** iOS SettingsView: profile card, then grouped settings sections. */
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
    val slate = Color(0xFF64748B)
    val emerald = Color(0xFF10B981)

    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { LargeTitle("Settings") }
            item {
                GlassCard(
                    Modifier.padding(horizontal = Spacing.screen).testTag("settings.profile"),
                    onClick = onOpenProfile,
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape)
                                .background(IosColors.spendOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                initials(state.email),
                                style = IosText.headline,
                                color = IosColors.spendOrange,
                            )
                        }
                        Column(Modifier.weight(1f).testTag("settings.account"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                "Spenditos User",
                                style = IosText.headline,
                                color = IosColors.textPrimary,
                                maxLines = 1,
                            )
                            state.email?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1)
                            }
                        }
                        Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            item { SectionHeader("Money") }
            item {
                GroupedCard {
                    IosRow("Currency", icon = SfSymbols.icon("dollarsign.circle.fill"), iconTint = finance.income, value = state.currency.ifBlank { null }, chevron = false, onClick = onOpenProfile)
                    IosRow("Categories", icon = SfSymbols.icon("tag.fill"), iconTint = emerald, onClick = onOpenCategories, modifier = Modifier.testTag("settings.categories"))
                    IosRow("Merchant rules", icon = SfSymbols.icon("storefront.fill"), iconTint = IosColors.spendOrange, onClick = onOpenMerchants, divider = false, modifier = Modifier.testTag("settings.merchants"))
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
                        if (state.pushEnabled) "Daily logging reminder" else "Enable notifications",
                        subtitle = if (state.pushEnabled) "A daily nudge to log your spending" else "Notifications are turned off",
                        icon = SfSymbols.icon(if (state.pushEnabled) "square.and.pencil" else "bell.slash.fill"),
                        iconTint = if (state.pushEnabled) IosColors.spendOrange else IosColors.neutral,
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
                                colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759)),
                                modifier = Modifier.testTag("settings.notifications.switch"),
                            )
                        },
                    )
                }
            }

            item { SectionHeader("Preferences") }
            item {
                GroupedCard {
                    IosRow(
                        "Progress",
                        subtitle = "Streak, level and achievements",
                        icon = SfSymbols.icon("flame.fill"),
                        iconTint = IosColors.spendOrange,
                        onClick = onOpenProgress,
                        divider = state.lockAvailable,
                        modifier = Modifier.testTag("settings.progress"),
                    )
                    if (state.lockAvailable) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val activity = remember(context) {
                            generateSequence(context) { current ->
                                (current as? android.content.ContextWrapper)?.baseContext
                            }.filterIsInstance<androidx.fragment.app.FragmentActivity>().firstOrNull()
                        }
                        IosRow(
                            "App lock",
                            subtitle = state.lockMessage ?: "Ask for your fingerprint, face or screen lock",
                            icon = SfSymbols.icon("lock.fill"),
                            iconTint = IosColors.info,
                            chevron = false,
                            divider = false,
                            modifier = Modifier.testTag("settings.appLock"),
                            trailing = {
                                Switch(
                                    checked = state.lockEnabled,
                                    onCheckedChange = { wanted -> activity?.let { viewModel.setLock(it, wanted) } },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759)),
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
                    IosRow("Import", subtitle = "Bank statement or CSV", icon = SfSymbols.icon("square.and.arrow.down"), iconTint = finance.income, onClick = onOpenStatementImport, modifier = Modifier.testTag("settings.importStatement"))
                    IosRow("Export", icon = SfSymbols.icon("square.and.arrow.up"), iconTint = Color(0xFF3B82F6), onClick = onOpenExport, modifier = Modifier.testTag("settings.export"))
                    IosRow(
                        if (state.unsyncedCount == 0) "Everything is synced" else "Waiting to sync (${state.unsyncedCount})",
                        icon = SfSymbols.icon(if (state.unsyncedCount > 0) "arrow.triangle.2.circlepath.icloud" else "checkmark.icloud"),
                        iconTint = if (state.unsyncedCount > 0) IosColors.spendOrange else slate,
                        onClick = onOpenNotSynced,
                        divider = false,
                        modifier = Modifier.testTag("settings.notSynced"),
                    )
                }
                Text(
                    if (state.unsyncedCount > 0) "Saved on this phone. They upload when you're back online."
                    else "Everything is synced. Changes saved offline show up here until they upload.",
                    style = IosText.footnote,
                    color = IosColors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
                )
            }

            item { SectionHeader("Subscription") }
            item {
                GroupedCard {
                    IosRow(
                        "Upgrade to Pro",
                        subtitle = "See how voice, receipt scan & insights can help",
                        icon = SfSymbols.icon("sparkles"),
                        iconTint = IosColors.spendGold,
                        onClick = onOpenPro,
                        divider = false,
                        modifier = Modifier.testTag("settings.pro"),
                    )
                }
            }

            item { SectionHeader("Account") }
            item {
                GroupedCard {
                    IosRow("Change Password", icon = SfSymbols.icon("lock.fill"), iconTint = IosColors.info, onClick = onOpenChangePassword, divider = false, modifier = Modifier.testTag("settings.changePassword"))
                }
            }

            item { SectionHeader("About") }
            item {
                GroupedCard {
                    IosRow("Version", icon = SfSymbols.icon("info.circle.fill"), iconTint = slate, value = com.spenditos.android.BuildConfig.VERSION_NAME, chevron = false)
                    IosRow("Environment", icon = SfSymbols.icon("server.rack"), iconTint = slate, value = "${state.envName} (${state.stage})", chevron = false, divider = false, modifier = Modifier.testTag("settings.environment"))
                }
            }

            item {
                GroupedCard(Modifier.padding(top = Spacing.l)) {
                    Box(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmSignOut = true }.padding(vertical = Spacing.m).testTag("settings.signOut"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Sign Out", style = IosText.callout, color = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign Out", style = IosText.headline) },
            text = { Text(viewModel.signOutMessage(), style = IosText.body) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }, modifier = Modifier.testTag("settings.signOut.confirm")) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }, modifier = Modifier.testTag("settings.signOut.cancel")) { Text("Cancel") }
            },
        )
    }
}

private fun initials(email: String?): String {
    val name = email.orEmpty()
    val parts = name.split(' ').filter { it.isNotBlank() }
    val letters = if (parts.size >= 2) {
        "${parts[0].first()}${parts[1].first()}"
    } else {
        name.take(2).ifBlank { "?" }
    }
    return letters.uppercase()
}
