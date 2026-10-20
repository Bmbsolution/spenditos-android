package com.spenditos.android.feature.debts

import androidx.compose.runtime.Composable
import com.spenditos.android.feature.debts.add.AddDebtSheet

/** Entry point used by Income & bills to open the "Add a debt" sheet. */
@Composable
fun DebtAddSheetHost(onDismiss: () -> Unit, onCreated: (debtId: String) -> Unit) {
    AddDebtSheet(onDismiss = onDismiss, onCreated = onCreated)
}
