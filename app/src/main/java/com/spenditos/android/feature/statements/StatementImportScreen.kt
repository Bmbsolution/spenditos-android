package com.spenditos.android.feature.statements

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import statement") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !state.importRequestSent,
                        modifier = Modifier.testTag("statementImport.back"),
                    ) { Text("Back") }
                },
            )
        },
        modifier = Modifier.testTag("statementImport.screen"),
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = Spacing.screen, vertical = Spacing.m),
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
                    StatementImportStep.ANALYZING -> {
                        ProgressCard(
                            title = if (state.isWorking) "Analyzing statement…" else "Statement ready to retry",
                            message = if (state.isWorking) "This can take a minute." else null,
                            error = state.error,
                            offline = state.isOffline,
                            showSpinner = state.isWorking,
                            canRetry = state.canRetry,
                            onRetry = viewModel::retry,
                            onChooseAnother = { documentPicker.launch(STATEMENT_MIME_TYPES) },
                        )
                    }
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
                            Text("Try again to reload the file.", modifier = Modifier.testTag("statementImport.mapping.waiting"))
                        }
                    }
                    StatementImportStep.PREVIEW -> {
                        ErrorCardIfNeeded(state.error, state.isOffline, state.canRetry, viewModel::retry)
                        val preview = state.preview
                        if (preview != null) {
                            PreviewStep(preview, state.isWorking, { showImportConfirmation = true }, Modifier.weight(1f))
                        } else if (state.isWorking) {
                            LoadingCard("Preparing preview…", "statementImport.preview.loading")
                        } else {
                            Text("Try again to rebuild the preview.", modifier = Modifier.testTag("statementImport.preview.waiting"))
                        }
                    }
                    StatementImportStep.IMPORTING -> ImportingStep(showBackHint = state.backBlockedHint)
                    StatementImportStep.COMPLETE -> CompleteStep(
                        state = state,
                        onDone = onBack,
                    )
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
private fun ChooseFileStep(error: String?, offline: Boolean, onChoose: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Import a bank statement", style = MaterialTheme.typography.titleLarge)
        Text("Choose a CSV or Excel file. Review the columns and transactions before importing.", style = MaterialTheme.typography.bodyMedium)
        Text("Files must be 25 MB or smaller.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (offline) OfflineMessage()
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("statementImport.choose.error"))
        Button(onClick = onChoose, modifier = Modifier.fillMaxWidth().testTag("statementImport.chooseFile")) { Text("Choose file") }
    }
}

@Composable
private fun ProFeatureCard() {
    Card(
        Modifier.fillMaxWidth().testTag("statementImport.proFeature"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Pro feature", style = MaterialTheme.typography.titleMedium)
            Text("Statement import with column detection is available with Pro.", style = MaterialTheme.typography.bodyMedium)
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
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (showSpinner) CircularProgressIndicator(Modifier.size(32.dp).testTag("statementImport.progress"))
        Text(title, style = MaterialTheme.typography.titleMedium)
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ErrorCardIfNeeded(error, offline, canRetry, onRetry)
        if (error != null) OutlinedButton(onClick = onChooseAnother, modifier = Modifier.fillMaxWidth().testTag("statementImport.chooseAnother")) { Text("Choose another file") }
    }
}

@Composable
private fun ImportingStep(showBackHint: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        CircularProgressIndicator(Modifier.size(32.dp).testTag("statementImport.importing.progress"))
        Text("Importing transactions…", style = MaterialTheme.typography.titleMedium)
        Text("This can take a minute.", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (showBackHint) "Please wait for the import to finish before leaving." else "Keep this screen open while the import finishes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("statementImport.importing.backHint"),
        )
    }
}

@Composable
private fun CompleteStep(state: StatementImportUiState, onDone: () -> Unit) {
    val result = state.result
    val stats = result?.stats
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Import complete", style = MaterialTheme.typography.titleLarge)
        if (result != null) {
            Text(result.message, style = MaterialTheme.typography.bodyMedium)
            Text("Created: ${stats?.created ?: result.count}", modifier = Modifier.testTag("statementImport.complete.created"))
            Text("Updated: ${stats?.updated ?: 0}", modifier = Modifier.testTag("statementImport.complete.updated"))
            Text("Skipped: ${stats?.skipped ?: 0}", modifier = Modifier.testTag("statementImport.complete.skipped"))
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("statementImport.complete.done")) { Text("Done") }
    }
}

@Composable
private fun LoadingCard(message: String, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        CircularProgressIndicator(Modifier.size(30.dp))
        Text(message)
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
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (message != null && !offline) Text(message, color = MaterialTheme.colorScheme.error)
        if (offline) OfflineMessage()
        if (retryEnabled) OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(retryTag)) { Text("Try again") }
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
    Text("You're offline. Check your connection and try again.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("statementImport.offline"))
}
