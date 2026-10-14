package com.spenditos.android.data.scan

import android.net.Uri
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ScanRulesTest {
    private fun input(origin: ScanInputOrigin, name: String? = null, kind: ScanInputKind = ScanInputKind.IMAGE) =
        ScanInput(kind, origin, ScanSource.OwnedFile(File("x")), name)

    // region Labels and chunking

    @Test
    fun `source label is the filename for one file and counts otherwise`() {
        assertEquals("Visa.pdf", ScanRules.sourceLabel(listOf(input(ScanInputOrigin.FILES, "Visa.pdf", ScanInputKind.PDF))))
        assertEquals("1 screenshot", ScanRules.sourceLabel(listOf(input(ScanInputOrigin.PHOTO_LIBRARY))))
        assertEquals("1 photo", ScanRules.sourceLabel(listOf(input(ScanInputOrigin.CAMERA))))
        assertEquals("1 file", ScanRules.sourceLabel(listOf(input(ScanInputOrigin.FILES, "  "))))
        assertEquals("3 screenshots", ScanRules.sourceLabel(List(3) { input(ScanInputOrigin.PHOTO_LIBRARY) }))
        assertEquals("2 files", ScanRules.sourceLabel(listOf(input(ScanInputOrigin.PHOTO_LIBRARY), input(ScanInputOrigin.FILES, "a.csv"))))
        assertEquals(80, ScanRules.sourceLabel(listOf(input(ScanInputOrigin.FILES, "x".repeat(200)))).length)
    }

    @Test
    fun `chunks keep order and split at 60`() {
        assertTrue(ScanRules.chunks(emptyList<Int>()).isEmpty())
        assertEquals(listOf(60, 60, 1), ScanRules.chunks((1..121).toList()).map { it.size })
        assertEquals((1..130).toList(), ScanRules.chunks((1..130).toList()).flatten())
        assertEquals("Statement.pdf (2 of 3)", ScanRules.chunkLabel("Statement.pdf", 1, 3))
        assertEquals("Statement.pdf", ScanRules.chunkLabel("Statement.pdf", 0, 1))
    }

    @Test
    fun `reading unit follows the inputs`() {
        assertEquals("Page", ScanRules.readingUnit(listOf(input(ScanInputOrigin.FILES, kind = ScanInputKind.PDF), input(ScanInputOrigin.FILES))))
        assertEquals("Image", ScanRules.readingUnit(listOf(input(ScanInputOrigin.PHOTO_LIBRARY))))
        assertEquals("File", ScanRules.readingUnit(listOf(input(ScanInputOrigin.FILES, kind = ScanInputKind.CSV), input(ScanInputOrigin.FILES))))
    }

    // endregion

    // region PDF grouping

    @Test
    fun `a pdf group id is pdf underscore uuid and fits the backend rule`() {
        val id = ScanRules.makePdfGroupId()
        assertTrue(id.startsWith("pdf_"))
        assertEquals(40, id.length)
        assertTrue(Regex("[A-Za-z0-9_-]{1,64}").matches(id))
        assertFalse(id == ScanRules.makePdfGroupId())
    }

    @Test
    fun `a spreadsheet never carries a pdf group id even if one was set`() {
        val request = ScanRules.createRequest(
            documents = listOf(
                UploadedScanDocument("k1", ScanDocumentKind.IMAGE, "p1.jpg", "pdf_abc"),
                UploadedScanDocument("k2", ScanDocumentKind.SPREADSHEET, "s.csv", "pdf_abc"),
                UploadedScanDocument("k3", ScanDocumentKind.IMAGE, "", null),
            ),
            sourceLabel = "3 files",
            context = ScanContext("", "en-CA", LocalDate.of(2026, 10, 11)),
        )
        assertEquals(listOf("pdf_abc", null, null), request.documents.map { it.pdfGroupId })
        assertNull(request.documents[2].filename)
        assertNull(request.userCurrency)
        assertEquals("2026-10-11", request.referenceDate)
    }

    @Test
    fun `pdf pages are named after the file and sized to a 2400 px long edge`() {
        assertEquals("Visa-September-page-1.jpg", pdfPageFilename("Visa-September.pdf", 1))
        assertEquals("page-page-4.jpg", pdfPageFilename(null, 4))
        assertEquals("NoExtension-page-2.jpg", pdfPageFilename("NoExtension", 2))
        assertEquals(1855 to 2400, pdfRenderSize(612, 792))
        assertEquals(2400 to 1855, pdfRenderSize(792, 612))
        assertEquals(1 to 2400, pdfRenderSize(0, 2400))
    }

    // endregion

    // region Credit estimate

    @Test
    fun `a pdf costs one credit per started block of ten pages and everything else one`() {
        assertEquals(1, ScanCredits.forPdfPages(1))
        assertEquals(1, ScanCredits.forPdfPages(10))
        assertEquals(2, ScanCredits.forPdfPages(11))
        assertEquals(2, ScanCredits.forPdfPages(12))
        assertEquals(6, ScanCredits.forPdfPages(60))
        assertEquals(1, ScanCredits.forPdfPages(0))
        assertEquals(
            1 + 2 + 1 + 1,
            ScanCredits.forFiles(
                listOf(
                    ScanCredits.FileEstimate(isPdf = false, pages = null),
                    ScanCredits.FileEstimate(isPdf = true, pages = 12),
                    ScanCredits.FileEstimate(isPdf = true, pages = null),
                    ScanCredits.FileEstimate(isPdf = false, pages = null),
                ),
            ),
        )
    }

    @Test
    fun `over limit message appears only for a known positive limit that is exceeded`() {
        assertEquals("You have 3 scans left — pick up to 3", ScanCredits.overLimitMessage(estimatedCredits = 5, remaining = 3))
        assertEquals("You have 1 scan left — pick up to 1", ScanCredits.overLimitMessage(2, 1))
        assertNull(ScanCredits.overLimitMessage(3, 3))
        assertNull(ScanCredits.overLimitMessage(1, 3))
        // Unknown / unlimited: no cap message. Zero is handled by the quota screen, not a toast.
        assertNull(ScanCredits.overLimitMessage(50, null))
        assertNull(ScanCredits.overLimitMessage(50, 0))
    }

    // endregion

    // region File types

    @Test
    fun `files are classified by extension first then by mime type`() {
        assertEquals(ScanInputKind.PDF, ScanFileTypes.classify("Statement.PDF", null))
        assertEquals(ScanInputKind.CSV, ScanFileTypes.classify("export.csv", "application/octet-stream"))
        assertEquals(ScanInputKind.XLS, ScanFileTypes.classify("a.xls", null))
        assertEquals(ScanInputKind.XLSX, ScanFileTypes.classify("a.xlsx", null))
        assertEquals(ScanInputKind.IMAGE, ScanFileTypes.classify("IMG_1.HEIC", null))
        assertEquals(ScanInputKind.PDF, ScanFileTypes.classify(null, "application/pdf"))
        assertEquals(ScanInputKind.CSV, ScanFileTypes.classify("download", "text/comma-separated-values"))
        assertEquals(ScanInputKind.XLSX, ScanFileTypes.classify("download", ScanRules.XLSX_MIME))
        assertEquals(ScanInputKind.IMAGE, ScanFileTypes.classify("download", "image/webp"))
        assertNull(ScanFileTypes.classify("notes.txt", "text/plain"))
        assertNull(ScanFileTypes.classify(null, null))
    }

    // endregion

    // region Resolution

    private fun detail(kind: String?, items: List<ScanItem>?, status: String = "ready") =
        ScanDetail(ScanSummary(scanId = "s", status = status, kind = kind, itemCount = items?.size ?: 0), items)

    @Test
    fun `a receipt with one item opens the prefill and everything else the review list`() {
        val one = listOf(ScanItem(id = "a", amount = 3.0))
        assertTrue(detail("receipt", one).resolution() is ScanResolution.Receipt)
        assertTrue(detail("list", one).resolution() is ScanResolution.Review)
        assertTrue(detail("receipt", one + ScanItem(id = "b")).resolution() is ScanResolution.Review)
        // An unknown kind is a list.
        assertTrue(detail("mystery", one).resolution() is ScanResolution.Review)
    }

    @Test
    fun `a ready scan without rows says nothing was found`() {
        assertEquals(ScanFailure.NoTransactionsFound, runCatching { detail("list", emptyList()).resolution() }.exceptionOrNull())
        assertEquals(ScanFailure.NoTransactionsFound, runCatching { detail("list", null).resolution() }.exceptionOrNull())
    }

    @Test
    fun `opening a scan explains every state that cannot be reviewed`() {
        val rows = listOf(ScanItem(id = "a"))
        assertEquals(ScanFailure.AlreadySaved, runCatching { detail("list", rows, "saved").resolveForOpening() }.exceptionOrNull())
        assertEquals(ScanFailure.Cancelled, runCatching { detail("list", rows, "discarded").resolveForOpening() }.exceptionOrNull())
        assertEquals(ScanFailure.Timeout, runCatching { detail("list", null, "processing").resolveForOpening() }.exceptionOrNull())
        assertEquals(ScanFailure.Timeout, runCatching { detail("list", null, "from-the-future").resolveForOpening() }.exceptionOrNull())
        assertEquals(ScanFailure.Failed("boom"), runCatching {
            ScanDetail(ScanSummary("s", "failed", error = "boom"), null).resolveForOpening()
        }.exceptionOrNull())
        assertTrue(detail("list", rows).resolveForOpening() is ScanResolution.Review)
    }

    // endregion

    @Test
    fun `uri sources are plain data`() {
        assertEquals(ScanSource.Content(Uri.parse("content://a/b")), ScanSource.Content(Uri.parse("content://a/b")))
    }
}
