package com.spenditos.android.contract

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.PatchMainIncomeTemplateRequest
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringContractTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/$name.json")).readText()

    private fun recurring(name: String): RecurringTemplate =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(RecurringTemplate.serializer()), fixture(name)).data!!

    @Test
    fun `canonical list and create fixtures decode`() {
        val list = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(kotlinx.serialization.builtins.ListSerializer(RecurringTemplate.serializer())),
            fixture("listRecurringTemplatesFixture"),
        ).data!!
        assertEquals("tmpl-rent-001", list.single().id)
        assertEquals("Monthly Rent", recurring("createRecurringTemplateFixture").description)
        assertEquals("monthly", list.single().frequency)
    }

    @Test
    fun `canonical update and paused rows decode`() {
        val updated = recurring("updateRecurringTemplateSuccessFixture")
        val paused = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), fixture("pausedRecurringTemplateFixture"))
        assertEquals(1600.0, updated.amount, 0.0)
        assertFalse(paused.isActive)
        assertTrue(paused.amountVaries)
        assertEquals("2026-02-10T09:30:00.000Z", paused.pausedAt)
        val ordinary = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), fixture("recurringTemplateFixture"))
        assertEquals("tmpl-rent-001", ordinary.id)
    }

    @Test
    fun `legacy variable row and unknown frequency remain readable`() {
        val raw = fixture("recurringTemplateFixture").replace("\"type\": \"fixed\"", "\"type\": \"variable\"")
            .replace("\"frequency\": \"monthly\"", "\"frequency\": \"every-new-moon\"")
        val decoded = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), raw)
        assertEquals("variable", decoded.type)
        assertEquals("every-new-moon", decoded.frequency)
    }

    @Test
    fun `handler-only generate request response and explicit null requests decode`() {
        val root = SpenditosJson.parseToJsonElement(fixture("handwritten/generateTransactions")).jsonObject
        val request = SpenditosJson.decodeFromString(GenerateTransactionsRequest.serializer(), root["request"].toString())
        val response = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(GenerateTransactionsResult.serializer()),
            root["response"].toString(),
        ).data!!
        assertEquals("2026-01-01", request.targetDate)
        assertEquals(2, response.generated)
        assertEquals(1, response.skipped)

        val create = SpenditosJson.decodeFromString(CreateRecurringTemplateRequest.serializer(), """{"categoryId":"cat","description":"Rent","amount":5,"frequency":"monthly","firstOccurrenceDate":"2026-01-01","occurrencesRemaining":null,"secondOccurrenceDay":null}""")
        val update = SpenditosJson.decodeFromString(UpdateRecurringTemplateRequest.serializer(), """{"occurrencesRemaining":null}""")
        val clearMain = SpenditosJson.decodeFromString(PatchMainIncomeTemplateRequest.serializer(), """{"mainIncomeTemplateId":null}""")
        assertTrue(create.occurrencesRemaining is kotlinx.serialization.json.JsonNull)
        assertNull(update.occurrencesRemaining)
        assertTrue(clearMain.mainIncomeTemplateId is kotlinx.serialization.json.JsonNull)
        val explicitNullUpdate = UpdateRecurringTemplateRequest(occurrencesRemaining = kotlinx.serialization.json.JsonNull)
        val serialized = SpenditosJson.encodeToString(UpdateRecurringTemplateRequest.serializer(), explicitNullUpdate)
        assertTrue(SpenditosJson.parseToJsonElement(serialized).jsonObject["occurrencesRemaining"] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `ended template fixture decodes as Ended`() {
        val ended = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), fixture("handwritten/endedRecurringTemplateFixture"))
        assertFalse(ended.isActive)
        assertEquals("2026-10-20T14:00:00.000Z", ended.endedAt)
        assertEquals(0, ended.occurrencesRemaining)
        assertEquals(com.spenditos.android.data.model.BillLifecycle.ENDED, ended.lifecycle)
        // Older payloads without endedAt/pausedAt still decode, and an active row stays active.
        val ordinary = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), fixture("recurringTemplateFixture"))
        assertNull(ordinary.endedAt)
        assertEquals(com.spenditos.android.data.model.BillLifecycle.ACTIVE, ordinary.lifecycle)
        val paused = SpenditosJson.decodeFromString(RecurringTemplate.serializer(), fixture("pausedRecurringTemplateFixture"))
        assertEquals(com.spenditos.android.data.model.BillLifecycle.PAUSED, paused.lifecycle)
    }

    @Test
    fun `bill history fixture decodes counts totals dates and entries`() {
        val history = SpenditosJson.decodeFromString(
            ApiEnvelope.serializer(
                MapSerializer(String.serializer(), com.spenditos.android.data.model.BillHistorySummary.serializer()),
            ),
            fixture("handwritten/billHistoryFixture"),
        ).data!!
        val summary = history.getValue("tmpl-rent-001")
        assertEquals(2, summary.count)
        assertEquals(229.72, summary.total, 0.0)
        assertEquals("2026-09-30", summary.firstDate)
        assertEquals("2026-10-14", summary.lastDate)
        assertEquals(listOf("txn-hist-2", "txn-hist-1"), summary.entries!!.map { it.id })
        assertEquals("Klarna Airbnb", summary.entries!!.first().description)
        assertEquals(114.86, summary.entries!!.first().amount, 0.0)
    }

    @Test
    fun `lifecycle update requests write the fields the backend schema names`() {
        val end = SpenditosJson.encodeToString(
            UpdateRecurringTemplateRequest.serializer(),
            UpdateRecurringTemplateRequest(
                isActive = false,
                endedAt = kotlinx.serialization.json.JsonPrimitive("2026-10-20T14:00:00Z"),
                pausedAt = kotlinx.serialization.json.JsonNull,
                upcomingEntries = "sync",
            ),
        )
        val body = SpenditosJson.parseToJsonElement(end).jsonObject
        assertEquals("2026-10-20T14:00:00Z", body["endedAt"]!!.toString().trim('"'))
        assertTrue(body["pausedAt"] is kotlinx.serialization.json.JsonNull)
        assertEquals("\"sync\"", body["upcomingEntries"].toString())
    }
}
