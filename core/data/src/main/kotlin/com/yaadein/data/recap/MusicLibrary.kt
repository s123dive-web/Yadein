package com.yaadein.data.recap

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A song on the phone the user added to Yaadein for reels. [uri] is a document URI from the system picker with a
 * persisted read grant; the file itself stays where it is (nothing is copied into the vault).
 */
@Serializable
data class OwnSong(
    val uri: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0L,
    val addedAt: Long = 0L,
) {
    /** "Title · Artist", or just the title. */
    val label: String get() = if (artist.isNullOrBlank()) title else "$title · $artist"
}

@Serializable
private data class MusicIndex(val songs: List<OwnSong> = emptyList())

/**
 * The user's own reel music: every song picked from the phone, kept so it can be chosen again for the next reel
 * without opening the picker. Works with the system document picker only (no audio or storage permission): the
 * picker shows every song on the phone, and the chosen ones are remembered here with a persistable read grant.
 *
 * Nothing leaves the device and no song is copied; the index is an encrypted JSON file in the vault written
 * atomically (temp file + rename), so "Erase everything" removes it too.
 */
@Singleton
class MusicLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStore,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _songs = MutableStateFlow<List<OwnSong>>(emptyList())
    private var loaded = false

    private val indexFile: File get() = File(mediaStore.reelDir, INDEX_NAME)

    /** Songs, most recently added first. Filled by [load] (or any suspend function here). */
    val songs: StateFlow<List<OwnSong>> = _songs.asStateFlow()

    suspend fun load(): List<OwnSong> = withContext(Dispatchers.IO) { mutex.withLock { ensureLoaded(); _songs.value } }

    /**
     * Reads title, artist and length of each picked [uris] (MediaMetadataRetriever, falling back to the file name) and
     * adds them; a song already in the library keeps its place and metadata. Returns the songs for [uris], in order.
     */
    suspend fun add(uris: List<Uri>, now: Long = System.currentTimeMillis()): List<OwnSong> = withContext(Dispatchers.IO) {
        val read = uris.distinct().map { uri -> describe(uri, now) }
        mutex.withLock {
            ensureLoaded()
            val merged = merge(_songs.value, read)
            if (merged != _songs.value) save(merged)
            read.map { r -> merged.first { it.uri == r.uri } }
        }
    }

    suspend fun remove(uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            val kept = _songs.value.filterNot { it.uri == uri }
            if (kept.size != _songs.value.size) save(kept)
        }
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Unit
    }

    /** True when the song can still be opened (not deleted, moved, or its grant revoked). */
    suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { } != null }.getOrDefault(false)
    }

    /** Title, artist and length of [uri]; the display name (without extension) when the file has no tags. */
    internal fun describe(uri: Uri, now: Long): OwnSong {
        var title: String? = null
        var artist: String? = null
        var duration = 0L
        // release(), not use {}: MediaMetadataRetriever is AutoCloseable only from API 29.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.ifBlank { null }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Not a readable audio file (or no tags): fall back to the file name below.
        } finally {
            runCatching { retriever.release() }
        }
        val name = title ?: displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/')?.let { stripExtension(it) }?.ifBlank { null } ?: "Song"
        return OwnSong(uri = uri.toString(), title = name, artist = artist, durationMs = duration, addedAt = now)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.let { stripExtension(it) }?.ifBlank { null }

    private fun ensureLoaded() {
        if (loaded) return
        val index = runCatching {
            if (indexFile.exists()) json.decodeFromString(MusicIndex.serializer(), mediaStore.crypto.decryptToBytes(indexFile).decodeToString()) else MusicIndex()
        }.getOrDefault(MusicIndex())
        _songs.value = index.songs.sortedByDescending { it.addedAt }
        loaded = true
    }

    private fun save(songs: List<OwnSong>) {
        val sorted = songs.sortedByDescending { it.addedAt }
        val bytes = json.encodeToString(MusicIndex.serializer(), MusicIndex(sorted)).encodeToByteArray()
        val tmp = File(mediaStore.reelDir, "$INDEX_NAME.tmp")
        mediaStore.crypto.encryptBytes(bytes, tmp)
        check(tmp.renameTo(indexFile)) { "Could not save your songs" }
        _songs.value = sorted
    }

    companion object {
        private const val INDEX_NAME = "music.enc"

        /** [existing] plus the [added] songs not already there (by URI); existing entries win so metadata and order are stable. */
        internal fun merge(existing: List<OwnSong>, added: List<OwnSong>): List<OwnSong> {
            val known = existing.mapTo(HashSet()) { it.uri }
            return existing + added.filter { known.add(it.uri) }
        }

        /** "Kesariya.mp3" → "Kesariya"; names without a short extension are kept whole ("Yad Lagla (Sairat)"). */
        internal fun stripExtension(name: String): String {
            val dot = name.lastIndexOf('.')
            return if (dot > 0 && name.length - dot in 2..5 && name.substring(dot + 1).all { it.isLetterOrDigit() }) name.substring(0, dot) else name
        }

        /**
         * Songs whose title or artist contains every word of [query] (case-insensitive, any order); an empty query
         * returns everything. "arijit kes" finds "Kesariya · Arijit Singh".
         */
        fun search(songs: List<OwnSong>, query: String): List<OwnSong> {
            val words = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return songs
            return songs.filter { s ->
                val hay = (s.title + " " + s.artist.orEmpty()).lowercase(Locale.ROOT)
                words.all { it in hay }
            }
        }

        /** 3:45 style length for a row; "" when unknown. */
        fun formatDuration(ms: Long): String {
            if (ms <= 0) return ""
            val total = ms / 1000
            return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
        }
    }
}
