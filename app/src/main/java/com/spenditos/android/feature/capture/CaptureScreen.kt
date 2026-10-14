package com.spenditos.android.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Stop
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.subtleGlassModifier
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import com.spenditos.android.data.quota.SentenceMeter
import com.spenditos.android.feature.transactions.AddTransactionPrefill
import java.text.NumberFormat
import java.util.Currency

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onFillManually: (AddTransactionPrefill?) -> Unit = { onBack() },
) {
    val viewModel: CaptureViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var returningFromSettings by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onRecordPermissionResult(granted)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CaptureEvent.Saved -> onSaved(event.message)
                // Smart entries used up: the typed text moves into the manual form, never a dead end.
                is CaptureEvent.ManualFallback -> { viewModel.cancel(); onFillManually(event.prefill) }
            }
        }
    }
    DisposableEffect(viewModel, context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onBackgrounded()
            if (event == Lifecycle.Event.ON_RESUME && returningFromSettings) {
                returningFromSettings = false
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                viewModel.onPermissionSettingsReturned(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler {
        viewModel.cancel()
        onBack()
    }

    val voiceTranscribeError = state.step == CaptureStep.Input &&
        state.mode == CaptureMode.SPEAK &&
        !state.permissionDenied &&
        !state.isRecording &&
        !state.offline &&
        state.error != null
    IosScreen {
        Column(Modifier.fillMaxSize().padding(bottom = 120.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                CaptureHeader(
                    title = captureTitle(state.step, state.mode),
                    onBack = { viewModel.cancel(); onBack() },
                )
                if (state.categoriesLoading) {
                    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.m)) {
                        Row(
                            Modifier.fillMaxWidth().padding(Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = IosColors.spendOrange)
                            Text("Loading categories…", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("capture.categories.loading"))
                        }
                    }
                }

                if (state.offline) {
                    CaptureMessage("You're offline. Try again when you're connected.", warning = true, Modifier.testTag("capture.offline"))
                } else if (state.error != null && !voiceTranscribeError) {
                    CaptureMessage(state.error!!, warning = false, Modifier.testTag("capture.error"))
                }

                if (state.quotaExceeded) {
                    QuotaExceededContent(
                        onUpgrade = com.spenditos.android.feature.pro.LocalOpenPro.current,
                        onFillManually = { viewModel.dismissQuota(); viewModel.cancel(); onFillManually(null) },
                    )
                } else if (voiceTranscribeError) {
                    VoiceErrorContent(
                        message = state.error!!,
                        onRetry = viewModel::retry,
                        onCancel = { viewModel.cancel(); onBack() },
                    )
                } else when (val step = state.step) {
                    CaptureStep.Input -> when (state.mode) {
                        CaptureMode.TYPE -> {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Text("Describe the transaction", style = IosText.headline, color = IosColors.textPrimary)
                                Text(
                                    "Enter the amount, merchant, and any details. Spenditos will turn it into a transaction.",
                                    style = IosText.subheadline,
                                    color = IosColors.textSecondary,
                                )
                            }
                            CaptureTextInput(
                                value = state.text,
                                onValueChange = viewModel::onTextChange,
                                modifier = Modifier.padding(horizontal = Spacing.screen),
                                fieldModifier = Modifier.testTag("capture.text"),
                            )
                            if (!state.isPro) {
                                state.sentenceMeter?.let { SentenceMeterLine(it, com.spenditos.android.feature.pro.LocalOpenPro.current) }
                            }
                            PillButton(
                                text = "Use text",
                                onClick = viewModel::parseTypedText,
                                enabled = state.text.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.parse"),
                            )
                        }
                        CaptureMode.SPEAK -> when {
                            state.permissionDenied -> {
                                PermissionContent(
                                    onOpenSettings = {
                                        returningFromSettings = true
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        context.startActivity(intent)
                                    },
                                )
                            }
                            state.isRecording -> {
                                RecordingContent(
                                    time = formatRecordingTime(state.recordingMillis),
                                    onStop = viewModel::stopRecording,
                                )
                                ExamplePrompts()
                            }
                            else -> {
                                VoiceIdleContent(
                                    onRecord = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.onRecordPermissionResult(true)
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                )
                                ExamplePrompts()
                            }
                        }
                    }
                    is CaptureStep.Analyzing -> {
                        val transcribing = step.phase == CaptureAnalysisPhase.TRANSCRIBE
                        CaptureProgress(
                            title = if (transcribing) "Transcribing…" else "Finding transactions…",
                            detail = if (transcribing) "Turning your recording into editable text" else "Turning your words into transaction drafts",
                            modifier = Modifier.testTag("capture.loading"),
                        )
                        PillButton(
                            text = "Cancel",
                            onClick = { viewModel.cancel(); onBack() },
                            filled = false,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.cancel"),
                        )
                    }
                    CaptureStep.CheckTranscript -> {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text("Describe the transaction", style = IosText.headline, color = IosColors.textPrimary)
                            Text("Edit the transcript, then review the transactions it finds.", style = IosText.subheadline, color = IosColors.textSecondary)
                        }
                        CaptureTextInput(
                            value = state.transcript,
                            onValueChange = viewModel::onTranscriptChange,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                            fieldModifier = Modifier.testTag("capture.transcript"),
                        )
                        PillButton(
                            text = "Find transactions",
                            onClick = viewModel::parseTranscript,
                            enabled = state.transcript.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.parseTranscript"),
                        )
                    }
                    CaptureStep.Review -> {
                        if (state.transcript.isNotBlank()) {
                            TranscriptBanner(state.transcript, onRestart = viewModel::restart)
                        }
                        if (state.items.isEmpty()) {
                            GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                                Column(
                                    Modifier.fillMaxWidth().padding(Spacing.l),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                                ) {
                                    Icon(SfSymbols.icon("square.grid.2x2"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(48.dp))
                                    Text("Nothing recognized — try naming an amount.", modifier = Modifier.testTag("capture.empty"), style = IosText.body, color = IosColors.textSecondary, textAlign = TextAlign.Center)
                                }
                            }
                            PillButton(text = "Try again", onClick = viewModel::retryEmptyAnalysis, filled = false, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.tryAgain"))
                        } else {
                            CaptureReviewStep(
                                items = state.items,
                                categories = state.categories,
                                currency = state.currency,
                                onIncludeChange = viewModel::toggleInclude,
                                onAmountChange = viewModel::onAmountChange,
                                onDescriptionChange = viewModel::onDescriptionChange,
                                onDateChange = viewModel::onDateChange,
                                onCategoryChange = viewModel::onCategoryChange,
                                onTypeChange = viewModel::onTypeChange,
                            )
                            val missingCategoryCount = state.includedItems.count { it.categoryId == null }
                            if (missingCategoryCount > 0) {
                                val noun = if (missingCategoryCount == 1) "item" else "items"
                                Text(
                                    if (state.offline) {
                                        "Select a category for $missingCategoryCount $noun before saving"
                                    } else {
                                        "$missingCategoryCount $noun will save as Uncategorized — pick a category to refine reports"
                                    },
                                    style = IosText.subheadline.w(FontWeight.SemiBold),
                                    color = IosColors.textSecondary,
                                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                                )
                            }
                            if (state.transcript.isBlank()) {
                                PillButton(
                                    text = "Start over",
                                    onClick = viewModel::restart,
                                    filled = false,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.restart"),
                                )
                            }
                        }
                    }
                    CaptureStep.Saving -> CaptureProgress(title = "Saving transactions…", modifier = Modifier.testTag("capture.saving"))
                    CaptureStep.Done -> {
                        DoneContent(
                            savedCount = state.savedCount,
                            onDone = onBack,
                            modifier = Modifier.testTag("capture.done"),
                            buttonModifier = Modifier.testTag("capture.done.back"),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.l))
            }

            if (state.step == CaptureStep.Input && !voiceTranscribeError) {
                CaptureMethodBar(
                    selected = state.mode,
                    enabled = !state.isRecording,
                    onSelect = viewModel::onModeChange,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s).testTag("capture.mode.row"),
                )
            }
            if (state.step == CaptureStep.Review && state.items.isNotEmpty()) {
                CaptureReviewFooter(
                    items = state.includedItems,
                    currency = state.currency,
                    enabled = state.canSave,
                    onSave = viewModel::save,
                    modifier = Modifier.testTag("capture.save"),
                )
            }
        }
    }
}

@Composable
private fun CaptureHeader(title: String?, onBack: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().testTag("capture.header").padding(horizontal = Spacing.screen).heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (title != null) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary, textAlign = TextAlign.Center)
        }
        Row(
            Modifier.align(Alignment.CenterStart).clickable(role = Role.Button, onClick = onBack)
                .testTag("capture.back").heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(19.dp))
            Text("Back", style = IosText.callout.w(FontWeight.Medium), color = IosColors.textPrimary, modifier = Modifier.padding(start = Spacing.xs))
        }
    }
}

private fun captureTitle(step: CaptureStep, mode: CaptureMode): String? = when (step) {
    CaptureStep.Input -> if (mode == CaptureMode.SPEAK) "Voice Entry" else null
    CaptureStep.CheckTranscript -> "Check what we heard"
    CaptureStep.Review -> "Review transactions"
    is CaptureStep.Analyzing -> if (step.phase == CaptureAnalysisPhase.TRANSCRIBE) "Voice Entry" else null
    else -> null
}

@Composable
private fun CaptureMethodBar(selected: CaptureMode, enabled: Boolean, onSelect: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m))).padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CaptureMethodButton(CaptureMode.TYPE, "text.alignleft", "Sentence", selected, enabled, onSelect)
        CaptureMethodButton(CaptureMode.SPEAK, "mic.fill", "Voice", selected, enabled, onSelect)
    }
}

@Composable
private fun RowScope.CaptureMethodButton(
    mode: CaptureMode,
    symbol: String,
    label: String,
    selected: CaptureMode,
    enabled: Boolean,
    onSelect: (CaptureMode) -> Unit,
) {
    val isSelected = selected == mode
    val tint = if (isSelected) IosColors.spendOrange else IosColors.textSecondary
    Column(
        Modifier.weight(1f).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.m))
            .background(if (isSelected) IosColors.spendOrange.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Tab) { onSelect(mode) }
            .semantics { this.selected = isSelected }
            .testTag("capture.mode.${mode.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = tint, modifier = Modifier.size((17 * 1.2).dp))
        Text(
            label,
            style = IosText.caption2.w(if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun CaptureTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.m)
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, IosColors.separator.copy(alpha = 0.55f), shape),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = IosText.callout.copy(color = IosColors.textPrimary),
            modifier = Modifier.fillMaxWidth().padding(Spacing.m).heightIn(min = 160.dp).then(fieldModifier),
        )
    }
}

@Composable
private fun VoiceIdleContent(onRecord: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(SfSymbols.icon("mic.fill"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(67.dp).padding(bottom = Spacing.s))
        Text("Tap and speak", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
        Text("Recording stops automatically when you pause", style = IosText.callout, color = IosColors.neutral, textAlign = TextAlign.Center)
        VoiceActionButton(
            text = "Start Recording",
            onClick = onRecord,
            modifier = Modifier.padding(top = Spacing.m).testTag("capture.record"),
        )
    }
}

@Composable
private fun RecordingContent(time: String, onStop: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "captureMicPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1_000), repeatMode = RepeatMode.Reverse),
        label = "captureMicPulseScale",
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(time, style = IosText.rounded(22, FontWeight.SemiBold), color = IosColors.textPrimary, modifier = Modifier.testTag("capture.timer"))
        Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Spacing.m), contentAlignment = Alignment.Center) {
            Icon(SfSymbols.icon("waveform"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(48.dp))
        }
        Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(100.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape).background(IosColors.expense.copy(alpha = 0.2f)),
            )
            Box(Modifier.size(72.dp).clip(CircleShape).background(IosColors.expense.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(SfSymbols.icon("mic.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Text("Auto-stops after 1.5s of silence", style = IosText.subheadline, color = IosColors.neutral, textAlign = TextAlign.Center)
        VoiceActionButton(
            text = "Stop & Transcribe",
            onClick = onStop,
            modifier = Modifier.testTag("capture.stop"),
            fill = IosColors.expense,
            textColor = Color.White,
            icon = SfSymbols.icon("stop.fill"),
        )
    }
}

@Composable
private fun PermissionContent(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(SfSymbols.icon("mic.slash.fill"), contentDescription = null, tint = IosColors.neutral, modifier = Modifier.size(67.dp))
        Text("Microphone Access Required", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
        Text("Allow microphone access in Settings to use voice entry.", style = IosText.callout, color = IosColors.neutral, textAlign = TextAlign.Center)
        VoiceActionButton(
            text = "Open Settings",
            onClick = onOpenSettings,
            cornerRadius = Radius.m,
            modifier = Modifier.testTag("capture.openSettings"),
        )
    }
}

@Composable
private fun VoiceActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = IosColors.spendOrange,
    textColor: Color = Color.Black,
    cornerRadius: androidx.compose.ui.unit.Dp = Radius.l,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Row(
        modifier.clip(shape).background(fill).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = Spacing.m).then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
        Text(text, style = IosText.headline, color = textColor)
    }
}

@Composable
private fun ExamplePrompts() {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).then(subtleGlassModifier(shape)).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text("Try saying:", style = IosText.subheadline, color = IosColors.neutral)
        listOf(
            "Spent \$25 at Starbucks for coffee",
            "Received \$500 salary deposit",
            "Paid \$12.50 for lunch today",
        ).forEach { text ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Icon(SfSymbols.icon("quote.opening"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
                Text(text, style = IosText.subheadline, color = IosColors.textSecondary)
            }
        }
    }
}

@Composable
private fun CaptureMessage(text: String, warning: Boolean, modifier: Modifier = Modifier) {
    val tint = if (warning) IosColors.warning else IosColors.expense
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.m)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(if (warning) SfSymbols.icon("wifi.slash") else SfSymbols.icon("exclamationmark.triangle.fill"), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, style = IosText.body, color = IosColors.textPrimary, modifier = modifier)
        }
    }
}

@Composable
private fun CaptureProgress(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            CircularProgressIndicator(modifier = modifier.size(36.dp), color = IosColors.spendOrange, strokeWidth = 3.dp)
            Text(title, style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            if (detail != null) Text(detail, style = IosText.callout, color = IosColors.neutral, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TranscriptBanner(transcript: String, onRestart: () -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m).clip(shape)
            .background(IosColors.spendOrange.copy(alpha = 0.08f)).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(SfSymbols.icon("waveform"), contentDescription = null, tint = IosColors.textPrimary, modifier = Modifier.size(18.dp))
            Text("Transcript", style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "Start over",
                style = IosText.caption.w(FontWeight.SemiBold),
                color = IosColors.expense,
                modifier = Modifier.clickable(role = Role.Button, onClick = onRestart).testTag("capture.restart"),
            )
        }
        Text(transcript, style = IosText.subheadline, color = IosColors.textSecondary, maxLines = 3, modifier = Modifier.testTag("review-transcript"))
    }
}

@Composable
private fun VoiceErrorContent(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Icon(
            SfSymbols.icon("exclamationmark.triangle.fill"),
            contentDescription = null,
            tint = IosColors.expense,
            modifier = Modifier.size((48 * 1.2).dp).testTag("capture.error"),
        )
        Text("Couldn't transcribe", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
        Text(message, style = IosText.callout, color = IosColors.neutral, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            val retryShape = RoundedCornerShape(Radius.m)
            Box(
                Modifier.fillMaxWidth().clip(retryShape).background(IosColors.spendOrange)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(vertical = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Text("Try Again", style = IosText.headline, color = Color.Black)
            }
            Box(
                Modifier.fillMaxWidth().then(subtleGlassModifier(RoundedCornerShape(Radius.m)))
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(vertical = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", style = IosText.headline, color = IosColors.textPrimary)
            }
        }
    }
}

@Composable
private fun CaptureReviewFooter(
    items: List<CaptureReviewItem>,
    currency: String,
    enabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val net = items.sumOf { item ->
        val amount = item.amountText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        if (item.transactionType.equals("income", ignoreCase = true)) amount else -amount
    }
    val count = items.size
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        androidx.compose.material3.HorizontalDivider(color = IosColors.separator)
        Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$count ${if (count == 1) "transaction" else "transactions"}", style = IosText.subheadline, color = IosColors.textSecondary)
                Text(
                    formatCurrency(kotlin.math.abs(net), currency),
                    style = IosText.rounded(20, FontWeight.SemiBold),
                    color = if (net < 0) IosColors.expense else IosColors.income,
                )
            }
            Spacer(Modifier.weight(1f))
            PillButton(
                text = "Add $count",
                onClick = onSave,
                enabled = enabled,
                modifier = Modifier.weight(1f).then(modifier).testTag("capture.save"),
            )
        }
    }
}

@Composable
private fun DoneContent(savedCount: Int, onDone: () -> Unit, modifier: Modifier = Modifier, buttonModifier: Modifier = Modifier) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(67.dp))
        Text("Transactions added", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.textPrimary, modifier = modifier)
        Text("$savedCount transactions", style = IosText.body, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        PillButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth().then(buttonModifier))
    }
}

private fun formatRecordingTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtMost(60)
    val tenths = ((milliseconds % 1_000) / 100).toInt()
    return "%d:%02d.%d".format(totalSeconds / 60, totalSeconds % 60, tenths)
}

private fun formatCurrency(value: Double, currencyCode: String): String = runCatching {
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode.uppercase()) }.format(value)
}.getOrDefault("${currencyCode.uppercase()} ${"%.2f".format(value)}")

/** Quiet counter after 3 uses; a soft inline Pro banner at 5 left or fewer. Free users only, never a modal. */
@Composable
private fun SentenceMeterLine(meter: SentenceMeter, onUpgrade: (() -> Unit)?) {
    val banner = meter.bannerText
    val counter = meter.counterText
    if (banner != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("quick-add-sentence-banner"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Text(banner, style = IosText.caption, color = IosColors.textSecondary, modifier = Modifier.weight(1f))
            if (onUpgrade != null) {
                Text(
                    "Upgrade",
                    style = IosText.caption.w(FontWeight.SemiBold),
                    color = IosColors.spendOrange,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onUpgrade).testTag("quick-add-sentence-upgrade"),
                )
            }
        }
    } else if (counter != null) {
        Text(
            counter,
            style = IosText.caption,
            color = IosColors.textTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("quick-add-sentence-counter"),
        )
    }
}

/** iOS VoiceEntryView `quotaExceededContent`. */
@Composable
private fun QuotaExceededContent(onUpgrade: (() -> Unit)?, onFillManually: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xl).testTag("capture.quota"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Icon(
            SfSymbols.icon("lock.fill"),
            contentDescription = null,
            tint = IosColors.spendOrange.copy(alpha = 0.8f),
            modifier = Modifier.size(67.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Monthly Limit Reached", style = IosText.title2.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "You've used all your free voice entries for this month.",
                style = IosText.callout,
                color = IosColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
            Text("Upgrade to Pro for unlimited voice entries.", style = IosText.callout, color = IosColors.textSecondary, textAlign = TextAlign.Center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (onUpgrade != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(Radius.l)).background(IosColors.spendOrange)
                        .clickable(role = Role.Button, onClick = onUpgrade)
                        .padding(horizontal = Spacing.xl, vertical = Spacing.m)
                        .testTag("capture.quota.upgrade"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("Upgrade to Pro", style = IosText.headline, color = Color.White)
                }
            }
            Box(
                Modifier.then(com.spenditos.android.ui.components.subtleGlassModifier())
                    .clickable(role = Role.Button, onClick = onFillManually)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.m)
                    .testTag("capture.quota.manual"),
            ) {
                Text("Fill Manually", style = IosText.headline, color = IosColors.textPrimary)
            }
        }
    }
}
