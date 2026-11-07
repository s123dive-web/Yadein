package com.yaadein.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.importing.ExportParsing
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.repository.MemoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Brings a Google Photos Takeout zip or a WhatsApp "Export chat" zip into Yaadein: photos and videos keep their real
 * dates (Takeout JSON sidecars, WhatsApp file names, or EXIF), Takeout albums become one memory each and the rest one
 * memory per day. Files are copied out of the zip one by one into the app cache, imported through the normal encrypted
 * pipeline, and deleted.
 */
class ExportImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: MediaImporter,
    private val memoryRepository: MemoryRepository,
) {
    sealed interface Progress {
        data class Reading(val files: Int) : Progress
        data class Importing(val done: Int, val total: Int) : Progress
    }

    data class Result(val memories: Int, val files: Int, val skipped: Int, val firstMemoryId: Long?)

    class NotAnExportException : Exception("no photos or videos in this file")
    class NotEnoughSpaceException(val neededBytes: Long) : Exception("not enough free space")

    suspend fun import(zip: Uri, optimize: Boolean, untitled: (LocalDate) -> String, chatTitle: (String, LocalDate) -> String, onProgress: (Progress) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val zipName = runCatching {
                resolver.query(zip, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            val chat = ExportParsing.chatName(zipName)
            val zipSize = runCatching {
                resolver.query(zip, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
            }.getOrNull()
            // The zip's photos are copied out, then encrypted into the vault: about twice its size, briefly.
            val free = android.os.StatFs(context.cacheDir.path).availableBytes
            if (zipSize != null && free < zipSize * 2 + SPACE_MARGIN) throw NotEnoughSpaceException(zipSize * 2 + SPACE_MARGIN)
            val dir = File(context.cacheDir, "shared/export-${System.nanoTime()}").apply { mkdirs() }
            try {
                // Pass 1: extract media, read sidecar dates.
                val extracted = LinkedHashMap<String, File>()
                val sidecarDates = HashMap<String, Long>()
                ZipInputStream(resolver.openInputStream(zip) ?: error("cannot open file")).use { zin ->
                    var entry = zin.nextEntry
                    var n = 0
                    while (entry != null) {
                        coroutineContext.ensureActive()
                        val name = entry.name
                        if (!entry.isDirectory && !name.contains("..")) {
                            val kind = ExportParsing.kindOf(name)
                            val sidecar = ExportParsing.sidecarTarget(name)
                            when {
                                kind != null && extracted.size < MAX_FILES -> {
                                    val ext = name.substringAfterLast('.').lowercase()
                                    val out = File(dir, "${n++}.$ext")
                                    out.outputStream().use { zin.copyTo(it) }
                                    extracted[name] = out
                                    onProgress(Progress.Reading(extracted.size))
                                }
                                sidecar != null -> ExportParsing.takenFromSidecar(zin.readBytes().decodeToString(throwOnInvalidSequence = false))?.let { sidecarDates[sidecar] = it }
                            }
                        }
                        entry = zin.nextEntry
                    }
                }
                if (extracted.isEmpty()) throw NotAnExportException()
                val items = extracted.keys.map { path ->
                    ExportParsing.Item(path, ExportParsing.kindOf(path)!!, sidecarDates[path], if (chat != null) null else ExportParsing.albumOf(path))
                }
                val groups = ExportParsing.groups(items, ZoneId.systemDefault(), LocalDate.now())
                // Pass 2: one memory per group, files imported into it.
                var done = 0
                var skipped = 0
                var first: Long? = null
                val total = items.size
                for (group in groups) {
                    coroutineContext.ensureActive()
                    val title = group.title ?: chat?.let { chatTitle(it, group.start) } ?: untitled(group.start)
                    val date = if (group.end != group.start) Dates.exact(group.start, group.end) else Dates.exact(group.start)
                    val memoryId = memoryRepository.create(Memory(type = MemoryType.EVENT, title = title, date = date), emptyList())
                    if (first == null) first = memoryId
                    group.items.forEachIndexed { position, item ->
                        val file = extracted[item.path] ?: return@forEachIndexed
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        val picked = importer.inspect(uri)?.let { p -> item.takenAtMillis?.let { p.copy(takenAtMillis = it) } ?: p }
                        val outcome = picked?.let { importer.import(it, memoryId, optimize, position) }
                        if (outcome !is ImportOutcome.Success) skipped++
                        file.delete()
                        onProgress(Progress.Importing(++done, total))
                    }
                }
                Result(groups.size, total - skipped, skipped, first)
            } finally {
                dir.deleteRecursively()
            }
        }

    companion object {
        /** One import at a time stays manageable on a phone; a bigger export can be brought in in parts. */
        const val MAX_FILES = 3000
        private const val SPACE_MARGIN = 200L * 1024 * 1024
    }
}
