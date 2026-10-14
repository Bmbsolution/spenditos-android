package com.spenditos.android.feature.export

import android.net.Uri
import com.spenditos.android.core.auth.SessionBinding
import com.spenditos.android.core.auth.SessionChangedException
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.export.ExportActions
import com.spenditos.android.data.export.ExportSaveFailedException
import com.spenditos.android.data.export.FullCopy
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FullCopyViewModelTest {
    private class FakeExport : ExportActions {
        var prepareFailure: Throwable? = null
        var saveFailure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        val saved = mutableListOf<Uri>()
        val discarded = mutableListOf<FullCopy>()

        override suspend fun exportCsv(start: java.time.LocalDate, end: java.time.LocalDate): File = error("not used")
        override suspend fun deleteExports() = Unit

        override suspend fun prepareFullCopy(): FullCopy {
            gate?.await()
            prepareFailure?.let { throw it }
            return FullCopy(File("full.json"), "spenditos-data-2026-10-14.json", SessionBinding("user-1", 1))
        }

        override suspend fun saveFullCopy(copy: FullCopy, target: Uri) {
            saveFailure?.let { throw it }
            saved += target
        }

        override fun discardFullCopy(copy: FullCopy) { discarded += copy }
    }

    private val uri: Uri = Uri.parse("content://documents/picked.json")

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `idle then preparing then picker then saving then saved`() = runTest {
        val export = FakeExport()
        val vm = FullCopyViewModel(export)
        assertEquals(FullCopyPhase.Idle, vm.state.value.phase)
        vm.download()
        assertEquals(FullCopyPhase.Preparing, vm.state.value.phase)
        advanceUntilIdle()
        assertEquals(FullCopyPhase.ChooseDestination, vm.state.value.phase)
        assertEquals("spenditos-data-2026-10-14.json", vm.state.value.suggestedName)
        vm.pickerRequested()
        assertTrue(vm.state.value.pickerRequested)
        vm.destinationPicked(uri)
        assertEquals(FullCopyPhase.Saving, vm.state.value.phase)
        advanceUntilIdle()
        assertEquals(FullCopyPhase.Saved, vm.state.value.phase)
        assertEquals(listOf(uri), export.saved)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `a 500 ExportFailed shows the server's message and allows a retry`() = runTest {
        val export = FakeExport()
        export.prepareFailure = ApiError.Server(500, "Could not build your data file. Please try again.")
        val vm = FullCopyViewModel(export)
        vm.download(); advanceUntilIdle()
        assertEquals(FullCopyPhase.Failed, vm.state.value.phase)
        assertEquals("Could not build your data file. Please try again.", vm.state.value.error)
        vm.dismissError()
        assertEquals(FullCopyPhase.Idle, vm.state.value.phase)
        export.prepareFailure = null
        vm.download(); advanceUntilIdle()
        assertEquals(FullCopyPhase.ChooseDestination, vm.state.value.phase)
    }

    @Test
    fun `offline expired session and unreadable destination have calm messages`() = runTest {
        val export = FakeExport()
        val vm = FullCopyViewModel(export)
        export.prepareFailure = ApiError.Network(IOException())
        vm.download(); advanceUntilIdle()
        assertEquals("You're offline. Check your connection and try again.", vm.state.value.error)
        export.prepareFailure = ApiError.Unauthorized
        vm.download(); advanceUntilIdle()
        assertEquals("Your session expired. Sign in and try again.", vm.state.value.error)
        export.prepareFailure = ApiError.Server(500, "Unknown error")
        vm.download(); advanceUntilIdle()
        assertEquals("Couldn't download your data. Try again.", vm.state.value.error)
        export.prepareFailure = null
        vm.download(); advanceUntilIdle()
        export.saveFailure = ExportSaveFailedException()
        vm.destinationPicked(uri); advanceUntilIdle()
        assertEquals(FullCopyPhase.Failed, vm.state.value.phase)
        assertEquals("Couldn't save the file there. Try another location.", vm.state.value.error)
    }

    @Test
    fun `an account switch before saving reports it and keeps nothing`() = runTest {
        val export = FakeExport()
        val vm = FullCopyViewModel(export)
        vm.download(); advanceUntilIdle()
        export.saveFailure = SessionChangedException()
        vm.destinationPicked(uri); advanceUntilIdle()
        assertEquals(FullCopyPhase.Failed, vm.state.value.phase)
        assertEquals("The signed-in account changed, so nothing was saved. Try again.", vm.state.value.error)
        assertTrue(export.saved.isEmpty())
    }

    @Test
    fun `backing out of the picker discards the temporary copy`() = runTest {
        val export = FakeExport()
        val vm = FullCopyViewModel(export)
        vm.download(); advanceUntilIdle()
        vm.destinationPicked(null)
        assertEquals(FullCopyPhase.Idle, vm.state.value.phase)
        assertEquals(1, export.discarded.size)
        assertNull(vm.state.value.suggestedName)
    }

    @Test
    fun `cancel stops a download that is still preparing`() = runTest {
        val export = FakeExport().apply { gate = CompletableDeferred() }
        val vm = FullCopyViewModel(export)
        vm.download()
        advanceUntilIdle()
        assertEquals(FullCopyPhase.Preparing, vm.state.value.phase)
        vm.cancel()
        advanceUntilIdle()
        assertEquals(FullCopyPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `a second tap while busy is ignored`() = runTest {
        val export = FakeExport().apply { gate = CompletableDeferred() }
        val vm = FullCopyViewModel(export)
        vm.download()
        vm.download()
        export.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(FullCopyPhase.ChooseDestination, vm.state.value.phase)
    }
}
