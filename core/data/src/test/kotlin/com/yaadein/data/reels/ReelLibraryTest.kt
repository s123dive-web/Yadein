package com.yaadein.data.reels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.media.MediaStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@UnstableApi
class ReelLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val crypto = MediaCrypto(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))
    private val mediaStore = MediaStore(context, crypto)

    @Before
    fun reset() {
        mediaStore.wipeAll()
        // FileProvider caches its roots per authority, but Robolectric gives every test a new cache dir.
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    private fun rendered(bytes: ByteArray): File = File(context.cacheDir, "r-${System.nanoTime()}.mp4").apply { writeBytes(bytes) }

    private suspend fun ReelLibrary.addUser(n: Int, bytes: ByteArray = byteArrayOf(n.toByte())) =
        add(rendered(bytes), "Reel $n", ReelKind.USER, 30_000, coverMediaId = n.toLong(), theme = "WARM", now = n * 1000L)

    @Test
    fun `keeps only the last ten user reels and deletes the older videos`() = runTest {
        val library = ReelLibrary(context, mediaStore)
        val first = library.addUser(1)
        repeat(11) { library.addUser(it + 2) }
        val kept = library.load()
        assertEquals(ReelLibrary.MAX_USER_REELS, kept.size)
        assertEquals("Reel 12", kept.first().title)
        assertEquals("Reel 3", kept.last().title)
        assertFalse(library.videoFile(first).exists())
    }

    @Test
    fun `an auto reel replaces the one in its slot and user reels do not push out auto reels`() = runTest {
        val library = ReelLibrary(context, mediaStore)
        val old = library.add(rendered(byteArrayOf(1)), "Recent", ReelKind.AUTO, 30_000, null, "WARM", autoSlot = "recent", signature = "a", now = 1)
        library.add(rendered(byteArrayOf(2)), "Rahul", ReelKind.AUTO, 30_000, null, "FESTIVE", autoSlot = "person", signature = "b", now = 2)
        library.add(rendered(byteArrayOf(3)), "Throwback", ReelKind.AUTO, 30_000, null, "CLASSIC_FILM", autoSlot = "throwback", signature = "c", now = 3)
        val newer = library.add(rendered(byteArrayOf(4)), "Recent again", ReelKind.AUTO, 30_000, null, "WARM", autoSlot = "recent", signature = "d", now = 4)
        repeat(12) { library.addUser(it + 10) }
        val autos = library.load().filter { it.kind == ReelKind.AUTO }
        assertEquals(setOf("recent", "person", "throwback"), autos.mapNotNull { it.autoSlot }.toSet())
        assertTrue(autos.any { it.id == newer.id })
        assertFalse(library.videoFile(old).exists())
        library.retainAutoSlots(setOf("recent"))
        assertEquals(listOf(newer.id), library.load().filter { it.kind == ReelKind.AUTO }.map { it.id })
    }

    @Test
    fun `videos and the list are encrypted, survive a restart and play back seekably`() = runTest {
        val plain = Random(7).nextBytes(100_000)
        val library = ReelLibrary(context, mediaStore)
        val entry = library.addUser(1, plain)
        val stored = library.videoFile(entry).readBytes()
        assertFalse("not stored in the clear", stored.copyOfRange(MediaCrypto.HEADER_SIZE, MediaCrypto.HEADER_SIZE + 64).contentEquals(plain.copyOfRange(0, 64)))
        assertFalse(File(library.dir, "index.enc").readBytes().decodeToString().contains("Reel 1"))

        val reopened = ReelLibrary(context, mediaStore)
        val again = reopened.get(entry.id)
        assertNotNull(again)
        assertEquals("Reel 1", again!!.title)

        val source = reopened.dataSourceFactory().createDataSource()
        source.open(DataSpec.Builder().setUri(reelUri(again)).setPosition(12_345).build())
        val buf = ByteArray(1000)
        var read = 0
        while (read < buf.size) {
            val n = source.read(buf, read, buf.size - read)
            if (n <= 0) break
            read += n
        }
        source.close()
        assertArrayEquals(plain.copyOfRange(12_345, 13_345), buf)
    }

    @Test
    fun `delete removes the entry and its video, and share decrypts a copy for the share sheet`() = runTest {
        val library = ReelLibrary(context, mediaStore)
        val entry = library.addUser(1, byteArrayOf(9, 8, 7))
        val chooser = library.shareIntent(entry, "Share")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertArrayEquals(byteArrayOf(9, 8, 7), context.contentResolver.openInputStream(uri)!!.readBytes())
        library.delete(entry.id)
        assertNull(library.get(entry.id))
        assertFalse(library.videoFile(entry).exists())
    }

    @Test
    fun `retain keeps ten user reels and one per auto slot`() {
        fun e(i: Int, kind: ReelKind, slot: String? = null) = ReelEntry("$i", "t", kind, i.toLong(), 1, 1, "$i", autoSlot = slot)
        val all = (1..15).map { e(it, ReelKind.USER) } + listOf(e(20, ReelKind.AUTO, "recent"), e(21, ReelKind.AUTO, "recent"), e(22, ReelKind.AUTO, "person"))
        val (kept, dropped) = ReelLibrary.retain(all)
        assertEquals(10, kept.count { it.kind == ReelKind.USER })
        assertEquals(listOf("22", "21"), kept.filter { it.kind == ReelKind.AUTO }.map { it.id })
        assertEquals(6, dropped.size)
    }
}
