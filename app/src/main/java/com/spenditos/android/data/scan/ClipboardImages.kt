package com.spenditos.android.data.scan

import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The images on the system clipboard, for Scan's "Paste screenshot" (Bmbsolution/spenditos#394).
 * Nothing here logs or keeps clipboard content: callers see content URIs and counts only.
 */
interface ClipboardImages {
    /**
     * True when the clipboard holds an image. Reads only the clip's description (its MIME types), never its data,
     * so asking is silent: no "pasted from your clipboard" notice, and safe to call when a menu opens.
     */
    fun hasImage(): Boolean

    /** The clip's image items in clipboard order, at most [ScanRules.MAX_DOCUMENTS_PER_UPLOAD]; empty when there are none. */
    fun imageUris(): List<Uri>
}

@Singleton
class AndroidClipboardImages @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClipboardImages {
    private val manager: ClipboardManager? get() = context.getSystemService(ClipboardManager::class.java)

    override fun hasImage(): Boolean = try {
        val clipboard = manager
        clipboard != null && clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(IMAGE_MIME) == true
    } catch (_: RuntimeException) {
        false
    }

    override fun imageUris(): List<Uri> = try {
        val clip = manager?.primaryClip
        if (clip == null) {
            emptyList()
        } else {
            val described = clip.description.hasMimeType(IMAGE_MIME)
            (0 until clip.itemCount)
                // Content URIs only: a file:// URI from another app must never make this app read its own files.
                .mapNotNull { clip.getItemAt(it).uri?.takeIf { uri -> uri.scheme == ContentResolver.SCHEME_CONTENT } }
                .filter { uri -> isImage(uri, described) }
                .take(ScanRules.MAX_DOCUMENTS_PER_UPLOAD)
        }
    } catch (_: RuntimeException) {
        emptyList()
    }

    /** The provider's own type when it states one; else the clip's description (an image clip whose provider does not say). */
    private fun isImage(uri: Uri, described: Boolean): Boolean {
        val type = try {
            context.contentResolver.getType(uri)
        } catch (_: RuntimeException) {
            null
        }
        return if (type != null) type.startsWith("image/") else described
    }

    private companion object {
        const val IMAGE_MIME = "image/*"
    }
}
