package com.spenditos.android.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.model.ReceiptResult
import com.spenditos.android.data.receipt.ReceiptScanFailure
import com.spenditos.android.data.receipt.ReceiptScanStage
import com.spenditos.android.data.receipt.ReceiptScanner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.spenditos.android.testing.FakeProAccess
import com.spenditos.android.testing.FakeQuotaSource
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReceiptScanViewModelTest {
    private lateinit var scanner: FakeScanner
    private lateinit var processor: FakeImageProcessor
    private lateinit var handle: SavedStateHandle
    private lateinit var viewModel: ReceiptScanViewModel
    private lateinit var access: FakeProAccess
    private lateinit var quota: FakeQuotaSource

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        scanner = FakeScanner()
        processor = FakeImageProcessor()
        handle = SavedStateHandle()
        access = FakeProAccess()
        quota = FakeQuotaSource(FakeQuotaSource.overview(), access)
        viewModel = ReceiptScanViewModel(scanner, processor, handle, Dispatchers.Main, access, quota) // same test scheduler
    }

    @After
    fun tearDown() {
        processor.createdFiles.forEach { it.delete() }
        Dispatchers.resetMain()
    }

    @Test
    fun `scan moves through preparation upload and analysis then emits prefill`() = runTest {
        val history = mutableListOf<ReceiptScanStep>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state ->
                history += state.step
                // The analyzing backdrop (iOS analyzingView) shows the processed image in flight.
                if (state.step == ReceiptScanStep.UPLOADING || state.step == ReceiptScanStep.ANALYZING) {
                    assertTrue(state.backdropPath != null)
                }
            }
        }
        val event = async { viewModel.events.first() }

        viewModel.onLibraryPhotoSelected(Uri.parse("content://fake/photo"))
        advanceUntilIdle()

        assertTrue(history.containsAll(listOf(ReceiptScanStep.PREPARING, ReceiptScanStep.UPLOADING, ReceiptScanStep.ANALYZING, ReceiptScanStep.DONE)))
        assertEquals("Fake Market", (event.await() as ReceiptScanEvent.Result).prefill.description)
        assertTrue(processor.createdFiles.all { !it.exists() })
    }

    @Test
    fun `quota 4xx shows server gate copy and retry returns to chooser`() = runTest {
        scanner.block = { _, _ -> throw ApiError.Server(403, "QuotaExhausted") }

        viewModel.onLibraryPhotoSelected(Uri.parse("content://fake/photo"))
        advanceUntilIdle()

        assertEquals(ReceiptScanStep.QUOTA, viewModel.state.value.step)
        assertEquals("You've used all your free scan credits for this month.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.error!!.contains("QuotaExhausted"))
        assertFalse(viewModel.state.value.offline)
        assertFalse(viewModel.state.value.error!!.contains("Pro"))

        viewModel.retry()
        assertEquals(ReceiptScanStep.CHOOSE, viewModel.state.value.step)
        assertEquals(null, viewModel.state.value.error)
    }

    private fun newViewModel(overview: com.spenditos.android.data.model.QuotaOverview?, pro: Boolean = false): ReceiptScanViewModel {
        access.setPro(pro)
        quota.fetched = overview
        return ReceiptScanViewModel(scanner, processor, SavedStateHandle(), Dispatchers.Main, access, quota)
    }

    @Test
    fun `a free user with no scan credits left sees the exhausted screen before scanning`() = runTest {
        val vm = newViewModel(FakeQuotaSource.overview(scanLeft = 0, scanUsed = 10))
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.QUOTA, vm.state.value.step)
        assertEquals("You've used all your free scan credits for this month.", vm.state.value.error)
        assertEquals(0, vm.state.value.scanCreditsLeft)
    }

    @Test
    fun `remaining scan credits come from the scan credits bucket`() = runTest {
        val vm = newViewModel(FakeQuotaSource.overview(scanLeft = 3, scanUsed = 7))
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.CHOOSE, vm.state.value.step)
        assertEquals(3, vm.state.value.scanCreditsLeft)
    }

    @Test
    fun `pro and unknown quota never block the chooser`() = runTest {
        val pro = newViewModel(FakeQuotaSource.overview(scanLeft = 0), pro = true)
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.CHOOSE, pro.state.value.step)
        assertEquals(null, pro.state.value.scanCreditsLeft)

        val offline = newViewModel(null)
        advanceUntilIdle()
        assertEquals(ReceiptScanStep.CHOOSE, offline.state.value.step)
    }

    @Test
    fun `a finished scan invalidates the quota cache`() = runTest {
        viewModel.onLibraryPhotoSelected(Uri.parse("content://fake/photo"))
        advanceUntilIdle()
        assertEquals(1, quota.invalidations)
    }

    @Test
    fun `network failure shows offline and manual entry copy`() = runTest {
        scanner.block = { _, _ -> throw ApiError.Network(IllegalStateException("test offline")) }

        viewModel.onLibraryPhotoSelected(Uri.parse("content://fake/photo"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.offline)
        assertEquals("Couldn't read that receipt. You can enter it yourself.", viewModel.state.value.error)
    }

    @Test
    fun `analysis failure shows manual entry fallback`() = runTest {
        scanner.block = { _, _ -> throw ReceiptScanFailure.AnalysisFailed }

        viewModel.onLibraryPhotoSelected(Uri.parse("content://fake/photo"))
        advanceUntilIdle()

        assertEquals(ReceiptScanStep.FAILURE, viewModel.state.value.step)
        assertEquals("Couldn't read that receipt. You can enter it yourself.", viewModel.state.value.error)
    }

    @Test
    fun `cancel stops active scan and deletes camera and processed temporary files`() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        scanner.block = { _, onStage ->
            onStage(ReceiptScanStage.ANALYZING)
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val cameraUri = "content://com.spenditos.android.fileprovider/receipts/receipt-camera-fake.jpg"
        viewModel.setPendingCameraUri(cameraUri)
        viewModel.onCameraCaptureResult(captured = true)
        runCurrent()
        started.await()

        viewModel.cancel()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(ReceiptScanStep.CHOOSE, viewModel.state.value.step)
        assertEquals(listOf(cameraUri), processor.deletedCameraFiles)
        assertTrue(processor.createdFiles.all { !it.exists() })
        assertEquals(null, handle.get<String>("receipt.pendingCameraUri"))
    }

    @Test
    fun `cancelled camera capture removes saved camera file`() {
        val cameraUri = "content://com.spenditos.android.fileprovider/receipts/receipt-camera-fake.jpg"
        viewModel.setPendingCameraUri(cameraUri)

        viewModel.onCameraCaptureResult(captured = false)

        assertEquals(listOf(cameraUri), processor.deletedCameraFiles)
        assertEquals(null, handle.get<String>("receipt.pendingCameraUri"))
    }

    private class FakeScanner : ReceiptScanner {
        var block: suspend (File, (ReceiptScanStage) -> Unit) -> ReceiptResult = { _, onStage ->
            onStage(ReceiptScanStage.UPLOADING)
            yield()
            onStage(ReceiptScanStage.ANALYZING)
            yield()
            ReceiptResult(amount = 12.5, description = "Shop", merchantName = "Fake Market", date = "2026-09-15")
        }

        override suspend fun scan(image: File, onStage: (ReceiptScanStage) -> Unit): ReceiptResult = block(image, onStage)
    }

    private class FakeImageProcessor : ReceiptImageProcessing {
        val createdFiles = mutableListOf<File>()
        val deletedCameraFiles = mutableListOf<String>()

        override fun createTempFile(): File = File.createTempFile("fake-receipt-", ".jpg").also { createdFiles += it }

        override fun process(uri: Uri, destination: File) {
            destination.writeBytes(byteArrayOf(1, 2, 3))
        }

        override fun deleteCameraFile(uriString: String) {
            deletedCameraFiles += uriString
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class ReceiptImageProcessorTest {
    @Test
    fun `processor rotates exif image and caps longest side`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("receipt-source-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(3000, 1000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val processor = ReceiptImageProcessor(context)
        val output = processor.createTempFile()
        try {
            processor.process(Uri.fromFile(source), output)
            val decoded = BitmapFactory.decodeFile(output.absolutePath)!!
            assertTrue(decoded.height > decoded.width)
            assertTrue(maxOf(decoded.width, decoded.height) <= 2048)
            decoded.recycle()
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test
    fun `startup removes scan leftovers and abandoned camera photos but keeps a recent camera photo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val now = 10L * 60 * 60 * 1000
        val scanLeftover = File(directory, "receipt-scan-left.jpg").apply { writeText("x"); setLastModified(now) }
        val abandoned = File(directory, "receipt-camera-old.jpg").apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000) }
        // A camera result can still arrive after process death: this one must survive.
        val pending = File(directory, "receipt-camera-new.jpg").apply { writeText("x"); setLastModified(now - 60 * 1000) }
        try {
            ReceiptImageProcessor(context, now = { now })
            assertFalse(scanLeftover.exists())
            assertFalse(abandoned.exists())
            assertTrue(pending.exists())
        } finally {
            listOf(scanLeftover, abandoned, pending).forEach { it.delete() }
        }
    }
}
