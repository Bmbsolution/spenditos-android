package com.spenditos.android.feature.statements

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IconChip
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.theme.Spacing

private val STATEMENT_MIME_TYPES = arrayOf(
    "text/csv",
    "text/plain",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/comma-separated-values",
    "application/octet-stream",
)

@Composable
fun StatementImportScreen(onBack: () -> Unit) {
    val viewModel: StatementImportViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showImportConfirmation by remember { mutableStateOf(false) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    BackHandler(enabled = state.importRequestSent) { viewModel.noteBackBlocked() }

    IosScreen(modifier = Modifier.testTag("statementImport.screen")) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            StatementHeader(
                onBack = onBack,
                enabled = !state.importRequestSent,
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = Spacing.screen, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                when {
                    state.isCheckingSettings -> LoadingCard("Checking account access…", "statementImport.settings.loading")
                    state.settingsError != null -> ErrorCard(
                        message = state.settingsError,
                        offline = state.settingsOffline,
                        retryEnabled = true,
                        onRetry = viewModel::retrySettings,
                        tag = "statementImport.settings.error",
                        retryTag = "statementImport.settings.retry",
                    )
                    !state.premiumActive -> ProFeatureCard()
                    else -> when (state.step) {
                        StatementImportStep.CHOOSE -> ChooseFileStep(
                            error = state.error,
                            offline = state.isOffline,
                            onChoose = { documentPicker.launch(STATEMENT_MIME_TYPES) },
                        )
                        StatementImportStep.ANALYZING -> ProgressCard(
                            title = if (state.isWorking) "Analyzing statement…" else "Statement ready to retry",
                            message = if (state.isWorking) "This can take a minute." else null,
                            error = state.error,
                            offline = state.isOffline,
                            showSpinner = state.isWorking,
                            canRetry = state.canRetry,
                            onRetry = viewModel::retry,
                            onChooseAnother = { documentPicker.launch(STATEMENT_MIME_TYPES) },
                        )
                        StatementImportStep.MAP_COLUMNS -> {
                            ErrorCardIfNeeded(state.error, state.isOffline, state.canRetry, viewModel::retry)
                            val analysis = state.analysis
                            val mapping = state.mapping
                            if (analysis != null && mapping != null) {
                                ColumnMappingStep(
                                    analysis = analysis,
                                    mapping = mapping,
                                    onMappingChange = viewModel::onMappingChange,
                                    onPreview = viewModel::preview,
                                    isWorking = state.isWorking,
                                )
                            } else if (state.isWorking) {
                                LoadingCard("Preparing columns…", "statementImport.mapping.loading")
                            } else {
                                Text("Try again to reload the file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("statementImport.mapping.waiting"))
                            }
                        }
                        StatementImportStep.PREVIEW -> {
                            ErrorCardIfNeeded(state.error, state.isOffline, state.canRetry, viewModel::retry)
                            val preview = state.preview
                            if (preview != null) {
                                PreviewStep(preview, state.isWorking, { showImportConfirmation = true }, Modifier.weight(1f), state.currency)
                            } else if (state.isWorking) {
                                LoadingCard("Preparing preview…", "statementImport.preview.loading")
                            } else {
                                Text("Try again to rebuild the preview.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("statementImport.preview.waiting"))
                            }
                        }
                        StatementImportStep.IMPORTING -> ImportingStep(showBackHint = state.backBlockedHint)
                        StatementImportStep.COMPLETE -> CompleteStep(state = state, onDone = onBack)
                    }
                }
            }
        }
    }

    if (showImportConfirmation) {
        val count = state.preview?.transactions?.size ?: 0
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("Import transactions?") },
            text = { Text("Import $count transactions into your account?") },
            confirmButton = {
                TextButton(
                    onClick = { showImportConfirmation = false; viewModel.startImport() },
                    modifier = Modifier.testTag("statementImport.confirmImport"),
                ) { Text("Import $count") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportConfirmation = false },
                    modifier = Modifier.testTag("statementImport.cancelImport"),
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatementHeader(onBack: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, enabled = enabled, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("statementImport.back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Text("Import statement", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChooseFileStep(error: String?, offline: Boolean, onChoose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GlassCard {
            Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                IconChip(Icons.Filled.Description, MaterialTheme.colorScheme.primary, size = 64.dp)
                Text("Import a bank statement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Choose a CSV or Excel file. Review the columns and transactions before importing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Files must be 25 MB or smaller.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (offline) OfflineMessage()
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("statementImport.choose.error"))
        PillButton(text = "Choose file", onClick = onChoose, modifier = Modifier.fillMaxWidth().testTag("statementImport.chooseFile"))
    }
}

@Composable
private fun ProFeatureCard() {
    GlassCard(Modifier.testTag("statementImport.proFeature")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            IconChip(Icons.Filled.AutoAwesome, Color(0xFFD69C00), size = 44.dp)
            Text("Pro feature", style = MaterialTheme.typography.titleMedium)
            Text("Statement import with column detection is available with Pro.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.spenditos.android.feature.pro.LocalOpenPro.current?.let { openPro ->
                PillButton(text = "See Spenditos Pro", onClick = openPro, filled = false, modifier = Modifier.fillMaxWidth().testTag("statementImport.seePro"))
            }
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    message: String?,
    error: String?,
    offline: Boolean,
    showSpinner: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (showSpinner) CircularProgressIndicator(Modifier.size(32.dp).testTag("statementImport.progress"), color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ErrorCardIfNeeded(error, offline, canRetry, onRetry)
            if (error != null) PillButton(text = "Choose another file", onClick = onChooseAnother, filled = false, modifier = Modifier.fillMaxWidth().testTag("statementImport.chooseAnother"))
        }
    }
}

@Composable
private fun ImportingStep(showBackHint: Boolean) {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CircularProgressIndicator(Modifier.size(36.dp).testTag("statementImport.importing.progress"), color = MaterialTheme.colorScheme.primary)
            Text("Importing transactions…", style = MaterialTheme.typography.titleMedium)
            Text("This can take a minute.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (showBackHint) "Please wait for the import to finish before leaving." else "Keep this screen open while the import finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("statementImport.importing.backHint"),
            )
        }
    }
}

@Composable
private fun CompleteStep(state: StatementImportUiState, onDone: () -> Unit) {
    val result = state.result
    val stats = result?.stats
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            IconChip(Icons.Filled.CheckCircle, Color(0xFF1F7F47), size = 56.dp)
            Text("Import complete", style = MaterialTheme.typography.titleLarge)
            if (result != null) {
                Text(result.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ImportStatRow("Created", stats?.created ?: result.count, Modifier.testTag("statementImport.complete.created"))
                ImportStatRow("Updated", stats?.updated ?: 0, Modifier.testTag("statementImport.complete.updated"))
                ImportStatRow("Skipped", stats?.skipped ?: 0, Modifier.testTag("statementImport.complete.skipped"))
            }
            PillButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("statementImport.complete.done"))
        }
    }
}

@Composable
private fun ImportStatRow(label: String, value: Int, valueModifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = valueModifier)
    }
}

@Composable
private fun LoadingCard(message: String, tag: String) {
    GlassCard(Modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            CircularProgressIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(
    message: String?,
    offline: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    tag: String,
    retryTag: String,
) {
    GlassCard(Modifier.testTag(tag)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (message != null && !offline) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (offline) OfflineMessage()
            if (retryEnabled) TextButton(onClick = onRetry, modifier = Modifier.testTag(retryTag)) { Text("Try again") }
        }
    }
}

@Composable
private fun ErrorCardIfNeeded(error: String?, offline: Boolean, canRetry: Boolean, onRetry: () -> Unit) {
    if (error != null) {
        ErrorCard(error, offline, canRetry, onRetry, "statementImport.error", "statementImport.retry")
    }
}

@Composable
private fun OfflineMessage() {
    Text("You're offline. Check your connection and try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("statementImport.offline"))
}
