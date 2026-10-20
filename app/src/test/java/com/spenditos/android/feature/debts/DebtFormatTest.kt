package com.spenditos.android.feature.debts

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.debts.DebtProjection
import com.spenditos.android.data.debts.DebtStatus
import com.spenditos.android.data.debts.DebtSummary
import com.spenditos.android.data.debts.DebtWithPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

/** iOS DebtFormatTests ported: same inputs, same expected text. */
class DebtFormatTest {
    private val us = Locale.US

    @Test
    fun `whole amounts drop the cents`() {
        assertEquals("$6,240", DebtFormat.money(6_240.0, "USD", us))
        assertEquals("$6,240.50", DebtFormat.money(6_240.5, "USD", us))
        assertEquals("$0", DebtFormat.money(0.0, "USD", us))
    }

    @Test
    fun `payment period uses the repeat interval`() {
        assertEquals("/ month", DebtFormat.paymentPeriod("monthly", 1))
        assertEquals("/ month", DebtFormat.paymentPeriod("monthly", null))
        assertEquals("every 3 weeks", DebtFormat.paymentPeriod("weekly", 3))
        assertEquals("every 2 weeks", DebtFormat.paymentPeriod("bi-weekly", 1))
        assertEquals("every 4 weeks", DebtFormat.paymentPeriod("bi-weekly", 2))
        assertEquals("twice a month", DebtFormat.paymentPeriod("semi-monthly", 1))
        assertEquals("/ quarter", DebtFormat.paymentPeriod("quarterly", 1))
        assertEquals("every 6 months", DebtFormat.paymentPeriod("quarterly", 2))
        assertEquals("/ year", DebtFormat.paymentPeriod("yearly", 1))
    }

    @Test
    fun `payment line`() {
        assertEquals("$320 / month", DebtFormat.paymentLine(320.0, "monthly", 1, "USD", us))
        assertTrue(DebtFormat.paymentLine(100.0, "weekly", 3, "USD", us).endsWith("every 3 weeks"))
    }

    @Test
    fun `an unknown frequency is shown as the amount alone`() {
        assertEquals("$100", DebtFormat.paymentLine(100.0, "fortnightly", 1, "USD", us))
    }

    @Test
    fun `month and date formats`() {
        assertEquals("Jul 2028", DebtFormat.monthYear("2028-07-01", us))
        assertEquals("Oct 1, 2026", DebtFormat.shortDate("2026-10-01", us))
        assertNull(DebtFormat.monthYear(null))
        assertEquals("2028-06", DebtFormat.monthKey(LocalDate.of(2028, 6, 14)))
    }

    @Test
    fun `row text for active too low and paid off debts`() {
        val list = DebtTestData.list
        val car = list.first { it.id == "debt-car" }
        assertEquals("$6,040 left of $10,000 · done by Apr 2028", DebtFormat.progressLine(car, "USD", us))

        val tooLow = DebtWithPlan(
            car.debt,
            car.summary,
            DebtProjection(
                paymentsLeft = 0, payoffDate = null, lastPaymentAmount = null,
                interestRemaining = 0.0, neverPaysOff = true, schedule = car.projection.schedule,
            ),
        )
        assertTrue(DebtFormat.progressLine(tooLow, "USD", us).endsWith("Payment too low to finish"))

        val phone = list.first { it.id == "debt-phone" }
        assertEquals("Paid off on Aug 15, 2026", DebtFormat.progressLine(phone, "USD", us))
    }

    @Test
    fun `stopped payments are recognised by nothing scheduled`() {
        val car = DebtTestData.car
        assertFalse(car.paymentsStopped)
        val stopped = DebtWithPlan(
            car.debt,
            car.summary.copy(nextPaymentDate = null),
            car.projection.copy(paymentsLeft = 0, payoffDate = null, lastPaymentAmount = null, interestRemaining = 0.0, schedule = emptyList()),
        )
        assertTrue(stopped.paymentsStopped)
        assertTrue(DebtFormat.progressLine(stopped, "USD", us).endsWith("Payments stopped"))
        val paidOff = DebtWithPlan(car.debt, car.summary.copy(status = DebtStatus.PAID_OFF), stopped.projection)
        assertFalse("A paid-off debt is not 'stopped'", paidOff.paymentsStopped)
    }

    @Test
    fun `banned words never appear in debt wording`() {
        val texts = listOf(
            DebtErrorMessage.NOT_FOUND, DebtErrorMessage.TARGET_TOO_SOON, DebtErrorMessage.PAYMENT_INCOMPLETE,
            DebtErrorMessage.OFFLINE, DebtErrorMessage.INVALID_INPUT, DebtErrorMessage.FUTURE_DATE,
            DebtErrorMessage.CANT_REACH, DebtErrorMessage.GENERIC, DebtErrorMessage.CATEGORY_UNAVAILABLE,
        )
        for (text in texts) {
            for (word in listOf("liability", "amortization", "principal", "template", "rule")) {
                assertFalse("\"$text\" contains $word", text.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `error messages are plain language and never show the server code`() {
        assertEquals(DebtErrorMessage.NOT_FOUND, DebtErrorMessage.message(ApiError.Server(404, "NotFound")))
        assertEquals(DebtErrorMessage.NOT_FOUND, DebtErrorMessage.message(ApiError.Server(400, "DebtNotFound")))
        assertEquals(DebtErrorMessage.FUTURE_DATE, DebtErrorMessage.message(ApiError.Server(400, "DateInFuture")))
        assertEquals(DebtErrorMessage.TARGET_TOO_SOON, DebtErrorMessage.message(ApiError.Server(400, "TARGET_TOO_SOON")))
        assertEquals(DebtErrorMessage.PAYMENT_INCOMPLETE, DebtErrorMessage.message(ApiError.Server(400, "PaymentIncomplete")))
        assertEquals(DebtErrorMessage.INVALID_INPUT, DebtErrorMessage.message(ApiError.Server(400, "ValidationError")))
        assertEquals(DebtErrorMessage.CATEGORY_UNAVAILABLE, DebtErrorMessage.message(ApiError.Server(409, "CategoryBeingDeleted")))
        assertEquals(DebtErrorMessage.GENERIC, DebtErrorMessage.message(ApiError.Server(500, "InternalError")))
        assertEquals(DebtErrorMessage.CANT_REACH, DebtErrorMessage.message(ApiError.Network(IOException("timeout"))))
        assertEquals(DebtErrorMessage.OFFLINE, DebtErrorMessage.message(ApiError.Network(IOException("Offline")), offline = true))
        assertEquals(DebtErrorMessage.GENERIC, DebtErrorMessage.message(ApiError.Decoding(IOException("x"))))
    }

    @Test
    fun `summary alone gives the payment line`() {
        val summary: DebtSummary = DebtTestData.car.summary
        assertEquals("$320 / month", DebtFormat.paymentLine(summary, "USD", us))
        assertNull(DebtFormat.paymentLine(summary.copy(payment = null), "USD", us))
    }

    @Test
    fun `amount fields parse both decimal marks and explain what is wrong`() {
        assertEquals(150.5, parseDebtAmount(" 150,5 ")!!, 0.0)
        assertEquals(150.5, parseDebtAmount("150.5")!!, 0.0)
        assertNull(parseDebtAmount("abc"))

        assertNull("No complaint before the first tap", extraPaymentAmountError("", showErrors = false))
        assertEquals("Enter how much you paid", extraPaymentAmountError("", showErrors = true))
        assertEquals("Enter an amount above zero", extraPaymentAmountError("0", showErrors = false))
        assertNull(extraPaymentAmountError("20", showErrors = true))

        assertEquals("Enter what you owe today", correctBalanceAmountError(" ", showErrors = true))
        assertNull("Zero is allowed when correcting a balance", correctBalanceAmountError("0", showErrors = true))
        assertEquals("Enter zero or more", correctBalanceAmountError("-5", showErrors = true))
        assertEquals("6040", balanceFieldText(6040.0))
        assertEquals("6040.50", balanceFieldText(6040.5))
    }
}
