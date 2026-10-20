package com.spenditos.android.data.debts

import com.spenditos.android.core.network.ApiEnvelope
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.data.model.MonthlyPlanOccurrence
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateTransactionRequest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Debts contract (docs/features/debts.md): iOS DebtModelsTests ported with the same fixtures and numbers. */
class DebtModelsTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource("contract/handwritten/debts/$name.json")).readText()

    private fun <T> data(serializer: kotlinx.serialization.KSerializer<T>, json: String): T =
        SpenditosJson.decodeFromString(ApiEnvelope.serializer(serializer), json).data!!

    private fun detail(json: String = fixture("detail")) = data(DebtDetail.serializer(), json)
    private fun list() = data(ListDebtsResponse.serializer(), fixture("list"))
    private fun encode(request: UpdateDebtRequest): JsonObject =
        SpenditosJson.encodeToJsonElement(UpdateDebtRequest.serializer(), request).jsonObject

    // region Decoding

    @Test
    fun `list envelope decodes a full payload`() {
        val list = list()
        assertEquals(3, list.debts.size)
        val car = list.debts.first { it.debt.id == "debt-car" }
        assertEquals("debt-car", car.id)
        assertEquals(10000.0, car.debt.startingBalance, 0.0)
        assertEquals("2026-01-01", car.debt.startDate)
        assertEquals(7.5, car.debt.apr!!, 0.0)
        assertEquals("bill-car", car.debt.billId)
        assertEquals(DebtStatus.ACTIVE, car.summary.status)
        assertFalse(car.summary.isPaidOff)
        assertEquals(6040.0, car.summary.balance, 0.0)
        assertEquals(212.4, car.summary.interestTotal, 0.0)
        assertEquals(0.396, car.summary.progress, 0.0)
        assertNull(car.summary.paidOffDate)
        assertEquals("2026-10-01", car.summary.nextPaymentDate)
        assertEquals(320.0, car.summary.payment!!, 0.0)
        assertEquals("monthly", car.summary.frequency)
        assertEquals(1, car.summary.repeatInterval)
        assertEquals(19, car.projection.paymentsLeft)
        assertEquals("2028-04-01", car.projection.payoffDate)
        assertEquals(140.0, car.projection.lastPaymentAmount!!, 0.0)
        assertEquals(
            listOf(DebtScheduleEntry("2026-10-01", 320.0), DebtScheduleEntry("2026-11-01", 320.0)),
            car.projection.schedule,
        )
    }

    @Test
    fun `interest free debt has null apr and zero interest`() {
        val laptop = list().debts.first { it.debt.id == "debt-laptop" }
        assertNull(laptop.debt.apr)
        assertEquals(0.0, laptop.summary.interestTotal, 0.0)
        assertEquals(0.0, laptop.projection.interestRemaining, 0.0)
        assertEquals("2026-10-05", laptop.summary.nextPaymentDate)
    }

    @Test
    fun `paid off debt has no bill and an empty schedule`() {
        val phone = list().debts.first { it.debt.id == "debt-phone" }
        assertEquals(DebtStatus.PAID_OFF, phone.summary.status)
        assertTrue(phone.summary.isPaidOff)
        assertEquals(0.0, phone.summary.balance, 0.0)
        assertEquals("2026-08-15", phone.summary.paidOffDate)
        assertNull(phone.summary.nextPaymentDate)
        assertNull(phone.debt.billId)
        assertNull(phone.summary.payment)
        assertNull(phone.summary.frequency)
        assertNull(phone.summary.repeatInterval)
        assertNull(phone.projection.payoffDate)
        assertNull(phone.projection.lastPaymentAmount)
        assertEquals(emptyList<DebtScheduleEntry>(), phone.projection.schedule)
    }

    @Test
    fun `detail decodes history newest first`() {
        val detail = detail()
        assertEquals("debt-car", detail.id)
        assertEquals(
            listOf(DebtHistoryKind.EXTRA_PAYMENT, DebtHistoryKind.PAYMENT, DebtHistoryKind.ADJUSTMENT),
            detail.history.map { it.kind },
        )
        assertEquals(listOf("2026-09-10", "2026-09-01", "2026-06-01"), detail.history.map { it.date })
        val extra = detail.history[0]
        assertEquals(200.0, extra.amount, 0.0)
        assertEquals("txn-extra-1", extra.transactionId)
        assertNull(extra.adjustmentId)
        assertEquals(33.75, detail.history[1].interest, 0.0)
        assertEquals("txn-bill-sep", detail.history[1].transactionId)
        assertEquals("adj-1", detail.history[2].adjustmentId)
        assertNull(detail.history[2].transactionId)
        assertEquals(detail.summary.balance, detail.history[0].balanceAfter, 0.0)
    }

    @Test
    fun `preview response decodes`() {
        val preview = data(DebtPreviewResponse.serializer(), fixture("preview"))
        assertEquals(320.0, preview.payment, 0.0)
        assertEquals(20, preview.paymentsCount)
        assertEquals("2028-05-01", preview.payoffDate)
        assertEquals(180.0, preview.lastPaymentAmount!!, 0.0)
        assertEquals(612.0, preview.totalInterest, 0.0)
        assertFalse(preview.neverPaysOff)
    }

    @Test
    fun `error envelope has no data`() {
        val envelope = SpenditosJson.decodeFromString(ApiEnvelope.serializer(ListDebtsResponse.serializer()), fixture("error"))
        assertNull(envelope.data)
        assertEquals("Debt not found", envelope.message)
        assertEquals("NOT_FOUND", envelope.error)
    }

    @Test
    fun `minimal payload decodes with every optional field missing`() {
        val minimal = """{"data":{"debt":{"id":"d","userId":"u","name":"Loan","startingBalance":500,"startDate":"2026-10-20",
            "categoryId":"c","createdAt":"2026-10-20T09:00:00Z","updatedAt":"2026-10-20T09:00:00Z"},
            "summary":{"status":"active","balance":500,"paidTotal":0,"interestTotal":0,"progress":0},
            "projection":{"paymentsLeft":0,"interestRemaining":0,"neverPaysOff":false,"schedule":[]},
            "history":[]}}"""
        val detail = detail(minimal)
        assertNull(detail.debt.apr)
        assertNull(detail.debt.billId)
        assertNull(detail.summary.nextPaymentDate)
        assertNull(detail.summary.payment)
        assertNull(detail.summary.frequency)
        assertNull(detail.summary.repeatInterval)
        assertNull(detail.projection.payoffDate)
        assertEquals(emptyList<DebtHistoryEntry>(), detail.history)
    }

    @Test
    fun `summary without repeatInterval from an older server decodes null and an explicit interval decodes`() {
        val older = fixture("detail").replace("\"repeatInterval\": 1", "\"note\": 1")
        assertNull(detail(older).summary.repeatInterval)
        assertEquals(320.0, detail(older).summary.payment!!, 0.0)
        val three = fixture("detail").replace("\"repeatInterval\": 1", "\"repeatInterval\": 3")
        assertEquals(3, detail(three).summary.repeatInterval)
    }

    @Test
    fun `unknown status and history kind decode as UNKNOWN`() {
        val detail = detail(fixture("unknown-enums"))
        assertEquals(DebtStatus.UNKNOWN, detail.summary.status)
        assertFalse(detail.summary.isPaidOff)
        assertEquals(DebtHistoryKind.UNKNOWN, detail.history.first().kind)
        assertNull(detail.history.first().transactionId)
    }

    @Test
    fun `an unknown status decodes even with a plain Json instance`() {
        val plain = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        assertEquals(DebtStatus.UNKNOWN, plain.decodeFromString(DebtStatus.serializer(), "\"archived\""))
        assertEquals(DebtStatus.PAID_OFF, plain.decodeFromString(DebtStatus.serializer(), "\"paid_off\""))
    }

    @Test
    fun `an unknown frequency string is preserved`() {
        val raw = fixture("detail").replace("\"frequency\": \"monthly\"", "\"frequency\": \"every-new-moon\"")
        assertEquals("every-new-moon", detail(raw).summary.frequency)
    }

    @Test
    fun `enums encode to their wire values`() {
        assertEquals("\"paid_off\"", SpenditosJson.encodeToString(DebtStatus.serializer(), DebtStatus.PAID_OFF))
        assertEquals("\"extra_payment\"", SpenditosJson.encodeToString(DebtHistoryKind.serializer(), DebtHistoryKind.EXTRA_PAYMENT))
    }

    // endregion

    // region debtId on existing models

    @Test
    fun `recurring template without debtId decodes null and with debtId decodes it`() {
        val base = """{"id":"bill-rent","userId":"user-1","categoryId":"cat-housing","description":"Rent","amount":1450,
            "type":"fixed","frequency":"monthly","firstOccurrenceDate":"2026-10-01"%s}"""
        assertNull(SpenditosJson.decodeFromString(RecurringTemplate.serializer(), base.format("")).debtId)
        assertEquals(
            "debt-car",
            SpenditosJson.decodeFromString(RecurringTemplate.serializer(), base.format(",\"debtId\":\"debt-car\"")).debtId,
        )
        assertNull(SpenditosJson.decodeFromString(RecurringTemplate.serializer(), base.format(",\"debtId\":null")).debtId)
    }

    @Test
    fun `transaction without debtId decodes null and with debtId decodes it`() {
        val base = """{"id":"txn-1","userId":"user-1","amount":25.5,"categoryId":"cat-1","description":"Lunch","date":"2026-03-28"%s}"""
        assertNull(SpenditosJson.decodeFromString(Transaction.serializer(), base.format("")).debtId)
        assertEquals(
            "debt-car",
            SpenditosJson.decodeFromString(Transaction.serializer(), base.format(",\"debtId\":\"debt-car\"")).debtId,
        )
    }

    @Test
    fun `month plan occurrence decodes debtId when present and null when absent`() {
        val base = """{"templateId":"bill-car","categoryId":"c","description":"Car loan","date":"2026-10-01","amount":320%s}"""
        assertNull(SpenditosJson.decodeFromString(MonthlyPlanOccurrence.serializer(), base.format("")).debtId)
        assertEquals(
            "debt-car",
            SpenditosJson.decodeFromString(MonthlyPlanOccurrence.serializer(), base.format(",\"debtId\":\"debt-car\"")).debtId,
        )
    }

    @Test
    fun `transaction update leaves debtId out unless set and sends JsonNull to unlink`() {
        val untouched = SpenditosJson.encodeToJsonElement(UpdateTransactionRequest.serializer(), UpdateTransactionRequest(amount = 5.0)).jsonObject
        assertFalse(untouched.containsKey("debtId"))
        val link = SpenditosJson.encodeToJsonElement(
            UpdateTransactionRequest.serializer(),
            UpdateTransactionRequest(debtId = JsonPrimitive("debt-car")),
        ).jsonObject
        assertEquals("debt-car", (link["debtId"] as JsonPrimitive).content)
        val unlink = SpenditosJson.encodeToJsonElement(
            UpdateTransactionRequest.serializer(),
            UpdateTransactionRequest(debtId = JsonNull),
        ).jsonObject
        assertTrue(unlink.containsKey("debtId"))
        assertEquals(JsonNull, unlink["debtId"])
    }

    // endregion

    // region Request encoding

    @Test
    fun `create request omits null optionals`() {
        val request = CreateDebtRequest(
            name = "Car loan", startingBalance = 10000.0, startDate = "2026-01-01", apr = 7.5, categoryId = "cat-debt",
            payment = CreateDebtPayment(amount = 320.0, frequency = "monthly", firstPaymentDate = "2026-10-01"),
        )
        val body = SpenditosJson.encodeToJsonElement(CreateDebtRequest.serializer(), request).jsonObject
        assertEquals("Car loan", (body["name"] as JsonPrimitive).content)
        assertEquals(7.5, (body["apr"] as JsonPrimitive).content.toDouble(), 0.0)
        val payment = body["payment"]!!.jsonObject
        assertEquals("monthly", (payment["frequency"] as JsonPrimitive).content)
        assertFalse(payment.containsKey("repeatInterval"))
        assertFalse(payment.containsKey("secondOccurrenceDay"))

        val minimal = SpenditosJson.encodeToJsonElement(
            CreateDebtRequest.serializer(),
            CreateDebtRequest(name = "Loan from mum", startingBalance = 500.0),
        ).jsonObject
        assertEquals(setOf("name", "startingBalance"), minimal.keys)
        assertEquals(request, SpenditosJson.decodeFromString(CreateDebtRequest.serializer(), SpenditosJson.encodeToString(CreateDebtRequest.serializer(), request)))
    }

    @Test
    fun `update request omits an unset payment key`() {
        val body = encode(UpdateDebtRequest(name = "Car loan (refi)"))
        assertEquals(setOf("name"), body.keys)
    }

    @Test
    fun `update request with Null payment sends an explicit JSON null`() {
        val raw = SpenditosJson.encodeToString(UpdateDebtRequest.serializer(), UpdateDebtRequest(payment = DebtPatch.Null))
        assertEquals("""{"payment":null}""", raw)
    }

    @Test
    fun `update request with Null apr clears interest`() {
        val raw = SpenditosJson.encodeToString(UpdateDebtRequest.serializer(), UpdateDebtRequest(apr = DebtPatch.Null))
        assertEquals("""{"apr":null}""", raw)
    }

    @Test
    fun `update request encodes a partial payment patch`() {
        val body = encode(UpdateDebtRequest(payment = DebtPatch.Value(UpdateDebtPayment(amount = 350.0))))
        val payment = body["payment"]!!.jsonObject
        assertEquals(350.0, (payment["amount"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(setOf("amount"), payment.keys)
    }

    @Test
    fun `update request round trips omitted values and explicit nulls`() {
        val cases = listOf(
            UpdateDebtRequest(),
            UpdateDebtRequest(payment = DebtPatch.Null),
            UpdateDebtRequest(apr = DebtPatch.Null, payment = DebtPatch.Null),
            UpdateDebtRequest(
                name = "Car loan", startingBalance = 9500.0, categoryId = "cat-debt", apr = DebtPatch.Value(6.9),
                payment = DebtPatch.Value(UpdateDebtPayment(300.0, "bi-weekly", "2026-10-03", 2)),
            ),
        )
        for (request in cases) {
            val raw = SpenditosJson.encodeToString(UpdateDebtRequest.serializer(), request)
            assertEquals(request, SpenditosJson.decodeFromString(UpdateDebtRequest.serializer(), raw))
        }
        val decoded = SpenditosJson.decodeFromString(UpdateDebtRequest.serializer(), """{"payment":null}""")
        assertEquals(DebtPatch.Null, decoded.payment)
        assertNull(decoded.apr)
    }

    @Test
    fun `preview request always sends apr as null when there is no interest`() {
        val request = DebtPreviewRequest(
            balance = 1200.0, apr = null, frequency = "monthly", firstPaymentDate = "2026-10-05", targetMonth = "2027-06",
        )
        val body = SpenditosJson.encodeToJsonElement(DebtPreviewRequest.serializer(), request).jsonObject
        assertTrue(body.containsKey("apr"))
        assertEquals(JsonNull, body["apr"])
        assertEquals("2027-06", (body["targetMonth"] as JsonPrimitive).content)
        assertFalse(body.containsKey("payment"))
        assertFalse(body.containsKey("repeatInterval"))
        assertEquals(request, SpenditosJson.decodeFromString(DebtPreviewRequest.serializer(), body.toString()))
    }

    @Test
    fun `preview request with apr and payment round trips`() {
        val request = DebtPreviewRequest(
            balance = 10000.0, apr = 7.5, frequency = "monthly", repeatInterval = 1, firstPaymentDate = "2026-10-01", payment = 320.0,
        )
        val body = SpenditosJson.encodeToJsonElement(DebtPreviewRequest.serializer(), request).jsonObject
        assertEquals(7.5, (body["apr"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(320.0, (body["payment"] as JsonPrimitive).content.toDouble(), 0.0)
        assertFalse(body.containsKey("targetMonth"))
        assertEquals(request, SpenditosJson.decodeFromString(DebtPreviewRequest.serializer(), body.toString()))
    }

    @Test
    fun `adjustment request round trips`() {
        val request = CreateDebtAdjustmentRequest(date = "2026-06-01", balance = 6500.0)
        val raw = SpenditosJson.encodeToString(CreateDebtAdjustmentRequest.serializer(), request)
        assertEquals(request, SpenditosJson.decodeFromString(CreateDebtAdjustmentRequest.serializer(), raw))
        assertEquals(setOf("date", "balance"), SpenditosJson.parseToJsonElement(raw).jsonObject.keys)
    }

    // endregion
}
