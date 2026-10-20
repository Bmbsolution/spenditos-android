package com.spenditos.android.feature.debts.add

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.ConnectivityStatus
import com.spenditos.android.data.debts.CreateDebtRequest
import com.spenditos.android.data.debts.Debt
import com.spenditos.android.data.debts.DebtActions
import com.spenditos.android.data.debts.DebtDetail
import com.spenditos.android.data.debts.DebtPreviewRequest
import com.spenditos.android.data.debts.DebtPreviewResponse
import com.spenditos.android.data.debts.DebtProjection
import com.spenditos.android.data.debts.DebtStatus
import com.spenditos.android.data.debts.DebtSummary
import com.spenditos.android.data.debts.DebtWithPlan
import com.spenditos.android.data.debts.UpdateDebtRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.user.UserSettingsActions
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** iOS `DebtFormViewModelTests`, same numbers. "Today" is Tuesday 2026-10-20. */
@OptIn(ExperimentalCoroutinesApi::class)
class DebtFormViewModelTest {
    private val today = LocalDate.of(2026, 10, 20)
    private lateinit var service: FakeDebtActions
    private lateinit var online: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        service = FakeDebtActions()
        online = MutableStateFlow(true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun makeViewModel(): DebtFormViewModel {
        val viewModel = DebtFormViewModel(
            actions = service,
            connectivity = object : ConnectivityStatus { override val isOnline: StateFlow<Boolean> = online.asStateFlow() },
            settings = FakeSettings(),
            todayProvider = { today },
            previewDebounceMs = 300,
        )
        viewModel.onFirstPaymentDateChange(LocalDate.of(2026, 11, 1))
        viewModel.onTargetMonthChange(YearMonth.of(2028, 6))
        return viewModel
    }

    private fun fill(viewModel: DebtFormViewModel, payment: String = "320") {
        viewModel.onNameChange("  Car loan ")
        viewModel.onOwedChange("10000")
        viewModel.onPaymentChange(payment)
    }

    private val DebtFormViewModel.ui get() = state.value

    // region Defaults and validation

    @Test
    fun `interest is off by default`() {
        val viewModel = makeViewModel()
        assertFalse(viewModel.ui.chargesInterest)
        assertEquals(DebtPlanMode.WHAT_I_PAY, viewModel.ui.mode)
        assertEquals("monthly", viewModel.ui.frequency)
        assertNull(viewModel.ui.effectiveApr)
    }

    @Test
    fun `an empty form is invalid and stays quiet until save is tapped`() = runTest {
        val viewModel = makeViewModel()

        assertFalse(viewModel.ui.isValid)
        assertNull(viewModel.ui.nameError)
        assertNull(viewModel.ui.owedError)
        assertNull(viewModel.ui.paymentError)

        viewModel.saveNow()

        assertEquals("Give this debt a name", viewModel.ui.nameError)
        assertEquals("Enter how much you owe", viewModel.ui.owedError)
        assertEquals("Enter how much you pay each time", viewModel.ui.paymentError)
    }

    @Test
    fun `bad amounts show inline at once`() {
        val viewModel = makeViewModel()
        viewModel.onOwedChange("0")
        viewModel.onPaymentChange("abc")
        viewModel.onNameChange("x".repeat(81))

        assertEquals("Enter an amount above zero", viewModel.ui.owedError)
        assertEquals("Enter an amount above zero", viewModel.ui.paymentError)
        assertEquals("Use 80 characters or fewer", viewModel.ui.nameError)
        assertFalse(viewModel.ui.isValid)
    }

    @Test
    fun `a filled form is valid`() {
        val viewModel = makeViewModel()
        fill(viewModel)
        assertTrue(viewModel.ui.isValid)
    }

    @Test
    fun `the interest rate is only checked when interest is on`() {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onAprChange("250")
        assertTrue("A leftover rate does nothing while the switch is off", viewModel.ui.isValid)

        viewModel.onChargesInterestChange(true)
        assertFalse(viewModel.ui.isValid)
        assertNotNull(viewModel.ui.aprError)

        viewModel.onAprChange("7,5")
        assertTrue(viewModel.ui.isValid)
        assertEquals(7.5, viewModel.ui.effectiveApr!!, 0.0)

        viewModel.onChargesInterestChange(false)
        assertNull(viewModel.ui.effectiveApr)
    }

    @Test
    fun `next payment cannot be in the past`() {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onFirstPaymentDateChange(LocalDate.of(2026, 10, 1))

        assertNotNull(viewModel.ui.firstPaymentError)
        assertFalse(viewModel.ui.isValid)
    }

    @Test
    fun `the target month must not be before the next payment`() {
        val viewModel = makeViewModel()
        fill(viewModel, payment = "")
        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)
        viewModel.onTargetMonthChange(YearMonth.of(2026, 10))

        assertNotNull(viewModel.ui.targetError)
        assertFalse(viewModel.ui.isValid)

        viewModel.onTargetMonthChange(YearMonth.of(2026, 11))
        assertNull(viewModel.ui.targetError)
        assertTrue(viewModel.ui.isValid)
    }

    @Test
    fun `amounts accept a decimal comma and grouping like iOS`() {
        assertEquals(7.5, DebtAmountParser.decimal("7,5")!!, 0.0)
        assertEquals(1234.56, DebtAmountParser.decimal("1.234,56")!!, 0.0)
        assertEquals(1234.56, DebtAmountParser.decimal("1,234.56")!!, 0.0)
        assertEquals(10000.0, DebtAmountParser.decimal(" 10000 ")!!, 0.0)
        assertNull(DebtAmountParser.decimal("abc"))
        assertNull(DebtAmountParser.decimal("1e5"))
        assertNull(DebtAmountParser.decimal(""))
    }

    // endregion

    // region Mode switch and the live plan

    @Test
    fun `preview request for what I pay`() {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onChargesInterestChange(true)
        viewModel.onAprChange("7.5")

        val request = viewModel.ui.previewRequest!!
        assertEquals(10_000.0, request.balance, 0.0)
        assertEquals(7.5, request.apr!!, 0.0)
        assertEquals(320.0, request.payment!!, 0.0)
        assertNull(request.targetMonth)
        assertEquals("2026-11-01", request.firstPaymentDate)
        assertEquals("monthly", request.frequency)
    }

    @Test
    fun `switching to when I'm done asks for a target month instead of a payment`() {
        val viewModel = makeViewModel()
        fill(viewModel)

        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)

        val request = viewModel.ui.previewRequest!!
        assertEquals("2028-06", request.targetMonth)
        assertNull(request.payment)
        assertNull("No payment is needed in this mode", viewModel.ui.paymentError)
    }

    @Test
    fun `switching back keeps the computed payment when nothing was typed`() = runTest {
        service.previewResult = Result.success(preview(payment = 447.0, count = 20, last = 180.0))
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")
        viewModel.onOwedChange("8500")
        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)
        viewModel.runPreview()

        viewModel.setMode(DebtPlanMode.WHAT_I_PAY)

        assertEquals("447", viewModel.ui.paymentText)
    }

    @Test
    fun `a plan for older inputs is never shown`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.runPreview()
        assertNotNull(viewModel.ui.preview)

        viewModel.onPaymentChange("400")

        assertNull(viewModel.ui.preview)
        assertNull(viewModel.ui.previewHeadline)
    }

    @Test
    fun `typing is debounced into one preview request`() = runTest {
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")
        viewModel.onOwedChange("1000")
        for (text in listOf("1", "12", "125")) viewModel.onPaymentChange(text)
        assertTrue(viewModel.ui.isPreviewing)

        advanceTimeBy(299)
        runCurrent()
        assertTrue("Still waiting for a pause in typing", service.previewRequests.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, service.previewRequests.size)
        assertEquals(125.0, service.previewRequests.first().payment!!, 0.0)
        assertNotNull(viewModel.ui.preview)
        assertFalse(viewModel.ui.isPreviewing)
    }

    @Test
    fun `preview text for what I pay matches the spec example`() = runTest {
        service.previewResult = Result.success(preview(payment = 320.0, count = 20, last = 180.0, interest = 612.0))
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onChargesInterestChange(true)
        viewModel.onAprChange("7.5")
        viewModel.runPreview()

        val done = DebtAddText.monthYear("2028-06-01")!!
        assertEquals(
            "Paid off by $done · 20 payments, last one ${DebtAddText.money(180.0, "USD")} · interest ≈ ${DebtAddText.money(612.0, "USD")}",
            viewModel.ui.previewHeadline,
        )
        assertNull(viewModel.ui.previewDetail)
    }

    @Test
    fun `no interest part when interest is off`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.runPreview()

        assertFalse(viewModel.ui.previewHeadline!!.contains("interest"))
    }

    @Test
    fun `preview text for when I'm done`() = runTest {
        service.previewResult = Result.success(preview(payment = 447.0, count = 20, last = 180.0))
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")
        viewModel.onOwedChange("8500")
        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)
        viewModel.runPreview()

        val done = DebtAddText.monthYear("2028-06-01")!!
        assertEquals("Pay ${DebtAddText.money(447.0, "USD")} / month · done by $done", viewModel.ui.previewHeadline)
        assertEquals("20 payments, last one ${DebtAddText.money(180.0, "USD")}", viewModel.ui.previewDetail)
    }

    @Test
    fun `never paid off asks for more`() = runTest {
        service.previewResult = Result.success(
            DebtPreviewResponse(payment = 20.0, paymentsCount = 0, payoffDate = null, lastPaymentAmount = null, totalInterest = 0.0, neverPaysOff = true),
        )
        val viewModel = makeViewModel()
        fill(viewModel, payment = "20")
        viewModel.onChargesInterestChange(true)
        viewModel.onAprChange("30")
        viewModel.runPreview()

        assertEquals("Never paid off — pay more each time", viewModel.ui.previewHeadline)
    }

    @Test
    fun `target too soon shows plain language`() = runTest {
        service.previewResult = Result.failure(ApiError.Server(400, "TARGET_TOO_SOON"))
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")
        viewModel.onOwedChange("800")
        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)
        viewModel.runPreview()

        assertEquals(DebtAddText.TARGET_TOO_SOON, viewModel.ui.previewError)
        assertNull(viewModel.ui.preview)
        assertFalse(viewModel.ui.isPreviewing)
    }

    // endregion

    // region Save

    @Test
    fun `save payload for what I pay`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onChargesInterestChange(true)
        viewModel.onAprChange("7.5")

        val detail = viewModel.saveNow()

        assertNotNull(detail)
        val (request, key) = service.createCalls.first()
        assertEquals("Trimmed", "Car loan", request.name)
        assertEquals(10_000.0, request.startingBalance, 0.0)
        assertEquals("2026-10-20", request.startDate)
        assertEquals(7.5, request.apr!!, 0.0)
        assertNull("The server files it under Debt Payments", request.categoryId)
        assertEquals(320.0, request.payment!!.amount, 0.0)
        assertEquals("monthly", request.payment.frequency)
        assertEquals("2026-11-01", request.payment.firstPaymentDate)
        assertNull(request.payment.secondOccurrenceDay)
        assertTrue(key.isNotEmpty())
    }

    @Test
    fun `when I'm done saves the computed payment`() = runTest {
        service.previewResult = Result.success(preview(payment = 447.0, count = 20, last = 180.0))
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")
        viewModel.onOwedChange("8500")
        viewModel.setMode(DebtPlanMode.WHEN_IM_DONE)

        // Save works the payment out itself when the plan has not arrived yet.
        val detail = viewModel.saveNow()

        assertNotNull(detail)
        assertEquals(447.0, service.createCalls.first().first.payment!!.amount, 0.0)
        assertNull(service.createCalls.first().first.apr)
    }

    @Test
    fun `twice a month sends the other day`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)
        viewModel.onFrequencyChange("semi-monthly")
        viewModel.onFirstPaymentDateChange(LocalDate.of(2026, 11, 3))

        viewModel.saveNow()

        assertEquals(18, service.createCalls.first().first.payment!!.secondOccurrenceDay)
    }

    @Test
    fun `a retry of the same attempt reuses the key and a changed debt gets a new one`() = runTest {
        service.createResult = Result.failure(ApiError.Network(IOException("timed out")))
        val viewModel = makeViewModel()
        fill(viewModel)

        assertNull(viewModel.saveNow())
        assertNotNull(viewModel.ui.error)
        viewModel.saveNow()
        viewModel.onPaymentChange("350")
        viewModel.saveNow()

        val keys = service.createCalls.map { it.second }
        assertEquals(3, keys.size)
        assertEquals("Same attempt, same key", keys[0], keys[1])
        assertNotEquals("A different debt is a different attempt", keys[1], keys[2])
    }

    @Test
    fun `a new key is used after a success`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)

        viewModel.saveNow()
        viewModel.saveNow()

        val keys = service.createCalls.map { it.second }
        assertEquals(2, keys.size)
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun `saving treats an entries-not-synced answer as success`() = runTest {
        // The repository returns the detail for a 207, so the form just sees success.
        val viewModel = makeViewModel()
        fill(viewModel)

        val detail = viewModel.saveNow()

        assertEquals("debt-car", detail?.id)
        assertNull(viewModel.ui.error)
        assertFalse(viewModel.ui.isSaving)
    }

    @Test
    fun `save emits the created debt id`() = runTest {
        val viewModel = makeViewModel()
        fill(viewModel)

        viewModel.save()
        val event = viewModel.events.first()

        assertEquals(DebtFormEvent.Created("debt-car"), event)
    }

    @Test
    fun `offline save shows a clear message`() = runTest {
        online.value = false
        service.createResult = Result.failure(ApiError.Network(IOException("Offline")))
        val viewModel = makeViewModel()
        fill(viewModel)

        val detail = viewModel.saveNow()

        assertNull(detail)
        assertEquals(DebtAddText.OFFLINE, viewModel.ui.error)
    }

    @Test
    fun `a server rejection shows plain language`() = runTest {
        service.createResult = Result.failure(ApiError.Server(400, "CategoryNotFound"))
        val viewModel = makeViewModel()
        fill(viewModel)

        viewModel.saveNow()

        assertEquals(
            "The category for this debt isn't available anymore. Pick another one and try again.",
            viewModel.ui.error,
        )
    }

    @Test
    fun `nothing is sent while the form is invalid`() = runTest {
        val viewModel = makeViewModel()
        viewModel.onNameChange("Loan")

        val detail = viewModel.saveNow()

        assertNull(detail)
        assertTrue(service.createCalls.isEmpty())
    }

    // endregion

    private fun preview(payment: Double, count: Int, last: Double, interest: Double = 0.0) = DebtPreviewResponse(
        payment = payment, paymentsCount = count, payoffDate = "2028-06-01",
        lastPaymentAmount = last, totalInterest = interest, neverPaysOff = false,
    )
}

private class FakeSettings : UserSettingsActions {
    override fun currency() = flowOf("USD")
    override suspend fun refresh() = UserSettings("fake-user", currency = "USD")
    override suspend fun updateSettings(update: UserSettingsUpdate) = UserSettings("fake-user", currency = "USD")
    override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
}

/** Fake [DebtActions]: records create and preview calls, answers with the configured results. */
internal class FakeDebtActions : DebtActions {
    val createCalls = mutableListOf<Pair<CreateDebtRequest, String>>()
    val previewRequests = mutableListOf<DebtPreviewRequest>()
    var createResult: Result<DebtDetail> = Result.success(DebtTestData.detail)
    var previewResult: Result<DebtPreviewResponse> = Result.success(
        DebtPreviewResponse(
            payment = 320.0, paymentsCount = 32, payoffDate = "2028-06-01",
            lastPaymentAmount = 240.0, totalInterest = 0.0, neverPaysOff = false,
        ),
    )

    override suspend fun create(request: CreateDebtRequest, idempotencyKey: String): DebtDetail {
        createCalls += request to idempotencyKey
        return createResult.getOrThrow()
    }

    override suspend fun preview(request: DebtPreviewRequest): DebtPreviewResponse {
        previewRequests += request
        return previewResult.getOrThrow()
    }

    override suspend fun list(asOf: LocalDate): List<DebtWithPlan> = error("not used")
    override suspend fun detail(id: String, asOf: LocalDate): DebtDetail = error("not used")
    override suspend fun update(id: String, request: UpdateDebtRequest): DebtDetail = error("not used")
    override suspend fun delete(id: String) = error("not used")
    override suspend fun addAdjustment(id: String, date: LocalDate, balance: Double): DebtDetail = error("not used")
}

internal object DebtTestData {
    val detail = DebtDetail(
        debt = Debt(
            id = "debt-car", userId = "fake-user", name = "Car loan", startingBalance = 10_000.0, startDate = "2026-10-20",
            billId = "bill-car", categoryId = "cat-debt-payments", createdAt = "2026-10-20T12:00:00.000Z", updatedAt = "2026-10-20T12:00:00.000Z",
        ),
        summary = DebtSummary(status = DebtStatus.ACTIVE, balance = 10_000.0, paidTotal = 0.0, interestTotal = 0.0, progress = 0.0),
        projection = DebtProjection(
            paymentsLeft = 32, payoffDate = "2029-06-01", lastPaymentAmount = 240.0,
            interestRemaining = 0.0, neverPaysOff = false, schedule = emptyList(),
        ),
        history = emptyList(),
    )
}
