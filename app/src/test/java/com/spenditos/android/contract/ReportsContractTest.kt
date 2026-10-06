package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.DailyAggregatesPayload
import com.spenditos.android.data.model.DashboardData
import com.spenditos.android.data.model.PaycheckPeriodsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportsContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/$name.json")).readText()

    private fun <T> envelope(name: String, serializer: kotlinx.serialization.KSerializer<T>): ApiEnvelope<T> =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(serializer), fixture(name))

    @Test
    fun `dashboard fixture decodes only backend report fields`() {
        val dashboard = envelope("dashboardSuccessFixture", DashboardData.serializer()).data!!
        assertEquals(6400.0, dashboard.summary!!.totalIncome, 0.0)
        assertEquals("cat-groceries", dashboard.categoryBreakdown.single().categoryId)
        assertFalse(dashboard.categoryBreakdown.single().isIncome)
        assertEquals(15, dashboard.totalTransactionCount)
        assertEquals(LocalDate.parse("2026-01-01"), dashboard.dateRange!!.startDate)
    }

    @Test
    fun `daily aggregates fixture decodes the nested data envelope`() {
        val payload = envelope("dailyAggregatesSuccessFixture", DailyAggregatesPayload.serializer()).data!!
        assertEquals(3, payload.data.size)
        assertEquals(LocalDate.parse("2026-01-16"), payload.data[1].date)
        assertEquals(2, payload.data[1].transactionCount)
    }

    @Test
    fun `paycheck periods fixture decodes dates and coverage`() {
        val response = envelope("paycheckPeriodsSuccessFixture", PaycheckPeriodsData.serializer()).data!!
        val period = response.periods.single()
        assertEquals(LocalDate.parse("2026-01-01"), period.startDate)
        assertEquals(3200.0, period.income.total, 0.0)
        assertEquals("COVERED", period.coverageStatus!!.name)
        assertTrue(response.periods.isNotEmpty())
    }
}
