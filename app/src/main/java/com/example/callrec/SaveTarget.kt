package com.example.callrec

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Saves straight into the folder chosen under Settings > Default Folder, so no picker is needed each time. */
object SaveTarget {
    /** The chosen folder, but only while the app still holds permission to write into it. */
    fun folder(c: Context): Uri? {
        val u = defaultFolderUri(c) ?: return null
        val ok = try {
            c.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
        } catch (e: Throwable) { false }
        return if (ok) u else null
    }

    /** A new empty file in the default folder, or null when there is no usable folder. */
    fun create(c: Context, name: String, mime: String): Uri? {
        val tree = folder(c) ?: return null
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(c.contentResolver, parent, mime, name)
        } catch (e: Throwable) { null }
    }
}
