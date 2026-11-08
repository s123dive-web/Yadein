package com.yaadein.data.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.yaadein.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import android.provider.MediaStore as AndroidMediaStore

/**
 * Downloads photos and videos out of the encrypted vault as normal, unencrypted files the person can open in their
 * gallery or send anywhere.
 *
 * - Android 10 and later: straight into the shared gallery (Pictures/Yaadein and Movies/Yaadein) through MediaStore.
 *   No storage permission is needed for files the app adds itself.
 * - Android 8 and 9: writing to the shared gallery would need a storage permission, which Yaadein never asks for, so
 *   the person picks a folder once (system folder picker) and the files are written there.
 *
 * The plaintext is streamed: the encrypted file is decrypted chunk by chunk straight into the target, so a large video
 * never sits decrypted in memory or in a temp file.
 */
class MediaDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val store: MediaStore,
) {
    data class Result(val saved: Int, val failed: Int)

    private val resolver: ContentResolver get() = context.contentResolver

    /** Saves to the phone's gallery (Android 10+). Callers on older phones use [saveToFolder] instead. */
    suspend fun saveToGallery(mediaIds: List<Long>): Result = withContext(Dispatchers.IO) {
        check(supportsGallery) { "Gallery downloads need Android 10" }
        run(mediaIds) { item -> insertIntoGallery(item) }
    }

    /** Saves into a folder the person picked with the system folder picker (ACTION_OPEN_DOCUMENT_TREE). */
    suspend fun saveToFolder(mediaIds: List<Long>, tree: Uri): Result = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        run(mediaIds) { item ->
            val uri = DocumentsContract.createDocument(resolver, parent, item.mime, item.fileName) ?: error("createDocument failed")
            try {
                write(item, uri)
            } catch (t: Throwable) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                throw t
            }
        }
    }

    private suspend fun run(mediaIds: List<Long>, save: (Item) -> Unit): Result {
        var saved = 0
        var failed = 0
        mediaIds.distinct().forEach { id ->
            val ok = runCatching {
                val row = db.mediaDao().get(id) ?: error("media $id not found")
                val file = store.file(row.encryptedPath)
                val isVideo = row.kind == "VIDEO"
                val type = store.crypto.decryptingStream(file.inputStream()).use { sniff(it, isVideo) }
                save(Item(file, type.mime, isVideo, fileName(id, row.takenAt ?: row.createdAt, type.extension), row.takenAt))
            }.isSuccess
            if (ok) saved++ else failed++
        }
        return Result(saved, failed)
    }

    private fun insertIntoGallery(item: Item) {
        val collection = if (item.isVideo) AndroidMediaStore.Video.Media.EXTERNAL_CONTENT_URI else AndroidMediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val folder = (if (item.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/" + FOLDER
        val values = ContentValues().apply {
            put(AndroidMediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(AndroidMediaStore.MediaColumns.MIME_TYPE, item.mime)
            put(AndroidMediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(AndroidMediaStore.MediaColumns.IS_PENDING, 1)
            // Keeps the photo in its real place in the gallery's timeline instead of "today".
            item.takenAt?.let { put(AndroidMediaStore.MediaColumns.DATE_TAKEN, it) }
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            write(item, uri)
            resolver.update(uri, ContentValues().apply { put(AndroidMediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    private fun write(item: Item, target: Uri) {
        val out: OutputStream = resolver.openOutputStream(target, "w") ?: error("openOutputStream returned null")
        out.use { o -> store.crypto.decryptingStream(item.file.inputStream()).use { it.copyTo(o, BUFFER) } }
    }

    private class Item(val file: File, val mime: String, val isVideo: Boolean, val fileName: String, val takenAt: Long?)

    companion object {
        const val FOLDER = "Yaadein"
        private const val BUFFER = 256 * 1024

        val supportsGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /** e.g. Yaadein_20240314_181502_42.jpg: sorts by date in any file manager and never collides. */
        internal fun fileName(id: Long, millis: Long, extension: String): String {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(millis))
            return "${FOLDER}_${stamp}_$id.$extension"
        }

        internal data class FileType(val mime: String, val extension: String)

        /** Reads the first bytes to tell what the original file was (imports keep the original when not optimised). */
        internal fun sniff(input: InputStream, isVideo: Boolean): FileType {
            val head = ByteArray(16)
            var n = 0
            while (n < head.size) {
                val r = input.read(head, n, head.size - n)
                if (r < 0) break
                n += r
            }
            fun at(offset: Int, vararg bytes: Int) = n >= offset + bytes.size && bytes.indices.all { head[offset + it] == bytes[it].toByte() }
            fun ascii(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
            return when {
                at(0, 0xFF, 0xD8, 0xFF) -> FileType("image/jpeg", "jpg")
                at(0, 0x89, 0x50, 0x4E, 0x47) -> FileType("image/png", "png")
                ascii(0, "GIF8") -> FileType("image/gif", "gif")
                ascii(0, "RIFF") && ascii(8, "WEBP") -> FileType("image/webp", "webp")
                ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1")) -> FileType("image/heic", "heic")
                ascii(4, "ftyp") && ascii(8, "qt  ") -> FileType("video/quicktime", "mov")
                ascii(4, "ftyp") && (ascii(8, "3gp") || ascii(8, "3g2")) -> FileType("video/3gpp", "3gp")
                ascii(0, "\u001AEß£") -> FileType("video/webm", "webm")
                isVideo -> FileType("video/mp4", "mp4")
                else -> FileType("image/jpeg", "jpg")
            }
        }
    }
}
