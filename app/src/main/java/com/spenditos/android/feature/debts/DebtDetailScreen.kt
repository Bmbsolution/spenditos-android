package com.spenditos.android.feature.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spenditos.android.ui.components.ErrorBanner
import com.spenditos.android.ui.components.GlassCard
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import com.spenditos.android.ui.theme.w
import kotlinx.coroutines.launch

/**
 * One debt (iOS `DebtDetailView`): what is left, the plan, "What if I pay more?", extra payments,
 * balance corrections, history and delete (docs/features/debts.md). Every write is online only.
 */
@Composable
fun DebtDetailScreen(onBack: () -> Unit, viewModel: DebtDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExtraPayment by rememberSaveable { mutableStateOf(false) }
    var showCorrectBalance by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DebtDetailEvent.ExtraPaymentAdded -> { showExtraPayment = false; snackbar.showSnackbar("Extra payment added") }
                DebtDetailEvent.BalanceCorrected -> { showCorrectBalance = false; snackbar.showSnackbar("Balance corrected") }
                DebtDetailEvent.PaymentUpdated -> snackbar.showSnackbar("Payment updated")
                DebtDetailEvent.Deleted -> onBack()
            }
        }
    }

    IosScreen {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                DebtTopBar(state.debt?.name ?: "Debt", onBack)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (state.plan == null) {
                        if (state.isLoading) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("debt.loading"), color = IosColors.spendOrange)
                        } else {
                            state.error?.let { error ->
                                ErrorBanner(error, Modifier.padding(Spacing.screen).testTag("debt.error"), onRetry = viewModel::retry)
                            }
                        }
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { scope.launch { isRefreshing = true; viewModel.refresh(); isRefreshing = false } },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            DebtContent(
                                state = state,
                                viewModel = viewModel,
                                onAddExtra = { viewModel.dismissError(); showExtraPayment = true },
                                onCorrect = { viewModel.dismissError(); showCorrectBalance = true },
                                onDelete = { showDeleteConfirmation = true },
                            )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showExtraPayment) {
        DebtExtraPaymentSheet(
            state = state,
            onDismiss = { showExtraPayment = false },
            onDismissError = viewModel::dismissError,
            onSubmit = viewModel::submitExtraPayment,
        )
    }
    if (showCorrectBalance) {
        DebtCorrectBalanceSheet(
            state = state,
            onDismiss = { showCorrectBalance = false },
            onDismissError = viewModel::dismissError,
            onSubmit = viewModel::submitCorrectBalance,
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this debt?") },
            text = { Text("Its payments are removed from your plan. Payments you already made stay in your transactions.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false; viewModel.submitDelete() },
                    modifier = Modifier.testTag("debt.delete.confirm"),
                ) { Text("Delete this debt", color = IosColors.expense) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, modifier = Modifier.testTag("debt.delete.cancel")) { Text("Keep it") }
            },
        )
    }
}

/** iOS inline navigation bar: back chevron, 17pt semibold title. */
@Composable
private fun DebtTopBar(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Spacing.s)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp).testTag("debt.back")) {
            Icon(SfSymbols.icon("chevron.left"), contentDescription = "Back", tint = IosColors.spendOrange)
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 52.dp),
            style = IosText.headline,
            color = IosColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DebtContent(
    state: DebtDetailUiState,
    viewModel: DebtDetailViewModel,
    onAddExtra: () -> Unit,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("debt.content")
            .padding(horizontal = Spacing.screen).padding(top = Spacing.m, bottom = Spacing.xl + 100.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        state.error?.let { ErrorBanner(it, onDismiss = viewModel::dismissError, modifier = Modifier.testTag("debt.banner")) }
        HeroCard(state)
        if (!state.isPaidOff) PlanCard(state)
        if (state.canWhatIf) WhatIfCard(state, viewModel)
        ActionsCard(state, onAddExtra, onCorrect)
        if (state.historyRows.isNotEmpty()) HistoryCard(state)
        Text(
            "Delete this debt",
            Modifier.fillMaxWidth().clickable(enabled = !state.isWorking, role = Role.Button, onClick = onDelete)
                .padding(vertical = Spacing.m).testTag("debt.delete"),
            style = IosText.callout.w(FontWeight.SemiBold),
            color = IosColors.expense,
            textAlign = TextAlign.Center,
        )
    }
}

// region Hero

@Composable
private fun HeroCard(state: DebtDetailUiState) {
    GlassCard(Modifier.testTag("debt.hero")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (state.isPaidOff) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SfSymbols.icon("checkmark.seal.fill"), contentDescription = null, tint = IosColors.income, modifier = Modifier.size(24.dp))
                    Text("Paid off", style = IosText.title3.w(FontWeight.SemiBold), color = IosColors.income)
                }
                state.paidOffOnText?.let { Text(it, style = IosText.callout, color = IosColors.textSecondary) }
            } else {
                Text("Left to pay", style = IosText.subheadline, color = IosColors.textSecondary)
                Text(
                    state.balanceText,
                    Modifier.semantics { contentDescription = "${state.balanceText} left to pay" }.testTag("debt.balance"),
                    style = IosText.rounded(34, FontWeight.Bold),
                    color = IosColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DebtProgressBar(state.progress, isPaidOff = state.isPaidOff, height = 10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.paidOffText.orEmpty(), Modifier.weight(1f), style = IosText.subheadline, color = IosColors.textSecondary)
                Text(
                    state.percentText,
                    Modifier.padding(start = Spacing.s).semantics { contentDescription = "" },
                    style = IosText.subheadline.w(FontWeight.SemiBold),
                    color = IosColors.textPrimary,
                )
            }
        }
    }
}

// endregion

// region Plan

@Composable
private fun PlanCard(state: DebtDetailUiState) {
    GlassCard(Modifier.testTag("debt.plan")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
            val payment = state.paymentText
            if (state.paymentsStopped) {
                PlanNote("Payments are stopped. Nothing is scheduled for this debt.", PauseIcon)
            } else if (payment != null) {
                InfoRow("Payment", payment)
                state.nextPaymentText?.let { DebtDivider(); InfoRow("Next payment", it) }
                if (state.neverPaysOff) {
                    DebtDivider()
                    PlanNote("This payment is too low to finish the debt. Pay more each time.", SfSymbols.icon("exclamationmark.triangle"), IosColors.expense)
                } else {
                    state.doneByText?.let { DebtDivider(); InfoRow("Done by", it) }
                    state.paymentsLeftText?.let {
                        DebtDivider()
                        Text(it, Modifier.padding(vertical = Spacing.s), style = IosText.subheadline, color = IosColors.textSecondary)
                    }
                }
            } else {
                PlanNote("No regular payment. You pay when you can.", SfSymbols.icon("hand.raised"))
            }

            if (state.showsInterest) {
                DebtDivider()
                state.aprText?.let { InfoRow("Interest rate", it); DebtDivider() }
                state.interestSoFarText?.let { InfoRow("Interest so far", it) }
                state.interestToComeText?.let { DebtDivider(); InfoRow("Interest still to come", it) }
            }
        }
    }
}

private val PauseIcon: ImageVector get() = Icons.Outlined.PauseCircle

@Composable
private fun DebtDivider() = HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s + 2.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f, fill = false), style = IosText.callout, color = IosColors.textSecondary)
        Spacer(Modifier.width(Spacing.s))
        Spacer(Modifier.weight(1f))
        Text(value, style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
    }
}

@Composable
private fun PlanNote(text: String, icon: ImageVector, tint: Color = IosColors.textSecondary) {
    Row(
        Modifier.padding(vertical = Spacing.s + 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = IosText.subheadline, color = tint)
    }
}

// endregion

// region What if I pay more?

@Composable
private fun WhatIfCard(state: DebtDetailUiState, viewModel: DebtDetailViewModel) {
    GlassCard(Modifier.testTag("debt.whatif")) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("What if I pay more?", style = IosText.headline, color = IosColors.textPrimary)

            val newPayment = state.newPayment
            val summary = state.summary
            val frequency = summary?.frequency
            if (newPayment != null && summary != null && frequency != null) {
                Text(
                    if (state.extra > 0) {
                        "${DebtFormat.paymentLine(newPayment, frequency, summary.repeatInterval, state.currency)} · ${DebtFormat.money(state.extra, state.currency)} extra"
                    } else {
                        "Slide to add to each payment"
                    },
                    style = IosText.callout,
                    color = IosColors.textSecondary,
                )
            }

            val max = state.extraMax.toFloat()
            Slider(
                value = state.extra.toFloat().coerceIn(0f, max),
                onValueChange = { viewModel.setExtra(it.toDouble()) },
                valueRange = 0f..max,
                steps = (max / DEBT_EXTRA_STEP.toFloat()).toInt().minus(1).coerceAtLeast(0),
                colors = SliderDefaults.colors(thumbColor = IosColors.spendOrange, activeTrackColor = IosColors.spendOrange),
                modifier = Modifier.fillMaxWidth().testTag("debt.whatif.slider").semantics {
                    contentDescription = "Extra amount each payment"
                    stateDescription = if (state.extra > 0) "${DebtFormat.money(state.extra, state.currency)} extra" else "No extra"
                },
            )

            if (state.extra > 0) WhatIfResultView(state, viewModel)
        }
    }
}

@Composable
private fun ColumnScope.WhatIfResultView(state: DebtDetailUiState, viewModel: DebtDetailViewModel) {
    val error = state.whatIfError
    val result = state.whatIfResult
    if (error != null) {
        Text(error, style = IosText.subheadline, color = IosColors.expense)
    } else if (result != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (result.stillNeverPaysOff) {
                Text("Still not enough to finish this debt.", style = IosText.subheadline, color = IosColors.expense)
            } else {
                result.doneBy?.let { InfoRow("New payoff date", it) }
                if (result.paymentsSooner > 0) InfoRow("Payments sooner", "${result.paymentsSooner}")
                if (state.showsInterest && result.interestSaved > 0) {
                    InfoRow("Interest saved", DebtFormat.money(result.interestSaved, state.currency))
                }
            }
            state.applyWhatIfTitle?.let { title ->
                DebtLoadingButton(title, isLoading = state.isWorking, modifier = Modifier.testTag("debt.whatif.apply")) {
                    viewModel.submitApplyWhatIf()
                }
            }
        }
    } else if (state.isWhatIfLoading) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                Modifier.size(24.dp).semantics { contentDescription = "Working out the new plan" },
                color = IosColors.spendOrange,
                strokeWidth = 2.dp,
            )
        }
    }
}

// endregion

// region Actions and history

@Composable
private fun ActionsCard(state: DebtDetailUiState, onAddExtra: () -> Unit, onCorrect: () -> Unit) {
    GlassCard(Modifier.testTag("debt.actions")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
            if (!state.isPaidOff) {
                ActionRow("Add extra payment", "plus.circle", "debt.addExtraPayment", onAddExtra)
                DebtDivider()
            }
            ActionRow("Correct balance", "slider.horizontal.3", "debt.correctBalance", onCorrect)
        }
    }
}

@Composable
private fun ActionRow(title: String, symbol: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.m).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SfSymbols.icon(symbol), contentDescription = null, tint = IosColors.spendOrange, modifier = Modifier.width(24.dp).height(24.dp))
        Text(title, Modifier.weight(1f), style = IosText.body, color = IosColors.textPrimary)
        Icon(SfSymbols.icon("chevron.right"), contentDescription = null, tint = IosColors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HistoryCard(state: DebtDetailUiState) {
    GlassCard(Modifier.testTag("debt.history")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs)) {
            Text("History", Modifier.padding(vertical = Spacing.s), style = IosText.headline, color = IosColors.textPrimary)
            state.historyRows.forEachIndexed { index, row ->
                if (index > 0) DebtDivider()
                HistoryRowView(row)
            }
        }
    }
}

@Composable
private fun HistoryRowView(row: DebtHistoryRow) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s + 2.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, Modifier.weight(1f), style = IosText.callout.w(FontWeight.SemiBold), color = IosColors.textPrimary)
            Text(
                row.amountText,
                Modifier.padding(start = Spacing.s),
                style = IosText.callout.w(FontWeight.SemiBold),
                color = if (row.kind == com.spenditos.android.data.debts.DebtHistoryKind.ADJUSTMENT) IosColors.textSecondary else IosColors.textPrimary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.dateText, Modifier.weight(1f), style = IosText.caption, color = IosColors.textSecondary)
            Text(row.balanceAfterText, Modifier.padding(start = Spacing.s), style = IosText.caption, color = IosColors.textSecondary)
        }
        row.interestNote?.let { Text(it, style = IosText.caption, color = IosColors.textTertiary) }
    }
}

// endregion
