package com.spenditos.android.feature.export

import android.net.Uri
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.restore.AccountDataCounts
import com.spenditos.android.data.restore.RestoreActions
import com.spenditos.android.data.restore.RestoreMode
import com.spenditos.android.data.restore.RestoreSession
import com.spenditos.android.data.restore.RestoreUndo
import com.spenditos.android.data.restore.RestoreUndoExpiredException
import com.spenditos.android.data.restore.RestoreUndoFailedException
import com.spenditos.android.data.restore.RestoreUndoStatus
import com.spenditos.android.data.restore.RestoreErrorCode
import com.spenditos.android.data.restore.RestoreFailedException
import com.spenditos.android.data.restore.RestoreProgress
import com.spenditos.android.data.restore.RestoreProgressPhase
import com.spenditos.android.data.restore.RestoreSummary
import com.spenditos.android.data.restore.RestoreUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreViewModelTest {
    private class FakeRestore : RestoreActions {
        var updates: List<RestoreUpdate> = emptyList()
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        var refreshed = 0
        val restored = mutableListOf<Uri>()
        val modes = mutableListOf<RestoreMode>()
        var counts = AccountDataCounts()
        var summaryFailure: Throwable? = null
        var summaryCalls = 0
        var undoable: RestoreSession? = null
        var undoFailure: Throwable? = null
        val undone = mutableListOf<String>()

        override suspend fun dataSummary(): AccountDataCounts {
            summaryCalls++
            summaryFailure?.let { throw it }
            return counts
        }

        override suspend fun undoableRestore(): RestoreSession? = undoable

        override suspend fun undo(id: String) {
            undone += id
            undoFailure?.let { throw it }
        }

        override fun restore(source: Uri, mode: RestoreMode): Flow<RestoreUpdate> = flow {
            restored += source
            modes += mode
            updates.forEach { emit(it) }
            gate?.await()
            failure?.let { throw it }
        }

        override suspend fun refreshAfterRestore() { refreshed++ }
    }

    private val uri: Uri = Uri.parse("content://documents/backup.json")

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `pick then confirm then uploading checking restoring done refreshes the app`() = runTest {
        val summary = RestoreSummary(transactions = 7, incomeAndBills = 2, debts = 1, categories = 4, rules = 3)
        val fake = FakeRestore().apply {
            updates = listOf(
                RestoreUpdate.Uploading(1, 2), RestoreUpdate.Checking,
                RestoreUpdate.Restoring(RestoreProgress(RestoreProgressPhase.TRANSACTIONS, 3, 7)),
                RestoreUpdate.Finished(summary),
            )
        }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Confirming, vm.state.value.phase)
        assertTrue(fake.restored.isEmpty())
        vm.confirm()
        assertEquals(RestoreUiPhase.Uploading, vm.state.value.phase)
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Done, vm.state.value.phase)
        assertEquals(summary, vm.state.value.summary)
        assertEquals(1, fake.refreshed)
        assertEquals(listOf(uri), fake.restored)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `progress states carry the numbers`() = runTest {
        val fake = FakeRestore().apply {
            updates = listOf(RestoreUpdate.Uploading(1, 4), RestoreUpdate.Restoring(RestoreProgress(RestoreProgressPhase.DEBTS, 1, 2)))
            gate = CompletableDeferred()
        }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Restoring, vm.state.value.phase)
        assertEquals(1, vm.state.value.uploadedParts)
        assertEquals(4, vm.state.value.totalParts)
        assertEquals(RestoreProgress(RestoreProgressPhase.DEBTS, 1, 2), vm.state.value.progress)
        assertTrue(vm.state.value.isBusy)
    }

    @Test
    fun `backing out of the picker or the dialog resets`() = runTest {
        val vm = RestoreViewModel(FakeRestore())
        vm.filePicked(null)
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
        vm.filePicked(uri)
        advanceUntilIdle()
        vm.cancelConfirmation()
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
        assertNull(vm.state.value.pickedFile)
    }

    @Test
    fun `cancel while uploading stops the upload`() = runTest {
        val fake = FakeRestore().apply { updates = listOf(RestoreUpdate.Uploading(0, 3)); gate = CompletableDeferred() }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
        assertEquals(0, fake.refreshed)
    }

    private fun failWith(error: Throwable): RestoreUiState {
        var result = RestoreUiState()
        runTest {
            val vm = RestoreViewModel(FakeRestore().apply { failure = error })
            vm.filePicked(uri)
            advanceUntilIdle()
            vm.confirm()
            advanceUntilIdle()
            result = vm.state.value
        }
        return result
    }

    @Test
    fun `every error code maps to its own message`() {
        val messages = listOf(
            RestoreErrorCode.CHECKSUM_MISMATCH, RestoreErrorCode.INVALID_BACKUP_FILE, RestoreErrorCode.UNSUPPORTED_BACKUP_VERSION,
            RestoreErrorCode.ACCOUNT_NOT_EMPTY, RestoreErrorCode.RESTORE_FAILED, RestoreErrorCode.UNKNOWN,
        ).map { failWith(RestoreFailedException(it, "english server text")).also { s -> assertEquals(RestoreUiPhase.Failed, s.phase) }.error!! }
        assertEquals(messages.size, messages.toSet().size)
        assertTrue(messages.none { it.contains("english server text") })
        assertTrue(messages[4].contains("same file"))
    }

    @Test
    fun `http error codes map to messages`() {
        val codes = listOf("BackupTooLarge", "TooManyRestores", "RestoreInProgress", "RestoreExpired", "Whatever")
        val messages = codes.map { failWith(ApiError.Server(400, it)).error!! }
        assertEquals(codes.size, messages.toSet().size)
        assertTrue(failWith(ApiError.Network(IOException())).error!!.isNotBlank())
    }

    @Test
    fun `dismissing the error returns to idle`() = runTest {
        val vm = RestoreViewModel(FakeRestore().apply { failure = IllegalStateException() })
        vm.filePicked(uri)
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Failed, vm.state.value.phase)
        vm.dismissError()
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
    }

    private fun undoable(status: RestoreUndoStatus = RestoreUndoStatus.AVAILABLE) =
        RestoreSession(id = "r9", mode = RestoreMode.REPLACE, undo = RestoreUndo(status, "2026-11-18T10:00:00Z"))

    // region summary branching and REPLACE gating

    @Test
    fun `an empty account gets the plain confirmation and mode fresh`() = runTest {
        val fake = FakeRestore().apply { counts = AccountDataCounts(categories = 18, rules = 4) }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        assertEquals(RestoreUiPhase.CheckingAccount, vm.state.value.phase)
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Confirming, vm.state.value.phase)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf(RestoreMode.FRESH), fake.modes)
    }

    @Test
    fun `an account with data gets the replace confirmation with its counts`() = runTest {
        val fake = FakeRestore().apply { counts = AccountDataCounts(entries = 1240, incomeAndBills = 12, debts = 2, categories = 18, rules = 4) }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.ConfirmingReplace, vm.state.value.phase)
        assertEquals(1240, vm.state.value.counts?.entries)
        assertEquals(AccountDataCounts(1240, 12, 2, 18, 4), vm.state.value.counts)
    }

    @Test
    fun `any one of entries, bills or debts makes it a replace`() = runTest {
        for (counts in listOf(AccountDataCounts(entries = 1), AccountDataCounts(incomeAndBills = 1), AccountDataCounts(debts = 1))) {
            val vm = RestoreViewModel(FakeRestore().apply { this.counts = counts })
            vm.filePicked(uri)
            advanceUntilIdle()
            assertEquals(RestoreUiPhase.ConfirmingReplace, vm.state.value.phase)
        }
    }

    @Test
    fun `replace is gated on exactly the word REPLACE`() = runTest {
        val fake = FakeRestore().apply { counts = AccountDataCounts(entries = 5); updates = listOf(RestoreUpdate.Finished(RestoreSummary())) }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        assertFalse(vm.state.value.canReplace)
        for (wrong in listOf("", "replace", "REPLAC", "REPLACE ", " REPLACE", "Replace")) {
            vm.onReplaceTextChange(wrong)
            assertFalse(wrong, vm.state.value.canReplace)
            vm.confirm()
            advanceUntilIdle()
            assertEquals(RestoreUiPhase.ConfirmingReplace, vm.state.value.phase)
            assertTrue(fake.restored.isEmpty())
        }
        vm.onReplaceTextChange("REPLACE")
        assertTrue(vm.state.value.canReplace)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(listOf(RestoreMode.REPLACE), fake.modes)
        assertEquals(RestoreUiPhase.Done, vm.state.value.phase)
    }

    @Test
    fun `setup picks skip the summary and always use fresh`() = runTest {
        val fake = FakeRestore().apply { counts = AccountDataCounts(entries = 5); updates = listOf(RestoreUpdate.Finished(RestoreSummary())) }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri, freshOnly = true)
        assertEquals(RestoreUiPhase.Confirming, vm.state.value.phase)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(0, fake.summaryCalls)
        assertEquals(listOf(RestoreMode.FRESH), fake.modes)
        assertEquals(RestoreUiPhase.Done, vm.state.value.phase)
    }

    @Test
    fun `a failed summary shows an error and sends nothing`() = runTest {
        val fake = FakeRestore().apply { summaryFailure = ApiError.Network(IOException()) }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Failed, vm.state.value.phase)
        assertTrue(fake.restored.isEmpty())
    }

    // endregion

    // region undo

    @Test
    fun `loadUndo shows the newest undoable restore`() = runTest {
        val vm = RestoreViewModel(FakeRestore().apply { undoable = undoable() })
        vm.loadUndo()
        advanceUntilIdle()
        assertEquals("r9", vm.state.value.undoSession?.id)
    }

    @Test
    fun `a finished replace shows the undo row`() = runTest {
        val fake = FakeRestore().apply {
            counts = AccountDataCounts(entries = 5)
            updates = listOf(RestoreUpdate.Finished(RestoreSummary()))
            undoable = undoable()
        }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
        advanceUntilIdle()
        vm.onReplaceTextChange("REPLACE")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Done, vm.state.value.phase)
        assertEquals("r9", vm.state.value.undoSession?.id)
    }

    @Test
    fun `undo asks first then restores the data and refreshes`() = runTest {
        val fake = FakeRestore().apply { undoable = undoable() }
        val vm = RestoreViewModel(fake)
        vm.loadUndo()
        advanceUntilIdle()
        vm.requestUndo()
        assertEquals(UndoPhase.Confirming, vm.state.value.undoPhase)
        assertTrue(fake.undone.isEmpty())
        vm.confirmUndo()
        assertEquals(UndoPhase.Running, vm.state.value.undoPhase)
        advanceUntilIdle()
        assertEquals(listOf("r9"), fake.undone)
        assertEquals(1, fake.refreshed)
        assertEquals(UndoPhase.Idle, vm.state.value.undoPhase)
        assertNull(vm.state.value.undoSession)
        assertTrue(vm.state.value.undoDone)
    }

    @Test
    fun `a failed undo keeps the row so it can be tried again`() = runTest {
        val fake = FakeRestore().apply { undoable = undoable(); undoFailure = RestoreUndoFailedException() }
        val vm = RestoreViewModel(fake)
        vm.loadUndo()
        advanceUntilIdle()
        vm.requestUndo()
        vm.confirmUndo()
        advanceUntilIdle()
        assertEquals("r9", vm.state.value.undoSession?.id)
        assertEquals(UndoPhase.Idle, vm.state.value.undoPhase)
        assertTrue(vm.state.value.undoError!!.isNotBlank())
        assertEquals(0, fake.refreshed)
        // Try again.
        fake.undoFailure = null
        vm.dismissUndoError()
        vm.requestUndo()
        vm.confirmUndo()
        advanceUntilIdle()
        assertTrue(vm.state.value.undoDone)
        assertEquals(listOf("r9", "r9"), fake.undone)
    }

    @Test
    fun `409 UndoUnavailable drops the row and RestoreInProgress keeps it`() = runTest {
        val fake = FakeRestore().apply { undoable = undoable(); undoFailure = ApiError.Server(409, "UndoUnavailable") }
        val vm = RestoreViewModel(fake)
        vm.loadUndo(); advanceUntilIdle()
        vm.requestUndo(); vm.confirmUndo(); advanceUntilIdle()
        assertNull(vm.state.value.undoSession)
        val unavailable = vm.state.value.undoError!!

        fake.undoFailure = ApiError.Server(409, "RestoreInProgress")
        vm.dismissUndoError()
        vm.loadUndo(); advanceUntilIdle()
        vm.requestUndo(); vm.confirmUndo(); advanceUntilIdle()
        assertEquals("r9", vm.state.value.undoSession?.id)
        assertTrue(vm.state.value.undoError!! != unavailable)
    }

    @Test
    fun `an expired undo drops the row`() = runTest {
        val fake = FakeRestore().apply { undoable = undoable(); undoFailure = RestoreUndoExpiredException() }
        val vm = RestoreViewModel(fake)
        vm.loadUndo(); advanceUntilIdle()
        vm.requestUndo(); vm.confirmUndo(); advanceUntilIdle()
        assertNull(vm.state.value.undoSession)
    }

    // endregion
}
