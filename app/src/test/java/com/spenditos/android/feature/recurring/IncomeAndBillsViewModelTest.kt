package com.spenditos.android.feature.recurring

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.CreateRecurringTemplateRequest
import com.spenditos.android.data.model.GenerateTransactionsResult
import com.spenditos.android.data.model.RecurringTemplate
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.model.UpdateRecurringTemplateRequest
import com.spenditos.android.data.recurring.RecurringActions
import com.spenditos.android.data.recurring.RecurringCategorySource
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.YearMonth

class IncomeAndBillsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `screen loads categories and divides income and bills by category`() = runTest {
        val recurring = FakeRecurring()
        val vm = viewModel(recurring)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals("CAD", vm.state.value.currency)
        assertEquals(listOf("income-1", "inactive-income"), vm.state.value.incomeTemplates.map { it.id })
        assertEquals(listOf("bill-1"), vm.state.value.billTemplates.map { it.id })
        assertNull(vm.state.value.error)
    }

    @Test
    fun `initial load maps client and network errors`() = runTest {
        val clientRecurring = FakeRecurring().apply { failures["refresh"] = ApiError.Server(400, "bad") }
        val clientVm = viewModel(clientRecurring)
        advanceUntilIdle()
        assertFalse(clientVm.state.value.isLoading)
        assertNotNull(clientVm.state.value.error)
        assertFalse(clientVm.state.value.isOffline)

        val offlineRecurring = FakeRecurring().apply { failures["refresh"] = ApiError.Network(IOException("offline")) }
        val offlineVm = viewModel(offlineRecurring)
        advanceUntilIdle()
        assertTrue(offlineVm.state.value.isOffline)
        assertTrue(offlineVm.state.value.error!!.contains("offline"))
    }

    @Test
    fun `all recurring writes succeed through their actions`() = runTest {
        val recurring = FakeRecurring()
        val vm = viewModel(recurring)
        advanceUntilIdle()

        vm.create(createRequest())
        advanceUntilIdle()
        vm.update("income-1", updateRequest())
        advanceUntilIdle()
        vm.delete("bill-1")
        advanceUntilIdle()
        vm.setMainIncome("income-1")
        advanceUntilIdle()
        vm.setMainIncome(null)
        advanceUntilIdle()
        vm.generate(YearMonth.of(2026, 1))
        advanceUntilIdle()

        assertEquals(1, recurring.created)
        assertEquals(1, recurring.updated)
        assertEquals(1, recurring.deleted)
        assertEquals(listOf("income-1", null), recurring.mainIncomeSelections)
        assertEquals(listOf(YearMonth.of(2026, 1)), recurring.generatedMonths)
        assertFalse(vm.state.value.isWriting)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `each write shows client and network errors`() = runTest {
        val actions = listOf("create", "update", "delete", "main-income", "generate")
        for (action in actions) {
            for (failure in listOf(ApiError.Server(400, "bad"), ApiError.Network(IOException("offline")))) {
                val recurring = FakeRecurring()
                val vm = viewModel(recurring)
                advanceUntilIdle()
                recurring.failures[action] = failure
                invoke(action, vm)
                advanceUntilIdle()
                assertFalse("$action should finish", vm.state.value.isWriting)
                assertNotNull("$action should show an error", vm.state.value.error)
                assertEquals("$action network classification", failure is ApiError.Network, vm.state.value.isOffline)
            }
        }
    }

    @Test
    fun `main income can only point to an active income category`() = runTest {
        val recurring = FakeRecurring()
        val vm = viewModel(recurring)
        advanceUntilIdle()
        vm.setMainIncome("bill-1")
        vm.setMainIncome("inactive-income")
        advanceUntilIdle()
        assertTrue(recurring.mainIncomeSelections.isEmpty())
    }

    @Test
    fun `recurring form validates amount and semi-monthly day and treats cleared cap as never`() {
        val valid = RecurringDraft(description = "Fake item", amountText = "12.50", categoryId = "cat")
        assertTrue(valid.canSave)
        assertFalse(valid.copy(amountText = "0").canSave)
        assertFalse(valid.copy(amountText = "nope").canSave)

        val semiMonthly = valid.copy(frequency = "semi-monthly")
        assertFalse(semiMonthly.copy(secondOccurrenceDayText = "0").canSave)
        assertTrue(semiMonthly.copy(secondOccurrenceDayText = "1").canSave)
        assertTrue(semiMonthly.copy(secondOccurrenceDayText = "31").canSave)
        assertFalse(semiMonthly.copy(secondOccurrenceDayText = "32").canSave)

        assertEquals(3, valid.copy(occurrencesRemainingText = "3").occurrencesRemaining)
        assertEquals(12, valid.copy(occurrencesRemainingText = " 12 ").occurrencesRemaining)
        listOf("", "  ", "0", "-2", "abc").forEach { input ->
            assertNull(valid.copy(occurrencesRemainingText = input).occurrencesRemaining)
        }
    }

    @Test
    fun `recurring invalidations refresh the screen`() = runTest {
        val recurring = FakeRecurring()
        val bus = FinanceInvalidationBus()
        val vm = viewModel(recurring, bus)
        advanceUntilIdle()
        val initial = recurring.refreshCount
        bus.invalidate(com.spenditos.android.core.sync.Aggregate.TRANSACTION)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(initial, recurring.refreshCount)
        bus.invalidate(com.spenditos.android.core.sync.Aggregate.RECURRING)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(initial + 1, recurring.refreshCount)
    }

    private fun viewModel(recurring: FakeRecurring, bus: FinanceInvalidationBus = FinanceInvalidationBus()) = IncomeAndBillsViewModel(
        recurring,
        FakeCategories(),
        FakeSettings(),
        bus,
    )

    private fun invoke(action: String, vm: IncomeAndBillsViewModel) {
        when (action) {
            "create" -> vm.create(createRequest())
            "update" -> vm.update("income-1", updateRequest())
            "delete" -> vm.delete("bill-1")
            "main-income" -> vm.setMainIncome("income-1")
            "generate" -> vm.generate(YearMonth.of(2026, 1))
        }
    }

    private class FakeRecurring : RecurringActions {
        private val rows = MutableStateFlow(listOf(incomeTemplate(), billTemplate(), inactiveIncomeTemplate()))
        private val selected = MutableStateFlow<String?>(null)
        val failures = mutableMapOf<String, Throwable>()
        var refreshCount = 0
        var created = 0
        var updated = 0
        var deleted = 0
        val mainIncomeSelections = mutableListOf<String?>()
        val generatedMonths = mutableListOf<YearMonth>()

        override fun observe(): StateFlow<List<RecurringTemplate>> = rows.asStateFlow()
        override fun observeMainIncomeId(): StateFlow<String?> = selected.asStateFlow()
        override suspend fun refresh() { refreshCount++; fail("refresh") }
        override suspend fun create(request: CreateRecurringTemplateRequest): RecurringTemplate {
            fail("create")
            created++
            return incomeTemplate().also { rows.value = rows.value + it }
        }
        override suspend fun update(id: String, request: UpdateRecurringTemplateRequest): RecurringTemplate {
            fail("update")
            updated++
            return rows.value.first { it.id == id }
        }
        override suspend fun delete(id: String) { fail("delete"); deleted++; rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun setMainIncome(id: String?) { fail("main-income"); mainIncomeSelections += id; selected.value = id }
        override suspend fun generate(month: YearMonth): GenerateTransactionsResult {
            fail("generate")
            generatedMonths += month
            return GenerateTransactionsResult(generated = 1)
        }
        private fun fail(operation: String) { failures[operation]?.let { throw it } }
    }

    private class FakeCategories : RecurringCategorySource {
        private val categories = listOf(
            Category("income-cat", "user-1", "Income", "#00AA00", isIncome = true),
            Category("bill-cat", "user-1", "Bills", "#AA0000", isIncome = false),
            Category("inactive-cat", "user-1", "Other income", "#00AA00", isIncome = true),
        )
        override fun observe(): Flow<List<Category>> = flowOf(categories)
        override suspend fun refresh() = Unit
    }

    private class FakeSettings : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("CAD")
        override suspend fun refresh() = UserSettings("user-1", currency = "CAD")
        override suspend fun updateSettings(update: UserSettingsUpdate) = UserSettings("user-1", currency = "CAD")
        override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
    }

    companion object {
        private fun incomeTemplate() = RecurringTemplate("income-1", "user-1", "income-cat", "Fake salary", 1200.0, "fixed", "monthly", "2026-01-01")
        private fun billTemplate() = RecurringTemplate("bill-1", "user-1", "bill-cat", "Fake rent", 800.0, "fixed", "monthly", "2026-01-03")
        private fun inactiveIncomeTemplate() = RecurringTemplate("inactive-income", "user-1", "inactive-cat", "Inactive income", 20.0, "fixed", "monthly", "2026-01-03", isActive = false)
        private fun createRequest() = CreateRecurringTemplateRequest("income-cat", "Fake income", 10.0, frequency = "monthly", firstOccurrenceDate = "2026-01-01", occurrencesRemaining = JsonNull, secondOccurrenceDay = JsonNull)
        private fun updateRequest() = UpdateRecurringTemplateRequest(occurrencesRemaining = JsonNull)
    }
}
