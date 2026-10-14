package com.spenditos.android.feature.receipt

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.core.sync.FinanceInvalidationBus
import com.spenditos.android.data.capture.CaptureDataSource
import com.spenditos.android.data.model.Category
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanOutcome
import com.spenditos.android.data.model.ScanProgress
import com.spenditos.android.data.model.ScanSummary
import com.spenditos.android.data.realtime.ForegroundMonitor
import com.spenditos.android.data.realtime.RealtimeEvents
import com.spenditos.android.data.realtime.ReconnectSignals
import com.spenditos.android.data.scan.ScanCoordinator
import com.spenditos.android.data.scan.ScanCreditsProvider
import com.spenditos.android.data.scan.ScanFailure
import com.spenditos.android.data.scan.ScanInputOrigin
import com.spenditos.android.data.scan.ScanSource
import com.spenditos.android.data.scan.ScanSubmission
import com.spenditos.android.testing.FakeScanActions
import com.spenditos.android.testing.FakeScanInputFactory
import com.spenditos.android.testing.FakeScanSession
import com.spenditos.android.testing.FakeScanTime
import com.spenditos.android.testing.InMemoryScanStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReceiptScanViewModelTest {
    private lateinit var scans: FakeScanActions
    private lateinit var processor: FakeImageProcessor
    private lateinit var handle: SavedStateHandle
    private lateinit var time: FakeScanTime
    private lateinit var coordinator: ScanCoordinator
    private lateinit var viewModel: ReceiptScanViewModel
    private var remainingCredits: Int? = null

    private val dataSource = object : CaptureDataSource {
        override fun categories() = flowOf(emptyList<Category>())
        override fun currency() = flowOf("CAD")
        override val currentUserId = "user-a"
    }

    private fun summary(done: Int = 0, total: Int = 0, label: String = "") =
        ScanSummary(scanId = "scn_1", status = "processing", progress = ScanProgress(done, total), sourceLabel = label)

    private fun detail(kind: String, vararg items: ScanItem) =
        ScanDetail(ScanSummary("scn_1", "ready", kind = kind, itemCount = items.size), items.toList())

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        scans = FakeScanActions()
        processor = FakeImageProcessor()
        handle = SavedStateHandle()
        time = FakeScanTime()
    }

    @After
    fun tearDown() {
        processor.createdFiles.forEach { it.delete() }
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): ReceiptScanViewModel {
        coordinator = ScanCoordinator(
            scans = scans,
            store = InMemoryScanStore(),
            session = FakeScanSession(),
            events = RealtimeEvents(FinanceInvalidationBus()),
            reconnects = object : ReconnectSignals {
                override val reconnects: SharedFlow<Unit> = MutableSharedFlow()
            },
            foreground = object : ForegroundMonitor {
                override val isForeground: StateFlow<Boolean> = MutableStateFlow(false)
            },
            time = time,
            scope = backgroundScope,
        )
        viewModel = ReceiptScanViewModel(
            scans = scans,
            coordinator = coordinator,
            inputs = FakeScanInputFactory(),
            imageProcessor = processor,
            credits = ScanCreditsProvider { remainingCredits },
            dataSource = dataSource,
            savedStateHandle = handle,
            ioDispatcher = Dispatchers.Main, // same test scheduler
        )
        return viewModel
    }

    private fun photos(vararg names: String) = names.map { Uri.parse("content://media/$it") }

    private fun clearViewModel() {
        val method = ViewModel::class.java.getDeclaredMethod("clear\$lifecycle_viewmodel")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    // region Library photos and files

    @Test
    fun `library photos are read by the scan engine in the order picked and a receipt opens the add form`() = runTest {
        val vm = newViewModel()
        scans.submitResult = { ScanSubmission("scn_1", 2, "2 screenshots") }
        scans.waitResult = { _, _ -> detail("receipt", ScanItem("only", amount = 12.5, merchantName = "Fake Market", description = "Shop", date = "2026-09-15")) }
        val event = async { vm.events.first() }

        vm.onPhotosPicked(photos("b.png", "a.png"))
        advanceUntilIdle()

        val prefill = (event.await() as ReceiptScanEvent.Result).prefill
        assertEquals("Fake Market", prefill.description)
        assertEquals(12.5, prefill.amount!!, 0.0)
        assertEquals("Receipt scan", prefill.source)
        assertEquals(listOf("b.png", "a.png"), scans.submitted.single().map { (it.source as ScanSource.Content).uri.lastPathSegment })
        // A single receipt is done as far as the engine goes: the scan is closed as saved.
        assertEquals(listOf("scn_1" to ScanOutcome.SAVED), scans.completed)
        assertEquals(ReceiptScanStep.DONE, vm.state.value.step)
    }

    @Test
    fun `a list result opens the review and keeps the detail for it`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> detail("list", ScanItem("a"), ScanItem("b")) }
        val event = async { vm.events.first() }

        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()

        assertEquals(ReceiptScanEvent.OpenReview("scn_1"), event.await())
        assertEquals(2, coordinator.takeDetail("scn_1")!!.items!!.size)
        assertTrue(scans.completed.isEmpty())
    }

    @Test
    fun `files keep their names and offer to leave from the start while photos only offer it for big scans`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> awaitCancellation() }

        vm.onFilesPicked(photos("Visa.pdf"))
        runCurrent()
        assertEquals("Visa.pdf", vm.state.value.reading.label)
        assertEquals("Page", vm.state.value.reading.unit)
        assertTrue(vm.state.value.reading.offersBackground)
        vm.cancelReading()

        vm.onPhotosPicked(photos("a.png"))
        runCurrent()
        assertFalse(vm.state.value.reading.offersBackground)
        assertEquals("Image", vm.state.value.reading.unit)
        vm.cancelReading()
    }

    @Test
    fun `a submission with more than three documents offers to leave`() = runTest {
        val vm = newViewModel()
        scans.submitResult = { ScanSubmission("scn_1", 12, "12 screenshots") }
        scans.waitResult = { _, onProgress ->
            onProgress(summary(done = 4, total = 12, label = "12 screenshots"))
            awaitCancellation()
        }
        vm.onPhotosPicked(photos("a.png"))
        runCurrent()

        val reading = vm.state.value.reading
        assertTrue(reading.offersBackground)
        assertEquals("scn_1", reading.scanId)
        assertEquals(4, reading.done)
        assertEquals(12, reading.total)
        assertEquals("Reading 12 screenshots…", reading.title)
        assertEquals("Image 5 of 12 · matching what you already have", reading.progressText)
        assertEquals(4f / 12f, reading.fraction!!, 0.0001f)
        vm.cancelReading()
    }

    @Test
    fun `upload progress shows before the scan exists`() {
        val uploading = ScanReadingState(label = "Visa.pdf", uploaded = 2, uploadTotal = 9)
        assertEquals("Reading Visa.pdf…", uploading.title)
        assertEquals("Uploading 3 of 9", uploading.progressText)
        assertEquals(2f / 9f, uploading.fraction!!, 0.0001f)
        assertEquals("Uploading…", ScanReadingState().progressText)
        assertNull(ScanReadingState().fraction)
        assertEquals("Reading your scan…", ScanReadingState().title)
        assertEquals("Matching what you already have", ScanReadingState(scanId = "s", total = 1).progressText)
    }

    @Test
    fun `an unsupported file is explained and never uploaded`() = runTest {
        val vm = newViewModel()
        vm.onFilesPicked(photos("notes.txt"))
        runCurrent()
        assertEquals(ReceiptScanStep.FAILURE, vm.state.value.step)
        assertEquals("notes.txt isn't a type Spenditos can read. Try a photo, PDF, CSV or Excel file.", vm.state.value.error)
        assertTrue(scans.submitted.isEmpty())
    }

    // endregion

    // region Credits

    @Test
    fun `a selection over the credits left shows the pick up to message and uploads nothing`() = runTest {
        val vm = newViewModel()
        remainingCredits = 3
        scans.creditEstimate = { 5 }
        vm.onPhotosPicked(photos("1.png", "2.png", "3.png", "4.png", "5.png"))
        runCurrent()
        assertEquals("You have 3 scans left — pick up to 3", vm.state.value.toast)
        assertEquals(ReceiptScanStep.CHOOSE, vm.state.value.step)
        assertTrue(scans.submitted.isEmpty())
        vm.dismissToast()
        assertNull(vm.state.value.toast)
    }

    @Test
    fun `a user with no credits left sees the limit screen on open and a null balance never does`() = runTest {
        remainingCredits = 0
        val vm = newViewModel()
        runCurrent()
        assertEquals(ReceiptScanStep.QUOTA, vm.state.value.step)
        assertEquals("You've used all your free scan credits for this month.", vm.state.value.error)

        remainingCredits = null
        val unlimited = newViewModel()
        runCurrent()
        assertEquals(ReceiptScanStep.CHOOSE, unlimited.state.value.step)
    }

    @Test
    fun `a selection that fits the credits, or an unknown balance, uploads`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> awaitCancellation() }
        remainingCredits = 5
        scans.creditEstimate = { 5 }
        vm.onPhotosPicked(photos("1.png"))
        runCurrent()
        assertEquals(1, scans.submitted.size)
        vm.cancelReading()

        remainingCredits = null
        scans.creditEstimate = { error("must not be asked when the balance is unknown") }
        vm.onPhotosPicked(photos("2.png"))
        runCurrent()
        assertEquals(2, scans.submitted.size)
        vm.cancelReading()
    }

    // endregion

    // region Camera

    @Test
    fun `a camera photo is resized for scanning then read by the engine and its original deleted`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> awaitCancellation() }
        val cameraUri = "content://com.spenditos.android.fileprovider/receipts/receipt-camera-fake.jpg"
        vm.setPendingCameraUri(cameraUri)

        vm.onCameraCaptureResult(captured = true)
        runCurrent()

        assertEquals(ReceiptScanStep.READING, vm.state.value.step)
        assertEquals(listOf(3000), processor.maxLongSides)
        assertEquals(listOf(cameraUri), processor.deletedCameraFiles.distinct())
        assertEquals(null, handle.get<String>("receipt.pendingCameraUri"))
        val input = scans.submitted.single().single()
        assertEquals(ScanInputOrigin.CAMERA, input.origin)
        // The engine owns the processed file now: the view model must not delete it under the upload.
        val file = (input.source as ScanSource.OwnedFile).file
        assertTrue(file.exists())
        assertEquals("1 photo", vm.state.value.reading.label)
        vm.cancelReading()
    }

    @Test
    fun `a camera photo that cannot be processed fails and leaves no temp files`() = runTest {
        val vm = newViewModel()
        processor.failProcessing = true
        val cameraUri = "content://com.spenditos.android.fileprovider/receipts/receipt-camera-fake.jpg"
        vm.setPendingCameraUri(cameraUri)

        vm.onCameraCaptureResult(captured = true)
        advanceUntilIdle()

        assertEquals(ReceiptScanStep.FAILURE, vm.state.value.step)
        assertEquals(ReceiptScanViewModel.MANUAL_FALLBACK, vm.state.value.error)
        assertTrue(processor.createdFiles.all { !it.exists() })
        assertEquals(listOf(cameraUri), processor.deletedCameraFiles.distinct())
        assertTrue(scans.submitted.isEmpty())
    }

    @Test
    fun `cancelled camera capture removes saved camera file`() {
        runTest {
            val vm = newViewModel()
            val cameraUri = "content://com.spenditos.android.fileprovider/receipts/receipt-camera-fake.jpg"
            vm.setPendingCameraUri(cameraUri)

            vm.onCameraCaptureResult(captured = false)

            assertEquals(listOf(cameraUri), processor.deletedCameraFiles)
            assertEquals(null, handle.get<String>("receipt.pendingCameraUri"))
        }
    }

    // endregion

    // region Errors

    @Test
    fun `quota exhausted shows the friendly limit screen and never the raw code`() = runTest {
        val vm = newViewModel()
        scans.submitResult = { throw ApiError.Server(403, "QuotaExhausted") }

        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()

        assertEquals(ReceiptScanStep.QUOTA, vm.state.value.step)
        assertEquals("You've used all your free scan credits for this month.", vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("QuotaExhausted"))
        assertFalse(vm.state.value.offline)

        vm.retry()
        assertEquals(ReceiptScanStep.CHOOSE, vm.state.value.step)
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `a forbidden document is a plain failure not the limit screen`() = runTest {
        val vm = newViewModel()
        scans.submitResult = { throw ApiError.Server(403, "Access denied to this document") }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.FAILURE, vm.state.value.step)
    }

    @Test
    fun `rate limit and offline failures have their own copy`() = runTest {
        val vm = newViewModel()
        scans.submitResult = { throw ApiError.Server(429, "RateLimited") }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertEquals("Too many attempts in a short time — wait a few seconds and try again.", vm.state.value.error)

        vm.retry()
        scans.submitResult = { throw ApiError.Network(java.io.IOException("offline")) }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.FAILURE, vm.state.value.step)
        assertTrue(vm.state.value.offline)
        assertEquals("Couldn't read your scan. You can enter it yourself.", vm.state.value.error)
    }

    @Test
    fun `a scan that failed on the server shows the server's message and an engine refusal its own`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> throw ScanFailure.Failed("These pages are blank") }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertEquals("These pages are blank", vm.state.value.error)

        vm.retry()
        scans.submitResult = { throw ScanFailure.PdfLocked("Secret.pdf") }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertEquals("Secret.pdf is password protected. Remove the password and try again.", vm.state.value.error)
    }

    // endregion

    // region Leaving

    private suspend fun TestScope.startReading(vm: ReceiptScanViewModel, withScanId: Boolean = true) {
        val waiting = CompletableDeferred<Unit>()
        if (withScanId) {
            scans.waitResult = { _, onProgress ->
                onProgress(summary(done = 1, total = 5))
                waiting.complete(Unit)
                awaitCancellation()
            }
        } else {
            scans.submitResult = { awaitCancellation() }
        }
        vm.onFilesPicked(photos("Visa.pdf"))
        runCurrent()
    }

    @Test
    fun `notify me leaves the scan running and shows it on Home`() = runTest {
        val vm = newViewModel()
        startReading(vm)
        val event = async { vm.events.first() }

        vm.leaveInBackground()
        advanceUntilIdle()

        assertEquals(ReceiptScanEvent.Close, event.await())
        val card = coordinator.state.value.scans.single()
        assertEquals("scn_1", card.scanId)
        assertEquals("Visa.pdf", card.sourceLabel)
        assertEquals(1, card.progress.done)
        assertTrue(coordinator.hasProcessing)
    }

    @Test
    fun `system back while reading a created scan leaves it running and does not discard it`() = runTest {
        val vm = newViewModel()
        startReading(vm)
        val event = async { vm.events.first() }

        vm.onBack()
        advanceUntilIdle()

        assertEquals(ReceiptScanEvent.Close, event.await())
        assertEquals(listOf("scn_1"), coordinator.state.value.scans.map { it.scanId })
        assertTrue(coordinator.state.value.dismissed.isEmpty())
    }

    @Test
    fun `back during the upload cancels it before any scan exists`() = runTest {
        val vm = newViewModel()
        startReading(vm, withScanId = false)
        val event = async { vm.events.first() }

        vm.onBack()
        advanceUntilIdle()

        assertEquals(ReceiptScanEvent.Close, event.await())
        assertTrue(coordinator.state.value.scans.isEmpty())
        assertEquals(ReceiptScanStep.CHOOSE, vm.state.value.step)
    }

    @Test
    fun `cancel while reading hides a created scan and returns to the camera`() = runTest {
        val vm = newViewModel()
        startReading(vm)

        vm.cancelReading()
        advanceUntilIdle()

        assertEquals(ReceiptScanStep.CHOOSE, vm.state.value.step)
        assertEquals(setOf("scn_1"), coordinator.state.value.dismissed)
        assertTrue(coordinator.state.value.scans.isEmpty())
    }

    @Test
    fun `still reading after the long wait hands the scan to Home`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, onProgress ->
            onProgress(summary(done = 2, total = 5))
            throw ScanFailure.Timeout
        }
        val event = async { vm.events.first() }

        vm.onFilesPicked(photos("Visa.pdf"))
        advanceUntilIdle()

        assertEquals(ReceiptScanEvent.Close, event.await())
        assertEquals(listOf("scn_1"), coordinator.state.value.scans.map { it.scanId })
    }

    @Test
    fun `the screen going away mid read is the same as notify me`() = runTest {
        val vm = newViewModel()
        startReading(vm)

        clearViewModel()

        assertEquals(listOf("scn_1"), coordinator.state.value.scans.map { it.scanId })
    }

    @Test
    fun `the screen going away after the scan was handed over does not track it again`() = runTest {
        val vm = newViewModel()
        scans.waitResult = { _, _ -> detail("list", ScanItem("a"), ScanItem("b")) }
        vm.onPhotosPicked(photos("a.png"))
        advanceUntilIdle()
        assertNotNull(coordinator.takeDetail("scn_1"))

        clearViewModel()

        assertTrue(coordinator.state.value.scans.isEmpty())
    }

    // endregion

    private class FakeImageProcessor : ReceiptImageProcessing {
        val createdFiles = mutableListOf<File>()
        val deletedCameraFiles = mutableListOf<String>()
        val maxLongSides = mutableListOf<Int>()
        var failProcessing = false

        override fun createTempFile(): File = File.createTempFile("fake-receipt-", ".jpg").also { createdFiles += it }

        override fun process(uri: Uri, destination: File, maxLongSide: Int) {
            maxLongSides += maxLongSide
            if (failProcessing) throw IllegalStateException("undecodable")
            destination.writeBytes(byteArrayOf(1, 2, 3))
        }

        override fun deleteCameraFile(uriString: String) {
            deletedCameraFiles += uriString
        }
    }
}
