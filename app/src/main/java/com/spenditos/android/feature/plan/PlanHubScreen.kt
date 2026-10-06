package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spenditos.android.feature.recurring.IncomeAndBillsScreen
import com.spenditos.android.ui.theme.Spacing

/**
 * Plan tab (iOS PlanHubView): "Income & bills" and "Month plan". Both screens stay composed per
 * selection only; the Month plan keeps its draft on the server, so switching loses nothing.
 */
@Composable
fun PlanHubScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Income & bills", "Month plan")
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.m, vertical = Spacing.s),
        ) {
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = { selected = index },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                    modifier = Modifier.testTag("plan.segment.$index"),
                ) { Text(label) }
            }
        }
        when (selected) {
            0 -> IncomeAndBillsScreen()
            else -> MonthPlanScreen()
        }
    }
}
