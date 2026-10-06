package com.spenditos.android.feature.home

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashFlowBreakdownTest {
    @Test
    fun `breakdown groups add back to the cash flow card totals`() {
        val categories = listOf(
            Category("income", "fake-user", "Income", "#22AA55", isIncome = true),
            Category("bills", "fake-user", "Housing", "#AA5522", isIncome = false),
            Category("food", "fake-user", "Food", "#EE8844", isIncome = false),
        )
        val transactions = listOf(
            tx("received", "income", "Income received", "2026-10-01", 2000.0),
            tx("expected", "income", "Income expected", "2026-10-20", 1000.0),
            tx("bill-paid", "bills", "Rent", "2026-10-01", 900.0, "rent"),
            tx("everyday", "food", "Lunch", "2026-10-01", 100.0),
            tx("bill-left", "bills", "Dentist", "2026-10-06", 120.0),
        )
        val today = LocalDate.parse("2026-10-01")
        val status = CashFlowStatus.make(transactions, categories, today)
        val breakdown = CashFlowBreakdown.make(transactions, categories, today)

        assertMoney(status.received, breakdown.received.sumOf { it.amount })
        assertMoney(status.expected, breakdown.expected.sumOf { it.amount })
        assertMoney(status.spent, breakdown.billsPaid.sumOf { it.amount } + breakdown.everyday.sumOf { it.amount })
        assertMoney(status.everyday, breakdown.everyday.sumOf { it.amount })
        assertMoney(status.billsLeft, breakdown.billsLeft.sumOf { it.amount })
        assertMoney(
            status.leftAfterBills,
            breakdown.received.sumOf { it.amount } + breakdown.expected.sumOf { it.amount } -
                breakdown.billsPaid.sumOf { it.amount } - breakdown.everyday.sumOf { it.amount } -
                breakdown.billsLeft.sumOf { it.amount },
        )
        assertEquals("Dentist", breakdown.billsLeft.first().name)
        assertTrue(breakdown.everyday.isEmpty().not())
    }

    private fun tx(id: String, categoryId: String, description: String, date: String, amount: Double, templateId: String? = null) =
        Transaction(id, "fake-user", categoryId, templateId, description, amount, date)

    private fun assertMoney(expected: BigDecimal, actual: BigDecimal) {
        assertTrue("Expected $expected, was $actual", expected.compareTo(actual) == 0)
    }
}
