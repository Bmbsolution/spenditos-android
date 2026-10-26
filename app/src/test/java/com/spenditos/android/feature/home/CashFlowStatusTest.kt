package com.spenditos.android.feature.home

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CashFlowStatusTest {
    private val categories = listOf(
        category("paycheck", "Income", isIncome = true),
        category("housing", "Housing"),
        category("car", "Car"),
        category("phone", "Phone"),
        category("childcare", "Childcare"),
        category("debt", "Credit card"),
        category("food", "Food"),
        category("fitness", "Fitness"),
        category("health", "Health"),
    )

    @Test
    fun `seed home values match the October parity target`() {
        val seed = listOf(
            tx("rent", "housing", "Rent", "2026-10-01", 1450.0, "rent-template"),
            tx("car-loan", "car", "Car loan", "2026-10-01", 380.0, "car-template"),
            tx("phone", "phone", "Phone", "2026-10-01", 85.0, "phone-template"),
            tx("insurance", "car", "Car insurance", "2026-10-01", 1082.0),
            tx("coffee", "food", "Coffee", "2026-10-01", 4.5),
            tx("dentist", "health", "Dentist", "2026-10-06", 120.0),
            tx("daycare", "childcare", "Daycare", "2026-10-10", 1200.0, "daycare-template"),
            tx("pay-oct-15", "paycheck", "Paycheck", "2026-10-15", 4440.0, "pay-template"),
            tx("card", "debt", "Credit card", "2026-10-20", 2600.0, "card-template"),
            tx("internet", "phone", "Internet", "2026-10-22", 75.0, "internet-template"),
            tx("gym", "fitness", "Gym", "2026-10-28", 45.0, "gym-template"),
            tx("pay-oct-31", "paycheck", "Paycheck", "2026-10-31", 4440.0, "pay-template"),
        )

        val today = LocalDate.parse("2026-10-01")
        val status = CashFlowStatus.make(seed, categories, today)
        val breakdown = CashFlowBreakdown.make(seed, categories, today)

        assertMoney("0.00", status.received)
        assertMoney("8880.00", status.expected)
        assertMoney("3001.50", status.spent)
        assertMoney("1915.00", status.spent - status.everyday)
        assertMoney("1086.50", status.everyday)
        assertMoney("4040.00", status.billsLeft)
        assertEquals(5, status.billsLeftCount)
        assertMoney("1838.50", status.leftAfterBills)
        assertEquals(21, status.incomeLeftShare.multiply(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toInt())
        assertMoney("59.31", status.affordablePace.setScale(2, java.math.RoundingMode.HALF_UP))
        assertEquals(CashFlowStatus.State.TIGHT, status.state)
        assertEquals("Dentist", breakdown.billsLeft.first().name)
        assertEquals(LocalDate.parse("2026-10-06"), breakdown.billsLeft.first().date)
        assertMoney("120.00", breakdown.billsLeft.first().amount)
    }

    @Test
    fun `splits received expected spent and bills left by date`() {
        val status = CashFlowStatus.make(
            listOf(
                tx("received", "paycheck", "Income", "2026-10-03", 3200.0, "pay-template"),
                tx("expected", "paycheck", "Income", "2026-10-17", 3200.0, "pay-template"),
                tx("paid-bill", "housing", "Rent", "2026-10-01", 1450.0, "rent-template"),
                tx("everyday", "food", "Lunch", "2026-10-05", 120.0),
                tx("future-bill", "housing", "Future bill", "2026-10-20", 65.0, "bill-template"),
                tx("outside-month", "food", "Old entry", "2026-09-30", 40.0),
            ),
            categories,
            LocalDate.parse("2026-10-10"),
        )

        assertMoney("3200.00", status.received)
        assertMoney("3200.00", status.expected)
        assertMoney("1570.00", status.spent)
        assertMoney("120.00", status.everyday)
        assertMoney("65.00", status.billsLeft)
        assertEquals(1, status.billsLeftCount)
        assertMoney("4765.00", status.leftAfterBills)
        assertEquals(10, status.daysElapsed)
        assertEquals(22, status.daysRemaining)
        assertEquals(CashFlowStatus.State.ON_TRACK, status.state)
    }

    @Test
    fun `setup over watch tight and on track follow the same boundaries as iOS`() {
        val today = LocalDate.parse("2026-10-10")
        val cases = listOf(
            Case("no income", emptyList(), CashFlowStatus.State.SETUP),
            Case("expenses without income remain setup", listOf(
                tx("spent", "food", "Spent", "2026-10-01", 300.0),
            ), CashFlowStatus.State.SETUP),
            Case("spending and bills exceed income", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 1000.0),
                tx("spent", "food", "Spent", "2026-10-01", 950.0),
                tx("bill", "housing", "Future bill", "2026-10-20", 100.0, "rent"),
            ), CashFlowStatus.State.OVER),
            Case("on track below the watch boundary", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 2000.0),
                tx("spent", "food", "Spent", "2026-10-05", 100.0),
            ), CashFlowStatus.State.ON_TRACK),
            Case("exactly eighty percent stays on track", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 1500.0),
                tx("spent", "food", "Spent", "2026-10-05", 400.0),
            ), CashFlowStatus.State.ON_TRACK),
            Case("above eighty percent is watch", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 1500.0),
                tx("spent", "food", "Spent", "2026-10-05", 400.01),
            ), CashFlowStatus.State.WATCH),
            Case("pace equal to affordable is watch", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 1600.0),
                tx("spent", "food", "Spent", "2026-10-05", 500.0),
            ), CashFlowStatus.State.WATCH),
            Case("pace above affordable is tight", listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 1600.0),
                tx("spent", "food", "Spent", "2026-10-05", 500.01),
            ), CashFlowStatus.State.TIGHT),
        )

        cases.forEach { case ->
            assertEquals(case.name, case.expected, CashFlowStatus.make(case.transactions, categories, today).state)
        }
    }

    @Test
    fun `today on the final day counts today and February uses leap day`() {
        val october = CashFlowStatus.make(
            listOf(tx("income", "paycheck", "Income", "2026-10-31", 1000.0)),
            categories,
            LocalDate.parse("2026-10-31"),
        )
        assertEquals(31, october.daysElapsed)
        assertEquals(1, october.daysRemaining)
        assertEquals(LocalDate.parse("2026-10-31"), october.monthEnd)

        val february = CashFlowStatus.make(
            listOf(
                tx("feb-income", "paycheck", "Income", "2024-02-29", 1000.0),
                tx("march", "food", "March", "2024-03-01", 700.0),
                tx("january", "food", "January", "2024-01-31", 500.0),
            ),
            categories,
            LocalDate.parse("2024-02-29"),
        )
        assertEquals(29, february.daysElapsed)
        assertEquals(1, february.daysRemaining)
        assertEquals(LocalDate.parse("2024-02-29"), february.monthEnd)
        assertMoney("1000.00", february.received)
        assertMoney("0.00", february.spent)
    }

    @Test
    fun `run out date uses the current everyday pace and stays within month`() {
        val status = CashFlowStatus.make(
            listOf(
                tx("income", "paycheck", "Income", "2026-10-01", 2000.0),
                tx("spent", "food", "Spent", "2026-10-05", 700.0),
            ),
            categories,
            LocalDate.parse("2026-10-10"),
        )
        assertEquals(LocalDate.parse("2026-10-28"), status.runOutDate())
        assertNull(CashFlowStatus.make(emptyList(), categories, LocalDate.parse("2026-10-10")).runOutDate())
    }

    // region carriedIn parity (packages/core/test/insights/month-balance.test.ts "makeCashFlowStatus carriedIn")

    private val carryMonth = listOf(
        tx("pay", "paycheck", "Income", "2026-10-01", 3000.0),
        tx("lunch", "food", "Lunch", "2026-10-05", 500.0),
    )
    private val carryToday = LocalDate.parse("2026-10-10")

    @Test
    fun `carriedIn defaults to zero and leaves the status as it was`() {
        val base = CashFlowStatus.make(carryMonth, categories, carryToday)
        assertMoney("0.00", base.carriedIn)
        assertMoney("2500.00", base.leftAfterBills)
    }

    @Test
    fun `carriedIn changes left after bills by exactly that amount and not income`() {
        val base = CashFlowStatus.make(carryMonth, categories, carryToday)
        val carried = CashFlowStatus.make(carryMonth, categories, carryToday, carriedIn = CashFlowStatus.cashAmount(150.0))
        assertMoney("150.00", carried.carriedIn)
        assertMoney("2650.00", carried.leftAfterBills)
        assertEquals(base.leftAfterBills + BigDecimal("150"), carried.leftAfterBills)
        assertMoney(base.income.toPlainString(), carried.income)
        assertMoney(base.spent.toPlainString(), carried.spent)
        assertMoney(base.billsLeft.toPlainString(), carried.billsLeft)
    }

    @Test
    fun `carriedIn does not turn a month with no income into a set up month`() {
        val none = CashFlowStatus.make(emptyList(), categories, carryToday, carriedIn = CashFlowStatus.cashAmount(150.0))
        assertEquals(CashFlowStatus.State.SETUP, none.state)
    }

    @Test
    fun `carriedIn keeps cents exact`() {
        val carried = CashFlowStatus.make(carryMonth, categories, carryToday, carriedIn = CashFlowStatus.cashAmount(150.1 + 0.2))
        assertMoney("150.30", carried.carriedIn)
        assertMoney("2650.30", carried.leftAfterBills)
    }

    // endregion

    private fun category(id: String, name: String, isIncome: Boolean = false) =
        Category(id, "fake-user", name, "#445566", isIncome = isIncome)

    private fun tx(
        id: String,
        categoryId: String,
        description: String,
        date: String,
        amount: Double,
        templateId: String? = null,
    ) = Transaction(id, "fake-user", categoryId, templateId, description, amount, date)

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue("Expected $expected, was $actual", actual.compareTo(BigDecimal(expected)) == 0)
    }

    private data class Case(val name: String, val transactions: List<Transaction>, val expected: CashFlowStatus.State)
}
