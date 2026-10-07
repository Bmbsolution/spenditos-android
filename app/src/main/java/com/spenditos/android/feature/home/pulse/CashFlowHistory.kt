package com.spenditos.android.feature.home.pulse

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import java.math.BigDecimal
import java.time.LocalDate

/** One day of money in and out (the iOS `DailyAggregate` of the 7-day sparkline). */
data class DayFlow(val date: LocalDate, val income: BigDecimal, val expenses: BigDecimal) {
    val net: BigDecimal get() = income - expenses
}

object CashFlowHistory {
    const val DAYS = 7

    /** Last seven days up to [today], built from the local cache with the same income rule as the status. */
    fun make(transactions: List<Transaction>, categories: List<Category>, today: LocalDate): List<DayFlow> {
        val incomeByCategory = categories.associate { it.id to it.isIncome }
        val start = today.minusDays((DAYS - 1).toLong())
        val income = HashMap<LocalDate, BigDecimal>()
        val expenses = HashMap<LocalDate, BigDecimal>()
        for (transaction in transactions) {
            val date = LocalDate.parse(transaction.date)
            if (date < start || date > today) continue
            val amount = CashFlowStatus.cashAmount(transaction.amount)
            val target = if (incomeByCategory[transaction.categoryId] == true) income else expenses
            target[date] = (target[date] ?: BigDecimal.ZERO) + amount
        }
        return (0 until DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayFlow(date, income[date] ?: BigDecimal.ZERO, expenses[date] ?: BigDecimal.ZERO)
        }
    }

    /** Sparkline points in 0..1 (x left to right, y 0 = top = highest net), ported from iOS. */
    fun sparkline(nets: List<BigDecimal>): List<Pair<Float, Float>> {
        if (nets.isEmpty()) return emptyList()
        val min = nets.minOrNull() ?: BigDecimal.ZERO
        val max = nets.maxOrNull() ?: BigDecimal.ONE
        val range = (max - min).max(BigDecimal.ONE)
        return nets.mapIndexed { index, value ->
            val x = index.toFloat() / maxOf(nets.size - 1, 1)
            val y = 1f - ((value - min).toFloat() / range.toFloat())
            x to y
        }
    }
}
