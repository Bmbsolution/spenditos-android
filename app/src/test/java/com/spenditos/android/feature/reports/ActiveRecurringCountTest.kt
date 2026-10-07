package com.spenditos.android.feature.reports

import com.spenditos.android.data.model.RecurringTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveRecurringCountTest {
    private fun template(id: String, type: String = "fixed", active: Boolean = true) = RecurringTemplate(
        id = id, userId = "u", categoryId = "c", description = id, amount = 10.0,
        type = type, frequency = "monthly", firstOccurrenceDate = "2026-10-01", isActive = active,
    )

    @Test
    fun countsActiveItemsButNotPausedOrLegacyVariableEstimates() {
        val templates = listOf(
            template("rent"),
            template("pay", type = "income"),
            template("paused", active = false),
            template("groceries-estimate", type = "variable"),
            template("legacy-upper", type = "VARIABLE"),
        )
        assertEquals(2, activeRecurringCount(templates))
    }

    @Test
    fun emptyListIsZero() = assertEquals(0, activeRecurringCount(emptyList()))
}
