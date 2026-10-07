package com.spenditos.android.feature.pro

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

/** Settings > Spenditos Pro: what Pro unlocks and how to subscribe, restore or manage it. */
@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val featureRows = listOf(
        Triple(Icons.Filled.ReceiptLong, "Paycheck periods: see how each paycheck covers what is due.", Color(0xFF3478F6)),
        Triple(Icons.Filled.ShowChart, "Statement import: bring in a bank CSV or Excel file with the columns mapped for you.", Color(0xFF2D8B57)),
        Triple(Icons.Filled.Mic, "More receipt scans and voice captures each month.", Color(0xFF8A63B2)),
    )

    IosScreen {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ProHeader(onBack)

            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0xFFFFD95A).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFFD69C00), modifier = Modifier.size(50.dp))
                }
                Text("Upgrade to Pro", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
                Text("Go further with your money.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionHeader("What’s included", Modifier.padding(top = Spacing.s))
            GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
                featureRows.forEachIndexed { index, (icon, description, tint) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        IconChip(icon, tint, size = 40.dp, shape = RoundedCornerShape(Radius.s))
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                    if (index < featureRows.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            state.error?.let {
                GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("pro.error"))
                        if (!state.isLoading && state.packages.isEmpty() && state.isAvailable) {
                            TextButton(onClick = viewModel::load, modifier = Modifier.testTag("pro.retry")) { Text("Try again") }
                        }
                    }
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.testTag("pro.loading"), color = MaterialTheme.colorScheme.primary)
                }
                state.isPro -> {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Text("You have Pro", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("pro.active"))
                            PillButton(
                                text = "Manage subscription",
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) },
                                filled = false,
                                modifier = Modifier.fillMaxWidth().testTag("pro.manage"),
                            )
                        }
                    }
                }
                !state.isAvailable -> {
                    GlassCard(Modifier.padding(horizontal = Spacing.screen)) {
                        Text(
                            "Subscriptions aren't available in this build.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.m).testTag("pro.unavailable"),
                        )
                    }
                }
                else -> {
                    SectionHeader("Choose a plan", Modifier.padding(top = Spacing.s))
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        state.packages.forEach { pkg ->
                            val selected = pkg.id == state.selectedId
                            Box(
                                Modifier.fillMaxWidth().testTag("pro.package.${pkg.id}")
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.select(pkg.id) })
                                    .semantics { this.selected = selected },
                            ) {
                                GlassCard {
                                    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                        Box(
                                            Modifier.size(22.dp).clip(CircleShape)
                                                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(pkg.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            if (pkg.isBestValue) Text("Best value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(pkg.price + (pkg.period?.let { " / $it" } ?: ""), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                if (selected) Box(Modifier.matchParentSize().clip(RoundedCornerShape(Radius.l)).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.l)))
                            }
                        }
                        PillButton(
                            text = if (state.isBusy) "Working…" else "Subscribe",
                            onClick = { context.findActivity()?.let(viewModel::buy) },
                            enabled = state.selectedId != null && !state.isBusy,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("pro.subscribe"),
                        )
                        TextButton(onClick = viewModel::restore, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().testTag("pro.restore")) {
                            Text("Restore purchases", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Payment is charged to your Google Play account and the subscription renews automatically each period until you cancel. " +
                            "Cancel anytime in Google Play > Payments & subscriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                    )
                }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.screen).testTag("pro.notice")) }
        }
    }
}

/** The calm card shown where a Pro-only screen is locked; opens the Pro screen. */
@Composable
fun ProUpsellCard(message: String, onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.testTag("pro.upsell")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Pro feature", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PillButton(text = "See Spenditos Pro", onClick = onSeePro, filled = false, modifier = Modifier.testTag("pro.upsell.see"))
        }
    }
}

@Composable
private fun ProHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("pro.back")) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Spenditos Pro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
