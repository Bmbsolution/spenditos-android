package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.DeleteMonthlyPlanDraftResult
import com.spenditos.android.data.model.MonthlyPlanData
import com.spenditos.android.data.model.MonthlyPlanDraftData
import com.spenditos.android.data.model.MonthlyPlanDraftLookup
import com.spenditos.android.data.model.SkipMonthlyPlanOccurrenceRequest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthPlanContractTest {
    private fun fixture(name: String): String = requireNotNull(
        javaClass.classLoader!!.getResource("contract/$name.json"),
    ) { "missing fixture $name" }.readText()

    @Test
    fun `monthly plan response fixture decodes`() {
        val plan = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(MonthlyPlanData.serializer()),
            fixture("monthlyPlanSuccessFixture"),
        ).data!!
        assertEquals("2026-02", plan.month)
        assertEquals(2, plan.scheduled.size)
        assertEquals(3200.0, plan.totals.expectedIncome, 0.0)
        assertTrue(plan.scheduled.any { it.categoryId == "cat-salary" })
    }

    @Test
    fun `monthly plan draft response and data fixtures decode without removed fields`() {
        val lookup = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(MonthlyPlanDraftLookup.serializer()),
            fixture("monthlyPlanDraftSuccessFixture"),
        ).data!!
        val draft = lookup.draft!!
        assertEquals("2026-02", draft.month)
        assertEquals(1, draft.entries.size)
        assertEquals(1, draft.pendingEdits.size)
        assertEquals(1, draft.pendingLogs.size)
        assertEquals(1, draft.pendingDeletes.size)
        assertTrue(draft.transactionDelta.isEmpty())

        val data = SpenditosJson.decodeFromString(MonthlyPlanDraftData.serializer(), fixture("monthlyPlanDraftDataFixture"))
        assertEquals(draft.entries, data.entries)
        assertTrue(data.pendingSkips.isEmpty())
        assertFalse("budgetDelta" in fixture("monthlyPlanDraftDataFixture"))
    }

    @Test
    fun `handler-only skip request and delete response decode from handwritten fixture`() {
        val root = SpenditosJson.parseToJsonElement(fixture("handwritten/monthlyPlanHandlerShapes")).jsonObject
        val skip = SpenditosJson.decodeFromString(SkipMonthlyPlanOccurrenceRequest.serializer(), root["skipRequest"].toString())
        val deleted = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(DeleteMonthlyPlanDraftResult.serializer()),
            root["deleteDraftResponse"].toString(),
        ).data!!
        assertEquals("2099-12-15", skip.date)
        assertEquals("2099-12", deleted.month)
        assertTrue(deleted.deleted)
    }
}
