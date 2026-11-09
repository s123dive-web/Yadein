package com.yaadein.data.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.robolectric.Robolectric
import java.io.File

/**
 * A gallery-like content provider for Robolectric tests: `content://test.media/<file>` serves a file from
 * `cacheDir/testmedia/`, with a mime type from its extension, a size and a display name, and a fresh stream on every
 * open (the shadow resolver's `registerInputStream` hands out one exhausted stream).
 */
class FakeMediaProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (uri.lastPathSegment?.substringAfterLast('.')?.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileFor(context!!, uri)
        if (!f.exists()) return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = fileFor(context!!, uri)
        val cols = projection ?: arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME)
        val cursor = MatrixCursor(cols)
        cursor.addRow(cols.map { c -> if (c == OpenableColumns.SIZE) f.length() else f.name })
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "test.media"

        fun fileFor(context: Context, uri: Uri): File = File(File(context.cacheDir, "testmedia"), uri.lastPathSegment!!)

        /** Registers the provider and returns a function that stores [bytes] under [name] and gives back its URI. */
        fun install(context: Context): (String, ByteArray) -> Uri {
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, AUTHORITY)
            return { name, bytes ->
                fileFor(context, Uri.parse("content://$AUTHORITY/$name")).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
                Uri.parse("content://$AUTHORITY/$name")
            }
        }
    }
}
