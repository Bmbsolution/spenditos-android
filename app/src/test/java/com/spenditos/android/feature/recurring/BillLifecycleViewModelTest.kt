package com.spenditos.android.feature.recurring

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.BillHistorySummary
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.Transaction
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.recurring.RecurringCategorySource
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** The Pause / Resume / End / Start again actions of Income & bills (#403) and the month data behind the strip. */
class BillLifecycleViewModelTest {
    private val savedLocale: Locale = Locale.getDefault()
    private val today = LocalDate.of(2026, 10, 26)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(savedLocale)
    }

    private fun bill(id: String, first: String, active: Boolean = true, pausedAt: String? = null, remaining: Int? = null, name: String = id) =
        RecurringTemplate(
            id, "user-1", "bill-cat", name, 50.0, "fixed", "monthly", first,
            occurrencesRemaining = remaining, isActive = active, pausedAt = pausedAt,
        )

    private fun viewModel(recurring: FakeRecurring) = IncomeAndBillsViewModel(recurring, FakeCategories(), FakeSettings(), FinanceInvalidationBus())

    @Test
    fun `pause sends pause and offers an undo that resumes`() = runTest {
        val recurring = FakeRecurring(listOf(bill("gym", "2026-06-05", name = "Gym")))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.pause(recurring.rows.value.first())
        advanceUntilIdle()
        val event = vm.events.first() as IncomeAndBillsEvent.Lifecycle

        assertEquals(listOf("pause:gym"), recurring.calls)
        assertEquals("Gym paused", event.text)
        event.undo!!.invoke()
        advanceUntilIdle()
        assertEquals(listOf("pause:gym", "resume:gym"), recurring.calls)
    }

    @Test
    fun `resume offers an undo that pauses again`() = runTest {
        val paused = bill("gym", "2026-06-05", active = false, pausedAt = "2026-09-12T10:00:00Z", name = "Gym")
        val recurring = FakeRecurring(listOf(paused))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.resume(paused)
        advanceUntilIdle()
        val event = vm.events.first() as IncomeAndBillsEvent.Lifecycle

        assertEquals("Gym resumed", event.text)
        event.undo!!.invoke()
        advanceUntilIdle()
        assertEquals(listOf("resume:gym", "pause:gym"), recurring.calls)
    }

    @Test
    fun `end now ends and the undo resumes an active bill but re-pauses a paused one`() = runTest {
        val active = bill("bell", "2026-10-03", name = "Bell internet")
        val paused = bill("gym", "2026-06-05", active = false, pausedAt = "2026-09-12T10:00:00Z", name = "Gym")
        val recurring = FakeRecurring(listOf(active, paused))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.end(active, oneMore = false, today = today)
        advanceUntilIdle()
        val first = vm.events.first() as IncomeAndBillsEvent.Lifecycle
        assertEquals("Bell internet ended", first.text)
        first.undo!!.invoke()
        advanceUntilIdle()

        vm.end(paused, oneMore = false, today = today)
        advanceUntilIdle()
        val second = vm.events.first() as IncomeAndBillsEvent.Lifecycle
        second.undo!!.invoke()
        advanceUntilIdle()

        assertEquals(listOf("end:bell:false", "resume:bell", "end:gym:false", "pause:gym"), recurring.calls)
    }

    @Test
    fun `one more payment in a later month ends after it, and the undo restores the previous cap`() = runTest {
        val bell = bill("bell", "2026-10-03", name = "Bell internet", remaining = 5)
        val recurring = FakeRecurring(listOf(bell))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.end(bell, oneMore = true, today = today)
        advanceUntilIdle()
        val event = vm.events.first() as IncomeAndBillsEvent.Lifecycle

        assertEquals("Bell internet ends after Nov 3", event.text)
        assertEquals(listOf("endAfterNextPayment:bell"), recurring.calls)
        event.undo!!.invoke()
        advanceUntilIdle()
        assertEquals(JsonPrimitive(5), recurring.updates.single().second.occurrencesRemaining)
    }

    @Test
    fun `undoing an end-after-next without a previous cap sends an explicit null`() = runTest {
        val bell = bill("bell", "2026-10-03", name = "Bell internet")
        val recurring = FakeRecurring(listOf(bell))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.end(bell, oneMore = true, today = today)
        advanceUntilIdle()
        (vm.events.first() as IncomeAndBillsEvent.Lifecycle).undo!!.invoke()
        advanceUntilIdle()

        assertTrue(recurring.updates.single().second.occurrencesRemaining is JsonNull)
    }

    @Test
    fun `one more payment due this month ends now and keeps the upcoming entry`() = runTest {
        val bell = bill("bell", "2026-10-28", name = "Bell internet")
        val recurring = FakeRecurring(listOf(bell))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.end(bell, oneMore = true, today = today)
        advanceUntilIdle()
        val event = vm.events.first() as IncomeAndBillsEvent.Lifecycle

        assertEquals("Bell internet ended", event.text)
        assertEquals(listOf("end:bell:true"), recurring.calls)
    }

    @Test
    fun `start again sends the date and has no undo`() = runTest {
        val ended = RecurringTemplate("car", "user-1", "bill-cat", "Car", 50.0, "fixed", "monthly", "2026-01-05", isActive = false, endedAt = "2026-10-20T14:00:00.000Z")
        val recurring = FakeRecurring(listOf(ended))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.startAgain(ended, LocalDate.of(2026, 11, 5))
        advanceUntilIdle()
        val event = vm.events.first() as IncomeAndBillsEvent.Lifecycle

        assertEquals("Car started again", event.text)
        assertNull(event.undo)
        assertEquals(listOf("startAgain:car:2026-11-05"), recurring.calls)
    }

    @Test
    fun `a failed lifecycle write shows the error and sends no toast`() = runTest {
        val gym = bill("gym", "2026-06-05")
        val recurring = FakeRecurring(listOf(gym)).apply { failure = ApiError.Network(IOException("offline")) }
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.pause(gym)
        advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.isOffline)
        assertTrue(recurring.calls.isEmpty())
    }

    @Test
    fun `month entries load with the screen, drop to null when they fail, and feed the summary`() = runTest {
        val recurring = FakeRecurring(listOf(bill("rent", "2026-10-01")))
        recurring.entries = listOf(
            Transaction("e1", "user-1", "bill-cat", templateId = "rent", amount = 50.0, date = "2026-10-01"),
        )
        val vm = viewModel(recurring)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.monthEntries!!.size)
        assertEquals(1, vm.state.value.monthSummary(today)!!.paidCount)

        recurring.entriesFailure = ApiError.Network(IOException("offline"))
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.state.value.monthEntries)
        assertNull(vm.state.value.monthSummary(today)!!.paidCount)
    }

    @Test
    fun `ended history fills the archive rows and a failure just leaves it out`() = runTest {
        val ended = RecurringTemplate("car", "user-1", "bill-cat", "Car", 50.0, "fixed", "monthly", "2026-01-05", isActive = false, endedAt = "2026-10-20T14:00:00.000Z")
        val recurring = FakeRecurring(listOf(ended))
        recurring.history = mapOf("car" to BillHistorySummary(count = 12, total = 600.0))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.loadEndedHistory()
        advanceUntilIdle()
        assertEquals(12, vm.state.value.endedHistory["car"]!!.count)
        assertEquals(listOf(listOf("car")), recurring.historyRequests)

        recurring.historyFailure = ApiError.Server(500, "boom")
        val other = viewModel(recurring)
        advanceUntilIdle()
        other.loadEndedHistory()
        advanceUntilIdle()
        assertTrue(other.state.value.endedHistory.isEmpty())
        assertNull(other.state.value.error)
    }

    @Test
    fun `generate passes the selected template ids`() = runTest {
        val recurring = FakeRecurring(listOf(bill("rent", "2026-10-01")))
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.generate(YearMonth.of(2026, 10), listOf("rent"))
        advanceUntilIdle()

        assertEquals(listOf(YearMonth.of(2026, 10) to listOf("rent")), recurring.generates)
        val event = vm.events.first() as IncomeAndBillsEvent.Generated
        assertEquals(1, event.result.generated)
    }

    private class FakeRecurring(initial: List<RecurringTemplate>) : RecurringActions {
        val rows = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        val updates = mutableListOf<Pair<String, UpdateRecurringTemplateRequest>>()
        val generates = mutableListOf<Pair<YearMonth, List<String>?>>()
        val historyRequests = mutableListOf<List<String>>()
        var failure: Throwable? = null
        var entries: List<Transaction> = emptyList()
        var entriesFailure: Throwable? = null
        var history: Map<String, BillHistorySummary> = emptyMap()
        var historyFailure: Throwable? = null

        private fun template(id: String) = rows.value.first { it.id == id }
        private fun record(call: String, id: String): RecurringTemplate {
            failure?.let { throw it }
            calls += call
            return template(id)
        }

        override fun observe(): Flow<List<RecurringTemplate>> = rows
        override fun observeMainIncomeId(): Flow<String?> = flowOf(null)
        override suspend fun refresh() = Unit
        override suspend fun create(request: CreateRecurringTemplateRequest): RecurringTemplate = error("unused")
        override suspend fun update(id: String, request: UpdateRecurringTemplateRequest): RecurringTemplate {
            failure?.let { throw it }
            updates += id to request
            return template(id)
        }
        override suspend fun delete(id: String) = error("unused")
        override suspend fun setMainIncome(id: String?) = error("unused")
        override suspend fun generate(month: YearMonth, templateIds: List<String>?): GenerateTransactionsResult {
            generates += month to templateIds
            return GenerateTransactionsResult(generated = 1)
        }
        override suspend fun pause(id: String, now: Instant) = record("pause:$id", id)
        override suspend fun resume(id: String) = record("resume:$id", id)
        override suspend fun end(id: String, keepUpcoming: Boolean, now: Instant) = record("end:$id:$keepUpcoming", id)
        override suspend fun endAfterNextPayment(id: String) = record("endAfterNextPayment:$id", id)
        override suspend fun startAgain(id: String, nextDate: LocalDate) = record("startAgain:$id:$nextDate", id)
        override suspend fun history(ids: List<String>, includeEntries: Boolean): Map<String, BillHistorySummary> {
            historyRequests += ids
            historyFailure?.let { throw it }
            return history
        }
        override suspend fun monthEntries(month: YearMonth): List<Transaction> {
            entriesFailure?.let { throw it }
            return entries
        }
    }

    private class FakeCategories : RecurringCategorySource {
        override fun observe(): Flow<List<Category>> = flowOf(listOf(Category("bill-cat", "user-1", "Bills", "#AA0000", isIncome = false)))
        override suspend fun refresh() = Unit
    }

    private class FakeSettings : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("USD")
        override suspend fun refresh() = UserSettings("user-1", currency = "USD")
        override suspend fun updateSettings(update: UserSettingsUpdate) = UserSettings("user-1", currency = "USD")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
    }
}
