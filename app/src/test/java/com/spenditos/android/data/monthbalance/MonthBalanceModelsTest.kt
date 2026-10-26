package com.spenditos.android.data.monthbalance

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.debts.CreateDebtPayment
import com.spenditos.android.data.debts.CreateDebtRequest
import com.spenditos.android.data.debts.Debt
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS `MonthBalanceTests` contract cases: the canonical fixtures from `packages/types` decode, an older server
 * and a newer one never fail Home, and the wire shapes of the answer and of a shortfall debt.
 */
class MonthBalanceModelsTest {
    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/monthbalance/$name.json")).readText()

    private fun decode(json: String): MonthBalanceData =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(MonthBalanceData.serializer()), json).data!!.normalized()

    @Test
    fun `decodes the shortfall fixture from the types package`() {
        val data = decode(fixture("shortfall"))
        assertEquals(-200.0, data.balance, 0.0)
        assertEquals(data.received - data.spent, data.balance, 0.0)
        assertEquals(MonthBalanceKind.SHORTFALL, data.kind)
        assertNull(data.choice)
        assertTrue(data.showPrompt)
        assertEquals(0.0, data.carriedIn, 0.0)
        assertNull(data.shortfallDebtId)
    }

    @Test
    fun `decodes the carried fixture from the types package`() {
        val data = decode(fixture("carried"))
        assertEquals(MonthBalanceKind.SURPLUS, data.kind)
        assertEquals(MonthBalanceChoice.CARRIED, data.choice)
        assertFalse(data.showPrompt)
        assertEquals(150.0, data.carriedIn, 0.0)
        assertEquals(data.balance, data.carriedIn, 0.0)
    }

    @Test
    fun `decodes the chained fixture and older servers without previousCarriedIn`() {
        val chained = decode(fixture("chained"))
        assertEquals(150.0, chained.previousCarriedIn, 0.0)
        assertEquals(chained.received - chained.spent + chained.previousCarriedIn, chained.balance, 0.0)
        assertEquals(250.0, chained.balance, 0.0)
        val older = decode(
            """{"data":{"month":"2026-10","previousMonth":"2026-09","received":3000,"spent":2850,"balance":150,"kind":"surplus","showPrompt":true}}""",
        )
        assertEquals(0.0, older.previousCarriedIn, 0.0)
        assertEquals(0.0, older.carriedIn, 0.0)
        assertTrue(older.showPrompt)
    }

    /** The server decides `kind` from the user's currency (5, or 500 for zero-decimal ones); the client honours it. */
    @Test
    fun `the client honours the servers currency aware kind`() {
        val none = decode(
            """{"data":{"month":"2026-10","previousMonth":"2026-09","received":300000,"spent":299501,"previousCarriedIn":0,"balance":499,"kind":"none","choice":null,"showPrompt":false,"carriedIn":0}}""",
        )
        assertFalse(none.showPrompt)
        assertEquals(MonthBalanceKind.NONE, none.kind)
        val surplus = decode(
            """{"data":{"month":"2026-10","previousMonth":"2026-09","received":300000,"spent":299500,"previousCarriedIn":0,"balance":500,"kind":"surplus","choice":null,"showPrompt":true,"carriedIn":0}}""",
        )
        assertTrue(surplus.showPrompt)
    }

    @Test
    fun `an unknown kind or choice from a newer server shows nothing instead of failing`() {
        val data = decode(
            """{"data":{"month":"2026-10","previousMonth":"2026-09","received":0,"spent":0,"balance":-5,"kind":"somethingNew","choice":"later","showPrompt":true}}""",
        )
        assertEquals(MonthBalanceKind.NONE, data.kind)
        assertNull(data.choice)
        assertFalse(data.showPrompt)
        assertEquals(0.0, data.carriedIn, 0.0)
    }

    @Test
    fun `the answer body carries the month and the wire choice`() {
        val body = SpenditosJson.encodeToJsonElement(
            SetMonthBalanceChoiceRequest.serializer(),
            SetMonthBalanceChoiceRequest("2026-10", MonthBalanceChoice.CARRIED),
        ).jsonObject
        assertEquals(setOf("month", "choice"), body.keys)
        assertEquals("\"2026-10\"", body.getValue("month").toString())
        assertEquals("\"carried\"", body.getValue("choice").toString())
        val dismissed = SpenditosJson.encodeToString(SetMonthBalanceChoiceRequest.serializer(), SetMonthBalanceChoiceRequest("2026-10", MonthBalanceChoice.DISMISSED))
        assertTrue(dismissed.contains("\"dismissed\""))
    }

    @Test
    fun `a debt decodes shortfallMonth and older servers without it`() {
        val base = """"id":"d","userId":"u","name":"September shortfall","startingBalance":200,"startDate":"2026-10-03","apr":null,"billId":null,"categoryId":"c","createdAt":"2026-10-03T00:00:00Z","updatedAt":"2026-10-03T00:00:00Z""""
        assertEquals("2026-09", SpenditosJson.decodeFromString(Debt.serializer(), "{$base,\"shortfallMonth\":\"2026-09\"}").shortfallMonth)
        assertNull(SpenditosJson.decodeFromString(Debt.serializer(), "{$base}").shortfallMonth)
        assertNull(SpenditosJson.decodeFromString(Debt.serializer(), "{$base,\"shortfallMonth\":null}").shortfallMonth)
    }

    @Test
    fun `a shortfall debt has no bill and sends shortfallMonth, other debts omit it`() {
        val prompt = SpenditosJson.encodeToJsonElement(
            CreateDebtRequest.serializer(),
            CreateDebtRequest(name = "September shortfall", startingBalance = 200.0, startDate = "2026-10-03", shortfallMonth = "2026-09"),
        ).jsonObject
        assertEquals("\"2026-09\"", prompt.getValue("shortfallMonth").toString())
        assertFalse("payment" in prompt)
        assertFalse("apr" in prompt)
        val plain = SpenditosJson.encodeToJsonElement(
            CreateDebtRequest.serializer(),
            CreateDebtRequest(name = "Car", startingBalance = 100.0, payment = CreateDebtPayment(10.0, "monthly", "2026-11-01")),
        ).jsonObject
        assertFalse("shortfallMonth" in plain)
    }
}
