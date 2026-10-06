package com.spenditos.android.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(Modifier.fillMaxWidth().testTag("capture.header"), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.cancel(); onBack() }, modifier = Modifier.testTag("capture.back")) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text("Describe it", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("capture.mode.row")) {
            CaptureMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.mode == mode,
                    onClick = { viewModel.onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, CaptureMode.entries.size),
                    enabled = state.step == CaptureStep.Input && !state.isRecording,
                    modifier = Modifier.testTag("capture.mode.${mode.name.lowercase()}"),
                ) { Text(if (mode == CaptureMode.TYPE) "Type" else "Speak") }
            }
        }

        if (state.categoriesLoading) {
            Text("Loading categories…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture.categories.loading"))
        }

        if (state.offline) {
            Text("You're offline. Try again when you're connected.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture.offline"))
        } else if (state.error != null) {
            Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture.error"))
        }

        when (val step = state.step) {
            CaptureStep.Input -> when (state.mode) {
                CaptureMode.TYPE -> {
                    Text("Describe one or more transactions.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = viewModel::onTextChange,
                        label = { Text("What happened?") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("capture.text"),
                    )
                    Button(
                        onClick = viewModel::parseTypedText,
                        enabled = state.text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("capture.parse"),
                    ) { Text("Parse transactions") }
                }
                CaptureMode.SPEAK -> when {
                    state.permissionDenied -> {
                        Text("Microphone access is needed for voice capture.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                returningFromSettings = true
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("capture.openSettings"),
                        ) { Text("Open Settings") }
                    }
                    state.isRecording -> {
                        Text("Recording · ${formatRecordingTime(state.recordingMillis)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("capture.timer"))
                        Text("Speak clearly. Recording stops after 60 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = viewModel::stopRecording, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("capture.stop")) { Text("Stop") }
                    }
                    else -> {
                        Text("Record up to 60 seconds.", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.onRecordPermissionResult(true)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("capture.record"),
                        ) { Text("Record") }
                    }
                }
            }
            is CaptureStep.Analyzing -> {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CircularProgressIndicator(modifier = Modifier.testTag("capture.loading"))
                    Text("Reading that...", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { viewModel.cancel(); onBack() }, modifier = Modifier.testTag("capture.cancel")) { Text("Cancel") }
                }
            }
            CaptureStep.CheckTranscript -> {
                Text("Check what we heard", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.transcript,
                    onValueChange = viewModel::onTranscriptChange,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("capture.transcript"),
                )
                Button(onClick = viewModel::parseTranscript, enabled = state.transcript.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("capture.parseTranscript")) { Text("Parse") }
            }
            CaptureStep.Review -> {
                Text("Review transactions", style = MaterialTheme.typography.titleMedium)
                if (state.items.isEmpty()) {
                    Text("We couldn't find any transactions in that. Try rephrasing.", modifier = Modifier.testTag("capture.empty"))
                    OutlinedButton(onClick = viewModel::retryEmptyAnalysis, modifier = Modifier.testTag("capture.tryAgain")) { Text("Try again") }
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
                        Text("Check each included amount and description.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("capture.save")) {
                        Text("Save ${state.includedItems.size} transactions")
                    }
                    OutlinedButton(onClick = viewModel::restart, modifier = Modifier.fillMaxWidth().testTag("capture.restart")) { Text("Start over") }
                }
            }
            CaptureStep.Saving -> {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CircularProgressIndicator(modifier = Modifier.testTag("capture.saving"))
                    Text("Saving transactions…")
                }
            }
            CaptureStep.Done -> {
                Text("Transactions saved", modifier = Modifier.testTag("capture.done"))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().testTag("capture.done.back")) { Text("Done") }
            }
        }
    }
}

private fun formatRecordingTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtMost(60)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
