package com.memoredia.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Hands files from the app's cache/share folder to other apps (for the share sheet). */
class ShareProvider : ContentProvider() {
    private fun file(uri: Uri): File? {
        val c = context ?: return null
        val name = uri.lastPathSegment ?: return null
        val dir = File(c.cacheDir, "share")
        val f = File(dir, name)
        return try { if (f.canonicalPath.startsWith(dir.canonicalPath + File.separator) && f.exists()) f else null } catch (e: Throwable) { null }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = file(uri) ?: throw FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = file(uri) ?: return null
        val cur = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cur.addRow(arrayOf<Any>(f.name, f.length()))
        return cur
    }

    override fun getType(uri: Uri): String? {
        val n = (uri.lastPathSegment ?: "").lowercase()
        return when {
            n.endsWith(".pdf") -> "application/pdf"
            n.endsWith(".png") -> "image/png"
            n.endsWith(".jpg") || n.endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
