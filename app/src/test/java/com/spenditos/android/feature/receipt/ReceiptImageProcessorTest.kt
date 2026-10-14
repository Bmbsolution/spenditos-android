package com.spenditos.android.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

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
