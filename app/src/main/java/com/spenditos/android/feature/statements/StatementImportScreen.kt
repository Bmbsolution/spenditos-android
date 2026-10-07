package com.spenditos.android.feature.statements

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.PillButton
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w

private val SystemBlue = Color(0xFF007AFF)

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

    Box(Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color.Black else Color.White).statusBarsPadding().testTag("statementImport.screen")) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            StatementHeader(
                onBack = onBack,
                enabled = !state.importRequestSent,
            )
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                when {
                    state.isCheckingSettings -> LoadingCard("Checking account access…", "statementImport.settings.loading", Modifier.weight(1f))
                    state.settingsError != null -> ErrorCard(
                        message = state.settingsError,
                        offline = state.settingsOffline,
                        retryEnabled = true,
                        onRetry = viewModel::retrySettings,
                        tag = "statementImport.settings.error",
                        retryTag = "statementImport.settings.retry",
                    )
                    !state.premiumActive -> ChooseFileStep(
                        modifier = Modifier.weight(1f),
                        proEnabled = false,
                        onChoose = { },
                        onUpgrade = com.spenditos.android.feature.pro.LocalOpenPro.current,
                    )
                    else -> when (state.step) {
                        StatementImportStep.CHOOSE -> ChooseFileStep(
                            modifier = Modifier.weight(1f),
                            proEnabled = true,
                            error = state.error,
                            offline = state.isOffline,
                            onChoose = { documentPicker.launch(STATEMENT_MIME_TYPES) },
                            onUpgrade = null,
                        )
                        StatementImportStep.ANALYZING -> ProgressCard(
                            modifier = Modifier.weight(1f),
                            title = if (state.isWorking) "AI is analyzing columns..." else "Statement ready to retry",
                            message = if (state.isWorking) "Detecting date, amount, description fields" else null,
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
                                    modifier = Modifier.weight(1f),
                                )
                            } else if (state.isWorking) {
                                LoadingCard("Preparing columns…", "statementImport.mapping.loading", Modifier.weight(1f))
                            } else {
                                Text("Try again to reload the file.", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("statementImport.mapping.waiting"))
                            }
                        }
                        StatementImportStep.PREVIEW -> {
                            ErrorCardIfNeeded(state.error, state.isOffline, state.canRetry, viewModel::retry)
                            val preview = state.preview
                            if (preview != null) {
                                PreviewStep(preview, state.isWorking, { showImportConfirmation = true }, Modifier.weight(1f), state.currency)
                            } else if (state.isWorking) {
                                LoadingCard("Preparing preview…", "statementImport.preview.loading", Modifier.weight(1f))
                            } else {
                                Text("Try again to rebuild the preview.", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.testTag("statementImport.preview.waiting"))
                            }
                        }
                        StatementImportStep.IMPORTING -> ImportingStep(showBackHint = state.backBlockedHint, modifier = Modifier.weight(1f))
                        StatementImportStep.COMPLETE -> CompleteStep(state = state, onDone = onBack, modifier = Modifier.weight(1f))
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
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        Text(
            "Cancel",
            style = IosText.body,
            color = if (enabled) SystemBlue else IosColors.textTertiary,
            modifier = Modifier.align(Alignment.CenterStart).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" }
                .padding(horizontal = Spacing.m, vertical = Spacing.s).testTag("statementImport.back"),
        )
        Text("Import Statement", style = IosText.headline, modifier = Modifier.align(Alignment.Center), color = IosColors.textPrimary)
    }
}

@Composable
private fun ChooseFileStep(
    modifier: Modifier = Modifier,
    proEnabled: Boolean,
    error: String? = null,
    offline: Boolean = false,
    onChoose: () -> Unit,
    onUpgrade: (() -> Unit)?,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.weight(1f))
        Icon(SfSymbols.icon("doc.text"), contentDescription = null, tint = SystemBlue, modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally))
        Text("Import Bank Statement", style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(
            "Upload your bank statement (CSV/Excel/XLSX). AI will automatically detect columns and map transactions.",
            style = IosText.body,
            color = IosColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (!proEnabled) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(secondarySystemBackgroundColor()).padding(16.dp).testTag("statementImport.proFeature"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Icon(SfSymbols.icon("sparkles"), contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(20.4.dp))
                Text("Pro Feature", style = IosText.headline, color = IosColors.textPrimary)
                Text("Statement import with AI column detection", style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        if (offline) OfflineMessage()
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = IosText.footnote, modifier = Modifier.testTag("statementImport.choose.error"))
        Text("Files must be 25 MB or smaller.", style = IosText.footnote, color = IosColors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        if (proEnabled || onUpgrade != null) {
            StatementActionButton(
                text = if (proEnabled) "Choose Statement File" else "Upgrade to Pro",
                symbol = if (proEnabled) "folder" else "sparkles",
                onClick = if (proEnabled) onChoose else onUpgrade!!,
                modifier = Modifier.fillMaxWidth().testTag(if (proEnabled) "statementImport.chooseFile" else "statementImport.seePro"),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun secondarySystemBackgroundColor(): Color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)

@Composable
internal fun StatementActionButton(
    text: String,
    symbol: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 54.dp,
) {
    androidx.compose.foundation.layout.Row(
        modifier.height(height).clip(RoundedCornerShape(12.dp)).background(if (enabled) SystemBlue else Color(0xFF8E8E93))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) Icon(SfSymbols.icon(symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.4.dp))
        Text(text, style = IosText.headline, color = Color.White)
    }
}

@Composable
private fun ProgressCard(
    modifier: Modifier = Modifier,
    title: String,
    message: String?,
    error: String?,
    offline: Boolean,
    showSpinner: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (showSpinner) CircularProgressIndicator(Modifier.size(30.dp).testTag("statementImport.progress"), color = SystemBlue)
            Text(title, style = IosText.headline, color = IosColors.textPrimary)
            message?.let { Text(it, style = IosText.subheadline, color = IosColors.textSecondary) }
            if (offline) OfflineMessage()
            ErrorCardIfNeeded(error, offline, canRetry, onRetry)
            if (error != null) PillButton(text = "Choose another file", onClick = onChooseAnother, filled = false, modifier = Modifier.fillMaxWidth().testTag("statementImport.chooseAnother"))
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ImportingStep(showBackHint: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        CircularProgressIndicator(Modifier.size(30.dp).testTag("statementImport.importing.progress"), color = SystemBlue)
            Text("Importing transactions...", style = IosText.headline, color = IosColors.textPrimary)
            Text("This can take a minute.", style = IosText.subheadline, color = IosColors.textSecondary)
            Text(
                if (showBackHint) "Please wait for the import to finish before leaving." else "Keep this screen open while the import finishes.",
                style = IosText.footnote,
                color = IosColors.textSecondary,
                modifier = Modifier.testTag("statementImport.importing.backHint"),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CompleteStep(state: StatementImportUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val result = state.result
    val stats = result?.stats
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Icon(SfSymbols.icon("checkmark.circle.fill"), contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(96.dp))
            Text("Import Complete!", style = IosText.largeTitle.w(FontWeight.Bold), color = IosColors.textPrimary, textAlign = TextAlign.Center)
            if (result != null) {
                Text(result.message, style = IosText.subheadline, color = IosColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                ImportStatRow("Created", stats?.created ?: result.count, Modifier.testTag("statementImport.complete.created"))
                ImportStatRow("Updated", stats?.updated ?: 0, Modifier.testTag("statementImport.complete.updated"))
                ImportStatRow("Skipped", stats?.skipped ?: 0, Modifier.testTag("statementImport.complete.skipped"))
            }
        }
        StatementActionButton("Done", null, onDone, Modifier.testTag("statementImport.complete.done"), height = 44.dp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ImportStatRow(label: String, value: Int, valueModifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = IosText.subheadline, color = IosColors.textSecondary)
        Text(value.toString(), style = IosText.subheadline.w(FontWeight.SemiBold), color = IosColors.textPrimary, modifier = valueModifier)
    }
}

@Composable
private fun LoadingCard(message: String, tag: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), color = SystemBlue)
        Text(message, style = IosText.body, color = IosColors.textSecondary)
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
            if (message != null && !offline) Text(message, color = MaterialTheme.colorScheme.error, style = IosText.footnote)
            if (offline) OfflineMessage()
            if (retryEnabled) TextButton(onClick = onRetry, modifier = Modifier.testTag(retryTag)) { Text("Try again", style = IosText.headline, color = SystemBlue) }
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
    Text("You're offline. Check your connection and try again.", color = MaterialTheme.colorScheme.error, style = IosText.footnote, modifier = Modifier.testTag("statementImport.offline"))
}
