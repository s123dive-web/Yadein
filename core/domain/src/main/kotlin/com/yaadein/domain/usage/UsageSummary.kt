package com.yaadein.domain.usage

import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.capture.CaptureStats

/**
 * What Settings → "How I use Yaadein" shows, computed from the local `capture_stats` rows only (nothing is sent anywhere).
 * All durations are milliseconds; nulls mean "not enough data yet".
 */
data class UsageSummary(
    val captures: Int,
    val photosAndClips: Int,
    /** Tap → Save: how long the user spends on the screen. */
    val tapToSaveMedianMs: Long?,
    val tapToSaveP90Ms: Long?,
    /** Save → every item encrypted: how long the phone works in the background. */
    val saveToEncryptedMedianMs: Long?,
    val saveToEncryptedP90Ms: Long?,
    /** Share of captures that stayed within the 10-second Quick Save budget. */
    val withinBudgetPercent: Int?,
    /** Captures per entry point, most used first. */
    val byEntry: List<Pair<CaptureEntry, Int>>,
    /** Newest-last tap-to-save values for a small sparkline. */
    val recentTapToSaveMs: List<Long>,
    /** Captures per week over the last [WEEKS] weeks, oldest first (index 0 = seven weeks ago). */
    val perWeek: List<Int>,
) {
    val isEmpty: Boolean get() = captures == 0

    companion object {
        const val QUICK_SAVE_BUDGET_MS = 10_000L
        const val WEEKS = 8
        const val SPARKLINE_POINTS = 20
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

        /** [stats] newest first (as the repository returns them); [now] is wall-clock millis. */
        fun from(stats: List<CaptureStat>, now: Long): UsageSummary {
            val tapToSave = stats.map { it.tapToSaveMs }
            val encrypted = stats.mapNotNull { it.saveToEncryptedMs }
            val byEntry = stats.groupingBy { it.entry }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<CaptureEntry, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { it.key to it.value }
            val perWeek = IntArray(WEEKS)
            stats.forEach { s ->
                val weeksAgo = ((now - s.createdAt) / WEEK_MS).toInt()
                if (weeksAgo in 0 until WEEKS) perWeek[WEEKS - 1 - weeksAgo]++
            }
            return UsageSummary(
                captures = stats.size,
                photosAndClips = stats.sumOf { it.itemCount },
                tapToSaveMedianMs = CaptureStats.median(tapToSave),
                tapToSaveP90Ms = CaptureStats.p90(tapToSave),
                saveToEncryptedMedianMs = CaptureStats.median(encrypted),
                saveToEncryptedP90Ms = CaptureStats.p90(encrypted),
                withinBudgetPercent = if (stats.isEmpty()) null else (stats.count { it.tapToSaveMs <= QUICK_SAVE_BUDGET_MS } * 100 / stats.size),
                byEntry = byEntry,
                recentTapToSaveMs = stats.take(SPARKLINE_POINTS).map { it.tapToSaveMs }.reversed(),
                perWeek = perWeek.toList(),
            )
        }
    }
}
