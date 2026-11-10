package com.yaadein.data.reels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.yaadein.data.media.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ReelKind { USER, AUTO }

/** One reel kept in the app. The video itself is encrypted in the vault ([videoPath] is relative to [ReelLibrary.dir]). */
@Serializable
data class ReelEntry(
    val id: String,
    val title: String,
    val kind: ReelKind,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val videoPath: String,
    /** A photo of the reel, shown as its cover (its vault thumbnail); null for reels made only of words. */
    val coverMediaId: Long? = null,
    /** RecapTheme name, used for the cover gradient when there is no photo. */
    val theme: String = "WARM",
    /** Auto reels only: which of the three slots this is ("recent", "person", "throwback"). */
    val autoSlot: String? = null,
    /** Auto reels only: fingerprint of the photos used, so an unchanged reel is not rendered again. */
    val signature: String? = null,
)

@Serializable
private data class ReelIndex(val reels: List<ReelEntry> = emptyList(), val lastAutoRunAt: Long = 0L)

/**
 * The reels kept inside Yaadein: the last [MAX_USER_REELS] reels the user made and up to [MAX_AUTO_REELS] reels the
 * app makes on its own. Videos are encrypted with the media key (AES-CTR, so the player can seek), the index is an
 * encrypted JSON file written atomically (temp file + rename). Everything lives under the vault, so "Erase everything"
 * removes it too.
 */
@Singleton
class ReelLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStore,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _reels = MutableStateFlow<List<ReelEntry>>(emptyList())
    private var loaded = false
    private var lastAutoRunAt = 0L

    val dir: File get() = mediaStore.reelDir
    private val indexFile: File get() = File(dir, INDEX_NAME)

    /** All kept reels, newest first. Call [load] (or any suspend function here) once to fill it. */
    val reels: StateFlow<List<ReelEntry>> = _reels.asStateFlow()

    suspend fun load(): List<ReelEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            // Videos can vanish under us (Erase everything wipes the vault); forget those entries.
            val present = _reels.value.filter { File(dir, it.videoPath).exists() }
            if (present.size != _reels.value.size) save(present)
            _reels.value
        }
    }

    suspend fun get(id: String): ReelEntry? = load().firstOrNull { it.id == id }

    suspend fun lastAutoRunAt(): Long = mutex.withLock { ensureLoaded(); lastAutoRunAt }

    suspend fun markAutoRun(at: Long) = mutex.withLock { ensureLoaded(); lastAutoRunAt = at; save(_reels.value) }

    /**
     * Encrypts [rendered] into the library and returns the new entry. A user reel pushes out the oldest user reel
     * beyond [MAX_USER_REELS]; an auto reel replaces the reel in the same [ReelEntry.autoSlot]. [rendered] is left in place.
     */
    suspend fun add(
        rendered: File,
        title: String,
        kind: ReelKind,
        durationMs: Long,
        coverMediaId: Long?,
        theme: String,
        autoSlot: String? = null,
        signature: String? = null,
        now: Long = System.currentTimeMillis(),
    ): ReelEntry = withContext(Dispatchers.IO) {
        // Load first: the first load deletes stray files in the folder, which must not include the one being written.
        mutex.withLock { ensureLoaded() }
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.mp4.enc")
        val tmp = File(dir, "$id.tmp")
        rendered.inputStream().use { mediaStore.crypto.encryptFile(it, tmp) }
        check(tmp.renameTo(target)) { "Could not store the reel" }
        val entry = ReelEntry(
            id = id, title = title, kind = kind, createdAt = now, durationMs = durationMs, sizeBytes = rendered.length(),
            videoPath = target.name, coverMediaId = coverMediaId, theme = theme, autoSlot = autoSlot, signature = signature,
        )
        mutex.withLock {
            ensureLoaded()
            val (kept, dropped) = retain(listOf(entry) + _reels.value)
            save(kept)
            dropped.forEach { File(dir, it.videoPath).delete() }
        }
        entry
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val entry = _reels.value.firstOrNull { it.id == id } ?: return@withLock
            save(_reels.value - entry)
            File(dir, entry.videoPath).delete()
        }
    }

    /** Removes auto reels whose slot is no longer in use (for example the library got smaller). */
    suspend fun retainAutoSlots(slots: Set<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val stale = _reels.value.filter { it.kind == ReelKind.AUTO && it.autoSlot !in slots }
            if (stale.isEmpty()) return@withLock
            save(_reels.value - stale.toSet())
            stale.forEach { File(dir, it.videoPath).delete() }
        }
    }

    internal fun cryptoForPlayback() = mediaStore.crypto

    /** The encrypted video file of [entry], for [EncryptedFileDataSource]. */
    fun videoFile(entry: ReelEntry): File = File(dir, entry.videoPath)

    /**
     * Decrypts [entry] into the shareable exports folder (`cache/exports/`, a FileProvider root) and returns the
     * content URI another app can read with [Intent.FLAG_GRANT_READ_URI_PERMISSION]. Only the most recent export is
     * kept there. Used by [shareIntent] and by "Send to WhatsApp", which targets one package instead of a chooser.
     */
    suspend fun shareUri(entry: ReelEntry): Uri = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        exportDir.listFiles { f -> f.name.startsWith(EXPORT_PREFIX) }?.forEach { it.delete() }
        val out = File(exportDir, exportName(entry))
        mediaStore.crypto.decryptingStream(videoFile(entry).inputStream()).use { input -> out.outputStream().use { input.copyTo(it) } }
        FileProvider.getUriForFile(context, context.packageName + ".files", out)
    }

    /**
     * Decrypts [entry] into the shareable exports folder and returns a ready ACTION_SEND chooser.
     * Only the most recent export is kept there.
     */
    suspend fun shareIntent(entry: ReelEntry, chooserTitle: CharSequence): Intent {
        val uri = shareUri(entry)
        val send = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newUri(context.contentResolver, exportName(entry), uri)
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun exportName(entry: ReelEntry): String =
        EXPORT_PREFIX + entry.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "Yaadein" } + ".mp4"

    private fun ensureLoaded() {
        if (loaded) return
        val index = runCatching {
            if (indexFile.exists()) json.decodeFromString(ReelIndex.serializer(), mediaStore.crypto.decryptToBytes(indexFile).decodeToString()) else ReelIndex()
        }.getOrDefault(ReelIndex())
        // Drop entries whose video is gone (e.g. a restore replaced the vault) and stray videos no entry points to.
        val present = index.reels.filter { File(dir, it.videoPath).exists() }
        val known = present.mapTo(HashSet()) { it.videoPath }
        dir.listFiles()?.forEach { f -> if (f.name != INDEX_NAME && f.name !in known) f.delete() }
        _reels.value = present.sortedByDescending { it.createdAt }
        lastAutoRunAt = index.lastAutoRunAt
        loaded = true
    }

    private fun save(reels: List<ReelEntry>) {
        val sorted = reels.sortedByDescending { it.createdAt }
        val bytes = json.encodeToString(ReelIndex.serializer(), ReelIndex(sorted, lastAutoRunAt)).encodeToByteArray()
        val tmp = File(dir, "$INDEX_NAME.tmp")
        mediaStore.crypto.encryptBytes(bytes, tmp)
        check(tmp.renameTo(indexFile)) { "Could not save the reel list" }
        _reels.value = sorted
    }

    companion object {
        const val MAX_USER_REELS = 10
        const val MAX_AUTO_REELS = 3
        private const val INDEX_NAME = "index.enc"
        private const val EXPORT_PREFIX = "reel-"

        /** Newest first: keeps the newest [MAX_USER_REELS] user reels and the newest auto reel per slot (at most [MAX_AUTO_REELS]). */
        internal fun retain(newestFirst: List<ReelEntry>): Pair<List<ReelEntry>, List<ReelEntry>> {
            val sorted = newestFirst.sortedByDescending { it.createdAt }
            val user = sorted.filter { it.kind == ReelKind.USER }
            val auto = sorted.filter { it.kind == ReelKind.AUTO }
            val keptUser = user.take(MAX_USER_REELS)
            val keptAuto = auto.distinctBy { it.autoSlot ?: it.id }.take(MAX_AUTO_REELS)
            val kept = (keptUser + keptAuto).sortedByDescending { it.createdAt }
            return kept to (sorted - kept.toSet())
        }
    }
}

/** Player plumbing for reels kept in the library: `yaadein-reel://reel/{id}` URIs decrypted on the fly. */
@androidx.media3.common.util.UnstableApi
fun ReelLibrary.dataSourceFactory(): androidx.media3.datasource.DataSource.Factory =
    EncryptedFileDataSource.Factory(cryptoForPlayback()) { uri -> reels.value.firstOrNull { it.id == uri.lastPathSegment }?.let(::videoFile) }

fun reelUri(entry: ReelEntry): Uri = Uri.parse("yaadein-reel://reel/${entry.id}")
