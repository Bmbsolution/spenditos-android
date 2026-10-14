package com.spenditos.android.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.spenditos.android.data.scan.ScanCardState
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

/**
 * Home's scan lines (iOS `scanCard`): "Reading {label}…" while a scan runs, "{n} transactions ready to review"
 * (tap to open) once it is ready. Nothing is drawn without a scan in progress or waiting.
 *
 * The list is re-read when Home comes to the foreground and every few seconds while a scan is still processing; the
 * coordinator also refreshes it on the realtime scan event and when the socket reconnects.
 */
@Composable
fun ScanHomeCards(
    onOpenReview: (String) -> Unit,
    onOpenReceipt: (AddTransactionPrefill) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScanCardsViewModel = hiltViewModel()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val processing by viewModel.hasProcessing.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(processing, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollUntilIdle() }
    }
    val review by rememberUpdatedState(onOpenReview)
    val receipt by rememberUpdatedState(onOpenReceipt)
    val message by rememberUpdatedState(onMessage)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanCardEvent.OpenReview -> review(event.scanId)
                is ScanCardEvent.OpenReceipt -> receipt(event.prefill)
                is ScanCardEvent.Message -> message(event.text)
            }
        }
    }
    if (cards.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        cards.forEach { card ->
            when (card.phase) {
                ScanCardState.Phase.READING -> ReadingCard(card)
                ScanCardState.Phase.READY -> ReadyCard(card, onClick = { viewModel.open(card.scanId) })
            }
        }
    }
}

@Composable
private fun ReadingCard(card: ScanCardState) {
    GlassCard(modifier = Modifier.testTag("home-scan-reading")) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = IosColors.spendOrange, strokeWidth = 2.5.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.title, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
                Text(card.subtitle ?: READING_FALLBACK, style = IosText.subheadline, color = IosColors.textSecondary)
            }
        }
    }
}

@Composable
private fun ReadyCard(card: ScanCardState, onClick: () -> Unit) {
    // iOS: spendOrange 8% in a 20pt rounded rectangle (no glass), the whole card is the button.
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(IosColors.spendOrange.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.m)
            .testTag("home-scan-ready"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        IconChip(SfSymbols.icon("doc.text.viewfinder"), IosColors.spendOrange, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.title, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            card.subtitle?.let {
                Text(it, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Review", style = IosText.subheadline.w(FontWeight.Bold), color = IosColors.spendOrange)
    }
}

// iOS says "We'll let you know when it's ready." (a push). Android sends none: the card itself turns into "Ready to review".
private const val READING_FALLBACK = "It'll show up here when it's ready."
