package com.yaadein.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Photos and videos taken with the phone's camera app from inside Yaadein ("Take photo" / "Record video").
 *
 * The camera app is given a file in Yaadein's own `cache/camera/` to write into (FileProvider URI with a one-off
 * write grant), so the shot never goes to the gallery, Google Photos or any shared folder. The plain file is deleted
 * as soon as the importer has copied it for encryption, when the user drops it, or after a day if it was abandoned.
 */
object CameraCapture {
    /** An empty private file for the camera app to fill. */
    fun newTarget(context: Context, video: Boolean): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        pruneOld(dir)
        val file = File(dir, (if (video) "video_" else "photo_") + System.currentTimeMillis() + (if (video) ".mp4" else ".jpg"))
        file.createNewFile()
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    /**
     * Call when the camera app returns. True when it wrote something (some camera apps report "cancelled" although they
     * saved, so the file decides); an empty file is removed.
     */
    fun finished(context: Context, uri: Uri): Boolean {
        val file = fileOf(context, uri) ?: return false
        if (file.length() > 0L) return true
        file.delete()
        return false
    }

    /** Deletes the plain file behind [uri] if it is one of ours; any other URI is left alone. */
    fun discard(context: Context, uri: Uri) {
        fileOf(context, uri)?.delete()
    }

    private fun fileOf(context: Context, uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != context.packageName + ".files") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != DIR) return null
        return File(File(context.cacheDir, DIR), segments[1]).takeIf { it.parentFile?.name == DIR && it.isFile }
    }

    private fun pruneOld(dir: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private const val DIR = "camera"
    private const val KEEP_MS = 24 * 60 * 60 * 1000L
}
