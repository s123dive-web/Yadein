package com.yaadein.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos and videos shared into Yaadein from Google Photos or any gallery ("Share → Yaadein").
 *
 * The sender's read grant only lasts while our activity is alive, and imports run after the user saves the
 * memory, so each file is copied straight into `cache/shared/` and handed on as a FileProvider URI (which
 * gives the importer a real mime type, name and size). Nothing is shown until the vault is unlocked.
 */
object SharedMediaInbox {
    private val _pending = MutableStateFlow<List<Uri>>(emptyList())
    /** Copied, ready-to-import URIs waiting for the Create memory screen. */
    val pending: StateFlow<List<Uri>> = _pending.asStateFlow()

    fun take(): List<Uri> = _pending.value.also { _pending.value = emptyList() }

    fun isShare(intent: Intent?): Boolean =
        intent != null && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && streams(intent).isNotEmpty()

    /** Copies the intent's media into the cache; returns how many were accepted. */
    suspend fun receive(context: Context, intent: Intent): Int = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "$DIR/${System.currentTimeMillis()}").apply { mkdirs() }
        pruneOld(context, keep = dir)
        val copied = streams(intent).mapIndexedNotNull { i, source ->
            runCatching {
                val mime = context.contentResolver.getType(source) ?: intent.type
                if (mime == null || !(mime.startsWith("image/") || mime.startsWith("video/"))) return@runCatching null
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (mime.startsWith("video/")) "mp4" else "jpg"
                val target = File(dir, "shared_${i + 1}.$ext")
                context.contentResolver.openInputStream(source)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
                FileProvider.getUriForFile(context, context.packageName + ".files", target)
            }.getOrNull()
        }
        if (copied.isNotEmpty()) _pending.value = copied
        copied.size
    }

    private fun streams(intent: Intent): List<Uri> {
        val list = LinkedHashSet<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(list::addAll)
        } else {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(list::add)
        }
        // Some apps only put the files in ClipData.
        intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(list::add) }
        return list.take(MAX_ITEMS)
    }

    /** Earlier shares are already imported (or abandoned) by the time a new one arrives. */
    private fun pruneOld(context: Context, keep: File) {
        File(context.cacheDir, DIR).listFiles()?.filter { it != keep }?.forEach { it.deleteRecursively() }
    }

    private const val DIR = "shared"
    /** Same cap as the in-app picker. */
    const val MAX_ITEMS = 50
}
