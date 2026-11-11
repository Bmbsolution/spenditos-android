package com.spenditos.android.feature.export

import android.net.Uri
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.restore.RestoreActions
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

        override fun restore(source: Uri): Flow<RestoreUpdate> = flow {
            restored += source
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
        vm.cancelConfirmation()
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
        assertNull(vm.state.value.pickedFile)
    }

    @Test
    fun `cancel while uploading stops the upload`() = runTest {
        val fake = FakeRestore().apply { updates = listOf(RestoreUpdate.Uploading(0, 3)); gate = CompletableDeferred() }
        val vm = RestoreViewModel(fake)
        vm.filePicked(uri)
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
        vm.confirm()
        advanceUntilIdle()
        assertEquals(RestoreUiPhase.Failed, vm.state.value.phase)
        vm.dismissError()
        assertEquals(RestoreUiPhase.Idle, vm.state.value.phase)
    }
}
