package com.spenditos.android.feature.statements

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.network.SpenditosJson
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.model.PremiumAccess
import com.spenditos.android.data.model.StatementAnalysisResult
import com.spenditos.android.data.model.StatementColumnMapping
import com.spenditos.android.data.model.StatementImportResult
import com.spenditos.android.data.model.StatementImportStats
import com.spenditos.android.data.model.StatementImportTransaction
import com.spenditos.android.data.model.StatementPreviewResult
import com.spenditos.android.data.model.StatementPreviewTransaction
import com.spenditos.android.data.model.UserSettings
import com.spenditos.android.data.model.UserSettingsUpdate
import com.spenditos.android.data.statements.StatementAnalysisDocument
import com.spenditos.android.data.statements.StatementImporter
import com.spenditos.android.data.user.UserSettingsActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatementImportViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `wizard proceeds from file selection through preview and confirmed import`() = runTest {
        val importer = FakeImporter()
        val handle = SavedStateHandle()
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()

        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        assertEquals(StatementImportStep.MAP_COLUMNS, viewModel.state.value.step)
        assertTrue(viewModel.state.value.mapping!!.isProcessable)
        assertNotNull(handle.get<String>(IDEMPOTENCY_KEY))

        viewModel.preview()
        advanceUntilIdle()
        assertEquals(StatementImportStep.PREVIEW, viewModel.state.value.step)

        viewModel.startImport()
        advanceUntilIdle()
        assertEquals(StatementImportStep.COMPLETE, viewModel.state.value.step)
        assertEquals(1, importer.importKeys.size)
        assertEquals(2, viewModel.state.value.result!!.stats!!.created)
    }

    @Test
    fun `analyze 4xx and network errors can retry the same file`() = runTest {
        val importer = FakeImporter().apply { analyzeFailure = ApiError.Server(400, "Bad input") }
        val handle = SavedStateHandle()
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        val uri = Uri.parse("content://fake/statement.csv")
        viewModel.onFileSelected(uri)
        advanceUntilIdle()
        val key = viewModel.state.value.step
        val idempotencyKey = requireNotNull(handle.get<String>(IDEMPOTENCY_KEY))
        assertEquals(StatementImportStep.ANALYZING, key)
        assertEquals(uri.toString(), viewModel.state.value.pickedUri)
        assertTrue(viewModel.state.value.canRetry)
        assertEquals("The server couldn't read this file. Check its columns and try again.", viewModel.state.value.error)

        importer.analyzeFailure = ApiError.Network(IllegalStateException("offline"))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isOffline)
        assertEquals(StatementImportStep.ANALYZING, viewModel.state.value.step)

        importer.analyzeFailure = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(StatementImportStep.MAP_COLUMNS, viewModel.state.value.step)
        assertEquals(idempotencyKey, handle.get<String>(IDEMPOTENCY_KEY))
        assertEquals(3, importer.analyzeCalls)
    }

    @Test
    fun `preview error retry stays on mapping step`() = runTest {
        val importer = FakeImporter().apply { previewFailure = ApiError.Server(400, "Invalid columns") }
        val viewModel = readyToMap(importer)
        viewModel.preview()
        advanceUntilIdle()
        assertEquals(StatementImportStep.MAP_COLUMNS, viewModel.state.value.step)
        assertEquals("The server couldn't read this file. Check its columns and try again.", viewModel.state.value.error)

        importer.previewFailure = ApiError.Network(IllegalStateException("offline"))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isOffline)
        assertEquals(StatementImportStep.MAP_COLUMNS, viewModel.state.value.step)

        importer.previewFailure = null
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(StatementImportStep.PREVIEW, viewModel.state.value.step)
        assertEquals(3, importer.previewCalls)
    }

    @Test
    fun `failed import retry keeps the same idempotency key and reports server errors`() = runTest {
        val importer = FakeImporter().apply { importFailure = ApiError.Server(400, "Invalid rows") }
        val handle = SavedStateHandle()
        val viewModel = readyToPreview(importer, handle)
        val key = requireNotNull(handle.get<String>(IDEMPOTENCY_KEY))

        viewModel.startImport()
        advanceUntilIdle()
        assertEquals(StatementImportStep.PREVIEW, viewModel.state.value.step)
        assertEquals("The server couldn't read this file. Check its columns and try again.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.importRequestSent)

        importer.importFailure = ApiError.Network(IllegalStateException("offline"))
        viewModel.retry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isOffline)
        importer.importFailure = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(StatementImportStep.COMPLETE, viewModel.state.value.step)
        assertEquals(listOf(key, key, key), importer.importKeys)
    }

    @Test
    fun `saved flow of another account is dropped`() = runTest {
        val importer = FakeImporter()
        val handle = SavedStateHandle()
        val first = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        first.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        assertNotNull(handle.get<String>(IDEMPOTENCY_KEY))

        importer.currentUserId = "user-2"
        val second = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        assertEquals(StatementImportStep.CHOOSE, second.state.value.step)
        assertEquals(null, second.state.value.pickedUri)
        assertEquals(null, handle.get<String>(IDEMPOTENCY_KEY))
    }

    @Test
    fun `a newly selected file receives a new idempotency key`() = runTest {
        val importer = FakeImporter()
        val handle = SavedStateHandle()
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        viewModel.onFileSelected(Uri.parse("content://fake/first.csv"))
        advanceUntilIdle()
        val firstKey = handle.get<String>(IDEMPOTENCY_KEY)
        viewModel.onFileSelected(Uri.parse("content://fake/second.xlsx"))
        advanceUntilIdle()
        val secondKey = handle.get<String>(IDEMPOTENCY_KEY)
        assertNotNull(firstKey)
        assertNotNull(secondKey)
        assertNotEquals(firstKey, secondKey)
    }

    @Test
    fun `inactive Pro access blocks every statement call`() = runTest {
        val importer = FakeImporter()
        val viewModel = viewModel(importer, FakeSettings(active = false))
        advanceUntilIdle()
        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        viewModel.preview()
        viewModel.startImport()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.premiumActive)
        assertEquals(0, importer.analyzeCalls + importer.previewCalls + importer.importKeys.size)
        assertEquals(StatementImportStep.CHOOSE, viewModel.state.value.step)
    }

    @Test
    fun `settings check failure shows offline state and blocks statement calls`() = runTest {
        val importer = FakeImporter()
        val viewModel = viewModel(importer, FakeSettings(failure = ApiError.Network(IllegalStateException("offline"))))
        advanceUntilIdle()
        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.settingsOffline)
        assertNotNull(viewModel.state.value.settingsError)
        assertEquals(0, importer.analyzeCalls)
    }

    @Test
    fun `settings check 4xx shows an error and blocks statement calls`() = runTest {
        val importer = FakeImporter()
        val viewModel = viewModel(importer, FakeSettings(failure = ApiError.Server(403, "Denied")))
        advanceUntilIdle()
        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.settingsOffline)
        assertEquals("Couldn't check your Pro access. Try again.", viewModel.state.value.settingsError)
        assertEquals(0, importer.analyzeCalls)
    }

    @Test
    fun `settings retry reloads access before opening the statement flow`() = runTest {
        val settings = FakeSettings(failure = ApiError.Network(IllegalStateException("offline")))
        val importer = FakeImporter()
        val viewModel = viewModel(importer, settings)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.settingsOffline)

        settings.failure = null
        viewModel.retrySettings()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.premiumActive)
        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        assertEquals(1, importer.analyzeCalls)
    }

    @Test
    fun `back stays blocked after import request is sent`() = runTest {
        val importer = FakeImporter().apply { pendingImport = CompletableDeferred() }
        val viewModel = readyToPreview(importer, SavedStateHandle())
        viewModel.startImport()
        runCurrent()
        assertEquals(StatementImportStep.IMPORTING, viewModel.state.value.step)
        assertTrue(viewModel.state.value.importRequestSent)

        viewModel.noteBackBlocked()
        assertTrue(viewModel.state.value.backBlockedHint)
        importer.pendingImport!!.complete(StatementImportResult("Imported fake rows.", 2, StatementImportStats(2, 0, 0)))
        advanceUntilIdle()
        assertEquals(StatementImportStep.COMPLETE, viewModel.state.value.step)
    }

    @Test
    fun `process death restores a preview using the same file and key`() = runTest {
        val mapping = StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Details", amountColumn = "Value", confidence = 0.8)
        val handle = SavedStateHandle(
            mapOf(
                "statement.user" to "user-1",
                STEP_KEY to StatementImportStep.PREVIEW.name,
                URI_KEY to "content://fake/statement.csv",
                IDEMPOTENCY_KEY to "fake-idempotency-key",
                MAPPING_KEY to SpenditosJson.encodeToString(StatementColumnMapping.serializer(), mapping),
            ),
        )
        val importer = FakeImporter()
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()

        assertEquals(StatementImportStep.PREVIEW, viewModel.state.value.step)
        assertEquals("Your statement needs to be prepared again. Try again to continue.", viewModel.state.value.error)
        assertEquals(0, importer.analyzeCalls)
        assertEquals(0, importer.previewCalls)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.error)
        assertEquals(1, importer.analyzeCalls)
        assertEquals(1, importer.previewCalls)
        assertEquals("fake-idempotency-key", handle.get<String>(IDEMPOTENCY_KEY))
        assertEquals(mapping, importer.lastMapping)
        assertTrue(importer.importKeys.isEmpty())
    }

    @Test
    fun `missing process restored file returns to chooser with a message`() = runTest {
        val handle = SavedStateHandle(
            mapOf("statement.user" to "user-1", STEP_KEY to StatementImportStep.PREVIEW.name, URI_KEY to "content://gone/file.csv", IDEMPOTENCY_KEY to "fake-key"),
        )
        val importer = FakeImporter().apply { readable = false }
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        assertEquals(StatementImportStep.CHOOSE, viewModel.state.value.step)
        assertEquals("That file is no longer available. Choose it again.", viewModel.state.value.error)
        assertEquals("content://gone/file.csv", handle.get<String>(URI_KEY))
        assertEquals("fake-key", handle.get<String>(IDEMPOTENCY_KEY))

        importer.readable = true
        viewModel.onFileSelected(Uri.parse("content://gone/file.csv"))
        advanceUntilIdle()
        assertEquals("fake-key", handle.get<String>(IDEMPOTENCY_KEY))
    }

    @Test
    fun `mapping choices enforce the amount xor debit and credit rule`() {
        val mapping = StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Description", debitColumn = "Out", creditColumn = "In")
        val amount = changeStatementMapping(mapping, StatementColumn.AMOUNT, "Amount")
        assertEquals("Amount", amount.amountColumn)
        assertEquals(null, amount.debitColumn)
        assertEquals(null, amount.creditColumn)
        val debit = changeStatementMapping(amount, StatementColumn.DEBIT, "Out")
        assertEquals(null, debit.amountColumn)
        assertEquals("Out", debit.debitColumn)
    }

    private suspend fun TestScope.readyToMap(importer: FakeImporter, handle: SavedStateHandle = SavedStateHandle()): StatementImportViewModel {
        val viewModel = viewModel(importer, FakeSettings(), handle)
        advanceUntilIdle()
        viewModel.onFileSelected(Uri.parse("content://fake/statement.csv"))
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun TestScope.readyToPreview(importer: FakeImporter, handle: SavedStateHandle): StatementImportViewModel {
        val viewModel = readyToMap(importer, handle)
        viewModel.preview()
        advanceUntilIdle()
        return viewModel
    }

    private fun viewModel(
        importer: FakeImporter,
        settings: FakeSettings = FakeSettings(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = StatementImportViewModel(importer, settings, handle, FinanceInvalidationBus())

    private class FakeSettings(
        private val active: Boolean = true,
        var failure: Throwable? = null,
    ) : UserSettingsActions {
        override fun currency(): Flow<String> = flowOf("USD")
        override suspend fun refresh(): UserSettings {
            failure?.let { throw it }
            return UserSettings("fake-user", premium = PremiumAccess(active = active))
        }
        override suspend fun updateSettings(update: UserSettingsUpdate): UserSettings = UserSettings("fake-user", premium = PremiumAccess(active = active))
        override suspend fun changePassword(currentPassword: String, newPassword: String) = Unit
    }

    private class FakeImporter : StatementImporter {
        override var currentUserId: String? = "user-1"
        var analyzeCalls = 0
        var readable = true
        var previewCalls = 0
        var analyzeFailure: Throwable? = null
        var previewFailure: Throwable? = null
        var importFailure: Throwable? = null
        val importKeys = mutableListOf<String>()
        var lastMapping: StatementColumnMapping? = null
        var pendingImport: CompletableDeferred<StatementImportResult>? = null

        override suspend fun isReadable(uri: Uri): Boolean = readable

        override suspend fun analyze(uri: Uri): StatementAnalysisDocument {
            analyzeCalls++
            analyzeFailure?.let { throw it }
            return StatementAnalysisDocument("fake.csv", "users/fake/statements/document-fake", analysis)
        }

        override suspend fun preview(filename: String, documentKey: String, mapping: StatementColumnMapping): StatementPreviewResult {
            previewCalls++
            lastMapping = mapping
            previewFailure?.let { throw it }
            return preview
        }

        override suspend fun importTransactions(transactions: List<StatementImportTransaction>, idempotencyKey: String): StatementImportResult {
            importKeys += idempotencyKey
            importFailure?.let { throw it }
            pendingImport?.let { return it.await() }
            return StatementImportResult("Imported 2 transactions.", 2, StatementImportStats(2, 0, 0))
        }

        private val analysis = StatementAnalysisResult(
            StatementColumnMapping(dateColumn = "Date", descriptionColumn = "Details", amountColumn = "Value", confidence = 0.8),
            headers = listOf("Date", "Details", "Value"),
        )
        private val preview = StatementPreviewResult(
            listOf(StatementPreviewTransaction(LocalDate.parse("2026-09-01"), "Fake Cafe", -4.5)),
        )
    }

    private companion object {
        const val STEP_KEY = "statement.step"
        const val URI_KEY = "statement.uri"
        const val IDEMPOTENCY_KEY = "statement.idempotencyKey"
        const val MAPPING_KEY = "statement.mapping"
    }
}
