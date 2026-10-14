package com.spenditos.android.data.scan

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.spenditos.android.feature.receipt.ReceiptImageProcessing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
class ScanDocumentPreparerTest {
    private lateinit var context: Context
    private lateinit var preparer: AndroidScanDocumentPreparer
    private lateinit var picked: File
    private val processed = mutableListOf<Pair<Uri, Int>>()

    private val processor = object : ReceiptImageProcessing {
        override fun createTempFile(): File = File.createTempFile("p-", ".jpg")
        override fun process(uri: Uri, destination: File, maxLongSide: Int) {
            processed += uri to maxLongSide
            if (uri.lastPathSegment == "broken.png") throw IllegalArgumentException("undecodable")
            destination.writeBytes(byteArrayOf(1, 2, 3))
        }
        override fun deleteCameraFile(uriString: String) = Unit
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preparer = AndroidScanDocumentPreparer(context, processor, Dispatchers.Unconfined)
        picked = File.createTempFile("picked-", ".csv", context.cacheDir)
    }

    @After
    fun tearDown() {
        picked.delete()
        File(context.cacheDir, "scan-upload").deleteRecursively()
    }

    private fun content(kind: ScanInputKind, file: File = picked, name: String? = file.name) =
        ScanInput(kind, ScanInputOrigin.FILES, ScanSource.Content(Uri.fromFile(file)), name)

    @Test
    fun `a csv is copied as is with its mime type and never grouped`() = runTest {
        picked.writeText("date,amount\n2026-10-01,4.50\n")
        var count = -1
        val docs = preparer.documents(content(ScanInputKind.CSV), 300) { count = it }.toList()
        assertEquals(1, count)
        val doc = docs.single()
        assertEquals("text/csv", doc.mimeType)
        assertEquals(ScanDocumentKind.SPREADSHEET, doc.kind)
        assertNull(doc.pdfGroupId)
        assertEquals("date,amount\n2026-10-01,4.50\n", doc.file.readText())
        assertFalse("the copy is a separate temp file, the pick is untouched", doc.file == picked)
        doc.file.delete()
    }

    @Test
    fun `spreadsheet mime types follow the kind`() = runTest {
        picked.writeText("x")
        assertEquals("application/vnd.ms-excel", preparer.documents(content(ScanInputKind.XLS), 1) {}.toList().single().mimeType.also { })
        assertEquals(ScanRules.XLSX_MIME, preparer.documents(content(ScanInputKind.XLSX), 1) {}.toList().single().mimeType)
    }

    @Test
    fun `a spreadsheet over 10 MB is refused and its partial copy removed`() = runTest {
        picked.outputStream().use { out -> repeat(11) { out.write(ByteArray(1024 * 1024)) } }
        val error = runCatching { preparer.documents(content(ScanInputKind.XLSX, name = "Big.xlsx"), 1) {}.toList() }.exceptionOrNull()
        assertEquals(ScanFailure.FileTooLarge("Big.xlsx"), error)
        assertEquals("That file is too large to upload (10 MB maximum).", ScanFailure.FileTooLarge(null).userMessage)
        assertTrue(File(context.cacheDir, "scan-upload").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `an image is processed at the scan size and a camera file is used as is`() = runTest {
        val photo = ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.PHOTO_LIBRARY, ScanSource.Content(Uri.parse("content://media/42")))
        val doc = preparer.documents(photo, 300) {}.toList().single()
        assertEquals(listOf(Uri.parse("content://media/42") to 3000), processed)
        assertEquals("image/jpeg", doc.mimeType)
        assertEquals(ScanDocumentKind.IMAGE, doc.kind)
        assertNull(doc.pdfGroupId)
        doc.file.delete()

        val captured = File.createTempFile("camera-", ".jpg", context.cacheDir).apply { writeBytes(byteArrayOf(9)) }
        val cameraDoc = preparer.documents(ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.CAMERA, ScanSource.OwnedFile(captured)), 300) {}.toList().single()
        assertEquals(captured, cameraDoc.file)
        assertEquals(1, processed.size)
        captured.delete()
    }

    @Test
    fun `an unreadable image fails with its name and leaves no temp file`() = runTest {
        val broken = ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.FILES, ScanSource.Content(Uri.parse("content://media/broken.png")), "broken.png")
        val error = runCatching { preparer.documents(broken, 300) {}.toList() }.exceptionOrNull()
        assertEquals(ScanFailure.UnreadableImage("broken.png"), error)
        assertTrue(File(context.cacheDir, "scan-upload").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `startup removes upload leftovers`() {
        val leftover = File(File(context.cacheDir, "scan-upload").apply { mkdirs() }, "scan-left.jpg").apply { writeText("x") }
        AndroidScanDocumentPreparer(context, processor, Dispatchers.Unconfined)
        assertFalse(leftover.exists())
    }
}
