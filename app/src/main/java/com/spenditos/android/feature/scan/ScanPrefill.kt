package com.spenditos.android.feature.scan

import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.feature.receipt.toPrefill
import com.spenditos.android.feature.transactions.AddTransactionPrefill

/** The single-receipt prefill of a scan item (iOS `AnalyzedReceipt(from: item)`). */
fun ScanItem.toPrefill(): AddTransactionPrefill = ReceiptResult(
    amount = amount,
    description = description,
    date = date,
    merchantName = merchantName,
    currency = currency,
    categoryId = categoryId,
    categoryHint = categoryHint,
).toPrefill()
