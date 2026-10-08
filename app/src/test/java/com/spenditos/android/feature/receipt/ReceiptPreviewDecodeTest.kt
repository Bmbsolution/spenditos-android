package com.spenditos.android.feature.receipt

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The Retake / Use Photo preview decode: bounded sampling, EXIF-oriented, never crashing. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ReceiptPreviewDecodeTest {
    @Test
    fun `sampling halves until the long edge is near 2048`() {
        assertEquals(1, previewSampleSize(2048, 1536))
        assertEquals(1, previewSampleSize(4000, 3000))
        assertEquals(1, previewSampleSize(4095, 100))
        assertEquals(2, previewSampleSize(8000, 6000)) // a 48 MP pick
        assertEquals(4, previewSampleSize(16000, 12000))
    }

    @Test
    fun `a garbage file decodes to null instead of throwing`() = runTest {
        val garbage = File.createTempFile("receipt-preview-garbage", ".jpg")
        garbage.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        try {
            assertNull(decodePreviewBitmap(ApplicationProvider.getApplicationContext<Context>(), garbage))
        } finally {
            assertTrue(garbage.delete())
        }
    }

    @Test
    fun `a missing file decodes to null instead of throwing`() = runTest {
        val missing = File.createTempFile("receipt-preview-missing", ".jpg")
        assertTrue(missing.delete())
        assertNull(decodePreviewBitmap(ApplicationProvider.getApplicationContext<Context>(), missing))
    }

    @Test
    fun `a large jpeg decodes to at most 2048 on the long edge`() = runTest {
        val source = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("receipt-preview-big", ".jpg")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        try {
            val decoded = decodePreviewBitmap(ApplicationProvider.getApplicationContext<Context>(), file)
            assertNotNull(decoded)
            assertEquals(2048, decoded!!.width.toLong())
            assertEquals(1536, decoded.height.toLong())
        } finally {
            assertTrue(file.delete())
        }
    }

    @Test
    fun `an EXIF rotate-90 portrait shows upright`() = runTest {
        val file = File.createTempFile("receipt-preview-exif", ".jpg")
        file.outputStream().use {
            Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        // Skipped (not failed) on JVM runners that cannot persist EXIF metadata.
        assumeTrue(
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ==
                ExifInterface.ORIENTATION_ROTATE_90,
        )
        try {
            val decoded = decodePreviewBitmap(ApplicationProvider.getApplicationContext<Context>(), file)
            assertNotNull(decoded)
            assertEquals(200, decoded!!.width.toLong()) // 400x200 rotated upright
            assertEquals(400, decoded.height.toLong())
        } finally {
            assertTrue(file.delete())
        }
    }
}
