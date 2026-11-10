package com.yaadein.domain.usage

import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageSummaryTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100L * day
    private fun stat(tap: Long, enc: Long?, entry: CaptureEntry = CaptureEntry.FAB, items: Int = 3, daysAgo: Int = 0) =
        CaptureStat(entry = entry, itemCount = items, tapToSaveMs = tap, saveToEncryptedMs = enc, createdAt = now - daysAgo * day)

    @Test
    fun `empty stats give an empty summary with nulls`() {
        val s = UsageSummary.from(emptyList(), now)
        assertTrue(s.isEmpty); assertNull(s.tapToSaveMedianMs); assertNull(s.withinBudgetPercent); assertEquals(List(8) { 0 }, s.perWeek)
    }

    @Test
    fun `percentiles, budget share, entry points, sparkline and weeks`() {
        val stats = listOf(
            stat(4_000, 2_000, daysAgo = 0),
            stat(12_000, null, CaptureEntry.SHARE, daysAgo = 1),
            stat(6_000, 8_000, CaptureEntry.HUB, items = 10, daysAgo = 8),
            stat(9_000, 3_000, daysAgo = 15),
            stat(30_000, 20_000, CaptureEntry.SHARE, daysAgo = 70),
        )
        val s = UsageSummary.from(stats, now)
        assertEquals(5, s.captures); assertEquals(22, s.photosAndClips)
        assertEquals(9_000, s.tapToSaveMedianMs); assertEquals(30_000, s.tapToSaveP90Ms)
        assertEquals(3_000, s.saveToEncryptedMedianMs); assertEquals(20_000, s.saveToEncryptedP90Ms)
        assertEquals(60, s.withinBudgetPercent)
        assertEquals(listOf(CaptureEntry.FAB to 2, CaptureEntry.SHARE to 2, CaptureEntry.HUB to 1), s.byEntry)
        assertEquals(listOf(30_000L, 9_000L, 6_000L, 12_000L, 4_000L), s.recentTapToSaveMs)
        // weeks: 0-1 days ago → this week (last index), 8 → one week ago, 15 → two weeks ago, 70 → outside the window
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 2), s.perWeek)
    }
}
