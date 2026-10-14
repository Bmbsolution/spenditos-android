package com.spenditos.android.feature.export

import com.spenditos.android.core.network.ApiError
import android.net.Uri
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.data.export.ExportActions
import com.spenditos.android.data.export.FullCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.File
import java.io.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private class FakeExport : ExportActions {
        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        var failure: Throwable? = null
        var deleted = 0
        override suspend fun exportCsv(start: LocalDate, end: LocalDate): File {
            calls += start to end
            failure?.let { throw it }
            return File("transactions.csv")
        }
        override suspend fun deleteExports() { deleted++ }
        override suspend fun prepareFullCopy(): FullCopy = error("not used")
        override suspend fun saveFullCopy(copy: FullCopy, target: Uri) = error("not used")
        override fun discardFullCopy(copy: FullCopy) = error("not used")
    }

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun clock(date: String) = Clock.fixed(Instant.parse("${date}T15:00:00Z"), ZoneId.of("UTC"))
    private fun viewModel(export: FakeExport, today: String = "2026-10-20") = ExportViewModel(export, clock(today))

    @Test
    fun `presets match iOS and last year crosses a year boundary`() = runTest {
        val vm = viewModel(FakeExport(), today = "2027-01-10")
        // iOS defaults to "This year".
        assertEquals(ExportRange.THIS_YEAR, vm.state.value.range)
        assertEquals(LocalDate.of(2027, 1, 1) to LocalDate.of(2027, 1, 10), vm.state.value.start to vm.state.value.end)
        vm.selectRange(ExportRange.THIS_MONTH)
        assertEquals(LocalDate.of(2027, 1, 1) to LocalDate.of(2027, 1, 10), vm.state.value.start to vm.state.value.end)
        vm.selectRange(ExportRange.LAST_YEAR)
        assertEquals(LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31), vm.state.value.start to vm.state.value.end)
        assertEquals(listOf("This month", "This year", "Last year", "Custom"), ExportRange.entries.map { it.label })
    }

    @Test
    fun `validation blocks backwards and oversized ranges`() = runTest {
        val vm = viewModel(FakeExport())
        vm.setStart(LocalDate.of(2026, 10, 25))
        assertEquals(ExportRange.CUSTOM, vm.state.value.range)
        assertNotNull(vm.state.value.validationError)
        assertFalse(vm.state.value.canExport)
        vm.setStart(LocalDate.of(2020, 1, 1))
        assertNotNull(vm.state.value.validationError)
        vm.setStart(LocalDate.of(2025, 10, 21))
        assertNull(vm.state.value.validationError)
    }

    @Test
    fun `export succeeds then hands the file over once`() = runTest {
        val export = FakeExport()
        val vm = viewModel(export)
        vm.export()
        assertTrue(vm.state.value.isExporting)
        advanceUntilIdle()
        assertEquals(1, export.calls.size)
        assertNotNull(vm.state.value.file)
        vm.shared()
        advanceUntilIdle()
        assertNull(vm.state.value.file)
        // The share target reads the file after startActivity returns: sharing must not delete it.
        assertEquals(0, export.deleted)
    }

    @Test
    fun `leaving the screen deletes the exported file`() = runTest {
        val export = FakeExport()
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel(export) as T
        })[ExportViewModel::class.java]
        vm.cleanupScope = CoroutineScope(Dispatchers.Unconfined)
        vm.export()
        advanceUntilIdle()
        vm.shared()
        advanceUntilIdle()
        assertEquals(0, export.deleted)
        store.clear()
        assertEquals(1, export.deleted)
    }

    @Test
    fun `errors map to calm messages and allow a retry`() = runTest {
        val export = FakeExport()
        val vm = viewModel(export)
        export.failure = ApiError.Network(IOException())
        vm.export(); advanceUntilIdle()
        assertEquals("You're offline. Check your connection and try again.", vm.state.value.error)
        export.failure = ApiError.Server(400, "Date range must not exceed 2 years.")
        vm.export(); advanceUntilIdle()
        assertEquals("Date range must not exceed 2 years.", vm.state.value.error)
        export.failure = ApiError.Server(503, "x")
        vm.export(); advanceUntilIdle()
        assertEquals("Downloads are temporarily unavailable. Try again.", vm.state.value.error)
        export.failure = SessionChangedException()
        vm.export(); advanceUntilIdle()
        assertEquals("The signed-in account changed, so nothing was downloaded. Try again.", vm.state.value.error)
        vm.dismissError()
        assertNull(vm.state.value.error)
        export.failure = null
        vm.export(); advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.file)
    }

    @Test
    fun `cancel stops the spinner`() = runTest {
        val vm = viewModel(FakeExport())
        vm.export()
        vm.cancel()
        advanceUntilIdle()
        assertFalse(vm.state.value.isExporting)
        assertNull(vm.state.value.file)
    }
}
