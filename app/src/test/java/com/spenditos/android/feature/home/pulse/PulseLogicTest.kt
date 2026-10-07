package com.spenditos.android.feature.home.pulse

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

class PulseLogicTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun status(
        received: String = "4000",
        expected: String = "4000",
        spent: String = "1000",
        everyday: String = "600",
        billsLeft: String = "500",
        elapsed: Int = 20,
        remaining: Int = 12,
    ) = CashFlowStatus(
        received = BigDecimal(received), expected = BigDecimal(expected), spent = BigDecimal(spent),
        everyday = BigDecimal(everyday), billsLeft = BigDecimal(billsLeft), billsLeftCount = 1,
        daysElapsed = elapsed, daysRemaining = remaining, today = today, monthEnd = LocalDate.of(2026, 10, 31),
    )

    private fun money(value: BigDecimal) = "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    @Test
    fun `weather follows the cash flow state`() {
        assertEquals(FinancialWeather.SUNNY, FinancialWeather.from(CashFlowStatus.State.ON_TRACK))
        assertEquals(FinancialWeather.CLOUDY, FinancialWeather.from(CashFlowStatus.State.WATCH))
        assertEquals(FinancialWeather.RAINY, FinancialWeather.from(CashFlowStatus.State.TIGHT))
        assertEquals(FinancialWeather.STORM, FinancialWeather.from(CashFlowStatus.State.OVER))
        assertEquals(FinancialWeather.SETUP, FinancialWeather.from(CashFlowStatus.State.SETUP))
        assertEquals("Overspending", FinancialWeather.STORM.shortTitle)
    }

    @Test
    fun `health score is the savings signal at full weight and null without income`() {
        // kept = 1 - (1000 + 500) / 8000 = 0.8125 -> (0.8125 * 0.8 + 0.2) * 100 = 85.0
        assertEquals(85, HealthScore.score(status()))
        assertNull(HealthScore.score(status(received = "0", expected = "0")))
        // Overspent: kept clamps to 0 -> 20
        assertEquals(20, HealthScore.score(status(received = "1000", expected = "0", spent = "900", billsLeft = "400")))
    }

    @Test
    fun `no income gives the starter breakdown`() {
        val breakdown = HealthScore.breakdown(status(received = "0", expected = "0"), emptyList())
        assertEquals(10, breakdown.score)
        assertEquals(SpendingTrend.STABLE, breakdown.trend)
        assertEquals(2, breakdown.recommendations.size)
        assertEquals(10, HealthScore.breakdown(null, emptyList()).score)
    }

    @Test
    fun `trend compares the second half of the week with the first`() {
        fun week(vararg values: Int) = values.map { BigDecimal(it) }
        assertEquals(SpendingTrend.INCREASING, HealthScore.trend(week(10, 10, 10, 0, 20, 20, 20))) // 10 vs 20 = 2.0
        assertEquals(SpendingTrend.DECREASING, HealthScore.trend(week(20, 20, 20, 0, 10, 10, 10))) // 0.5
        assertEquals(SpendingTrend.STABLE, HealthScore.trend(week(10, 10, 10, 0, 11, 11, 11)))     // 1.1
        assertEquals(SpendingTrend.INCREASING, HealthScore.trend(week(0, 0, 0, 0, 1, 1, 1)))        // from nothing
        assertEquals(SpendingTrend.STABLE, HealthScore.trend(week(0, 0, 0, 0, 0, 0, 0)))
        assertEquals(SpendingTrend.STABLE, HealthScore.trend(week(5)))
        // Boundaries: exactly 1.15 and 0.85 are stable.
        assertEquals(SpendingTrend.STABLE, HealthScore.trend(listOf(BigDecimal("100"), BigDecimal("115"))))
        assertEquals(SpendingTrend.STABLE, HealthScore.trend(listOf(BigDecimal("100"), BigDecimal("85"))))
    }

    @Test
    fun `recommendations name the weakest factor first and never repeat`() {
        val low = HealthScore.breakdown(
            status(received = "1000", expected = "0", spent = "900", billsLeft = "50"),
            List(7) { BigDecimal(10) },
        )
        assertTrue(low.savingsRate < 0.2)
        assertTrue(low.recommendations.first().startsWith("Your savings rate is low"))
        assertEquals(low.recommendations.size, low.recommendations.toSet().size)
        assertTrue(low.recommendations.size <= 3)
    }

    @Test
    fun `reason copy follows iOS for every state`() {
        val us = Locale.US
        assertTrue(PulseCopy.reason(status(received = "0", expected = "0", spent = "0"), us, ::money).startsWith("Add your income"))
        assertTrue(PulseCopy.reason(status(received = "0", expected = "0", spent = "50"), us, ::money).contains("You've spent \$50.00"))
        assertEquals(
            "Spending and bills due are \$100.00 more than this month's income.",
            PulseCopy.reason(status(received = "1000", expected = "0", spent = "900", billsLeft = "200"), us, ::money),
        )
        assertTrue(PulseCopy.reason(status(), us, ::money).startsWith("After bills, you can spend about"))
        assertTrue(PulseCopy.reason(status(), us, ::money).endsWith("until Oct 31."))
        // Early in the month a pace is not a pace: iOS shows the remaining amount instead.
        val early = status(received = "1000", expected = "0", spent = "100", billsLeft = "0", everyday = "900", elapsed = 3, remaining = 29)
        assertEquals(CashFlowStatus.State.TIGHT, early.state)
        assertTrue(PulseCopy.reason(early, us, ::money).contains("left after bills for 29 days"))
    }

    @Test
    fun `percent label never says zero while something is left`() {
        assertEquals("<1%", PulseCopy.percentLabel(BigDecimal("0.004")))
        assertEquals("0%", PulseCopy.percentLabel(BigDecimal.ZERO))
        assertEquals("21%", PulseCopy.percentLabel(BigDecimal("0.2087")))
        assertEquals("100%", PulseCopy.percentLabel(BigDecimal.ONE))
    }

    @Test
    fun `history covers seven days across a month boundary with the category income rule`() {
        val categories = listOf(
            Category(id = "inc", userId = "u", name = "Pay", color = "#000000", isIncome = true),
            Category(id = "food", userId = "u", name = "Food", color = "#000000", isIncome = false),
        )
        fun tx(id: String, category: String, date: String, amount: Double) =
            Transaction(id = id, userId = "u", categoryId = category, description = id, amount = amount, date = date)
        val firstOfMonth = LocalDate.of(2026, 10, 3)
        val history = CashFlowHistory.make(
            listOf(
                tx("a", "food", "2026-09-28", 10.0),
                tx("b", "food", "2026-10-03", 5.5),
                tx("c", "inc", "2026-10-03", 100.0),
                tx("old", "food", "2026-09-26", 99.0),
                tx("future", "food", "2026-10-04", 99.0),
            ),
            categories,
            firstOfMonth,
        )
        assertEquals(7, history.size)
        assertEquals(LocalDate.of(2026, 9, 27), history.first().date)
        assertEquals(firstOfMonth, history.last().date)
        assertEquals(BigDecimal("10.00"), history.first { it.date == LocalDate.of(2026, 9, 28) }.expenses)
        assertEquals(BigDecimal("94.50"), history.last().net)
        assertEquals(BigDecimal.ZERO, history.first().expenses)
    }

    @Test
    fun `sparkline points are ordered left to right and normalized`() {
        val points = CashFlowHistory.sparkline(listOf(BigDecimal(5), BigDecimal(-5), BigDecimal(0), BigDecimal(10)))
        assertEquals(listOf(0f, 1f / 3, 2f / 3, 1f), points.map { it.first })
        assertTrue(points.all { it.second in 0f..1f })
        assertEquals(1f, points[1].second, 0.0001f) // the lowest net sits at the bottom
        assertEquals(0f, points[3].second, 0.0001f) // the highest at the top
        assertTrue(CashFlowHistory.sparkline(emptyList()).isEmpty())
    }
}
