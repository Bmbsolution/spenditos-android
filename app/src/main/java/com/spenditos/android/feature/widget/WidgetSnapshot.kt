package com.spenditos.android.feature.widget

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import com.spenditos.android.ui.components.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** iOS `WidgetCategory`. */
data class WidgetCategory(val name: String, val amount: Double, val colorHex: String?)

/** Home's current-month figures reduced to the values shown in the widget. */
data class WidgetSnapshot(
    val state: CashFlowStatus.State?,
    val leftAfterBills: BigDecimal?,
    val daysLeft: Int?,
    val currency: String,
    val userId: String?,
    /** iOS widget values (DashboardViewModel.updateWidgetData): month totals like iOS's report summary (scheduled entries included). */
    val spentToday: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpenses: Double = 0.0,
    /** Expenses per day, oldest first, the last 7 days through today. */
    val last7Days: List<Double> = emptyList(),
    /** Top 3 expense categories this month. */
    val topCategories: List<WidgetCategory> = emptyList(),
    val streakDays: Int = 0,
    val daysInMonth: Int = 0,
) {
    /** iOS "storm mode": more went out than came in this month. */
    val isStormMode: Boolean get() = monthExpenses > monthIncome
    val net: Double get() = monthIncome - monthExpenses

    /** iOS `dailyBudget`: this month's income spread over its days. */
    val dailyPace: Double get() = if (daysInMonth > 0) monthIncome / daysInMonth else 0.0

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
            /** Transactions of the last 7 days (may start in the previous month). */
            recent: List<Transaction> = emptyList(),
            streakDays: Int = 0,
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
            val incomeIds = ownCategories.filter { it.isIncome }.map { it.id }.toSet()
            val expenses = ownTransactions.filter { it.categoryId !in incomeIds }
            val byName = ownCategories.associateBy { it.id }
            val ownRecent = (recent.ifEmpty { ownTransactions }).filter { it.userId == userId && it.categoryId !in incomeIds }
            return WidgetSnapshot(
                state = status.state,
                leftAfterBills = status.leftAfterBills,
                daysLeft = status.daysRemaining,
                currency = currency,
                userId = userId,
                spentToday = expenses.filter { it.date == today.toString() }.sumOf { it.amount },
                monthIncome = ownTransactions.filter { it.categoryId in incomeIds }.sumOf { it.amount },
                monthExpenses = expenses.sumOf { it.amount },
                last7Days = (6 downTo 0).map { back ->
                    val day = today.minusDays(back.toLong()).toString()
                    ownRecent.filter { it.date == day }.sumOf { it.amount }
                },
                topCategories = expenses.groupBy { it.categoryId }
                    .map { (id, rows) -> WidgetCategory(byName[id]?.name ?: "Other", rows.sumOf { it.amount }, byName[id]?.color) }
                    .sortedByDescending { it.amount }
                    .take(3),
                streakDays = streakDays,
                daysInMonth = today.lengthOfMonth(),
            )
        }

        fun placeholder(currency: String = "USD", userId: String? = null) =
            WidgetSnapshot(null, null, null, currency, userId)
    }
}
