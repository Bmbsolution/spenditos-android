package com.spenditos.android.feature.debts

import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.Aggregate
import com.spenditos.android.core.sync.FinanceInvalidationBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** iOS DebtListViewModelTests ported with the same scenarios and numbers. */
class DebtListViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(service: FakeDebtActions, bus: FinanceInvalidationBus = FinanceInvalidationBus()) =
        DebtListViewModel(service, FakeCurrency(), bus, testClock())

    @Test
    fun `load asks for the devices local date`() = runTest {
        val service = FakeDebtActions().apply { listResult = Result.success(DebtTestData.list) }
        val vm = viewModel(service)
        advanceUntilIdle()

        assertEquals(listOf(TEST_TODAY), service.listAsOf)
        assertEquals(3, vm.state.value.debts.size)
        assertTrue(vm.state.value.hasLoaded)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `total owed counts only debts still open`() = runTest {
        val service = FakeDebtActions().apply { listResult = Result.success(DebtTestData.list) }
        val vm = viewModel(service)
        advanceUntilIdle()

        // Car 6,040 + laptop 900; the paid-off phone plan adds nothing.
        assertEquals(6_940.0, vm.state.value.totalOwed, 0.0)
        assertEquals("${DebtFormat.money(6_940.0, "USD")} owed", vm.state.value.headerSubtitle)
        assertFalse(vm.state.value.allPaidOff)
    }

    @Test
    fun `paid off debts sort last and keep server order within groups`() = runTest {
        val list = DebtTestData.list.toMutableList()
        // Put the paid-off debt first from the server.
        list.add(0, list.removeAt(2))
        val service = FakeDebtActions().apply { listResult = Result.success(list) }
        val vm = viewModel(service)
        advanceUntilIdle()

        assertEquals("debt-phone", vm.state.value.debts.first().id)
        assertEquals(listOf("debt-car", "debt-laptop", "debt-phone"), vm.state.value.sortedDebts.map { it.id })
    }

    @Test
    fun `all paid off header`() = runTest {
        val service = FakeDebtActions().apply { listResult = Result.success(DebtTestData.list.filter { it.summary.isPaidOff }) }
        val vm = viewModel(service)
        advanceUntilIdle()

        assertTrue(vm.state.value.allPaidOff)
        assertEquals("All paid off", vm.state.value.headerSubtitle)
        assertEquals(0.0, vm.state.value.totalOwed, 0.0)
    }

    @Test
    fun `no debts has no subtitle`() = runTest {
        val vm = viewModel(FakeDebtActions())
        advanceUntilIdle()

        assertFalse(vm.state.value.hasDebts)
        assertNull(vm.state.value.headerSubtitle)
        assertTrue(vm.state.value.hasLoaded)
    }

    @Test
    fun `a failed refresh keeps what is on screen and says why`() = runTest {
        val service = FakeDebtActions().apply { listResult = Result.success(DebtTestData.list) }
        val vm = viewModel(service)
        advanceUntilIdle()

        service.listResult = Result.failure(ApiError.Network(IOException("offline")))
        vm.refresh()

        assertEquals(3, vm.state.value.debts.size)
        assertNotNull(vm.state.value.error)
        assertEquals(DebtErrorMessage.CANT_REACH, vm.state.value.error)
    }

    @Test
    fun `a transaction or bill change reloads the list`() = runTest {
        val service = FakeDebtActions().apply { listResult = Result.success(DebtTestData.list) }
        val bus = FinanceInvalidationBus()
        viewModel(service, bus)
        advanceUntilIdle()
        assertEquals(1, service.listAsOf.size)

        bus.invalidate(Aggregate.CATEGORY)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(1, service.listAsOf.size)

        bus.invalidate(Aggregate.TRANSACTION)
        advanceTimeBy(300)
        advanceUntilIdle()
        assertEquals(2, service.listAsOf.size)
    }
}
