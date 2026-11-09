package com.yaadein.domain.capture

/** Where a capture started. Stored by name in `capture_stats`. */
enum class CaptureEntry {
    /** The + button on Home (Quick Save). */
    FAB,
    /** Share → Yaadein from another app. */
    SHARE,
    /** A hub option (event-first, photo-first, text, voice, bulk, past). */
    HUB,
    /** The home-screen widget's + button. */
    WIDGET,
}

/**
 * One saved memory, as measured on the phone: how long from the first tap (or the share intent) to Save, and from
 * Save to the last photo being encrypted. Local only; shown under Settings → How I use Yaadein.
 */
data class CaptureStat(
    val id: Long = 0,
    val entry: CaptureEntry,
    val itemCount: Int,
    val tapToSaveMs: Long,
    /** Null until every item has been encrypted (or when there were no items). */
    val saveToEncryptedMs: Long?,
    val createdAt: Long,
)

/**
 * Pure stopwatch for one capture. Feed it a monotonic clock (elapsedRealtime) so a changed date does not skew it.
 * [startedAt] is the + tap or the moment a share arrived.
 */
class CaptureTimer(val entry: CaptureEntry, val startedAt: Long) {
    var savedAt: Long? = null
        private set

    /** Records the Save tap; returns the tap-to-save duration. */
    fun saved(now: Long): Long {
        val at = savedAt ?: now.also { savedAt = it }
        return (at - startedAt).coerceAtLeast(0L)
    }

    /** Milliseconds from Save to [now] (the last item encrypted), or null when Save was never recorded. */
    fun encrypted(now: Long): Long? = savedAt?.let { (now - it).coerceAtLeast(0L) }

    /** The row to store once Save was tapped. [createdAt] is wall-clock time for display. */
    fun stat(itemCount: Int, createdAt: Long, encryptedAt: Long? = null): CaptureStat = CaptureStat(
        entry = entry,
        itemCount = itemCount,
        tapToSaveMs = saved(savedAt ?: startedAt),
        saveToEncryptedMs = encryptedAt?.let { encrypted(it) },
        createdAt = createdAt,
    )
}

/** Percentiles over capture timings, for the usage screen. */
object CaptureStats {
    /** The value at [percentile] (0..100) of [values], nearest-rank; null for an empty list. */
    fun percentile(values: List<Long>, percentile: Int): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = Math.ceil(percentile.coerceIn(0, 100) / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun median(values: List<Long>): Long? = percentile(values, 50)
    fun p90(values: List<Long>): Long? = percentile(values, 90)
}
