package com.yaadein.data.recap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Saves a rendered MP4 to Movies/Yaadein and returns a shareable URI. */
object RecapOutput {
    fun save(context: Context, rendered: File, displayName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Yaadein")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)!!.use { out -> rendered.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            rendered.delete()
            uri
        } else {
            // Android 8–9: app-specific Movies folder (visible to file managers, no permission needed) shared via FileProvider.
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Yaadein").apply { mkdirs() }
            val target = File(dir, "$displayName.mp4")
            rendered.copyTo(target, overwrite = true)
            rendered.delete()
            FileProvider.getUriForFile(context, context.packageName + ".files", target)
        }
    }
}
