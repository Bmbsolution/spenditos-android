package com.spenditos.android.feature.home.pulse

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import com.spenditos.android.feature.home.CashFlowStatus
import java.math.BigDecimal
import java.math.MathContext

enum class SpendingTrend { INCREASING, STABLE, DECREASING }

data class HealthScoreBreakdown(
    val score: Int,
    val savingsRate: Double,
    val trend: SpendingTrend,
    val recommendations: List<String>,
)

/**
 * Client-side health score, ported from iOS (`pulseHealthScore`, `healthScoreBreakdown`,
 * `computeSpendingTrend`). Budgets are gone (iOS #336): the savings signal carries full weight.
 */
object HealthScore {
    private val NO_INCOME = HealthScoreBreakdown(
        score = 10,
        savingsRate = 0.0,
        trend = SpendingTrend.STABLE,
        recommendations = listOf(
            Strings.get(R.string.home_start_logging_income_and_expenses),
            Strings.get(R.string.home_add_your_first_transaction_to_2),
        ),
    )

    /** Share of this month's income not spent and not owed on bills, 0...1; null without income. */
    fun savingsRate(status: CashFlowStatus): Double? {
        val income = status.income
        if (income.signum() <= 0) return null
        val owed = (status.spent + status.billsLeft).divide(income, MathContext.DECIMAL64)
        return (BigDecimal.ONE - owed).max(BigDecimal.ZERO).toDouble()
    }

    /** The badge number on Home; null without income. */
    fun score(status: CashFlowStatus): Int? = savingsRate(status)?.let(::scoreFor)

    private fun scoreFor(savingsRate: Double): Int = ((savingsRate * 0.8 + 0.2) * 100).toInt().coerceIn(0, 100)

    /** [weekExpenses] is the daily spending of the last seven days, oldest first. */
    fun breakdown(status: CashFlowStatus?, weekExpenses: List<BigDecimal>): HealthScoreBreakdown {
        val rate = status?.let(::savingsRate) ?: return NO_INCOME
        val trend = trend(weekExpenses)
        val factors = listOf(
            Strings.get(R.string.home_savings_rate) to rate,
            Strings.get(R.string.common_spending_trend) to when (trend) {
                SpendingTrend.DECREASING -> 1.0
                SpendingTrend.STABLE -> 0.5
                SpendingTrend.INCREASING -> 0.0
            },
        ).sortedBy { it.second } // stable: the weakest factor first, like iOS

        val tips = mutableListOf<String>()
        for ((name, _) in factors.take(3)) {
            val tip = when (name) {
                "Savings Rate" -> when {
                    rate < 0.2 -> Strings.get(R.string.home_your_savings_rate_is_low)
                    rate < 0.4 -> Strings.get(R.string.home_increase_your_savings_rate_by)
                    else -> Strings.get(R.string.home_great_savings_rate_consider_automating)
                }
                else -> when (trend) {
                    SpendingTrend.INCREASING -> Strings.get(R.string.home_spending_is_trending_up_audit)
                    SpendingTrend.STABLE -> Strings.get(R.string.home_spending_is_flat_look_for)
                    SpendingTrend.DECREASING -> Strings.get(R.string.home_spending_is_declining_nice_work)
                }
            }
            if (tip !in tips) tips += tip
        }
        return HealthScoreBreakdown(scoreFor(rate), rate, trend, tips.take(3))
    }

    /** Second half of the week against the first half (iOS: >15% up, <15% down). */
    fun trend(expenses: List<BigDecimal>): SpendingTrend {
        if (expenses.size < 2) return SpendingTrend.STABLE
        val mid = expenses.size / 2
        val first = expenses.take(mid)
        val second = expenses.takeLast(mid)
        val firstAvg = first.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(maxOf(first.size, 1)), MathContext.DECIMAL64)
        val secondAvg = second.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(maxOf(second.size, 1)), MathContext.DECIMAL64)
        if (firstAvg.signum() <= 0) return if (secondAvg.signum() > 0) SpendingTrend.INCREASING else SpendingTrend.STABLE
        val ratio = secondAvg.divide(firstAvg, MathContext.DECIMAL64)
        return when {
            ratio > BigDecimal("1.15") -> SpendingTrend.INCREASING
            ratio < BigDecimal("0.85") -> SpendingTrend.DECREASING
            else -> SpendingTrend.STABLE
        }
    }
}
