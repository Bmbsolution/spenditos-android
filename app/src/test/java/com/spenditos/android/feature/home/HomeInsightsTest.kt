package com.spenditos.android.feature.home

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.insights.HomeInsights
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeInsightsTest {
    private val categories = listOf(
        Category("salary", "fake-user", "Salary", "#2E9E6B", icon = "banknote.fill", isIncome = true),
        Category("food", "fake-user", "Food", "#E07A3F", icon = "fork.knife", isIncome = false),
        Category("home", "fake-user", "Housing", "#B85F42", icon = "house.fill", isIncome = false),
    )
    private val defaultToday = LocalDate.parse("2026-09-10")

    private val sample get() = listOf(
        entry("pay", 2_000.0, 1, "salary", "tpl-pay"),
        entry("rent", 1_000.0, 2, "home", "tpl-rent"),
        entry("lunch", 30.0, 9, "food"),
        entry("dinner", 50.0, 10, "food"),
        entry("pay-2", 2_000.0, 15, "salary", "tpl-pay"),
        entry("phone", 60.0, 18, "home", "tpl-phone"),
    )

    @Test
    fun `in and out add up to today then only what is scheduled`() {
        val insights = make(sample)
        val actual = insights.points.filterNot { it.isProjected }

        assertEquals(10, actual.size)
        assertMoney("2000.00", actual.last().moneyIn)
        assertMoney("1080.00", actual.last().moneyOut)
        assertMoney("4000.00", insights.monthIn)
        assertMoney("1140.00", insights.monthOut)
        assertMoney("2860.00", insights.monthEndGap)
        assertNull(insights.shortfall)
    }

    @Test
    fun `month end gap matches the status card`() {
        val transactions = sample
        val status = CashFlowStatus.make(transactions, categories, defaultToday)
        assertMoney(status.leftAfterBills, make(transactions).monthEndGap)
    }

    @Test
    fun `bills before the paycheck show a shortfall until it arrives`() {
        val insights = make(
            listOf(
                entry("insurance", 1_082.0, 1, "food"),
                entry("rent", 1_000.0, 5, "home", "tpl-rent"),
                entry("pay", 2_500.0, 15, "salary", "tpl-pay"),
            ),
            today = day(1),
        )

        val shortfall = insights.shortfall
        assertEquals(day(1), shortfall?.start)
        assertEquals(day(14), shortfall?.end)
        assertMoney("2082.00", shortfall!!.amount)
        assertMoney("418.00", insights.monthEndGap)
    }

    @Test
    fun `bills and paychecks are pinned on their line`() {
        val insights = make(sample)

        assertEquals(listOf("pay", "rent", "pay-2", "phone"), insights.pins.map { it.id })
        assertMoney("1000.00", insights.pins.first { it.id == "rent" }.level)
        assertMoney("4000.00", insights.pins.first { it.id == "pay-2" }.level)
        assertTrue(insights.pins.first { it.id == "rent" }.isPast)
        assertFalse(insights.pins.first { it.id == "phone" }.isPast)
        assertEquals("phone", insights.nextBill?.id)
    }

    @Test
    fun `week and top category count everyday spending only`() {
        val insights = make(sample)

        assertEquals(7, insights.week.size)
        assertTrue(insights.week.last().isToday)
        assertMoney("80.00", insights.weekTotal)
        assertEquals("Food", insights.topCategory?.name)
        assertMoney(BigDecimal.ONE, insights.topCategory!!.share)
    }

    @Test
    fun `no bills left and no spending`() {
        val insights = make(listOf(entry("pay", 2_000.0, 1, "salary", "tpl-pay")))

        assertNull(insights.nextBill)
        assertNull(insights.topCategory)
        assertMoney("0.00", insights.weekTotal)
    }

    @Test
    fun `upcoming hand entered payments count as next bill`() {
        val insights = make(sample + entry("dentist", 120.0, 12, "food"))

        assertEquals("dentist", insights.nextBill?.id)
        assertTrue(insights.pins.any { it.id == "dentist" })
        assertFalse(insights.pins.any { it.id == "lunch" || it.id == "dinner" })
    }

    @Test
    fun `river point series are monotone for both lines`() {
        val insights = make(sample + listOf(
            entry("bonus", 250.0, 23, "salary", "tpl-pay"),
            entry("market", 45.0, 22, "food"),
        ))

        listOf(false, true).forEach { projected ->
            val series = insights.points.filter { it.isProjected == projected }
            assertTrue(series.zipWithNext().all { (before, after) -> after.moneyIn >= before.moneyIn })
            assertTrue(series.zipWithNext().all { (before, after) -> after.moneyOut >= before.moneyOut })
        }
    }

    private fun make(transactions: List<Transaction>, today: LocalDate = defaultToday) =
        HomeInsights.make(transactions, categories, today)

    private fun entry(id: String, amount: Double, day: Int, category: String, template: String? = null) =
        Transaction(
            id = id,
            userId = "fake-user",
            categoryId = category,
            templateId = template,
            description = id,
            amount = amount,
            date = "2026-09-${day.toString().padStart(2, '0')}",
        )

    private fun day(day: Int) = LocalDate.parse("2026-09-${day.toString().padStart(2, '0')}")

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue("Expected $expected, was $actual", BigDecimal(expected).compareTo(actual) == 0)
    }

    private fun assertMoney(expected: BigDecimal, actual: BigDecimal) {
        assertTrue("Expected $expected, was $actual", expected.compareTo(actual) == 0)
    }
}
