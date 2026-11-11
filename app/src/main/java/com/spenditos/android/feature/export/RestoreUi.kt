package com.spenditos.android.feature.export

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.spenditos.android.R
import com.spenditos.android.data.restore.RestoreProgressPhase
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "Uploading 2/5", "Checking the file", "Saving a copy for Undo" or "Restoring entries 40/120" while a restore runs. */
@Composable
internal fun restoreBusyLabel(state: RestoreUiState): String = when (state.phase) {
    RestoreUiPhase.Uploading -> stringResource(R.string.restore_uploading, state.uploadedParts, state.totalParts)
    RestoreUiPhase.Restoring -> state.progress?.let { progress ->
        when (progress.phase) {
            RestoreProgressPhase.SNAPSHOT -> stringResource(R.string.restore_phase_snapshot)
            RestoreProgressPhase.WIPE -> stringResource(R.string.restore_phase_wipe)
            else -> {
                val phase = when (progress.phase) {
                    RestoreProgressPhase.SETTINGS -> R.string.restore_phase_settings
                    RestoreProgressPhase.CATEGORIES -> R.string.restore_phase_categories
                    RestoreProgressPhase.INCOME_AND_BILLS -> R.string.restore_phase_income_and_bills
                    RestoreProgressPhase.DEBTS -> R.string.restore_phase_debts
                    RestoreProgressPhase.RULES -> R.string.restore_phase_rules
                    RestoreProgressPhase.TRANSACTIONS -> R.string.restore_phase_transactions
                    RestoreProgressPhase.MONTH_BALANCE_CHOICES -> R.string.restore_phase_month_choices
                    else -> null
                }
                phase?.let { stringResource(R.string.restore_restoring, stringResource(it), progress.done, progress.total) }
            }
        }
    } ?: stringResource(R.string.restore_restoring_plain)
    RestoreUiPhase.CheckingAccount -> stringResource(R.string.restore_checking)
    else -> stringResource(R.string.restore_checking)
}

/** The plain confirmation, the Replace confirmation (type the word) and the restore error. Shown wherever a restore can start. */
@Composable
internal fun RestoreDialogs(state: RestoreUiState, viewModel: RestoreViewModel) {
    when (state.phase) {
        RestoreUiPhase.Confirming -> AlertDialog(
            onDismissRequest = viewModel::cancelConfirmation,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message), modifier = Modifier.testTag("restore.confirm.message")) },
            confirmButton = { TextButton(onClick = viewModel::confirm, modifier = Modifier.testTag("restore.confirm")) { Text(stringResource(R.string.restore_confirm_action)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirmation) { Text(stringResource(R.string.common_cancel)) } },
        )
        RestoreUiPhase.ConfirmingReplace -> AlertDialog(
            onDismissRequest = viewModel::cancelConfirmation,
            title = { Text(stringResource(R.string.restore_replace_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.counts?.let { c ->
                        Text(
                            stringResource(
                                R.string.restore_replace_erased,
                                formatCount(c.entries), formatCount(c.incomeAndBills), formatCount(c.debts), formatCount(c.categories), formatCount(c.rules),
                            ),
                            modifier = Modifier.testTag("restore.replace.erased"),
                        )
                    }
                    Text(stringResource(R.string.restore_replace_undo_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = state.replaceText,
                        onValueChange = viewModel::onReplaceTextChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.restore_replace_type_hint)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().testTag("restore.replace.input"),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirm, enabled = state.canReplace, modifier = Modifier.testTag("restore.replace.confirm")) {
                    Text(stringResource(R.string.restore_replace_action), color = if (state.canReplace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelConfirmation) { Text(stringResource(R.string.common_cancel)) } },
        )
        RestoreUiPhase.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.restore_failed_title)) },
            text = { Text(state.error.orEmpty(), modifier = Modifier.testTag("restore.error")) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.common_ok)) } },
        )
        else -> Unit
    }
}

/** Undo confirmation and Undo error. */
@Composable
internal fun UndoDialogs(state: RestoreUiState, viewModel: RestoreViewModel) {
    if (state.undoPhase == UndoPhase.Confirming) {
        AlertDialog(
            onDismissRequest = viewModel::cancelUndoConfirmation,
            title = { Text(stringResource(R.string.restore_undo_confirm_title)) },
            text = { Text(stringResource(R.string.restore_undo_confirm_message)) },
            confirmButton = { TextButton(onClick = viewModel::confirmUndo, modifier = Modifier.testTag("restore.undo.confirm")) { Text(stringResource(R.string.restore_undo_confirm_action)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelUndoConfirmation) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    state.undoError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUndoError,
            title = { Text(stringResource(R.string.restore_undo_failed_title)) },
            text = { Text(message, modifier = Modifier.testTag("restore.undo.error")) },
            confirmButton = { TextButton(onClick = viewModel::dismissUndoError) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

internal fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** `2026-11-18T10:00:00Z` -> a medium local date; the raw text when it cannot be read. */
internal fun formatUntil(iso: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrDefault(iso)
