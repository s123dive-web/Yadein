package com.yaadein.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
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
class CameraCaptureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir get() = File(context.cacheDir, "camera")

    /** FileProvider caches its roots per authority, but Robolectric gives every test a new cache dir. */
    @Before
    fun resetFileProviderCache() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @Test
    fun `the camera writes into the app's private cache and the shot is readable as a photo`() {
        val uri = CameraCapture.newTarget(context, video = false)
        assertEquals(context.packageName + ".files", uri.authority)
        // What the camera app does with its write grant.
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        assertTrue(CameraCapture.finished(context, uri))
        val file = dir.listFiles()!!.single()
        assertTrue(file.path.startsWith(context.cacheDir.path))
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertEquals("image/jpeg", context.contentResolver.getType(uri))
    }

    @Test
    fun `a video target is an mp4`() {
        val uri = CameraCapture.newTarget(context, video = true)
        assertEquals("video/mp4", context.contentResolver.getType(uri))
    }

    @Test
    fun `a cancelled shot leaves nothing behind`() {
        val uri = CameraCapture.newTarget(context, video = false)
        assertFalse(CameraCapture.finished(context, uri))
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `discard deletes our file and ignores every other uri`() {
        val uri = CameraCapture.newTarget(context, video = false)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(9)) }
        val other = File(context.cacheDir, "exports/keep.jpg").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(7)) }
        CameraCapture.discard(context, FileProvider.getUriForFile(context, context.packageName + ".files", other))
        CameraCapture.discard(context, Uri.parse("content://media/picker/0/photo/1"))
        assertTrue(other.exists())
        CameraCapture.discard(context, uri)
        assertTrue(dir.listFiles().isNullOrEmpty())
        assertFalse(CameraCapture.finished(context, uri))
    }

    @Test
    fun `an abandoned shot is removed the next day`() {
        val old = CameraCapture.newTarget(context, video = false)
        context.contentResolver.openOutputStream(old)!!.use { it.write(byteArrayOf(1)) }
        dir.listFiles()!!.single().setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        CameraCapture.newTarget(context, video = true)
        assertEquals(listOf("mp4"), dir.listFiles()!!.map { it.extension })
    }
}
