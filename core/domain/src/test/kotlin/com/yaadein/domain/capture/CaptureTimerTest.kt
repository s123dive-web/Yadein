package com.yaadein.domain.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptureTimerTest {
    @Test
    fun `tap to save and save to encrypted are measured from a monotonic clock`() {
        val t = CaptureTimer(CaptureEntry.FAB, startedAt = 10_000)
        assertEquals(6_500, t.saved(16_500))
        assertEquals(6_500, t.saved(99_999)) // a second Save does not move the mark
        assertEquals(20_000, t.encrypted(36_500))
        val stat = t.stat(itemCount = 10, createdAt = 1_700_000_000_000, encryptedAt = 36_500)
        assertEquals(CaptureEntry.FAB, stat.entry)
        assertEquals(10, stat.itemCount)
        assertEquals(6_500, stat.tapToSaveMs)
        assertEquals(20_000, stat.saveToEncryptedMs)
    }

    @Test
    fun `without a save there is no encryption duration and the clock never goes negative`() {
        val t = CaptureTimer(CaptureEntry.SHARE, startedAt = 5_000)
        assertNull(t.encrypted(9_000))
        assertEquals(0, t.saved(4_000))
        assertNull(t.stat(itemCount = 0, createdAt = 1).saveToEncryptedMs)
    }

    @Test
    fun `median and p90 are nearest rank`() {
        val values = listOf(9_000L, 4_000L, 12_000L, 7_000L, 30_000L)
        assertEquals(9_000, CaptureStats.median(values))
        assertEquals(30_000, CaptureStats.p90(values))
        assertEquals(4_000, CaptureStats.percentile(values, 0))
        assertNull(CaptureStats.median(emptyList()))
        assertEquals(7_000, CaptureStats.median(listOf(7_000L)))
    }
}
