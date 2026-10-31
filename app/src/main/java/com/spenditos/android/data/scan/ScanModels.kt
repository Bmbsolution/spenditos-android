package com.spenditos.android.data.scan

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import android.net.Uri
import com.spenditos.android.data.model.CreateScanDocument
import com.spenditos.android.data.model.CreateScanRequest
import com.spenditos.android.data.model.ScanDetail
import com.spenditos.android.data.model.ScanItem
import com.spenditos.android.data.model.ScanKind
import com.spenditos.android.data.model.ScanStatus
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** What a picked file is, decided from its extension / MIME type (iOS `ScanInput.Kind`). */
enum class ScanInputKind { IMAGE, PDF, CSV, XLS, XLSX }

/** Where a pick came from; decides the source label ("3 screenshots") and whether "Notify me" is offered. */
enum class ScanInputOrigin { PHOTO_LIBRARY, FILES, CAMERA }

/** The bytes of a pick: a content URI (copied and prepared on demand) or a file the engine now owns (and deletes after upload). */
sealed interface ScanSource {
    data class Content(val uri: Uri) : ScanSource

    /** An already prepared JPEG (a camera capture processed by the screen); deleted after it is uploaded. */
    data class OwnedFile(val file: File) : ScanSource
}

/** One thing the user picked: a photo, or a file from the document picker. */
data class ScanInput(
    val kind: ScanInputKind,
    val origin: ScanInputOrigin,
    val source: ScanSource,
    /** Shown to the user (source label) when this is the only input. */
    val filename: String? = null,
)

enum class ScanDocumentKind(val wire: String) { IMAGE("image"), SPREADSHEET("spreadsheet") }

/** A document ready to upload. [file] is a temp file the uploader deletes once it has been sent. */
data class PreparedScanDocument(
    val file: File,
    val mimeType: String,
    val kind: ScanDocumentKind,
    val filename: String?,
    /** Shared by every page rendered from the same PDF (`pdf_<UUID>`); null for photos and spreadsheets. */
    val pdfGroupId: String? = null,
)

/** An uploaded document, ready to list in `POST /scans`. */
data class UploadedScanDocument(
    val documentKey: String,
    val kind: ScanDocumentKind,
    val filename: String?,
    val pdfGroupId: String? = null,
)

data class ScanUploadProgress(val uploaded: Int, val total: Int?)

data class ScanSubmission(val scanId: String, val total: Int, val sourceLabel: String)

/** Device-local values `POST /scans` needs so "Today" / "Yesterday" rows resolve correctly. */
data class ScanContext(
    val currency: String,
    val locale: String,
    val referenceDate: LocalDate = LocalDate.now(),
)

/** Why a scan could not be read, with the copy iOS `ScanError` shows. */
sealed class ScanFailure(val userMessage: String) : Exception(userMessage) {
    data object NoDocuments : ScanFailure(Strings.get(R.string.data_scan_choose_at_least_one_photo))
    data class UnreadableImage(val name: String?) : ScanFailure("Couldn't read ${name ?: "one of the images"}.")
    data class PdfLocked(val name: String?) :
        ScanFailure("${name ?: "That PDF"} is password protected. Remove the password and try again.")
    data class PdfEmpty(val name: String?) : ScanFailure("${name ?: "That PDF"} has no pages to read.")
    data class FileTooLarge(val name: String?) : ScanFailure("${name ?: "That file"} is too large to upload (10 MB maximum).")
    data class UnsupportedFile(val name: String?) :
        ScanFailure("${name ?: "That file"} isn't a type Spenditos can read. Try a photo, PDF, CSV or Excel file.")
    data class Failed(val serverMessage: String?) :
        ScanFailure(serverMessage?.takeIf { it.isNotBlank() } ?: Strings.get(R.string.data_scan_couldn_t_read_transactions_from))
    data object Timeout : ScanFailure(Strings.get(R.string.data_scan_reading_is_taking_longer_than))
    data object NoTransactionsFound : ScanFailure(Strings.get(R.string.data_scan_no_transactions_were_found_in))
    data object Cancelled : ScanFailure(Strings.get(R.string.data_scan_this_scan_was_cancelled))
    data object AlreadySaved : ScanFailure(Strings.get(R.string.data_scan_those_transactions_were_already_added))
    data object InvalidUploadUrl : ScanFailure(Strings.get(R.string.data_scan_couldn_t_upload_that_file))
    data class UploadFailed(val status: Int) : ScanFailure(Strings.get(R.string.data_scan_couldn_t_upload_that_file))
}

/** Where a ready scan leads (iOS `ScanResolution`). */
sealed interface ScanResolution {
    /** One purchase: opens the existing single-receipt prefill. */
    data class Receipt(val item: ScanItem, val scanId: String) : ScanResolution

    /** Everything else: the month-grouped review list. */
    data class Review(val detail: ScanDetail) : ScanResolution
}

/** Route a ready scan: a receipt-kind scan with exactly one item prefills the add form; everything else is a list. */
fun ScanDetail.resolution(): ScanResolution {
    val rows = items
    if (rows.isNullOrEmpty()) throw ScanFailure.NoTransactionsFound
    if (kind == ScanKind.RECEIPT && rows.size == 1) return ScanResolution.Receipt(rows.first(), scanId)
    return ScanResolution.Review(this)
}

/** Decide where a fetched scan leads, or why it cannot be opened (iOS `ScanService.resolve`). */
fun ScanDetail.resolveForOpening(): ScanResolution = when (state) {
    ScanStatus.READY -> resolution()
    ScanStatus.SAVED -> throw ScanFailure.AlreadySaved
    ScanStatus.FAILED -> throw ScanFailure.Failed(summary.error)
    ScanStatus.DISCARDED -> throw ScanFailure.Cancelled
    ScanStatus.PROCESSING, ScanStatus.UNKNOWN -> throw ScanFailure.Timeout
}

/** Pure rules of a submission: limits, labels, grouping, the request body. */
object ScanRules {
    /** Backend limit per scan. Bigger uploads are split into several scans, so the user never sees this number. */
    const val MAX_DOCUMENTS = 60

    /** Quiet guard across one upload (pages + images), split into scans of [MAX_DOCUMENTS]. */
    const val MAX_DOCUMENTS_PER_UPLOAD = 300
    const val MAX_CONCURRENT_UPLOADS = 3
    const val MAX_SOURCE_LABEL_LENGTH = 80

    /** The server accepts 10 MiB per scan upload. */
    const val MAX_FILE_BYTES = 10L * 1024 * 1024

    const val CSV_MIME = "text/csv"
    const val XLS_MIME = "application/vnd.ms-excel"
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val JPEG_MIME = "image/jpeg"

    /** "pdf_" + UUID: 40 characters, inside the backend's 1-64 `[A-Za-z0-9_-]` rule. */
    fun makePdfGroupId(): String = "pdf_" + UUID.randomUUID().toString()

    /** What the user sees ("Reading {label}…", Home card): the filename for a single file, else "N screenshots" / "N files". */
    fun sourceLabel(inputs: List<ScanInput>): String {
        if (inputs.size == 1) {
            val name = inputs[0].filename?.trim().orEmpty()
            if (name.isNotEmpty()) return name.take(MAX_SOURCE_LABEL_LENGTH)
            return when (inputs[0].origin) {
                ScanInputOrigin.PHOTO_LIBRARY -> "1 screenshot"
                ScanInputOrigin.CAMERA -> "1 photo"
                ScanInputOrigin.FILES -> "1 file"
            }
        }
        val allPhotos = inputs.all { it.origin == ScanInputOrigin.PHOTO_LIBRARY }
        return if (allPhotos) "${inputs.size} screenshots" else "${inputs.size} files"
    }

    /** Splits uploaded documents into scan-sized groups, preserving order. */
    fun <T> chunks(documents: List<T>, size: Int = MAX_DOCUMENTS): List<List<T>> =
        if (documents.isEmpty()) emptyList() else documents.chunked(size)

    /** "Statement.pdf (1 of 2)" when an upload is split; the label as is otherwise. */
    fun chunkLabel(label: String, index: Int, count: Int): String =
        if (count > 1) "$label (${index + 1} of $count)" else label

    /** The reading screen's unit: pages for a PDF, images for photos, files otherwise. */
    fun readingUnit(inputs: List<ScanInput>): String = when {
        inputs.any { it.kind == ScanInputKind.PDF } -> Strings.get(R.string.data_scan_page)
        inputs.all { it.kind == ScanInputKind.IMAGE } -> Strings.get(R.string.common_image)
        else -> Strings.get(R.string.data_scan_file)
    }

    /** Body for `POST /scans`. A spreadsheet never carries a `pdfGroupId` (the server rejects it with 400). */
    fun createRequest(
        documents: List<UploadedScanDocument>,
        sourceLabel: String,
        context: ScanContext,
    ): CreateScanRequest = CreateScanRequest(
        documents = documents.map { document ->
            CreateScanDocument(
                documentKey = document.documentKey,
                kind = document.kind.wire,
                filename = document.filename?.takeIf { it.isNotEmpty() }?.take(255),
                pdfGroupId = document.pdfGroupId.takeIf { document.kind != ScanDocumentKind.SPREADSHEET },
            )
        },
        referenceDate = context.referenceDate.toString(),
        locale = context.locale,
        userCurrency = context.currency.takeIf { it.isNotBlank() },
        sourceLabel = sourceLabel.take(MAX_SOURCE_LABEL_LENGTH),
    )
}

/** Mirrors the server's charge: 1 credit per file, a PDF 1 per started block of 10 pages. The server stays the source of truth. */
object ScanCredits {
    const val PAGES_PER_CREDIT = 10

    fun forPdfPages(pages: Int): Int = maxOf(1, (maxOf(1, pages) + PAGES_PER_CREDIT - 1) / PAGES_PER_CREDIT)

    /** One entry per picked file; [pages] null = an unreadable PDF (1 credit). */
    data class FileEstimate(val isPdf: Boolean, val pages: Int?)

    fun forFiles(files: List<FileEstimate>): Int = files.sumOf { file ->
        if (file.isPdf && file.pages != null) forPdfPages(file.pages) else 1
    }

    /** "You have N scans left — pick up to N" when the selection costs more than the user has; null when it fits or is unlimited. */
    fun overLimitMessage(estimatedCredits: Int, remaining: Int?): String? {
        if (remaining == null || remaining <= 0 || estimatedCredits <= remaining) return null
        return if (remaining == 1) {
            Strings.get(R.string.data_scan_you_have_1_scan_left)
        } else {
            Strings.get(R.string.data_scan_you_have_scans_left_pick, remaining, remaining)
        }
    }
}

/**
 * Where the picker learns how many scan credits the user has left. `null` = unknown or unlimited: no cap
 * message. The quota / premium model plugs in by providing its own implementation (see [ScanCreditsModule]).
 */
fun interface ScanCreditsProvider {
    suspend fun remainingScanCredits(): Int?

    /** A scan was started (credits reserved on the server): the next count must be re-read. */
    fun spent() {}
}
