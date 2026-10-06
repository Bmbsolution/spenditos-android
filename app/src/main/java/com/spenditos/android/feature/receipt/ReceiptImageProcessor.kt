package com.spenditos.android.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import javax.inject.Singleton

interface ReceiptImageProcessing {
    fun createTempFile(): File
    fun process(uri: Uri, destination: File)
    fun deleteCameraFile(uriString: String)
}

@Module
@InstallIn(SingletonComponent::class)
object ReceiptImageProcessorModule {
    @Provides
    @Singleton
    fun receiptImageProcessor(@ApplicationContext context: Context): ReceiptImageProcessing = ReceiptImageProcessor(context)
}

class ReceiptImageProcessor(
    private val context: Context,
    now: () -> Long = System::currentTimeMillis,
) : ReceiptImageProcessing {
    init {
        // Leftovers of a process that died mid-scan. Scan copies are never resumed, so all go.
        // Camera originals (full size, with EXIF location) are kept for a while because a camera
        // result can still arrive after process death; older ones were abandoned and are removed.
        val staleBefore = now() - STALE_CAMERA_FILE_MILLIS
        File(context.cacheDir, RECEIPT_DIRECTORY).apply { mkdirs() }
            .listFiles { file ->
                file.name.startsWith("receipt-scan-") ||
                    (file.name.startsWith("receipt-camera-") && file.lastModified() < staleBefore)
            }
            ?.forEach { it.delete() }
    }

    override fun createTempFile(): File {
        val directory = File(context.cacheDir, RECEIPT_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Unable to prepare receipt image")
        return File.createTempFile("receipt-scan-", ".jpg", directory)
    }

    override fun process(uri: Uri, destination: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: throw IllegalArgumentException("Receipt image could not be opened")
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Receipt image could not be decoded" }

            val orientation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalArgumentException("Receipt image could not be decoded")

            val oriented = orient(decoded, orientation)
            val scaled = fit(oriented)
            FileOutputStream(destination).use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IllegalStateException("Receipt image could not be compressed")
                }
            }
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    override fun deleteCameraFile(uriString: String) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        if (uri.authority != "${context.packageName}.fileprovider") return
        val name = uri.lastPathSegment ?: return
        if ('/' in name || name == "." || name == "..") return
        File(File(context.cacheDir, RECEIPT_DIRECTORY), name).delete()
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun fit(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_LONG_SIDE) return bitmap
        val ratio = MAX_LONG_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_LONG_SIDE) sample *= 2
        return sample
    }

    private companion object {
        const val RECEIPT_DIRECTORY = "receipts"
        const val MAX_LONG_SIDE = 2048
        const val JPEG_QUALITY = 80
        const val STALE_CAMERA_FILE_MILLIS = 60 * 60 * 1000L
    }
}
