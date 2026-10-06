package com.spenditos.android.feature.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.capture.AudioRecorder
import com.spenditos.android.data.capture.CaptureActions
import com.spenditos.android.data.capture.CaptureContext
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.capture.CaptureFailure
import com.spenditos.android.data.capture.CaptureParseResult
import com.spenditos.android.data.capture.CaptureSaveItem
import com.spenditos.android.data.capture.CaptureSaveResult
import com.spenditos.android.data.capture.CaptureTranscript
import com.spenditos.android.data.model.CaptureAnalysisItem
import com.spenditos.android.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typed capture advances to review and matches category id and hint by type`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply {
            parsed = CaptureParseResult(
                items = listOf(
                    CaptureAnalysisItem(24.0, "CAD", "2026-10-03", "missing-id", "fOoD", null, "Lunch", null, "expense"),
                    CaptureAnalysisItem(80.0, "CAD", "2026-10-04", null, "side income", null, "Side job", null, "income"),
                    CaptureAnalysisItem(6.0, "CAD", null, "cat-other", null, null, "Other", null, "expense"),
                ),
                analysisId = "analysis-fake-typed",
                transcript = "Lunch and side job",
                parentAnalysisId = null,
                captureSessionId = null,
            )
        }
        val source = FakeCaptureDataSource(categories = emptyList())
        val vm = viewModel(actions, source, FakeRecorder())
        advanceUntilIdle()

        vm.onTextChange("Lunch and side job")
        vm.parseTypedText()
        advanceUntilIdle()
        source.setCategories(categories())
        advanceUntilIdle()
        val state = vm.state.first { it.step == CaptureStep.Review }
        assertEquals("typed", actions.parseCalls.single().inputType)
        assertNull(actions.parseCalls.single().parentAnalysisId)
        assertEquals("cat-food", state.items[0].categoryId)
        assertEquals("cat-income", state.items[1].categoryId)
        assertEquals("cat-other", state.items[2].categoryId)
        assertTrue(state.items.all { it.include })
        assertEquals(3, state.items.map { it.idempotencyKeyId }.distinct().size)
        assertTrue(state.canSave)
    }

    @Test
    fun `voice capture checks editable transcript then parses with parent lineage`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions()
        val recorder = FakeRecorder().apply { amplitude = 120 }
        val vm = viewModel(actions, FakeCaptureDataSource(categories()), recorder)
        advanceUntilIdle()

        vm.onRecordPermissionResult(true)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        vm.stopRecording()
        advanceUntilIdle()
        assertEquals(CaptureStep.CheckTranscript, vm.state.value.step)
        assertEquals("Paid 24 for lunch.", vm.state.value.transcript)
        assertFalse(recorder.lastFile!!.exists())

        vm.onTranscriptChange("Edited: paid 24 for lunch.")
        vm.parseTranscript()
        advanceUntilIdle()
        assertEquals(CaptureStep.Review, vm.state.value.step)
        assertEquals("voice", actions.parseCalls.single().inputType)
        assertEquals("analysis-fake-transcription", actions.parseCalls.single().parentAnalysisId)
        assertEquals("voice", vm.state.value.source)
    }

    @Test
    fun `save validation requires valid amount and description and can exclude rows`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply {
            parsed = CaptureParseResult(
                listOf(
                    CaptureAnalysisItem(null, "USD", null, null, null, null, "", null, "expense"),
                    CaptureAnalysisItem(10.0, "USD", null, null, null, null, "Coffee", null, "expense"),
                ), "analysis-fake-validation", null, null, null,
            )
        }
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder())
        advanceUntilIdle()
        vm.onTextChange("two things")
        vm.parseTypedText()
        advanceUntilIdle()
        val items = vm.state.value.items
        assertFalse(vm.state.value.canSave)

        vm.onAmountChange(items[0].idempotencyKeyId, "3.50")
        assertFalse(vm.state.value.canSave) // included item still has an empty description
        vm.onDescriptionChange(items[0].idempotencyKeyId, "Lunch")
        assertTrue(vm.state.value.canSave)
        vm.toggleInclude(items[1].idempotencyKeyId)
        assertTrue(vm.state.value.canSave)
        vm.toggleInclude(items[0].idempotencyKeyId)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `partial save leaves per item failure and a later 4xx keeps its key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply {
            parsed = CaptureParseResult(listOf(
                CaptureAnalysisItem(10.0, "USD", "2026-10-04", null, null, null, "Coffee", null, "expense"),
                CaptureAnalysisItem(20.0, "USD", "2026-10-04", null, null, null, "Lunch", null, "expense"),
                CaptureAnalysisItem(30.0, "USD", "2026-10-04", null, null, null, "Bus", null, "expense"),
            ), "analysis-fake-partial", null, null, null)
            saveBlocks.add { items, _, _ ->
                CaptureSaveResult(
                    savedIds = setOf(items[0].idempotencyKeyId, items[2].idempotencyKeyId),
                    failures = mapOf(items[1].idempotencyKeyId to "Category is unavailable"),
                    savedCount = 2,
                )
            }
            saveBlocks.add { _, _, _ -> throw ApiError.Server(400, "Invalid transaction") }
        }
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder())
        advanceUntilIdle()
        vm.onTextChange("three transactions")
        vm.parseTypedText()
        advanceUntilIdle()
        val failedKey = vm.state.value.items[1].idempotencyKeyId
        vm.save()
        advanceUntilIdle()
        assertEquals(CaptureStep.Review, vm.state.value.step)
        assertEquals(2, vm.state.value.savedCount)
        assertEquals(listOf(failedKey), vm.state.value.items.map { it.idempotencyKeyId })
        assertEquals("Category is unavailable", vm.state.value.items.single().failureReason)

        vm.save()
        advanceUntilIdle()
        assertEquals("Couldn't complete that. Try again.", vm.state.value.error)
        assertEquals(listOf(failedKey), actions.saveCalls.last().items.map { it.idempotencyKeyId })
    }

    @Test
    fun `analysis with no items reaches the empty review state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply { parsed = CaptureParseResult(emptyList(), "analysis-fake-empty", null, null, null) }
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder())
        advanceUntilIdle()
        vm.onTextChange("nothing specific")
        vm.parseTypedText()
        advanceUntilIdle()
        assertEquals(CaptureStep.Review, vm.state.value.step)
        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.canSave)
        vm.retryEmptyAnalysis()
        assertEquals(CaptureStep.Input, vm.state.value.step)
        assertEquals("nothing specific", vm.state.value.text)
    }

    @Test
    fun `network save retry and restored state keep the same idempotency keys`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply {
            parsed = CaptureParseResult(listOf(
                CaptureAnalysisItem(10.0, "USD", "2026-10-04", null, null, null, "Coffee", null, "expense"),
                CaptureAnalysisItem(20.0, "USD", "2026-10-04", null, null, null, "Lunch", null, "expense"),
            ), "analysis-fake-save", null, null, null)
            saveBlocks.add { _, _, _ -> throw ApiError.Network(IllegalStateException("offline")) }
            saveBlocks.add { items, _, _ -> CaptureSaveResult(items.map { it.idempotencyKeyId }.toSet(), emptyMap(), items.size) }
        }
        val handle = SavedStateHandle()
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder(), handle)
        advanceUntilIdle()
        vm.onTextChange("coffee and lunch")
        vm.parseTypedText()
        advanceUntilIdle()
        val originalKeys = vm.state.value.items.map { it.idempotencyKeyId }

        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertEquals("You're offline. Try again when you're connected.", vm.state.value.error)
        assertEquals(originalKeys, vm.state.value.items.map { it.idempotencyKeyId })
        val amountBeforeRetry = vm.state.value.items.first().amountText
        vm.onAmountChange(originalKeys.first(), "999.99")
        assertEquals(amountBeforeRetry, vm.state.value.items.first().amountText)

        val restoredHandle = SavedStateHandle(mapOf("capture.state" to requireNotNull(handle.get<String>("capture.state")), "capture.owner" to "user-1"))
        val restored = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder(), restoredHandle)
        advanceUntilIdle()
        assertEquals(originalKeys, restored.state.value.items.map { it.idempotencyKeyId })
        val event = async { restored.events.first() }
        restored.save()
        advanceUntilIdle()
        assertEquals(CaptureStep.Done, restored.state.value.step)
        assertEquals("Saved 2 transactions", event.await().let { (it as CaptureEvent.Saved).message })
        assertEquals(originalKeys, actions.saveCalls.map { call -> call.items.map { it.idempotencyKeyId } }.flatten().takeLast(2))
    }

    @Test
    fun `saved capture of another account is dropped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions()
        val handle = SavedStateHandle()
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), FakeRecorder(), handle)
        advanceUntilIdle()
        vm.onTextChange("coffee")
        vm.parseTypedText()
        advanceUntilIdle()
        assertTrue(vm.state.value.items.isNotEmpty())

        val other = FakeCaptureDataSource(emptyList()).apply { currentUserId = "user-2" }
        val restored = viewModel(actions, other, FakeRecorder(), handle)
        advanceUntilIdle()
        assertEquals(CaptureStep.Input, restored.state.value.step)
        assertTrue(restored.state.value.items.isEmpty())
    }

    @Test
    fun `no speech is rejected and recording file is always removed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply { transcribeBlock = { _, _, _ -> throw CaptureFailure.NoSpeechDetected } }
        val recorder = FakeRecorder().apply { amplitude = 0 }
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), recorder)
        advanceUntilIdle()
        vm.onRecordPermissionResult(true)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        vm.stopRecording()
        advanceUntilIdle()
        assertEquals("No speech was detected. Please speak clearly and try again.", vm.state.value.error)
        assertFalse(recorder.lastFile!!.exists())
        assertEquals(false, actions.lastTranscribeHadSpeech)
    }

    @Test
    fun `denied microphone permission explains settings path without starting recorder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakeRecorder()
        val vm = viewModel(FakeCaptureActions(), FakeCaptureDataSource(emptyList()), recorder)
        advanceUntilIdle()
        vm.onRecordPermissionResult(false)
        assertTrue(vm.state.value.permissionDenied)
        assertEquals(0, recorder.startCount)
        assertTrue(vm.state.value.error!!.contains("Settings"))
    }

    @Test
    fun `clearing the ViewModel stops recording and removes its file`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val recorder = FakeRecorder()
        val vm = viewModel(FakeCaptureActions(), FakeCaptureDataSource(emptyList()), recorder)
        val store = ViewModelStore().apply { put("capture", vm) }
        advanceUntilIdle()
        vm.onRecordPermissionResult(true)
        runCurrent()
        val recording = requireNotNull(recorder.lastFile)
        assertTrue(recording.exists())

        store.clear()

        assertFalse(recording.exists())
        assertTrue(recorder.startCount > 0)
    }

    @Test
    fun `voice quota 403 and typed network failures keep the correct retry state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val actions = FakeCaptureActions().apply {
            parseBlock = { _, _, _, _ -> throw ApiError.Network(IllegalStateException("offline")) }
        }
        val recorder = FakeRecorder().apply { amplitude = 120 }
        val vm = viewModel(actions, FakeCaptureDataSource(emptyList()), recorder)
        advanceUntilIdle()
        vm.onTextChange("text still parses")
        vm.parseTypedText()
        advanceUntilIdle()
        assertTrue(vm.state.value.offline)
        assertEquals("You're offline. Try again when you're connected.", vm.state.value.error)

        vm.onModeChange(CaptureMode.SPEAK)
        actions.transcribeBlock = { _, _, _ -> throw ApiError.Server(403, "QuotaExhausted") }
        vm.onRecordPermissionResult(true)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        vm.stopRecording()
        advanceUntilIdle()
        assertEquals("You've used this month's free voice captures.", vm.state.value.error)
        assertEquals(CaptureStep.Input, vm.state.value.step)
        assertFalse(vm.state.value.offline)
    }

    private fun viewModel(
        actions: FakeCaptureActions,
        source: FakeCaptureDataSource,
        recorder: FakeRecorder,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = CaptureViewModel(actions, source, recorder, handle, Dispatchers.Main)

    private fun categories() = listOf(
        Category("cat-food", "user-fake", "Food", "#000000", isIncome = false),
        Category("cat-other", "user-fake", "Other", "#000000", isIncome = false),
        Category("cat-income", "user-fake", "Side Income", "#000000", isIncome = true),
    )

    private class FakeCaptureDataSource(categories: List<Category>) : CaptureDataSource {
        private val categoryFlow = MutableStateFlow(categories)
        private val currencyFlow = MutableStateFlow("CAD")
        fun setCategories(categories: List<Category>) { categoryFlow.value = categories }
        override fun categories() = categoryFlow
        override fun currency() = currencyFlow
        override var currentUserId: String? = "user-1"
    }

    private class FakeCaptureActions : CaptureActions {
        var parsed = CaptureParseResult(
            listOf(CaptureAnalysisItem(12.5, "CAD", "2026-10-04", null, null, null, "Fake item", null, "expense")),
            "analysis-fake-default", null, null, null,
        )
        var parseBlock: suspend (String, CaptureContext, String, String?) -> CaptureParseResult = { _, _, _, _ -> parsed }
        var transcribeBlock: suspend (File, CaptureContext, Boolean) -> CaptureTranscript = { _, _, hasSpeech ->
            lastTranscribeHadSpeech = hasSpeech
            if (hasSpeech) CaptureTranscript("Paid 24 for lunch.", "analysis-fake-transcription", "analysis-fake-transcription") else throw CaptureFailure.NoSpeechDetected
        }
        var lastTranscribeHadSpeech: Boolean? = null
        val parseCalls = mutableListOf<ParseCall>()
        val saveCalls = mutableListOf<SaveCall>()
        val saveBlocks = ArrayDeque<suspend (List<CaptureSaveItem>, String, String?) -> CaptureSaveResult>()

        override suspend fun parseText(text: String, context: CaptureContext, inputType: String, parentAnalysisId: String?): CaptureParseResult {
            parseCalls += ParseCall(text, inputType, parentAnalysisId)
            return parseBlock(text, context, inputType, parentAnalysisId)
        }

        override suspend fun transcribeAudio(recording: File, context: CaptureContext, hasSpeech: Boolean): CaptureTranscript {
            lastTranscribeHadSpeech = hasSpeech
            return transcribeBlock(recording, context, hasSpeech)
        }

        override suspend fun save(items: List<CaptureSaveItem>, source: String, analysisId: String?): CaptureSaveResult {
            saveCalls += SaveCall(items, source, analysisId)
            val block: suspend (List<CaptureSaveItem>, String, String?) -> CaptureSaveResult = if (saveBlocks.isEmpty()) {
                { included, _, _ -> CaptureSaveResult(included.map { it.idempotencyKeyId }.toSet(), emptyMap(), included.size) }
            } else {
                saveBlocks.removeFirst()
            }
            return block(items, source, analysisId)
        }

        override suspend fun closeCaptureSession(analysisId: String, outcome: String) = Unit
    }

    private data class ParseCall(val text: String, val inputType: String, val parentAnalysisId: String?)
    private data class SaveCall(val items: List<CaptureSaveItem>, val source: String, val analysisId: String?)

    private class FakeRecorder : AudioRecorder {
        var amplitude = 100
        var startCount = 0
        var lastFile: File? = null
        override fun start(): File {
            startCount += 1
            return File.createTempFile("capture-vm-fake-", ".m4a").also { lastFile = it }
        }
        override fun maxAmplitude(): Int = amplitude
        override fun stop(): File? = lastFile
        override fun cancel() { lastFile?.delete() }
    }
}
