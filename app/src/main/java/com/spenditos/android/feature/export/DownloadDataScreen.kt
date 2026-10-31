package com.spenditos.android.feature.export

import com.spenditos.android.core.i18n.Strings
import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import android.content.Intent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * iOS `DownloadDataView` (Settings > Download my data): the user's own copy of their data, either one full
 * JSON file (`GET /user/export`, saved through the system picker) or a spreadsheet of transactions (CSV).
 * Statements and CSVs come IN through Scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDataScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
    fullCopyViewModel: FullCopyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fullCopy by fullCopyViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        fullCopyViewModel.destinationPicked(uri)
    }
    LaunchedEffect(fullCopy.phase, fullCopy.pickerRequested) {
        val name = fullCopy.suggestedName
        if (fullCopy.phase == FullCopyPhase.ChooseDestination && !fullCopy.pickerRequested && name != null) {
            fullCopyViewModel.pickerRequested()
            saveLauncher.launch(name)
        }
    }

    LaunchedEffect(state.file) {
        val file = state.file ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, Strings.get(R.string.export_download_spreadsheet)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        viewModel.shared()
    }

    IosScreen {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            DownloadHeader(onBack = { viewModel.cancel(); fullCopyViewModel.cancel(); onBack() })

            Text(
                stringResource(R.string.export_your_data_is_saved_to),
                style = IosText.subheadline,
                color = IosColors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.screen + Spacing.xs),
            )

            // Full copy
            GlassCard(Modifier.padding(horizontal = Spacing.screen).testTag("download.fullCopy")) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CardHeader(
                        icon = SfSymbols.icon("doc.on.doc.fill"),
                        tint = IosColors.info,
                        title = stringResource(R.string.export_full_copy),
                        subtitle = stringResource(R.string.export_your_account_categories_income_bills),
                    )
                    DownloadButton(
                        title = stringResource(R.string.export_download_full_copy),
                        icon = SfSymbols.icon("arrow.down.circle"),
                        busyLabel = if (fullCopy.phase == FullCopyPhase.Saving) stringResource(R.string.common_saving_2) else stringResource(R.string.export_preparing),
                        busy = fullCopy.isBusy,
                        onClick = fullCopyViewModel::download,
                        modifier = Modifier.testTag("download.fullCopy.button"),
                    )
                    when (fullCopy.phase) {
                        FullCopyPhase.Saved -> Text(
                            stringResource(R.string.export_saved_your_file_is_where),
                            style = IosText.caption,
                            color = IosColors.income,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().testTag("download.fullCopy.saved"),
                        )
                        FullCopyPhase.Preparing -> Text(
                            stringResource(R.string.common_cancel),
                            style = IosText.caption,
                            color = IosColors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = fullCopyViewModel::cancel).padding(vertical = Spacing.xs)
                                .testTag("download.fullCopy.cancel"),
                        )
                        else -> Text(
                            "JSON file",
                            style = IosText.caption,
                            color = IosColors.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Spreadsheet
            GlassCard(Modifier.padding(horizontal = Spacing.screen).testTag("download.spreadsheet")) {
                Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    CardHeader(
                        icon = SfSymbols.icon("tablecells.fill"),
                        tint = IosColors.income,
                        title = stringResource(R.string.export_spreadsheet),
                        subtitle = stringResource(R.string.export_transactions_for_excel_numbers_or),
                    )
                    PeriodPicker(selected = state.range, onSelect = viewModel::selectRange)
                    Column {
                        IosRow(
                            title = stringResource(R.string.common_from),
                            value = state.start.format(dateFormat),
                            icon = SfSymbols.icon("calendar"),
                            iconTint = IosColors.spendOrange,
                            onClick = { picking = DateTarget.START },
                            modifier = Modifier.testTag("export.start"),
                        )
                        IosRow(
                            title = stringResource(R.string.common_to),
                            value = state.end.format(dateFormat),
                            icon = SfSymbols.icon("calendar"),
                            iconTint = IosColors.spendOrange,
                            onClick = { picking = DateTarget.END },
                            divider = false,
                            modifier = Modifier.testTag("export.end"),
                        )
                    }
                    state.validationError?.let {
                        Text(it, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("export.validation"))
                    }
                    DownloadButton(
                        title = stringResource(R.string.export_download_spreadsheet),
                        icon = SfSymbols.icon("arrow.down.circle"),
                        busyLabel = stringResource(R.string.export_preparing),
                        busy = state.isExporting,
                        enabled = state.canExport,
                        onClick = viewModel::export,
                        modifier = Modifier.testTag("export.run"),
                    )
                    Text(
                        stringResource(R.string.export_csv_file_up_to_2_years),
                        style = IosText.caption,
                        color = IosColors.textTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    (fullCopy.error ?: state.error)?.let { message ->
        AlertDialog(
            onDismissRequest = { fullCopyViewModel.dismissError(); viewModel.dismissError() },
            title = { Text(stringResource(R.string.export_download_failed)) },
            text = { Text(message, modifier = Modifier.testTag("download.error")) },
            confirmButton = { TextButton(onClick = { fullCopyViewModel.dismissError(); viewModel.dismissError() }) { Text("OK") } },
        )
    }

    picking?.let { target ->
        val initial = if (target == DateTarget.START) state.start else state.end
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                        if (target == DateTarget.START) viewModel.setStart(date) else viewModel.setEnd(date)
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.common_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DownloadHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("export.back")) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text(stringResource(R.string.common_download_my_data), style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}

/** iOS `cardHeader`: 40pt tinted tile, headline title, subheadline subtitle. */
@Composable
private fun CardHeader(icon: ImageVector, tint: Color, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = IosText.headline, color = IosColors.textPrimary)
            Text(subtitle, style = IosText.subheadline, color = IosColors.textSecondary)
        }
    }
}

/** iOS segmented `Picker("Period")`. */
@Composable
private fun PeriodPicker(selected: ExportRange, onSelect: (ExportRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(IosColors.textTertiary.copy(alpha = 0.12f)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ExportRange.entries.forEach { range ->
            val isSelected = range == selected
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(range) }
                    .semantics { this.selected = isSelected }
                    .testTag("export.range.${range.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(range.label, style = IosText.footnote.w(if (isSelected) FontWeight.SemiBold else FontWeight.Normal), color = IosColors.textPrimary, maxLines = 1)
            }
        }
    }
}

/** iOS `ExportButton`: full-width orange capsule; "Preparing…" with a spinner while the file is made. */
@Composable
private fun DownloadButton(
    title: String,
    icon: ImageVector,
    busyLabel: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().alpha(if (busy || !enabled) 0.6f else 1f).clip(CircleShape).background(IosColors.spendOrange)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.s + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp).testTag("download.progress"), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Text(if (busy) busyLabel else title, style = IosText.callout.w(FontWeight.SemiBold), color = Color.White)
    }
}

private enum class DateTarget { START, END }
