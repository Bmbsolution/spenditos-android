package com.spenditos.android.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.LocalFinanceColors
import com.spenditos.android.ui.theme.Radius
import com.spenditos.android.ui.theme.Spacing

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
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
        viewModel.events.collect { event -> if (event is CaptureEvent.Saved) onSaved(event.message) }
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

    IosScreen {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(Modifier.fillMaxWidth().testTag("capture.header").padding(start = Spacing.xs, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.cancel(); onBack() }, modifier = Modifier.testTag("capture.back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Text("Back", modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            LargeTitle("Describe it", modifier = Modifier.padding(top = 0.dp, bottom = 0.dp))
            CaptureModeSelector(
                selected = state.mode,
                enabled = state.step == CaptureStep.Input && !state.isRecording,
                onSelect = viewModel::onModeChange,
                modifier = Modifier.padding(horizontal = Spacing.screen).testTag("capture.mode.row"),
            )

            if (state.categoriesLoading) {
                GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.m)) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Text("Loading categories…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture.categories.loading"))
                    }
                }
            }

            if (state.offline) {
                CaptureMessage("You're offline. Try again when you're connected.", true, Modifier.testTag("capture.offline"))
            } else if (state.error != null) {
                CaptureMessage(state.error!!, true, Modifier.testTag("capture.error"))
            }

            when (val step = state.step) {
                CaptureStep.Input -> when (state.mode) {
                    CaptureMode.TYPE -> {
                        Text(
                            "Describe one or more transactions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                        )
                        CaptureTextInput(
                            value = state.text,
                            onValueChange = viewModel::onTextChange,
                            label = "What happened?",
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                            fieldModifier = Modifier.testTag("capture.text"),
                        )
                        PillButton(
                            text = "Parse transactions",
                            onClick = viewModel::parseTypedText,
                            enabled = state.text.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.parse"),
                        )
                    }
                    CaptureMode.SPEAK -> when {
                        state.permissionDenied -> {
                            Text("Microphone access is needed for voice capture.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.screen))
                            PillButton(
                                text = "Open Settings",
                                onClick = {
                                    returningFromSettings = true
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    context.startActivity(intent)
                                },
                                filled = false,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.openSettings"),
                            )
                        }
                        state.isRecording -> {
                            GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                                Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                    Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                    Text("Recording · ${formatRecordingTime(state.recordingMillis)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("capture.timer"))
                                    Text("Speak clearly. Recording stops after 60 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            PillButton(
                                text = "Stop",
                                onClick = viewModel::stopRecording,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.stop"),
                            )
                        }
                        else -> {
                            CapturePrompt(icon = Icons.Filled.Mic, title = "Record up to 60 seconds.")
                            PillButton(
                                text = "Record",
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.onRecordPermissionResult(true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.record"),
                            )
                        }
                    }
                }
                is CaptureStep.Analyzing -> {
                    CaptureProgress("Reading that...", Modifier.testTag("capture.loading"))
                    PillButton(text = "Cancel", onClick = { viewModel.cancel(); onBack() }, filled = false, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.cancel"))
                }
                CaptureStep.CheckTranscript -> {
                    Text("Check what we heard", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.screen))
                    CaptureTextInput(
                        value = state.transcript,
                        onValueChange = viewModel::onTranscriptChange,
                        label = "Check what we heard",
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                        fieldModifier = Modifier.testTag("capture.transcript"),
                    )
                    PillButton(text = "Parse", onClick = viewModel::parseTranscript, enabled = state.transcript.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.parseTranscript"))
                }
                CaptureStep.Review -> {
                    Text("Review transactions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.screen))
                    if (state.items.isEmpty()) {
                        GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
                            Text("We couldn't find any transactions in that. Try rephrasing.", modifier = Modifier.padding(Spacing.m).testTag("capture.empty"), style = MaterialTheme.typography.bodyMedium)
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
                        )
                        if (!state.canSave && state.includedItems.isNotEmpty()) {
                            Text("Check each included amount and description.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.screen))
                        }
                        PillButton(text = "Save ${state.includedItems.size} transactions", onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.save"))
                        PillButton(text = "Start over", onClick = viewModel::restart, filled = false, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.restart"))
                    }
                }
                CaptureStep.Saving -> CaptureProgress("Saving transactions…", Modifier.testTag("capture.saving"))
                CaptureStep.Done -> {
                    CapturePrompt(icon = Icons.Filled.TextFields, title = "Transactions saved", textModifier = Modifier.testTag("capture.done"))
                    PillButton(text = "Done", onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).testTag("capture.done.back"))
                }
            }
        }
    }
}

@Composable
private fun CaptureModeSelector(selected: CaptureMode, enabled: Boolean, onSelect: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CaptureMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val label = if (mode == CaptureMode.TYPE) "Type" else "Speak"
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(mode) }
                    .testTag("capture.mode.${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CaptureTextInput(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, fieldModifier: Modifier = Modifier) {
    GlassCard(modifier, shape = RoundedCornerShape(Radius.m)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).then(fieldModifier),
            )
        }
    }
}

@Composable
private fun CaptureMessage(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val finance = LocalFinanceColors.current
    GlassCard(
        modifier = Modifier.padding(horizontal = Spacing.screen),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (isError) finance.expense else MaterialTheme.colorScheme.onSurface, modifier = modifier.padding(Spacing.m))
    }
}

@Composable
private fun CapturePrompt(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = textModifier)
        }
    }
}

@Composable
private fun CaptureProgress(text: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = Modifier.padding(horizontal = Spacing.screen), shape = RoundedCornerShape(Radius.l)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CircularProgressIndicator(modifier = modifier, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatRecordingTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtMost(60)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
