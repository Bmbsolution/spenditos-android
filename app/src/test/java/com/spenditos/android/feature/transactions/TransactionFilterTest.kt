package com.spenditos.android.feature.transactions

import com.spenditos.android.core.db.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TransactionFilterTest {
    private val today = LocalDate.of(2026, 10, 7)

    private fun row(
        id: String,
        title: String = id,
        categoryId: String = "food",
        categoryName: String = "Food",
        amount: Double = 10.0,
        isIncome: Boolean = false,
    ) = TransactionRowUi(
        id = id,
        title = title,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = null,
        amount = amount,
        isIncome = isIncome,
        date = today,
        syncStatus = SyncStatus.SYNCED,
    )

    private val rows = listOf(
        row("coffee", title = "Latte", amount = 4.5),
        row("rent", title = "Rent", categoryId = "home", categoryName = "Home", amount = 1200.0),
        row("pay", title = "Paycheck", categoryId = "salary", categoryName = "Salary", amount = 3000.0, isIncome = true),
    )

    @Test
    fun hasActiveFilters_matchesIos() {
        assertFalse(hasActiveTransactionFilters(TransactionFilterState()))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(dateRange = DateRangeFilter.LAST_MONTH)))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(dateRange = DateRangeFilter.CUSTOM)))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(selectedCategoryIds = setOf("food"))))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(selectedType = EntryType.INCOME)))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(minAmount = 1.0)))
        assertTrue(hasActiveTransactionFilters(TransactionFilterState(maxAmount = 5.0)))
        assertFalse(
            hasActiveTransactionFilters(
                TransactionFilterState(dateRange = DateRangeFilter.THIS_MONTH, customStart = today, customEnd = today),
            ),
        )
    }

    @Test
    fun filter_searchCategoryTypeAndAmount() {
        assertEquals(listOf("coffee"), filterTransactionRows(rows, "lat", emptySet(), null, null, null).map { it.id })
        assertEquals(listOf("pay"), filterTransactionRows(rows, "sal", emptySet(), null, null, null).map { it.id })
        assertEquals(
            listOf("rent"),
            filterTransactionRows(rows, "", setOf("home"), null, null, null).map { it.id },
        )
        assertEquals(
            listOf("pay"),
            filterTransactionRows(rows, "", emptySet(), EntryType.INCOME, null, null).map { it.id },
        )
        assertEquals(
            listOf("coffee", "rent"),
            filterTransactionRows(rows, "", emptySet(), EntryType.EXPENSE, null, null).map { it.id },
        )
        assertEquals(
            listOf("rent", "pay"),
            filterTransactionRows(rows, "", emptySet(), null, 100.0, null).map { it.id },
        )
        assertEquals(
            listOf("pay"),
            filterTransactionRows(rows, "", emptySet(), null, 2000.0, null).map { it.id },
        )
        assertEquals(
            listOf("coffee"),
            filterTransactionRows(rows, "", emptySet(), null, null, 10.0).map { it.id },
        )
        assertEquals(
            emptyList<String>(),
            filterTransactionRows(rows, "latte", setOf("home"), EntryType.EXPENSE, 1.0, 10.0).map { it.id },
        )
        assertEquals(
            listOf("coffee"),
            filterTransactionRows(rows, "latte", setOf("food"), EntryType.EXPENSE, 1.0, 10.0).map { it.id },
        )
    }

    @Test
    fun pillLabels_followIosOrder() {
        val filters = TransactionFilterState(
            dateRange = DateRangeFilter.LAST_MONTH,
            selectedCategoryIds = setOf("food"),
            selectedType = EntryType.INCOME,
            minAmount = 10.0,
            maxAmount = 20.0,
        )
        assertEquals(
            listOf(
                "Date: Last Month",
                "Type: Income",
                "Category: Food",
                "Min: $10.00 · Max: $20.00",
            ),
            activeFilterPillLabels(filters, mapOf("food" to "Food"), "USD", Locale.US),
        )

        val many = filters.copy(
            dateRange = DateRangeFilter.CUSTOM,
            customStart = LocalDate.of(2026, 1, 2),
            customEnd = LocalDate.of(2026, 3, 4),
            selectedCategoryIds = setOf("a", "b"),
            selectedType = EntryType.EXPENSE,
            minAmount = null,
        )
        assertEquals(
            listOf(
                "Date: 1/2/26 - 3/4/26",
                "Type: Expense",
                "Categories: 2",
                "Max: $20.00",
            ),
            activeFilterPillLabels(many, emptyMap(), "USD", Locale.US),
        )
    }

    @Test
    fun sanitizeAmount_keepsOneDecimal() {
        assertEquals("12.50", sanitizeAmountInput("12.50"))
        assertEquals("12.5", sanitizeAmountInput("12a.5b"))
        assertEquals("12.34", sanitizeAmountInput("12.3.4"))
        assertEquals("", sanitizeAmountInput(""))
    }
}
