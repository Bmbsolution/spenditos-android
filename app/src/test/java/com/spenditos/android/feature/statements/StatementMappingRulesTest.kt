package com.spenditos.android.feature.statements

import com.spenditos.android.data.model.StatementColumnMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementMappingRulesTest {
    @Test
    fun `processable table matches the iOS cases`() {
        val cases = listOf(
            "date description amount" to (StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc", amountColumn = "Amt") to true),
            "date description debit and credit" to (StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc", debitColumn = "Out", creditColumn = "In") to true),
            "missing amount pair" to (StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc") to false),
            "missing description" to (StatementColumnMapping(dateColumn = "Date", amountColumn = "Amt") to false),
            "missing date" to (StatementColumnMapping(descriptionColumn = "Desc", amountColumn = "Amt") to false),
            "debit without credit" to (StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc", debitColumn = "Out") to false),
        )
        cases.forEach { (label, case) -> assertEquals(label, case.second, case.first.isProcessable) }
    }

    @Test
    fun `amount choice clears debit and credit`() {
        val mapping = StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc", debitColumn = "Out", creditColumn = "In")
        val updated = changeStatementMapping(mapping, StatementColumn.AMOUNT, "Amount")
        assertEquals("Amount", updated.amountColumn)
        assertEquals(null, updated.debitColumn)
        assertEquals(null, updated.creditColumn)
        assertTrue(updated.isProcessable)
    }

    @Test
    fun `debit and credit choices clear amount and both are required`() {
        val amountMapping = StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Desc", amountColumn = "Amount")
        val debit = changeStatementMapping(amountMapping, StatementColumn.DEBIT, "Out")
        assertEquals(null, debit.amountColumn)
        assertEquals("Out", debit.debitColumn)
        assertFalse(debit.isProcessable)
        val pair = changeStatementMapping(debit, StatementColumn.CREDIT, "In")
        assertEquals("In", pair.creditColumn)
        assertTrue(pair.isProcessable)
    }
}
