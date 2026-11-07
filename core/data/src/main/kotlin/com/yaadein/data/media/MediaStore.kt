package com.yaadein.data.media

import android.content.Context
import com.yaadein.data.crypto.MediaCrypto
import java.io.File
import java.util.UUID

/**
 * Owns the app-private directories where encrypted media lives.
 *
 * media/   full-size encrypted photos and videos
 * thumbs/  encrypted JPEG thumbnails
 * reels/   the in-app reel library (encrypted MP4s + index, see ReelLibrary)
 * tmp/     in-flight imports (temp-file-then-rename keeps writes atomic)
 */
class MediaStore(context: Context, val crypto: MediaCrypto) {
    private val root: File = File(context.filesDir, "vault").apply { mkdirs() }
    val mediaDir: File = File(root, "media").apply { mkdirs() }
    val thumbDir: File = File(root, "thumbs").apply { mkdirs() }
    /** Not counted by [usedBytes] or touched by [pruneOrphans]; removed by [wipeAll] with the rest of the vault. */
    val reelDir: File get() = File(root, "reels").apply { mkdirs() }
    val tmpDir: File = File(context.cacheDir, "import_tmp").apply { mkdirs() }
    val renderDir: File = File(context.cacheDir, "render").apply { mkdirs() }

    fun newMediaFile(extension: String): File = File(mediaDir, "${UUID.randomUUID()}.$extension.enc")
    fun newThumbFile(): File = File(thumbDir, "${UUID.randomUUID()}.jpg.enc")
    fun newTempFile(): File = File(tmpDir, "${UUID.randomUUID()}.tmp")

    fun file(relativeOrAbsolute: String): File {
        val f = File(relativeOrAbsolute)
        return if (f.isAbsolute) f else File(root, relativeOrAbsolute)
    }

    /** Store paths relative to the vault root so the vault survives a change of filesDir (e.g. restore). */
    fun relativize(file: File): String = file.relativeTo(root).path

    fun deleteFiles(vararg paths: String) {
        paths.forEach { runCatching { file(it).delete() } }
    }

    fun usedBytes(): Long = allFiles().sumOf { it.length() }

    private fun allFiles(): List<File> = (mediaDir.listFiles()?.toList() ?: emptyList()) + (thumbDir.listFiles()?.toList() ?: emptyList())

    fun clearTemp() {
        tmpDir.listFiles()?.forEach { it.delete() }
    }

    /** Deletes files on disk that no DB row references (crash recovery). */
    fun pruneOrphans(referenced: Set<String>) {
        allFiles().forEach { f ->
            if (relativize(f) !in referenced) f.delete()
        }
    }

    fun wipeAll() {
        root.deleteRecursively(); root.mkdirs(); mediaDir.mkdirs(); thumbDir.mkdirs()
        clearTemp()
    }
}
