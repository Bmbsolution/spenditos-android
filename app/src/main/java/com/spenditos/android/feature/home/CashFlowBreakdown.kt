package com.spenditos.android.feature.home

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate

/** The entries behind each `CashFlowStatus` total for the “See the math” sheet. */
data class CashFlowBreakdown(
    val received: List<Line>,
    val expected: List<Line>,
    val billsPaid: List<Line>,
    val everyday: List<Line>,
    val billsLeft: List<Line>,
) {
    data class Line(
        val id: String,
        val name: String,
        val category: String,
        val date: LocalDate,
        val amount: BigDecimal,
    )

    companion object {
        fun make(transactions: List<Transaction>, categories: List<Category>, today: LocalDate): CashFlowBreakdown {
            val categoryById = categories.associateBy(Category::id)
            val groups = CashFlowStatus.Bucket.entries.associateWith { mutableListOf<Line>() }
            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

            for (transaction in transactions) {
                val category = categoryById[transaction.categoryId]
                val bucket = CashFlowStatus.bucket(
                    transaction = transaction,
                    isIncome = category?.isIncome == true,
                    today = today,
                    monthStart = monthStart,
                    monthEnd = monthEnd,
                ) ?: continue
                val categoryName = category?.name ?: Strings.get(R.string.common_uncategorized)
                groups.getValue(bucket) += Line(
                    id = transaction.id,
                    name = if (transaction.description.isEmpty()) categoryName else transaction.description,
                    category = categoryName,
                    date = LocalDate.parse(transaction.date),
                    amount = CashFlowStatus.cashAmount(transaction.amount),
                )
            }

            fun sorted(bucket: CashFlowStatus.Bucket): List<Line> =
                groups.getValue(bucket).sortedWith(compareBy<Line> { it.date }.thenByDescending { it.amount })

            return CashFlowBreakdown(
                received = sorted(CashFlowStatus.Bucket.RECEIVED),
                expected = sorted(CashFlowStatus.Bucket.EXPECTED),
                billsPaid = sorted(CashFlowStatus.Bucket.BILL_PAID),
                everyday = sorted(CashFlowStatus.Bucket.EVERYDAY),
                billsLeft = sorted(CashFlowStatus.Bucket.BILL_LEFT),
            )
        }
    }
}
