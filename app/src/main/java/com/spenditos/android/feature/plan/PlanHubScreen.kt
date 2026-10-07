package com.spenditos.android.feature.plan

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import com.spenditos.android.feature.recurring.IncomeAndBillsScreen
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
fun PlanHubScreen() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var addOneTimeRequest by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf("Income & bills", "Month plan")

    IosScreen {
        Column(Modifier.fillMaxSize()) {
            if (selected == 0) {
                LargeTitle(labels[selected])
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
                            contentDescription = "Add one-time item",
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
                    0 -> IncomeAndBillsScreen()
                    else -> MonthPlanScreen(
                        addOneTimeRequest = addOneTimeRequest,
                        onAddOneTimeRequestHandled = { handled ->
                            if (addOneTimeRequest == handled) addOneTimeRequest = 0
                        },
                        onOpenIncomeAndBills = { selected = 0 },
                    )
                }
            }
        }
    }
}
