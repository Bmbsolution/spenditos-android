package com.spenditos.android.feature.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.theme.Spacing

/** Settings > Spenditos Pro: what Pro unlocks and how to subscribe, restore or manage it. */
@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("pro.back")) { Text("Back") }
            Text("Spenditos Pro", style = MaterialTheme.typography.titleLarge)
        }
        Text("Go further with your money.", style = MaterialTheme.typography.titleMedium)
        listOf(
            "Paycheck periods: see how each paycheck covers what is due.",
            "Statement import: bring in a bank CSV or Excel file with the columns mapped for you.",
            "More receipt scans and voice captures each month.",
        ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }

        when {
            state.isLoading -> CircularProgressIndicator(Modifier.testTag("pro.loading"))
            state.isPro -> {
                Text("You have Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("pro.active"))
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) },
                    modifier = Modifier.fillMaxWidth().testTag("pro.manage"),
                ) { Text("Manage subscription") }
            }
            !state.isAvailable -> Text(
                "Subscriptions aren't available in this build.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("pro.unavailable"),
            )
            else -> {
                state.packages.forEach { pkg ->
                    val selected = pkg.id == state.selectedId
                    Card(
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.select(pkg.id) }).testTag("pro.package.${pkg.id}"),
                    ) {
                        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            RadioButton(selected = selected, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(pkg.title, fontWeight = FontWeight.SemiBold)
                                if (pkg.isBestValue) Text("Best value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(pkg.price + (pkg.period?.let { " / $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                Button(
                    onClick = { context.findActivity()?.let(viewModel::buy) },
                    enabled = state.selectedId != null && !state.isBusy,
                    modifier = Modifier.fillMaxWidth().testTag("pro.subscribe"),
                ) { Text(if (state.isBusy) "Working…" else "Subscribe") }
                TextButton(onClick = viewModel::restore, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth().testTag("pro.restore")) { Text("Restore purchases") }
                Text(
                    "Payment is charged to your Google Play account and the subscription renews automatically each period until you cancel. " +
                        "Cancel anytime in Google Play > Payments & subscriptions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.notice?.let { Text(it, modifier = Modifier.testTag("pro.notice")) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pro.error"))
            if (!state.isLoading && state.packages.isEmpty() && state.isAvailable) OutlinedButton(onClick = viewModel::load, modifier = Modifier.testTag("pro.retry")) { Text("Try again") }
        }
    }
}

/** The calm card shown where a Pro-only screen is locked; opens the Pro screen. */
@Composable
fun ProUpsellCard(message: String, onSeePro: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().testTag("pro.upsell")) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Pro feature", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSeePro, modifier = Modifier.testTag("pro.upsell.see")) { Text("See Spenditos Pro") }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
