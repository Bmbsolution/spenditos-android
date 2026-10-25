package com.spenditos.android.data.scan

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClipboardImagesTest {
    private lateinit var context: Context
    private lateinit var manager: ClipboardManager
    private lateinit var clipboard: AndroidClipboardImages

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(ClipboardManager::class.java)
        clipboard = AndroidClipboardImages(context)
    }

    private fun imageClip(vararg uris: String): ClipData {
        val items = uris.map { ClipData.Item(Uri.parse(it)) }
        val clip = ClipData(ClipDescription("image", arrayOf("image/png")), items.first())
        items.drop(1).forEach(clip::addItem)
        return clip
    }

    @Test
    fun `an empty clipboard has no image`() {
        manager.clearPrimaryClip()
        assertFalse(clipboard.hasImage())
        assertTrue(clipboard.imageUris().isEmpty())
    }

    @Test
    fun `a text clip has no image`() {
        manager.setPrimaryClip(ClipData.newPlainText("note", "lunch 12.50"))
        assertFalse(clipboard.hasImage())
        assertTrue(clipboard.imageUris().isEmpty())
    }

    @Test
    fun `an image clip is offered and its uri is returned`() {
        manager.setPrimaryClip(imageClip("content://shots/1"))
        assertTrue(clipboard.hasImage())
        assertEquals(listOf(Uri.parse("content://shots/1")), clipboard.imageUris())
    }

    @Test
    fun `several images come back in clipboard order`() {
        manager.setPrimaryClip(imageClip("content://shots/b", "content://shots/a", "content://shots/c"))
        assertEquals(
            listOf("b", "a", "c"),
            clipboard.imageUris().map { it.lastPathSegment },
        )
    }

    @Test
    fun `only content uris are read, never a file uri from another app`() {
        manager.setPrimaryClip(imageClip("file:///data/data/com.spenditos.android/files/secret.png", "content://shots/ok"))
        assertEquals(listOf(Uri.parse("content://shots/ok")), clipboard.imageUris())
    }

    @Test
    fun `an image clip is capped at the documents one upload takes`() {
        manager.setPrimaryClip(imageClip(*Array(ScanRules.MAX_DOCUMENTS_PER_UPLOAD + 10) { "content://shots/$it" }))
        assertEquals(ScanRules.MAX_DOCUMENTS_PER_UPLOAD, clipboard.imageUris().size)
    }
}
