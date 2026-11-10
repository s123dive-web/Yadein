package com.yaadein.feature.settings

import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.repository.CaptureStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Settings → How I use Yaadein: the ViewModel turns the local `capture_stats` rows into a [UsageSummary] on load. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {
    private class FakeCaptureStats(private val rows: List<CaptureStat>) : CaptureStatsRepository {
        var lastLimit: Int? = null
        override suspend fun record(stat: CaptureStat): Long = rows.size + 1L
        override suspend fun markEncrypted(id: Long, saveToEncryptedMs: Long) = Unit
        override suspend fun recent(limit: Int): List<CaptureStat> {
            lastLimit = limit
            return rows.take(limit)
        }
    }

    private val now = 1_700_000_000_000L
    private val stats = listOf(
        CaptureStat(id = 3, entry = CaptureEntry.FAB, itemCount = 4, tapToSaveMs = 6_000, saveToEncryptedMs = 3_000, createdAt = now),
        CaptureStat(id = 2, entry = CaptureEntry.SHARE, itemCount = 1, tapToSaveMs = 12_000, saveToEncryptedMs = null, createdAt = now - 60_000),
        CaptureStat(id = 1, entry = CaptureEntry.FAB, itemCount = 2, tapToSaveMs = 8_000, saveToEncryptedMs = 2_000, createdAt = now - 120_000),
    )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state becomes a summary of the recorded captures`() = runTest {
        val repo = FakeCaptureStats(stats)
        val vm = UsageViewModel(repo)

        val summary = vm.state.value
        assertNotNull(summary)
        assertFalse(summary!!.isEmpty)
        assertEquals(3, summary.captures)
        assertEquals(7, summary.photosAndClips)
        assertEquals(8_000L, summary.tapToSaveMedianMs)
        assertEquals(66, summary.withinBudgetPercent)
        assertEquals(listOf(CaptureEntry.FAB to 2, CaptureEntry.SHARE to 1), summary.byEntry)
        assertTrue((repo.lastLimit ?: 0) >= stats.size)
    }

    @Test
    fun `no captures gives the empty summary`() = runTest {
        val vm = UsageViewModel(FakeCaptureStats(emptyList()))

        val summary = vm.state.value
        assertNotNull(summary)
        assertTrue(summary!!.isEmpty)
        assertEquals(0, summary.captures)
        assertNull(summary.tapToSaveMedianMs)
        assertNull(summary.withinBudgetPercent)
    }

    @Test
    fun `refresh reloads from the repository`() = runTest {
        val repo = FakeCaptureStats(stats)
        val vm = UsageViewModel(repo)
        repo.lastLimit = null

        vm.refresh()

        assertNotNull(repo.lastLimit)
        assertEquals(3, vm.state.value?.captures)
    }
}
