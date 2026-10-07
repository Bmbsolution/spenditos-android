package com.spenditos.android.feature.widget

import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.feature.home.CashFlowStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    @Test
    fun `maps cash flow states to short widget labels`() {
        val labels = mapOf(
            CashFlowStatus.State.ON_TRACK to "On track",
            CashFlowStatus.State.WATCH to "Watch your pace",
            CashFlowStatus.State.TIGHT to "Getting tight",
            CashFlowStatus.State.OVER to "Over",
            CashFlowStatus.State.SETUP to "Income needed",
        )

        labels.forEach { (state, label) ->
            val snapshot = WidgetSnapshot(state, BigDecimal("12.50"), 3, "USD", "user-1")
            assertEquals(label, snapshot.stateLabel)
            assertFalse(snapshot.isPlaceholder)
        }
    }

    @Test
    fun `maps Home status left amount days left and currency formatting`() {
        val snapshot = WidgetSnapshot.from(
            transactions = listOf(
                transaction("income", "income", "2026-10-01", 1000.0),
            ),
            categories = listOf(category("income", "user-1", isIncome = true)),
            today = LocalDate.parse("2026-10-10"),
            currency = "USD",
            userId = "user-1",
        )

        assertEquals(CashFlowStatus.State.ON_TRACK, snapshot.state)
        assertEquals(BigDecimal("1000.00"), snapshot.leftAfterBills)
        assertEquals(22, snapshot.daysLeft)
        assertEquals("$1,000.00", snapshot.formattedLeftAfterBills(Locale.US))
    }

    @Test
    fun `signed out and no cached transactions use the open app placeholder`() {
        val signedOut = WidgetSnapshot.from(
            transactions = listOf(transaction("income", "income", "2026-10-01", 1000.0)),
            categories = listOf(category("income", "user-1", isIncome = true)),
            today = LocalDate.parse("2026-10-10"),
            currency = "CAD",
            userId = null,
        )
        val noData = WidgetSnapshot.from(
            transactions = emptyList(),
            categories = listOf(category("income", "user-1", isIncome = true)),
            today = LocalDate.parse("2026-10-10"),
            currency = "CAD",
            userId = "user-1",
        )

        assertTrue(signedOut.isPlaceholder)
        assertNull(signedOut.stateLabel)
        assertEquals("CAD", noData.currency)
        assertTrue(noData.isPlaceholder)
        assertNull(noData.formattedLeftAfterBills(Locale.US))
    }

    @Test
    fun `another user's cached transactions and categories are ignored`() {
        val snapshot = WidgetSnapshot.from(
            transactions = listOf(transaction("their-income", "their-income", "2026-10-01", 100000.0, "user-2")),
            categories = listOf(category("their-income", "user-2", isIncome = true)),
            today = LocalDate.parse("2026-10-10"),
            currency = "USD",
            userId = "user-1",
        )

        assertEquals("user-1", snapshot.userId)
        assertTrue(snapshot.isPlaceholder)
        assertNull(snapshot.leftAfterBills)
    }

    @Test
    fun `iOS widget values - today, month totals booked so far, 7 days and top categories`() {
        val today = LocalDate.parse("2026-10-07")
        val month = listOf(
            transaction("pay", "income", "2026-10-01", 3100.0),
            transaction("rent", "rent", "2026-10-01", 1200.0),
            transaction("food1", "food", "2026-10-06", 30.0),
            transaction("food2", "food", "2026-10-07", 12.5),
            transaction("gas", "car", "2026-10-07", 40.0),
            transaction("future", "rent", "2026-10-20", 999.0), // scheduled, not booked yet
        )
        val snapshot = WidgetSnapshot.from(
            transactions = month,
            categories = listOf(category("income", "user-1", isIncome = true), category("rent", "user-1"), category("food", "user-1"), category("car", "user-1")),
            today = today,
            currency = "USD",
            userId = "user-1",
            recent = month.filter { it.date <= "2026-10-07" } + transaction("sept", "food", "2026-10-01", 0.0) + transaction("lastmonth", "food", "2026-09-30", 7.0),
            streakDays = 4,
        )
        assertEquals(52.5, snapshot.spentToday, 0.001)
        assertEquals(3100.0, snapshot.monthIncome, 0.001)
        assertEquals(1282.5, snapshot.monthExpenses, 0.001)
        assertFalse(snapshot.isStormMode)
        assertEquals(100.0, snapshot.dailyPace, 0.001)
        assertEquals(listOf(1200.0, 0.0, 0.0, 0.0, 0.0, 30.0, 52.5), snapshot.last7Days) // Oct 1..7; Sep 30 is outside
        assertEquals(listOf("rent", "food", "car"), snapshot.topCategories.map { it.name })
        assertEquals(4, snapshot.streakDays)
    }

    private fun category(id: String, userId: String, isIncome: Boolean = false) =
        Category(id, userId, id, "#445566", isIncome = isIncome)

    private fun transaction(
        id: String,
        categoryId: String,
        date: String,
        amount: Double,
        userId: String = "user-1",
    ) = Transaction(id, userId, categoryId, null, id, amount, date)
}
