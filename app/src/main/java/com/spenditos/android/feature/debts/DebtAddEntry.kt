package com.spenditos.android.feature.debts

import androidx.compose.runtime.Composable
import com.spenditos.android.feature.debts.add.AddDebtSheet
import com.spenditos.android.feature.debts.add.DebtPrefill

/** Entry point used by Income & bills to open the "Add a debt" sheet. */
@Composable
fun DebtAddSheetHost(
    onDismiss: () -> Unit,
    onCreated: (debtId: String) -> Unit,
    prefill: DebtPrefill? = null,
) {
    AddDebtSheet(onDismiss = onDismiss, onCreated = onCreated, prefill = prefill)
}
