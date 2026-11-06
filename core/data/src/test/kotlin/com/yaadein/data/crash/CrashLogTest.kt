package com.yaadein.data.crash

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrashLogTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() = CrashLog.clear(context)

    @Test
    fun `nothing to share when the log is empty`() {
        CrashLog.clear(context)
        assertNull(CrashLog.shareIntent(context, "Share"))
    }

    @Test
    fun `share intent exposes a copy through the file provider instead of crashing`() {
        // Regression: sharing files/crash.log threw IllegalArgumentException because files/ is not a
        // FileProvider root; the log is now copied to cache/exports/ first.
        CrashLog.append(context, "test", Thread.currentThread(), IllegalStateException("boom"))
        val chooser = CrashLog.shareIntent(context, "Share")
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".files", uri.authority)
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("boom"))
        val shared = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertTrue(shared.contains("IllegalStateException: boom"))
    }

    @Test
    fun `handled failures are logged without raising the crash banner`() {
        CrashLog.clear(context)
        CrashLog.recordNonFatal(context, "recap export", RuntimeException("Asset loader error", IllegalStateException("no audio")))
        assertTrue(CrashLog.read(context).contains("Handled: recap export"))
        assertTrue(CrashLog.read(context).contains("no audio"))
        assertFalse(CrashLog.hasPendingReport(context))
    }
}
