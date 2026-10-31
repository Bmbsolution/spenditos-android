package com.spenditos.android.feature.plan

import androidx.compose.ui.res.stringResource
import com.spenditos.android.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.spenditos.android.feature.recurring.IncomeAndBillsScreen
import com.spenditos.android.feature.recurring.IncomeAndBillsViewModel
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.IosSegmented
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.components.SfSymbols
import com.spenditos.android.ui.theme.IosColors
import com.spenditos.android.ui.theme.IosText
import com.spenditos.android.ui.theme.Spacing
import androidx.compose.ui.unit.dp

/**
 * Plan tab (iOS PlanHubView): one large title and a shared segmented control sit above either
 * section, matching the hierarchy of the native iOS tab.
 */
@Composable
fun PlanHubScreen(onOpenDebt: (debtId: String) -> Unit = {}, onOpenEnded: () -> Unit = {}) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var addOneTimeRequest by rememberSaveable { mutableIntStateOf(0) }
    var generateRequest by rememberSaveable { mutableIntStateOf(0) }
    // The bills view model is shared with the section below, so the overflow menu can show the Ended count.
    val billsViewModel: IncomeAndBillsViewModel = hiltViewModel()
    val billsState by billsViewModel.state.collectAsStateWithLifecycle()
    // "Plan November" from Generate (#403): the month Month plan should open on, as "yyyy-MM".
    var requestedMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val requestedMonth = requestedMonthKey?.let { key -> runCatching { YearMonth.parse(key) }.getOrNull() }
    val labels = listOf(stringResource(R.string.plan_income_bills), stringResource(R.string.plan_month_plan))

    IosScreen {
        Column(Modifier.fillMaxSize()) {
            if (selected == 0) {
                LargeTitle(
                    labels[selected],
                    trailing = {
                        BillsOverflowMenu(
                            endedCount = billsState.endedTemplates.size,
                            isGenerating = billsState.isWriting,
                            onGenerate = { generateRequest += 1 },
                            onEnded = onOpenEnded,
                        )
                    },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Spacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(44.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(labels[selected], style = IosText.headline, color = IosColors.textPrimary)
                    }
                    IconButton(
                        onClick = { addOneTimeRequest += 1 },
                        modifier = Modifier.size(44.dp).testTag("planning.add-item"),
                    ) {
                        Icon(
                            SfSymbols.icon("plus"),
                            contentDescription = stringResource(R.string.plan_add_one_time_item),
                            tint = IosColors.spendOrange,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            IosSegmented(
                options = labels,
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(vertical = Spacing.s),
                tagPrefix = "plan.segment",
            )
            Box(Modifier.weight(1f)) {
                when (selected) {
                    0 -> IncomeAndBillsScreen(
                        onOpenDebt = onOpenDebt,
                        onOpenEnded = onOpenEnded,
                        onPlanMonth = { month ->
                            // Generate sends a future month to Month plan instead of logging it (#403).
                            requestedMonthKey = month.toString()
                            selected = 1
                        },
                        generateRequest = generateRequest,
                        onGenerateRequestHandled = { handled -> if (generateRequest == handled) generateRequest = 0 },
                    )
                    else -> MonthPlanScreen(
                        addOneTimeRequest = addOneTimeRequest,
                        onAddOneTimeRequestHandled = { handled ->
                            if (addOneTimeRequest == handled) addOneTimeRequest = 0
                        },
                        onOpenIncomeAndBills = { selected = 0 },
                        requestedMonth = requestedMonth,
                        onRequestedMonthHandled = { requestedMonthKey = null },
                    )
                }
            }
        }
    }
}

/** iOS toolbar menu (#403): the rarely-used tools, out of the way of the list. */
@Composable
private fun BillsOverflowMenu(endedCount: Int, isGenerating: Boolean, onGenerate: () -> Unit, onEnded: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(44.dp).testTag("bills.more")) {
            Icon(SfSymbols.icon("ellipsis.circle"), contentDescription = "More", tint = IosColors.textPrimary, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_generate_a_month), style = IosText.subheadline) },
                leadingIcon = { Icon(SfSymbols.icon("calendar.badge.plus"), contentDescription = null) },
                enabled = !isGenerating,
                onClick = { expanded = false; onGenerate() },
                modifier = Modifier.testTag("bills.more.generate"),
            )
            DropdownMenuItem(
                text = { Text(if (endedCount == 0) stringResource(R.string.common_ended) else stringResource(R.string.common_ended_2, endedCount), style = IosText.subheadline) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = { expanded = false; onEnded() },
                modifier = Modifier.testTag("bills.more.ended"),
            )
        }
    }
}
