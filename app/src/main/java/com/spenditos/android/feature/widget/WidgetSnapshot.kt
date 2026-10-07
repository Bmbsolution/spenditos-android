package com.spenditos.android.feature.widget

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.ui.components.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** Home's current-month figures reduced to the values shown in the widget. */
data class WidgetSnapshot(
    val state: CashFlowStatus.State?,
    val leftAfterBills: BigDecimal?,
    val daysLeft: Int?,
    val currency: String,
    val userId: String?,
) {
    val isPlaceholder: Boolean
        get() = userId == null || state == null || leftAfterBills == null || daysLeft == null

    val stateLabel: String?
        get() = when (state) {
            CashFlowStatus.State.ON_TRACK -> "On track"
            CashFlowStatus.State.WATCH -> "Watch your pace"
            CashFlowStatus.State.TIGHT -> "Getting tight"
            CashFlowStatus.State.OVER -> "Over"
            CashFlowStatus.State.SETUP -> "Income needed"
            null -> null
        }

    fun formattedLeftAfterBills(locale: Locale = Locale.getDefault()): String? =
        leftAfterBills?.let { Money.format(it.toDouble(), currency, locale) }

    companion object {
        /** Purely maps the same signed-in user's cached Home data into widget values. */
        fun from(
            transactions: List<Transaction>,
            categories: List<Category>,
            today: LocalDate,
            currency: String,
            userId: String?,
        ): WidgetSnapshot {
            if (userId == null) return placeholder(currency)

            val monthStart = today.withDayOfMonth(1)
            val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
            val ownTransactions = transactions.filter { transaction ->
                transaction.userId == userId &&
                    LocalDate.parse(transaction.date).let { it >= monthStart && it <= monthEnd }
            }
            if (ownTransactions.isEmpty()) return placeholder(currency, userId)

            val ownCategories = categories.filter { it.userId == userId }
            val status = CashFlowStatus.make(ownTransactions, ownCategories, today)
            return WidgetSnapshot(
                state = status.state,
                leftAfterBills = status.leftAfterBills,
                daysLeft = status.daysRemaining,
                currency = currency,
                userId = userId,
            )
        }

        fun placeholder(currency: String = "USD", userId: String? = null) =
            WidgetSnapshot(null, null, null, currency, userId)
    }
}
