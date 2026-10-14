package com.spenditos.android.data.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.spenditos.android.data.receipt.ReceiptIoDispatcher
import com.spenditos.android.feature.receipt.ReceiptImageProcessing
import com.spenditos.android.feature.receipt.SCAN_MAX_LONG_SIDE
import com.spenditos.android.feature.receipt.writeJpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Turns one pick into the documents to upload, lazily, so 60 PDF pages are never all on disk at once. */
interface ScanDocumentPreparer {
    /**
     * Emits the documents of [input] in order, at most [remaining]. [onCount] is called once, before the first
     * document, with how many will be emitted. Every emitted file belongs to the collector (it deletes it after upload).
     * Throws a [ScanFailure] for an unreadable, locked, empty or oversized input.
     */
    fun documents(input: ScanInput, remaining: Int, onCount: (Int) -> Unit): Flow<PreparedScanDocument>

    /** Page count of a PDF pick for the credit estimate; null when it cannot be read (counts as one credit). */
    suspend fun pdfPageCount(input: ScanInput): Int?
}

/** PDF pages are rendered so the long edge is about this many pixels: enough for small statement text (iOS `pdfRenderLongEdge`). */
internal const val PDF_RENDER_LONG_EDGE = 2400

/** Size in pixels for rendering a PDF page of [widthPoints] x [heightPoints] with its long edge at [longEdge]. */
internal fun pdfRenderSize(widthPoints: Int, heightPoints: Int, longEdge: Int = PDF_RENDER_LONG_EDGE): Pair<Int, Int> {
    val longest = maxOf(widthPoints, heightPoints).coerceAtLeast(1)
    val scale = longEdge.toDouble() / longest
    return (widthPoints * scale).roundToInt().coerceAtLeast(1) to (heightPoints * scale).roundToInt().coerceAtLeast(1)
}

/** "Statement.pdf" -> "Statement-page-1.jpg" (iOS page naming). */
internal fun pdfPageFilename(pdfName: String?, pageNumber: Int): String {
    val base = pdfName?.let { it.substringBeforeLast('.', it) }?.takeIf { it.isNotBlank() } ?: "page"
    return "$base-page-$pageNumber.jpg"
}

@Singleton
class AndroidScanDocumentPreparer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: ReceiptImageProcessing,
    @ReceiptIoDispatcher private val io: CoroutineDispatcher,
) : ScanDocumentPreparer {
    private val directory: File get() = File(context.cacheDir, WORK_DIRECTORY).apply { mkdirs() }

    init {
        // Leftovers of a process that died mid-upload. Nothing here is ever resumed.
        File(context.cacheDir, WORK_DIRECTORY).listFiles()?.forEach { it.delete() }
    }

    override fun documents(input: ScanInput, remaining: Int, onCount: (Int) -> Unit): Flow<PreparedScanDocument> = when (input.kind) {
        ScanInputKind.IMAGE -> flow {
            onCount(1)
            emit(withContext(io) { prepareImage(input) })
        }
        ScanInputKind.PDF -> pdfPages(input, remaining, onCount)
        ScanInputKind.CSV, ScanInputKind.XLS, ScanInputKind.XLSX -> flow {
            onCount(1)
            emit(withContext(io) { prepareSpreadsheet(input) })
        }
    }

    override suspend fun pdfPageCount(input: ScanInput): Int? {
        val uri = (input.source as? ScanSource.Content)?.uri ?: return null
        return withContext(io) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                    PdfRenderer(fd).use { it.pageCount }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun prepareImage(input: ScanInput): PreparedScanDocument {
        when (val source = input.source) {
            is ScanSource.OwnedFile -> return PreparedScanDocument(source.file, ScanRules.JPEG_MIME, ScanDocumentKind.IMAGE, input.filename)
            is ScanSource.Content -> {
                val out = File.createTempFile("scan-", ".jpg", directory)
                try {
                    processor.process(source.uri, out, SCAN_MAX_LONG_SIDE)
                } catch (error: Throwable) {
                    out.delete()
                    if (error is CancellationException) throw error
                    throw ScanFailure.UnreadableImage(input.filename)
                }
                return PreparedScanDocument(out, ScanRules.JPEG_MIME, ScanDocumentKind.IMAGE, input.filename)
            }
        }
    }

    private fun prepareSpreadsheet(input: ScanInput): PreparedScanDocument {
        val mime = when (input.kind) {
            ScanInputKind.CSV -> ScanRules.CSV_MIME
            ScanInputKind.XLS -> ScanRules.XLS_MIME
            else -> ScanRules.XLSX_MIME
        }
        val copy = copyToTemp(input, ".bin", limit = ScanRules.MAX_FILE_BYTES)
        return PreparedScanDocument(copy, mime, ScanDocumentKind.SPREADSHEET, input.filename)
    }

    private fun pdfPages(input: ScanInput, remaining: Int, onCount: (Int) -> Unit): Flow<PreparedScanDocument> = flow {
        // PdfRenderer needs a seekable file: copy the pick first (document providers are not always seekable).
        val copy = withContext(io) { copyToTemp(input, ".pdf", limit = null) }
        var renderer: PdfRenderer? = null
        try {
            val opened = withContext(io) { openRenderer(copy, input.filename) }
            renderer = opened
            val pageCount = opened.pageCount
            if (pageCount <= 0) throw ScanFailure.PdfEmpty(input.filename)
            val count = minOf(pageCount, maxOf(0, remaining))
            onCount(count)
            val groupId = ScanRules.makePdfGroupId()
            for (index in 0 until count) {
                val page = withContext(io) { renderPage(opened, index) }
                emit(
                    PreparedScanDocument(
                        file = page,
                        mimeType = ScanRules.JPEG_MIME,
                        kind = ScanDocumentKind.IMAGE,
                        filename = pdfPageFilename(input.filename, index + 1),
                        pdfGroupId = groupId,
                    ),
                )
            }
        } finally {
            withContext(NonCancellable + io) {
                runCatching { renderer?.close() }
                copy.delete()
            }
        }
    }

    private fun openRenderer(copy: File, name: String?): PdfRenderer = try {
        PdfRenderer(ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY))
    } catch (_: SecurityException) {
        // PdfRenderer reports a password protected document as a SecurityException.
        throw ScanFailure.PdfLocked(name)
    } catch (_: IOException) {
        throw ScanFailure.UnreadableImage(name)
    } catch (_: IllegalArgumentException) {
        throw ScanFailure.UnreadableImage(name)
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): File {
        val out = File.createTempFile("scan-page-", ".jpg", directory)
        try {
            renderer.openPage(index).use { page ->
                val (width, height) = pdfRenderSize(page.width, page.height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    writeJpeg(bitmap, out)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (error: Throwable) {
            out.delete()
            if (error is CancellationException) throw error
            throw ScanFailure.UnreadableImage(null)
        }
        return out
    }

    /** Streams a content pick into a temp file; over [limit] bytes the copy is dropped and the pick is refused. */
    private fun copyToTemp(input: ScanInput, suffix: String, limit: Long?): File {
        val uri = (input.source as? ScanSource.Content)?.uri ?: throw ScanFailure.UnreadableImage(input.filename)
        val out = File.createTempFile("scan-", suffix, directory)
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: throw ScanFailure.UnreadableImage(input.filename)
            stream.use { source ->
                out.outputStream().use { sink ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (limit != null && total > limit) throw ScanFailure.FileTooLarge(input.filename)
                        sink.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: ScanFailure) {
            out.delete()
            throw error
        } catch (error: Throwable) {
            out.delete()
            if (error is CancellationException) throw error
            throw ScanFailure.UnreadableImage(input.filename)
        }
        return out
    }

    private companion object {
        const val WORK_DIRECTORY = "scan-upload"
        const val COPY_BUFFER = 64 * 1024
    }
}

/** Builds [ScanInput]s from what the pickers return. */
interface ScanInputFactory {
    /** A photo from the Photo Picker. */
    fun photo(uri: Uri): ScanInput

    /** A document-picker pick, or null when it is not a type the engine reads. */
    fun file(uri: Uri): ScanInput?

    /** A camera capture already processed to a JPEG; the engine deletes the file after upload. */
    fun camera(file: File): ScanInput
}

/** Decides what a picked file is, by extension first, then by MIME type (iOS `ScanInput.fromFile`). */
object ScanFileTypes {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "tif", "tiff", "bmp")

    fun classify(name: String?, mimeType: String?): ScanInputKind? {
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        when {
            extension == "pdf" -> return ScanInputKind.PDF
            extension == "csv" -> return ScanInputKind.CSV
            extension == "xls" -> return ScanInputKind.XLS
            extension == "xlsx" -> return ScanInputKind.XLSX
            extension in imageExtensions -> return ScanInputKind.IMAGE
        }
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        return when {
            mime == "application/pdf" -> ScanInputKind.PDF
            mime == "text/csv" || mime == "text/comma-separated-values" || mime == "application/csv" -> ScanInputKind.CSV
            mime == "application/vnd.ms-excel" -> ScanInputKind.XLS
            mime == ScanRules.XLSX_MIME -> ScanInputKind.XLSX
            mime.startsWith("image/") -> ScanInputKind.IMAGE
            else -> null
        }
    }
}

@Singleton
class AndroidScanInputFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScanInputFactory {
    override fun photo(uri: Uri) = ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.PHOTO_LIBRARY, ScanSource.Content(uri))

    override fun file(uri: Uri): ScanInput? {
        val name = displayName(uri)
        val kind = ScanFileTypes.classify(name, runCatching { context.contentResolver.getType(uri) }.getOrNull()) ?: return null
        return ScanInput(kind, ScanInputOrigin.FILES, ScanSource.Content(uri), filename = name)
    }

    override fun camera(file: File) = ScanInput(ScanInputKind.IMAGE, ScanInputOrigin.CAMERA, ScanSource.OwnedFile(file))

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    } catch (_: Exception) {
        null
    }
}
