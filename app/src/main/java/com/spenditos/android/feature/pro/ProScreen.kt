package com.spenditos.android.feature.pro

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

private val SystemBlue = Color(0xFF007AFF)

@Composable
private fun secondarySystemBackgroundColor(): Color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

/** Settings > Spenditos Pro: what Pro unlocks and how to subscribe, restore or manage it. */
@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val featureRows = PRO_FEATURES.map { Feature(SfSymbols.icon(it.symbol), it.title, it.description, SystemBlue) }

    Box(Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color.Black else Color.White).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            ProHeader(onBack)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(72.dp))
                    Text(stringResource(R.string.common_upgrade_to_pro), style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.pro_unlock_the_full_power_of), style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(secondarySystemBackgroundColor()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    featureRows.forEach { feature -> FeatureRow(feature) }
                }

                state.error?.let {
                    val canRetry = !state.isLoading && state.packages.isEmpty() && state.isAvailable
                    ErrorBanner(
                        message = it,
                        modifier = Modifier.testTag("pro.error"),
                        onRetry = if (canRetry) viewModel::load else null,
                    )
                }

                when {
                    state.isLoading -> Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp).testTag("pro.loading"), color = SystemBlue)
                    }
                    state.isPro -> {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(secondarySystemBackgroundColor()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.m),
                        ) {
                            Text(stringResource(R.string.pro_you_have_pro), style = IosText.headline, color = SystemBlue, modifier = Modifier.testTag("pro.active"))
                            SystemButton(
                                stringResource(R.string.pro_manage_subscription),
                                null,
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) },
                                modifier = Modifier.fillMaxWidth().testTag("pro.manage"),
                            )
                        }
                    }
                    !state.isAvailable -> {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(secondarySystemBackgroundColor()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Text(stringResource(R.string.pro_subscriptions_aren_t_available_in), style = IosText.callout, color = IosColors.textSecondary, modifier = Modifier.testTag("pro.unavailable"))
                        }
                    }
                    state.packages.isEmpty() -> OfferingsUnavailable(onRetry = viewModel::load, hasError = state.error != null)
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.packages.forEach { pkg ->
                                val selected = pkg.id == state.selectedId
                                Box(
                                    Modifier.fillMaxWidth().testTag("pro.package.${pkg.id}")
                                        .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.select(pkg.id) })
                                        .semantics { this.selected = selected },
                                ) {
                                    val packageShape = RoundedCornerShape(12.dp)
                                    Box(
                                        Modifier.fillMaxWidth().clip(packageShape)
                                            .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
                                            .border(2.dp, SystemBlue, packageShape),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                                        ) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                                Text(pkg.title, style = IosText.headline, color = IosColors.textPrimary)
                                                Text(pkg.price + (pkg.period?.let { " / $it" } ?: ""), style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary)
                                                if (pkg.isBestValue) Text(stringResource(R.string.pro_best_value), style = IosText.caption, color = SystemBlue)
                                            }
                                            Box(
                                                Modifier.size(24.dp).clip(CircleShape)
                                                    .background(if (selected) SystemBlue else Color.Transparent)
                                                    .border(2.dp, SystemBlue, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (selected) Icon(SfSymbols.icon("checkmark"), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            SystemButton(
                                if (state.isBusy) stringResource(R.string.pro_working) else stringResource(R.string.pro_subscribe),
                                null,
                                enabled = state.selectedId != null && !state.isBusy,
                                onClick = { context.findActivity()?.let(viewModel::buy) },
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("pro.subscribe"),
                            )
                        }
                    }
                }

                if (!state.isPro && state.isAvailable) {
                    TextButton(onClick = viewModel::restore, enabled = !state.isLoading && !state.isBusy, modifier = Modifier.fillMaxWidth().testTag("pro.restore")) {
                        Text(stringResource(R.string.common_restore_purchases), style = IosText.subheadline, color = IosColors.textSecondary)
                    }
                }

                if (!state.isPro && state.isAvailable) {
                    Column(
                        Modifier.padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.pro_payment_will_be_charged_to), style = IosText.caption, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.pro_subscription_automatically_renews_unless_c), style = IosText.caption, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.pro_your_account_will_be_charged), style = IosText.caption, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.pro_payment_is_charged_to_your), style = IosText.caption, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LegalLink(stringResource(R.string.common_privacy_policy), "https://spenditos.com/privacy", context)
                            LegalLink(stringResource(R.string.pro_terms_of_use), "https://spenditos.com/terms", context)
                        }
                    }
                }

                state.notice?.let { Text(it, style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("pro.notice")) }
            }
        }
    }
}

/** The paywall list, copied from iOS PaywallView.featuresSection (same rows, same words). */
internal data class ProFeatureSpec(val symbol: String, val title: String, val description: String)

internal val PRO_FEATURES = listOf(
    ProFeatureSpec("doc.viewfinder", Strings.get(R.string.pro_unlimited_ai_receipt_scanning), Strings.get(R.string.pro_snap_photos_of_receipts_and)),
    ProFeatureSpec("waveform", Strings.get(R.string.pro_smart_voice_entry), Strings.get(R.string.pro_just_speak_to_add_transactions)),
    ProFeatureSpec("chart.line.uptrend.xyaxis", Strings.get(R.string.pro_advanced_analytics), Strings.get(R.string.pro_daily_trends_income_analysis_and)),
    ProFeatureSpec("arrow.triangle.2.circlepath", Strings.get(R.string.pro_unlimited_income_items_and_bills), Strings.get(R.string.pro_add_as_many_income_items)),
)

private data class Feature(val icon: ImageVector, val title: String, val description: String, val tint: Color)

@Composable
private fun FeatureRow(feature: Feature) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(feature.icon, contentDescription = null, tint = feature.tint, modifier = Modifier.size(28.8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(feature.title, style = IosText.headline, color = IosColors.textPrimary)
            Text(feature.description, style = IosText.subheadline, color = IosColors.textSecondary)
        }
    }
}

@Composable
private fun OfferingsUnavailable(onRetry: () -> Unit, hasError: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(secondarySystemBackgroundColor()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(43.2.dp)) {
            Icon(SfSymbols.icon("cart"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.fillMaxSize())
            Icon(SfSymbols.icon("exclamationmark.circle.fill"), contentDescription = null, tint = IosColors.textSecondary, modifier = Modifier.size(18.dp).align(Alignment.BottomEnd))
        }
        Text(stringResource(R.string.pro_prices_unavailable), style = IosText.headline, color = IosColors.textPrimary)
        Text(stringResource(R.string.pro_we_couldn_t_load_subscription), style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        SystemButton(
            stringResource(R.string.common_retry),
            SfSymbols.icon("arrow.clockwise"),
            onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).then(if (hasError) Modifier else Modifier.testTag("pro.retry")),
        )
    }
}

@Composable
private fun SystemButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.height(54.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) SystemBlue else SystemBlue.copy(alpha = 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
        Text(text, style = IosText.headline, color = Color.White)
    }
}

@Composable
private fun LegalLink(label: String, url: String, context: Context) {
    Text(
        label,
        style = IosText.caption,
        color = SystemBlue,
        modifier = Modifier.clickable(role = Role.Button) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
    )
}

@Composable
private fun ProHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Text(stringResource(R.string.common_spenditos_pro), style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
        Text(
            stringResource(R.string.common_close),
            style = IosText.body,
            color = SystemBlue,
            modifier = Modifier.align(Alignment.CenterEnd).clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = Strings.get(R.string.common_back) }
                .padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("pro.back"),
        )
    }
}

/** The calm card shown where a Pro-only screen is locked; opens the Pro screen. */
@Composable
fun ProUpsellCard(message: String, onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.testTag("pro.upsell")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.common_pro_feature), style = IosText.headline)
            Text(message, style = IosText.body, color = IosColors.textSecondary)
            androidx.compose.material3.TextButton(onClick = onSeePro, modifier = Modifier.testTag("pro.upsell.see")) {
                Text(stringResource(R.string.common_see_spenditos_pro), style = IosText.headline, color = SystemBlue)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
