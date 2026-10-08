package com.spenditos.android.feature.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterAmountDescriptionTest {
    @Test
    fun `amount fields announce their bound and currency`() {
        assertEquals("Minimum amount in $", amountFieldDescription("Min", "$"))
        assertEquals("Maximum amount in CA$", amountFieldDescription("Max", "CA$"))
        assertEquals("Minimum amount in €", amountFieldDescription("min", "€"))
    }
}
