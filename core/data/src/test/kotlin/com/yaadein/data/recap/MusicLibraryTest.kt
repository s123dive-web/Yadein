package com.yaadein.data.recap

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.media.MediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MusicLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val crypto = MediaCrypto(javax.crypto.spec.SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))
    private val mediaStore = MediaStore(context, crypto)

    @Before
    fun reset() { mediaStore.wipeAll() }

    private fun song(n: Int, title: String = "Song $n", artist: String? = "Artist $n") =
        OwnSong(uri = "content://media/$n", title = title, artist = artist, durationMs = 180_000L, addedAt = n * 1000L)

    @Test
    fun `added songs are kept across instances, newest first, encrypted on disk`() = runTest {
        val first = MusicLibrary(context, mediaStore)
        // Plain files (no tags): the file name becomes the title.
        val a = File(context.cacheDir, "Kesariya.mp3").apply { writeBytes(ByteArray(16)) }
        val b = File(context.cacheDir, "Yad Lagla.m4a").apply { writeBytes(ByteArray(16)) }
        val added = first.add(listOf(Uri.fromFile(a), Uri.fromFile(b)), now = 10L)
        assertEquals(listOf("Kesariya", "Yad Lagla"), added.map { it.title })

        val again = MusicLibrary(context, mediaStore)
        val loaded = again.load()
        assertEquals(2, loaded.size)
        assertEquals(setOf("Kesariya", "Yad Lagla"), loaded.map { it.title }.toSet())

        val index = File(mediaStore.reelDir, "music.enc")
        assertTrue(index.exists())
        assertFalse("index must not be plaintext", index.readText(Charsets.ISO_8859_1).contains("Kesariya"))
    }

    @Test
    fun `adding the same song twice keeps one entry and remove forgets it`() = runTest {
        val library = MusicLibrary(context, mediaStore)
        val a = File(context.cacheDir, "Zingaat.mp3").apply { writeBytes(ByteArray(16)) }
        library.add(listOf(Uri.fromFile(a)), now = 1L)
        library.add(listOf(Uri.fromFile(a), Uri.fromFile(a)), now = 2L)
        assertEquals(1, library.load().size)
        assertEquals(1L, library.load().single().addedAt)

        library.remove(Uri.fromFile(a).toString())
        assertTrue(library.load().isEmpty())
        assertTrue(MusicLibrary(context, mediaStore).load().isEmpty())
    }

    @Test
    fun `readable only while the file exists`() = runTest {
        val library = MusicLibrary(context, mediaStore)
        val a = File(context.cacheDir, "Chaleya.mp3").apply { writeBytes(ByteArray(16)) }
        val uri = Uri.fromFile(a).toString()
        assertTrue(library.isReadable(uri))
        a.delete()
        assertFalse(library.isReadable(uri))
    }

    @Test
    fun `merge keeps existing entries and order`() {
        val existing = listOf(song(1), song(2))
        val merged = MusicLibrary.merge(existing, listOf(song(2, title = "Renamed"), song(3)))
        assertEquals(listOf("Song 1", "Song 2", "Song 3"), merged.map { it.title })
    }

    @Test
    fun `search matches every word in title or artist, any order and case`() {
        val songs = listOf(
            song(1, "Kesariya", "Arijit Singh"), song(2, "Yad Lagla", "Ajay Gogavale"),
            song(3, "Zingaat", "Ajay-Atul"), song(4, "Husn", null),
        )
        assertEquals(songs, MusicLibrary.search(songs, "  "))
        assertEquals(listOf("Kesariya"), MusicLibrary.search(songs, "arijit kes").map { it.title })
        assertEquals(listOf("Yad Lagla", "Zingaat"), MusicLibrary.search(songs, "ajay").map { it.title })
        assertEquals(listOf("Husn"), MusicLibrary.search(songs, "HUSN").map { it.title })
        assertTrue(MusicLibrary.search(songs, "sajni").isEmpty())
    }

    @Test
    fun `file names lose their extension, durations format as m-ss`() {
        assertEquals("Kesariya", MusicLibrary.stripExtension("Kesariya.mp3"))
        assertEquals("Yad Lagla (Sairat)", MusicLibrary.stripExtension("Yad Lagla (Sairat)"))
        assertEquals("track.final", MusicLibrary.stripExtension("track.final.flac"))
        assertEquals(".hidden", MusicLibrary.stripExtension(".hidden"))
        assertEquals("3:05", MusicLibrary.formatDuration(185_000))
        assertEquals("", MusicLibrary.formatDuration(0))
    }
}
