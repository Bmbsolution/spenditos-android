package com.spenditos.android.feature.home.pulse

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
            "Start logging income and expenses to build your health score.",
            "Add your first transaction to activate tracking.",
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
            "Savings Rate" to rate,
            "Spending Trend" to when (trend) {
                SpendingTrend.DECREASING -> 1.0
                SpendingTrend.STABLE -> 0.5
                SpendingTrend.INCREASING -> 0.0
            },
        ).sortedBy { it.second } // stable: the weakest factor first, like iOS

        val tips = mutableListOf<String>()
        for ((name, _) in factors.take(3)) {
            val tip = when (name) {
                "Savings Rate" -> when {
                    rate < 0.2 -> "Your savings rate is low. Try the 50/30/20 rule: 50% needs, 30% wants, 20% savings."
                    rate < 0.4 -> "Increase your savings rate by reviewing subscriptions and discretionary spending."
                    else -> "Great savings rate. Consider automating transfers to a savings or investment account."
                }
                else -> when (trend) {
                    SpendingTrend.INCREASING -> "Spending is trending up. Audit your last 7 days for impulse purchases."
                    SpendingTrend.STABLE -> "Spending is flat. Look for one category to optimize this week."
                    SpendingTrend.DECREASING -> "Spending is declining — nice work. Lock in the habit with a weekly review."
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
