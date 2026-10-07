package com.spenditos.android.feature.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spenditos.android.feature.recurring.IncomeAndBillsScreen
import com.spenditos.android.ui.components.IosScreen
import com.spenditos.android.ui.components.IosSegmented
import com.spenditos.android.ui.components.LargeTitle
import com.spenditos.android.ui.theme.Spacing

/**
 * Plan tab (iOS PlanHubView): one large title and a shared segmented control sit above either
 * section, matching the hierarchy of the native iOS tab.
 */
@Composable
fun PlanHubScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Income & bills", "Month plan")

    IosScreen {
        Column(Modifier.fillMaxSize()) {
            LargeTitle(labels[selected])
            IosSegmented(
                options = labels,
                selectedIndex = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(bottom = Spacing.s),
                tagPrefix = "plan.segment",
            )
            Box(Modifier.weight(1f)) {
                when (selected) {
                    0 -> IncomeAndBillsScreen()
                    else -> MonthPlanScreen()
                }
            }
        }
    }
}
