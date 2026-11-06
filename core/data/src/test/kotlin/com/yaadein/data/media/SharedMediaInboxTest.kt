package com.yaadein.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SharedMediaInboxTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** FileProvider caches its roots per authority, but Robolectric gives every test a new cache dir. */
    @Before
    fun resetFileProviderCache() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    /** A file the "gallery app" shares, served through the app's own FileProvider (exports/ root). */
    private fun galleryFile(name: String, bytes: ByteArray): Uri {
        val f = File(context.cacheDir, "exports/$name").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        return FileProvider.getUriForFile(context, context.packageName + ".files", f)
    }

    @Test
    fun `multiple shared photos are copied and handed on with their image type`() = runTest {
        val a = galleryFile("a.jpg", byteArrayOf(1, 2, 3))
        val b = galleryFile("b.png", byteArrayOf(4, 5))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        assertTrue(SharedMediaInbox.isShare(intent))
        assertEquals(2, SharedMediaInbox.receive(context, intent))
        val copies = SharedMediaInbox.take()
        assertEquals(2, copies.size)
        assertEquals("image/jpeg", context.contentResolver.getType(copies[0]))
        assertArrayEquals(byteArrayOf(1, 2, 3), context.contentResolver.openInputStream(copies[0])!!.readBytes())
        assertTrue("taken once", SharedMediaInbox.take().isEmpty())
    }

    @Test
    fun `non-media shares are ignored`() = runTest {
        val doc = galleryFile("notes.txt", "hello".toByteArray())
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, doc)
        assertEquals(0, SharedMediaInbox.receive(context, intent))
        assertTrue(SharedMediaInbox.take().isEmpty())
        assertFalse(SharedMediaInbox.isShare(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `an unopened share is restored after a restart, but only once`() = runTest {
        val a = galleryFile("c.jpg", byteArrayOf(7, 8))
        SharedMediaInbox.receive(context, Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, a))
        // Simulate the process dying: the in-memory list is gone, the copied files are not.
        val lost = SharedMediaInbox.take()
        File(context.cacheDir, "shared").listFiles()!!.forEach { File(it, ".taken").delete() }
        assertEquals(1, lost.size)
        SharedMediaInbox.restore(context)
        val restored = SharedMediaInbox.take()
        assertEquals(1, restored.size)
        assertArrayEquals(byteArrayOf(7, 8), context.contentResolver.openInputStream(restored[0])!!.readBytes())
        SharedMediaInbox.restore(context)
        assertTrue("already used", SharedMediaInbox.take().isEmpty())
    }
}
