package com.spenditos.android.feature.export

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.GroupedCard
import com.spenditos.android.ui.components.IosRow
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.components.SectionHeader
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Settings > Export transactions: pick a range, download the CSV, then share it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var picking by remember { mutableStateOf<DateTarget?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    LaunchedEffect(state.file) {
        val file = state.file ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export transactions").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        viewModel.shared()
    }

    IosScreen {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = Spacing.m, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ExportHeader(onBack = { viewModel.cancel(); onBack() })

            SectionHeader("Transactions", Modifier.padding(top = Spacing.s).testTag("export.section.transactions"))
            GroupedCard {
                run {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m, vertical = Spacing.s),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        ExportRange.entries.forEach { range ->
                            val selected = state.range == range
                            Box(
                                Modifier.clip(RoundedCornerShape(50.dp))
                                    .background(if (selected) IosColors.spendOrange.copy(alpha = 0.12f) else IosColors.textTertiary.copy(alpha = 0.18f))
                                    .clickable(role = Role.Tab) { viewModel.selectRange(range) }
                                    .semantics { this.selected = selected }
                                    .testTag("export.range.${range.name.lowercase()}")
                                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(range.label, style = IosText.caption.w(FontWeight.SemiBold), color = if (selected) IosColors.spendOrange else IosColors.textSecondary)
                            }
                        }
                    }
                    IosRow(
                        title = "Start Date",
                        value = state.start.format(dateFormat),
                        icon = SfSymbols.icon("calendar"),
                        iconTint = IosColors.spendOrange,
                        onClick = { picking = DateTarget.START },
                        modifier = Modifier.testTag("export.start"),
                    )
                    IosRow(
                        title = "End Date",
                        value = state.end.format(dateFormat),
                        icon = SfSymbols.icon("calendar"),
                        iconTint = IosColors.spendOrange,
                        onClick = { picking = DateTarget.END },
                        divider = false,
                        modifier = Modifier.testTag("export.end"),
                    )
                }
                HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = state.canExport && !state.isExporting, role = Role.Button, onClick = viewModel::export)
                        .padding(horizontal = Spacing.m, vertical = Spacing.s)
                        .testTag("export.run"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(Modifier.size(18.dp).testTag("export.progress"), strokeWidth = 2.dp, color = IosColors.spendOrange)
                    } else {
                        Icon(SfSymbols.icon("square.and.arrow.up"), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.size(18.dp))
                    }
                    Text("Export Transactions", style = IosText.callout, color = IosColors.spendOrange, modifier = Modifier.weight(1f))
                    if (state.isExporting) {
                        Text(
                            "Cancel",
                            style = IosText.callout,
                            color = IosColors.textSecondary,
                            modifier = Modifier.clickable(role = Role.Button, onClick = viewModel::cancel).testTag("export.cancel"),
                        )
                    }
                }
            }

            (state.validationError ?: state.error)?.let {
                GlassCard(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.m)) {
                    Text(it, style = IosText.footnote, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.m).testTag("export.error"))
                }
            }
        }
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
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun ExportHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        androidx.compose.material3.IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("export.back")) {
            androidx.compose.material3.Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text("Export Data", style = IosText.headline, color = IosColors.textPrimary, modifier = Modifier.align(Alignment.Center))
    }
}

private enum class DateTarget { START, END }
