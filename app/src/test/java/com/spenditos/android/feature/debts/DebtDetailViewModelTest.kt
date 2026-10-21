package com.spenditos.android.feature.debts

import androidx.lifecycle.SavedStateHandle
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.debts.DebtDetail
import com.spenditos.android.data.debts.DebtPatch
import com.spenditos.android.data.debts.DebtPreviewResponse
import com.spenditos.android.data.debts.DebtProjection
import com.spenditos.android.data.debts.UpdateDebtPayment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** iOS DebtDetailViewModelTests ported with the same scenarios and numbers. */
class DebtDetailViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate.of(2026, 10, 20)

    private fun TestScope.viewModel(
        service: FakeDebtActions,
        detail: DebtDetail? = DebtTestData.detail,
        connectivity: FakeConnectivity = FakeConnectivity(),
        id: String = detail?.id ?: "debt-car",
    ): DebtDetailViewModel {
        if (detail != null) service.detailResult = Result.success(detail)
        val vm = DebtDetailViewModel(
            SavedStateHandle(mapOf("debtId" to id)), service, FakeCurrency(), connectivity, FinanceInvalidationBus(), testClock(),
        )
        advanceUntilIdle()
        return vm
    }

    /** The laptop loan: no interest. */
    private val interestFreeDetail: DebtDetail
        get() {
            val plan = DebtTestData.list.first { it.id == "debt-laptop" }
            return DebtDetail(plan.debt, plan.summary, plan.projection, emptyList())
        }

    private fun money(value: Double) = DebtFormat.money(value, "USD")

    // region Where it stands

    @Test
    fun `load asks for the local date and shows the numbers`() = runTest {
        val service = FakeDebtActions()
        val state = viewModel(service).state.value

        assertEquals(today, service.detailCalls.first().second)
        assertEquals(money(6_040.0), state.balanceText)
        assertEquals("39%", state.percentText)
        assertEquals("${money(3_960.0)} of ${money(10_000.0)} paid off", state.paidOffText)
        assertEquals("19 payments left, the last one ${money(140.0)}", state.paymentsLeftText)
    }

    @Test
    fun `interest lines appear only when the debt charges interest`() = runTest {
        val with = viewModel(FakeDebtActions()).state.value
        assertTrue(with.showsInterest)
        assertEquals(money(212.4), with.interestSoFarText)
        assertEquals(money(590.1), with.interestToComeText)
        assertEquals("7.5% a year", with.aprText)

        val without = viewModel(FakeDebtActions(), interestFreeDetail).state.value
        assertFalse(without.showsInterest)
        assertNull(without.interestSoFarText)
        assertNull(without.interestToComeText)
        assertNull(without.aprText)
    }

    @Test
    fun `a whole rate reads without decimals`() = runTest {
        val detail = DebtTestData.detail
        val whole = detail.copy(debt = detail.debt.copy(apr = 8.0))
        assertEquals("8% a year", viewModel(FakeDebtActions(), whole).state.value.aprText)
    }

    @Test
    fun `history reads in plain words`() = runTest {
        val rows = viewModel(FakeDebtActions()).state.value.historyRows

        assertEquals(listOf("Extra payment", "Payment", "Balance corrected"), rows.map { it.title })
        assertNull(rows[0].interestNote)
        assertEquals("Includes ${money(33.75)} interest", rows[1].interestNote)
        assertEquals("Set to ${money(6_500.0)}", rows[2].amountText)
        assertEquals("Balance ${money(6_240.0)}", rows[1].balanceAfterText)
    }

    @Test
    fun `stopped payments are recognised by nothing scheduled not by bill id`() = runTest {
        val car = DebtTestData.car
        // payment: null keeps billId; payment still set, nothing scheduled.
        val stopped = DebtDetail(
            car.debt,
            car.summary.copy(nextPaymentDate = null, payment = 320.0, frequency = "monthly", repeatInterval = 1),
            DebtProjection(paymentsLeft = 0, payoffDate = null, lastPaymentAmount = null, interestRemaining = 0.0, neverPaysOff = false, schedule = emptyList()),
            emptyList(),
        )
        assertNotNull(stopped.debt.billId)
        val state = viewModel(FakeDebtActions(), stopped).state.value

        assertTrue(state.paymentsStopped)
        assertFalse(state.hasPayment)
        assertFalse(state.canWhatIf)
        assertNull(state.paymentText)
        assertTrue(DebtFormat.progressLine(state.plan!!, "USD").contains("Payments stopped"))
        assertFalse(DebtTestData.car.paymentsStopped)
    }

    // endregion

    // region What if I pay more?

    @Test
    fun `slider snaps to steps of twenty five`() = runTest {
        val vm = viewModel(FakeDebtActions())

        vm.setExtra(37.0)
        assertEquals(25.0, vm.state.value.extra, 0.0)
        vm.setExtra(63.0)
        assertEquals(75.0, vm.state.value.extra, 0.0)
        vm.setExtra(-50.0)
        assertEquals(0.0, vm.state.value.extra, 0.0)
        vm.setExtra(1_000_000.0)
        assertEquals(vm.state.value.extraMax, vm.state.value.extra, 0.0)
        assertEquals(650.0, vm.state.value.extraMax, 0.0)
    }

    @Test
    fun `what if is debounced and sends payment plus extra`() = runTest {
        val service = FakeDebtActions()
        val vm = viewModel(service)

        vm.setExtra(25.0)
        vm.setExtra(50.0)
        vm.setExtra(75.0)
        advanceTimeBy(DEBT_WHAT_IF_DEBOUNCE_MS - 1)
        assertTrue("Nothing is asked while the slider is still moving", service.previewRequests.isEmpty())
        advanceUntilIdle()

        assertEquals("Only the last slider position is asked for", 1, service.previewRequests.size)
        val request = service.previewRequests.single()
        assertEquals(6_040.0, request.balance, 0.0)
        assertEquals(7.5, request.apr!!, 0.0)
        assertEquals("monthly", request.frequency)
        assertEquals(1, request.repeatInterval)
        assertEquals("2026-10-01", request.firstPaymentDate)
        assertEquals(395.0, request.payment!!, 0.0)
        assertNull(request.targetMonth)
        assertNotNull(vm.state.value.whatIf)
        assertFalse(vm.state.value.isWhatIfLoading)
    }

    @Test
    fun `a slow answer for an old slider position is dropped`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val service = FakeDebtActions().apply { previewGate = { gate.await() } }
        val vm = viewModel(service)
        vm.setExtra(25.0)
        val first = async { vm.runWhatIf() }
        advanceUntilIdle()
        assertTrue("The request for 25 is in flight", service.previewRequests.isNotEmpty())

        vm.setExtra(50.0) // the slider moves on while it is out
        gate.complete(Unit)
        first.await()

        assertNull("The old answer must not be shown for the new position", vm.state.value.whatIf)
        assertTrue(vm.state.value.isWhatIfLoading)
    }

    @Test
    fun `what if result compares with the current plan`() = runTest {
        val service = FakeDebtActions().apply {
            previewResult = Result.success(
                DebtPreviewResponse(payment = 370.0, paymentsCount = 15, payoffDate = "2027-12-01", lastPaymentAmount = 90.0, totalInterest = 400.0, neverPaysOff = false),
            )
        }
        val vm = viewModel(service)

        vm.setExtra(50.0)
        vm.runWhatIf()

        val state = vm.state.value
        val result = state.whatIfResult!!
        assertEquals(4, result.paymentsSooner) // 19 -> 15
        assertEquals(190.1, result.interestSaved, 0.0) // 590.1 -> 400
        assertEquals(DebtFormat.monthYear("2027-12-01"), result.doneBy)
        assertEquals("Pay ${money(370.0)} from now on", state.applyWhatIfTitle)
    }

    @Test
    fun `a preview failure shows plain language and no result`() = runTest {
        val service = FakeDebtActions().apply { previewResult = Result.failure(ApiError.Server(400, "ValidationError")) }
        val vm = viewModel(service)

        vm.setExtra(50.0)
        vm.runWhatIf()

        assertNull(vm.state.value.whatIfResult)
        assertEquals(DebtErrorMessage.INVALID_INPUT, vm.state.value.whatIfError)
        assertFalse(vm.state.value.isWhatIfLoading)
    }

    @Test
    fun `no extra means no preview and no result`() = runTest {
        val service = FakeDebtActions()
        val vm = viewModel(service)

        vm.setExtra(0.0)
        vm.runWhatIf()

        assertTrue(service.previewRequests.isEmpty())
        assertNull(vm.state.value.whatIfResult)
        assertNull(vm.state.value.applyWhatIfTitle)
    }

    @Test
    fun `applying the what if changes the payment and refreshes`() = runTest {
        val service = FakeDebtActions().apply { updateResult = Result.success(DebtTestData.detail) }
        val vm = viewModel(service)
        vm.setExtra(50.0)
        vm.runWhatIf()
        val callsBefore = service.detailCalls.size

        val succeeded = vm.applyWhatIf()

        assertTrue(succeeded)
        val call = service.updateCalls.single()
        assertEquals("debt-car", call.first)
        assertEquals(DebtPatch.Value(UpdateDebtPayment(amount = 370.0)), call.second.payment)
        assertNull(call.second.apr)
        assertEquals("Refreshes after the write", callsBefore + 1, service.detailCalls.size)
        assertEquals("The slider resets once the payment is real", 0.0, vm.state.value.extra, 0.0)
        assertNull(vm.state.value.whatIf)
    }

    // endregion

    // region Writes

    @Test
    fun `extra payment carries the debt and refreshes`() = runTest {
        val service = FakeDebtActions()
        val vm = viewModel(service)
        val callsBefore = service.detailCalls.size

        val succeeded = vm.addExtraPayment(150.0, today)

        assertTrue(succeeded)
        val call = service.extraPaymentCalls.single()
        assertEquals("debt-car", call.first.id)
        assertEquals(150.0, call.second, 0.0)
        assertEquals(today, call.third)
        assertEquals(callsBefore + 1, service.detailCalls.size)
    }

    @Test
    fun `a second tap while the extra payment is in flight sends nothing more`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val service = FakeDebtActions().apply { extraPaymentGate = { gate.await() } }
        val vm = viewModel(service)

        val first = async { vm.addExtraPayment(150.0, today) }
        advanceUntilIdle()
        assertTrue(vm.state.value.isWorking)
        val second = vm.addExtraPayment(150.0, today)
        gate.complete(Unit)

        assertFalse("The second tap is refused", second)
        assertTrue(first.await())
        assertEquals("Exactly one payment is recorded", 1, service.extraPaymentCalls.size)
        assertFalse(vm.state.value.isWorking)
    }

    @Test
    fun `a failed extra payment shows an error and is not resent`() = runTest {
        val service = FakeDebtActions().apply { extraPaymentResult = Result.failure(ApiError.Network(IOException("timeout"))) }
        val vm = viewModel(service)

        val first = vm.addExtraPayment(150.0, today)

        assertFalse(first)
        assertEquals(DebtErrorMessage.CANT_REACH, vm.state.value.error)
        assertEquals(1, service.extraPaymentCalls.size)
    }

    @Test
    fun `retrying the same extra payment keeps its key until success`() = runTest {
        val service = FakeDebtActions().apply { extraPaymentResult = Result.failure(ApiError.Network(IOException("timeout"))) }
        val vm = viewModel(service)
        assertFalse(vm.addExtraPayment(150.0, today))
        assertFalse(vm.addExtraPayment(150.0, today))
        val key = service.extraPaymentKeys.first()
        assertNotNull(key)
        assertEquals(key, service.extraPaymentKeys[1])
        service.extraPaymentResult = Result.success(Unit)
        assertTrue(vm.addExtraPayment(150.0, today))
        assertEquals(key, service.extraPaymentKeys[2])
        assertTrue(vm.addExtraPayment(150.0, today))
        assertTrue(key != service.extraPaymentKeys[3])
    }

    @Test
    fun `correct balance sends the date and balance`() = runTest {
        val service = FakeDebtActions().apply { adjustmentResult = Result.success(DebtTestData.detail) }
        val vm = viewModel(service)

        val succeeded = vm.correctBalance(5_900.0, today)

        assertTrue(succeeded)
        assertEquals(Triple("debt-car", today, 5_900.0), service.adjustmentCalls.single())
    }

    @Test
    fun `a future date error is shown in plain language`() = runTest {
        val service = FakeDebtActions().apply { adjustmentResult = Result.failure(ApiError.Server(400, "DateInFuture")) }
        val vm = viewModel(service)

        val succeeded = vm.correctBalance(5_900.0, LocalDate.of(2030, 1, 1))

        assertFalse(succeeded)
        assertEquals(DebtErrorMessage.FUTURE_DATE, vm.state.value.error)
    }

    @Test
    fun `offline write shows a clear message and sends nothing`() = runTest {
        val service = FakeDebtActions().apply { extraPaymentResult = Result.failure(ApiError.Network(IOException("Offline"))) }
        val vm = viewModel(service, connectivity = FakeConnectivity(online = false))

        vm.addExtraPayment(20.0, today)

        assertEquals(DebtErrorMessage.OFFLINE, vm.state.value.error)
    }

    @Test
    fun `delete marks the debt gone without refreshing`() = runTest {
        val service = FakeDebtActions()
        val vm = viewModel(service)
        val callsBefore = service.detailCalls.size

        val succeeded = vm.delete()

        assertTrue(succeeded)
        assertTrue(vm.state.value.wasDeleted)
        assertEquals(listOf("debt-car"), service.deleteCalls)
        assertEquals(callsBefore, service.detailCalls.size)
    }

    @Test
    fun `a missing debt shows not found`() = runTest {
        val service = FakeDebtActions().apply { detailResult = Result.failure(ApiError.Server(404, "NotFound")) }
        val vm = viewModel(service, detail = null, id = "gone")

        assertEquals(DebtErrorMessage.NOT_FOUND, vm.state.value.error)
        assertNull(vm.state.value.plan)
    }

    // endregion
}
