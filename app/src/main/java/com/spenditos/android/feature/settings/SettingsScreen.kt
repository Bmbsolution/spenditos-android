package com.spenditos.android.feature.settings

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosRow
import androidx.compose.ui.text.font.FontWeight
import com.spenditos.android.ui.theme.w
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Spacing
/** iOS SettingsView: profile card, then grouped settings sections. */
@Composable
fun SettingsScreen(
    onOpenNotSynced: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenDownloadData: () -> Unit,
    onOpenMerchants: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.refreshPremium() }
    val restoreMessage by viewModel.restoreMessage.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val languageChoice = com.spenditos.android.core.i18n.AppLanguage.currentChoice()
    val context = LocalContext.current
    val finance = LocalFinanceColors.current
    val slate = Color(0xFF64748B)
    val biometricName = remember(context) { biometricName(context) }
    val emerald = Color(0xFF10B981)

    IosScreen {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { LargeTitle(stringResource(R.string.common_settings)) }
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(
                                    stringResource(R.string.settings_spenditos_user),
                                    style = IosText.headline,
                                    color = IosColors.textPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (plan.isPro) {
                                    // iOS: caption2 bold white on a gold-to-orange capsule.
                                    Text(
                                        "PRO",
                                        style = IosText.caption2.w(FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(IosColors.spendGold, IosColors.spendOrange)))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("settings.proBadge"),
                                    )
                                }
                            }
                            state.email?.takeIf(String::isNotBlank)?.let {
                                Text(it, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1)
                            }
                        }
                        Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_money)) }
            item {
                GroupedCard {
                    IosRow(stringResource(R.string.common_currency), icon = SfSymbols.icon("dollarsign.circle.fill"), iconTint = finance.income, value = state.currency.ifBlank { null }, chevron = false, onClick = onOpenProfile)
                    IosRow(stringResource(R.string.common_categories), icon = SfSymbols.icon("tag.fill"), iconTint = emerald, onClick = onOpenCategories, modifier = Modifier.testTag("settings.categories"))
                    IosRow(stringResource(R.string.settings_rules), icon = SfSymbols.icon("storefront.fill"), iconTint = IosColors.spendOrange, onClick = onOpenMerchants, divider = false, modifier = Modifier.testTag("settings.merchants"))
                }
            }

            item { SectionHeader(stringResource(R.string.settings_notifications)) }
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val permission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted -> if (granted) viewModel.setPush(true) }
                GroupedCard {
                    IosRow(
                        if (state.pushEnabled) stringResource(R.string.settings_daily_logging_reminder) else stringResource(R.string.settings_enable_notifications),
                        subtitle = if (state.pushEnabled) stringResource(R.string.settings_daily_nudge) else stringResource(R.string.settings_notifications_are_turned_off),
                        icon = SfSymbols.icon(if (state.pushEnabled) "square.and.pencil" else "bell.slash.fill"),
                        iconTint = if (state.pushEnabled) IosColors.spendOrange else IosColors.neutral,
                        chevron = false,
                        divider = false,
                            modifier = Modifier.testTag("settings.notifications"),
                        trailing = {
                            com.spenditos.android.ui.components.IosSwitch(
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

            item { SectionHeader(stringResource(R.string.settings_preferences)) }
            item {
                GroupedCard {
                    IosRow(
                        stringResource(R.string.settings_language),
                        icon = SfSymbols.icon("globe"),
                        iconTint = IosColors.info,
                        value = languageChoice.nativeName ?: stringResource(R.string.settings_language_device),
                        onClick = { showLanguage = true },
                        modifier = Modifier.testTag("settings.language"),
                    )
                    IosRow(
                        stringResource(R.string.common_progress),
                        subtitle = stringResource(R.string.settings_streak_level_and_achievements),
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
                            stringResource(R.string.settings_lock_with, biometricName),
                            subtitle = state.lockMessage,
                            icon = SfSymbols.icon("lock.fill"),
                            iconTint = IosColors.info,
                            chevron = false,
                            divider = false,
                            modifier = Modifier.testTag("settings.appLock"),
                            trailing = {
                                com.spenditos.android.ui.components.IosSwitch(
                                    checked = state.lockEnabled,
                                    onCheckedChange = { wanted -> activity?.let { viewModel.setLock(it, wanted) } },
                                    modifier = Modifier.testTag("settings.appLock.switch"),
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_turn_off_streaks_and_xp),
                    style = IosText.footnote,
                    color = IosColors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_your_data)) }
            item {
                GroupedCard {
                    // Statements and CSVs come in through Scan; this is the way out.
                    IosRow(
                        stringResource(R.string.common_download_my_data),
                        subtitle = stringResource(R.string.settings_spreadsheet_or_a_full_copy),
                        icon = SfSymbols.icon("square.and.arrow.down"),
                        iconTint = IosColors.info,
                        onClick = onOpenDownloadData,
                        divider = state.unsyncedCount > 0,
                        modifier = Modifier.testTag("settings.downloadData"),
                    )
                    if (state.unsyncedCount > 0) {
                        IosRow(
                            stringResource(R.string.settings_waiting_to_sync, state.unsyncedCount),
                            icon = SfSymbols.icon("arrow.triangle.2.circlepath.icloud"),
                            iconTint = IosColors.spendOrange,
                            onClick = onOpenNotSynced,
                            divider = false,
                            modifier = Modifier.testTag("settings.notSynced"),
                        )
                    }
                }
                Text(
                    if (state.unsyncedCount > 0) stringResource(R.string.settings_saved_on_this_phone_they)
                    else stringResource(R.string.settings_everything_is_saved_to_your),
                    style = IosText.footnote,
                    color = IosColors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_subscription)) }
            item {
                GroupedCard {
                    when {
                        // Server-granted welcome Premium: no subscription yet, so the row leads to the Pro screen.
                        plan.welcomeText != null -> IosRow(
                            requireNotNull(plan.welcomeText),
                            subtitle = stringResource(R.string.settings_keep_pro_after_it_ends),
                            icon = SfSymbols.icon("sparkles"),
                            iconTint = IosColors.spendGold,
                            onClick = onOpenPro,
                            modifier = Modifier.testTag("settings.welcomePremium"),
                        )
                        // Pro from a subscription or a grant by Spenditos.
                        plan.isPro -> IosRow(
                            stringResource(R.string.common_spenditos_pro),
                            value = stringResource(R.string.common_active),
                            icon = SfSymbols.icon("sparkles"),
                            iconTint = IosColors.spendGold,
                            chevron = false,
                            modifier = Modifier.testTag("settings.proActive"),
                        )
                        else -> IosRow(
                            stringResource(R.string.common_upgrade_to_pro),
                            subtitle = stringResource(R.string.settings_see_how_voice_receipt_scan),
                            icon = SfSymbols.icon("sparkles"),
                            iconTint = IosColors.spendGold,
                            onClick = onOpenPro,
                            modifier = Modifier.testTag("settings.pro"),
                        )
                    }
                    IosRow(
                        stringResource(R.string.common_restore_purchases),
                        subtitle = restoreMessage,
                        icon = SfSymbols.icon("arrow.clockwise"),
                        iconTint = IosColors.info,
                        chevron = false,
                        onClick = viewModel::restorePurchases,
                        divider = false,
                        modifier = Modifier.testTag("settings-restore-purchases"),
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_account)) }
            item {
                GroupedCard {
                    IosRow(stringResource(R.string.settings_change_password), icon = SfSymbols.icon("lock.fill"), iconTint = IosColors.info, onClick = onOpenChangePassword, divider = false, modifier = Modifier.testTag("settings.changePassword"))
                }
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                GroupedCard {
                    IosRow(
                        stringResource(R.string.common_privacy_policy),
                        icon = SfSymbols.icon("hand.raised.fill"),
                        iconTint = Color(0xFF8B5CF6),
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://spenditos.com/privacy"))) },
                    )
                    IosRow(
                        stringResource(R.string.settings_terms_of_service),
                        icon = SfSymbols.icon("doc.text"),
                        iconTint = slate,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://spenditos.com/terms"))) },
                    )
                    IosRow(stringResource(R.string.settings_version), icon = SfSymbols.icon("info.circle.fill"), iconTint = slate, value = com.spenditos.android.BuildConfig.VERSION_NAME, chevron = false)
                    IosRow(stringResource(R.string.settings_environment), icon = SfSymbols.icon("server.rack"), iconTint = slate, value = "${state.envName} (${state.stage})", chevron = false, divider = false, modifier = Modifier.testTag("settings.environment"))
                }
            }

            item {
                GroupedCard(Modifier.padding(top = Spacing.l)) {
                    Box(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmSignOut = true }.padding(vertical = Spacing.m).testTag("settings.signOut"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.settings_sign_out), style = IosText.callout, color = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.settings_language), style = IosText.headline) },
            text = {
                Column {
                    com.spenditos.android.core.i18n.LanguageChoice.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = choice == languageChoice, role = Role.RadioButton, onClick = {
                                    showLanguage = false
                                    if (choice != languageChoice) viewModel.setLanguage(choice)
                                })
                                .padding(vertical = 12.dp)
                                .testTag("settings.language.${choice.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = choice == languageChoice, onClick = null)
                            Spacer(Modifier.width(Spacing.m))
                            Text(choice.nativeName ?: stringResource(R.string.settings_language_device), style = IosText.body, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out), style = IosText.headline) },
            text = { Text(viewModel.signOutMessage(), style = IosText.body) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }, modifier = Modifier.testTag("settings.signOut.confirm")) { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }, modifier = Modifier.testTag("settings.signOut.cancel")) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

private fun biometricName(context: android.content.Context): String {
    val pm = context.packageManager
    val face = pm.hasSystemFeature(PackageManager.FEATURE_FACE)
    val finger = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    return when {
        face && !finger -> Strings.get(R.string.settings_face)
        finger && !face -> Strings.get(R.string.settings_fingerprint)
        else -> Strings.get(R.string.settings_biometrics)
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
