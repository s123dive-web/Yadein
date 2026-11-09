package com.yaadein.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.capture.CaptureStats
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureStatsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: CaptureStatsRepositoryImpl

    @Before
    fun setUp() {
        db = DatabaseFactory.plainForTests(ApplicationProvider.getApplicationContext<Context>())
        repo = CaptureStatsRepositoryImpl(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `records, completes and lists newest first`() = runTest {
        val a = repo.record(CaptureStat(entry = CaptureEntry.FAB, itemCount = 10, tapToSaveMs = 6_500, saveToEncryptedMs = null, createdAt = 100))
        repo.record(CaptureStat(entry = CaptureEntry.SHARE, itemCount = 2, tapToSaveMs = 3_000, saveToEncryptedMs = 4_000, createdAt = 200))
        repo.markEncrypted(a, 21_000)
        val recent = repo.recent(10)
        assertEquals(listOf(CaptureEntry.SHARE, CaptureEntry.FAB), recent.map { it.entry })
        assertEquals(21_000L, recent[1].saveToEncryptedMs)
        assertEquals(10, recent[1].itemCount)
        assertEquals(1, repo.recent(1).size)
        assertEquals(6_500L, CaptureStats.p90(recent.map { it.tapToSaveMs }))
        assertNull(CaptureStats.median(emptyList()))
    }
}
