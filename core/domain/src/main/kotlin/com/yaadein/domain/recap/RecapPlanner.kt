package com.yaadein.domain.recap

import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import kotlinx.serialization.Serializable
import com.yaadein.domain.model.MemoryDetail
import kotlin.math.max
import kotlin.math.min

enum class RecapTheme { WARM, MINIMAL, FESTIVE, CLASSIC_FILM }

/** Target length presets; the planner trims or pads segment durations to land near the target. */
/**
 * Reel length presets. [STATUS] is the WhatsApp status preset: a hard 30 s ceiling (the selection is trimmed, never
 * stretched) and a lower bitrate so the file stays under WhatsApp's ~16 MB media limit.
 */
enum class RecapLength(val targetSeconds: Int, val hardCap: Boolean = false, val videoBitrate: Int? = null) {
    STATUS(30, hardCap = true, videoBitrate = STATUS_BITRATE), SHORT(30), MEDIUM(60), LONG(120),
}

/** ≈ 3.5 Mbit/s video + 128 kbit/s audio × 30 s ≈ 13.6 MB, under WhatsApp's 16 MB status/media ceiling with headroom. */
const val STATUS_BITRATE = 3_500_000
const val STATUS_AUDIO_BITRATE = 128_000

const val REEL_WIDTH = 1080
const val REEL_HEIGHT = 1920

/** Normalised rectangle (0..1) of the source photo shown at a point in time. */
@Serializable
data class Framing(val cx: Float, val cy: Float, val zoom: Float)

@Serializable
sealed interface RecapSegment {
    val durationMs: Long

    @Serializable
    data class TitleCard(val title: String, val subtitle: String, override val durationMs: Long, val isOutro: Boolean = false) : RecapSegment
    @Serializable
    data class Photo(val mediaId: Long, override val durationMs: Long, val start: Framing, val end: Framing, val caption: String?, val dateLabel: String?) : RecapSegment
    @Serializable
    data class Clip(val mediaId: Long, val startMs: Long, val endMs: Long, val caption: String?, val dateLabel: String?) : RecapSegment {
        override val durationMs: Long get() = endMs - startMs
    }
}

@Serializable
data class RecapPlan(
    val theme: RecapTheme,
    val segments: List<RecapSegment>,
    // Vertical 9:16 reel (Instagram Reels / YouTube Shorts / WhatsApp status size).
    val width: Int = REEL_WIDTH,
    val height: Int = REEL_HEIGHT,
    /** Encoder bitrates for this reel; null = the renderer's quality default. */
    val videoBitrate: Int? = null,
    val audioBitrate: Int? = null,
) {
    val totalDurationMs: Long get() = segments.sumOf { it.durationMs }
    val mediaCount: Int get() = segments.count { it is RecapSegment.Photo || it is RecapSegment.Clip }
}

/**
 * Deterministic storyboard builder: chronological order, Ken Burns pan/zoom on photos, trimmed clips
 * from videos, a caption on the first item of each memory, intro and outro cards.
 */
object RecapPlanner {
    const val INTRO_MS = 3_000L
    const val OUTRO_MS = 3_500L
    const val TEXT_CARD_MS = 2_500L
    const val MIN_PHOTO_MS = 1_500L
    const val MAX_PHOTO_MS = 4_500L
    const val DEFAULT_PHOTO_MS = 3_000L
    const val MAX_CLIP_MS = 6_000L
    const val MIN_CLIP_MS = 2_000L

    /** Most photos and clips one reel can hold; beyond this the export gets very long on a phone. */
    const val MAX_SELECTED_ITEMS = 150

    /** How many photos and clips are picked automatically for each length (the user can add or remove any). */
    fun defaultItemCount(length: RecapLength): Int = when (length) {
        RecapLength.STATUS -> 12
        RecapLength.SHORT -> 20
        RecapLength.MEDIUM -> 40
        RecapLength.LONG -> 80
    }

    /** Media of [memory] that can go into a reel, in album order. */
    fun usableMedia(memory: MemoryDetail, includeVideos: Boolean): List<Media> =
        memory.media.filter { includeVideos || it.kind == MediaKind.PHOTO }.sortedWith(compareBy({ it.position }, { it.id }))

    /**
     * Picks up to [maxItems] photos/clips across [memories] fairly: every memory gets a share before any album gets
     * a second helping (small albums are taken whole, big ones share what is left), and within an album the picks are
     * evenly spaced so the beginning, middle and end are all there. Everything is picked when it fits.
     */
    fun defaultSelection(memories: List<MemoryDetail>, includeVideos: Boolean, maxItems: Int): Set<Long> {
        val albums = ordered(memories).map { usableMedia(it, includeVideos) }.filter { it.isNotEmpty() }
        val total = albums.sumOf { it.size }
        if (total <= maxItems) return albums.flatten().mapTo(LinkedHashSet()) { it.id }
        // Water-filling: raise a common per-album quota until the budget is used up.
        val quota = IntArray(albums.size)
        var left = maxItems
        while (left > 0) {
            var gave = false
            for (i in albums.indices) {
                if (left == 0) break
                if (quota[i] < albums[i].size) { quota[i]++; left--; gave = true }
            }
            if (!gave) break
        }
        val picked = LinkedHashSet<Long>()
        albums.forEachIndexed { i, album -> evenlySpaced(album, quota[i]).forEach { picked += it.id } }
        return picked
    }

    private fun <T> evenlySpaced(items: List<T>, count: Int): List<T> = when {
        count <= 0 -> emptyList()
        count >= items.size -> items
        count == 1 -> listOf(items[items.size / 2])
        else -> (0 until count).map { i -> items[(i.toLong() * (items.size - 1) / (count - 1)).toInt()] }.distinct()
    }

    private fun ordered(memories: List<MemoryDetail>) = memories.sortedWith(compareBy({ it.memory.date.startEpochDay }, { it.memory.id }))

    /**
     * @param selectedMediaIds the photos and clips to use, chosen by the user; null picks them with [defaultSelection].
     *   A memory with media but nothing selected is left out; memories with no media at all become text cards.
     */
    /** Rounds a photo duration to whole beats (at least two) so cuts fall on the music; unchanged without a beat. */
    fun snapToBeat(ms: Long, beatMs: Long?): Long {
        if (beatMs == null || beatMs <= 0 || ms <= 0) return ms
        val beats = ((ms + beatMs / 2) / beatMs).coerceAtLeast(2)
        return beats * beatMs
    }

    fun plan(
        memories: List<MemoryDetail>,
        theme: RecapTheme,
        length: RecapLength,
        title: String,
        subtitle: String,
        outroText: String,
        dateLabel: (MemoryDetail) -> String,
        includeVideos: Boolean = true,
        selectedMediaIds: Set<Long>? = null,
        outroSubtitle: String = "",
        /** Beat length of the soundtrack; photo cuts then land on the beat (whole beats, at least two). */
        beatMs: Long? = null,
    ): RecapPlan {
        val ordered = ordered(memories)
        val selection = selectedMediaIds ?: defaultSelection(ordered, includeVideos, defaultItemCount(length))
        val items = ArrayList<RecapSegment>()
        var seed = 17L
        fun nextRandom(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % 1000) / 1000f }

        // Memory by memory: its chosen media, or a text card when it has no media at all.
        val merged = ArrayList<Pair<MemoryDetail, Media?>>()
        for (m in ordered) {
            if (m.media.isEmpty()) { merged += m to null; continue }
            usableMedia(m, includeVideos).filter { it.id in selection }.forEach { merged += m to it }
        }
        val textCards = merged.count { it.second == null }
        val clipCount = merged.count { it.second?.kind == MediaKind.VIDEO }
        val photoCount = merged.count { it.second?.kind == MediaKind.PHOTO }

        val targetMs = length.targetSeconds * 1000L - INTRO_MS - OUTRO_MS
        val mediaBudget = max(0L, targetMs - textCards * TEXT_CARD_MS)
        // Clips take up to MAX_CLIP_MS; photos share what is left, within bounds (a long selection makes a longer reel).
        val clipMs = if (clipCount == 0) 0L else min(MAX_CLIP_MS, max(MIN_CLIP_MS, mediaBudget / max(1, (photoCount + clipCount * 2)) * 2))
        val rawPhotoMs = if (photoCount == 0) 0L else (mediaBudget - clipMs * clipCount).let { left -> (left / photoCount).coerceIn(MIN_PHOTO_MS, MAX_PHOTO_MS) }
        val photoMs = snapToBeat(rawPhotoMs, beatMs)

        // Status preset: never longer than the target. Drop media from the end until the minimum photo length fits.
        if (length.hardCap) {
            while (merged.size > 1) {
                val cards = merged.count { it.second == null }
                val clips = merged.count { it.second?.kind == MediaKind.VIDEO }
                val photos = merged.size - cards - clips
                if (cards * TEXT_CARD_MS + clips * MIN_CLIP_MS + photos * MIN_PHOTO_MS <= targetMs) break
                merged.removeAt(merged.lastIndex)
            }
        }
        val cappedPhotoMs = if (length.hardCap && photoMs > 0) {
            val cards = merged.count { it.second == null }; val clips = merged.count { it.second?.kind == MediaKind.VIDEO }
            val photos = merged.size - cards - clips
            if (photos == 0) photoMs else min(photoMs, ((targetMs - cards * TEXT_CARD_MS - clips * clipMs) / photos).coerceAtLeast(MIN_PHOTO_MS))
        } else photoMs

        items += RecapSegment.TitleCard(title, subtitle, INTRO_MS)
        var lastMemoryId = -1L
        for ((m, item) in merged) {
            val firstOfMemory = m.memory.id != lastMemoryId
            lastMemoryId = m.memory.id
            val caption = if (firstOfMemory) m.memory.title else null
            val date = if (firstOfMemory) dateLabel(m) else null
            when {
                item == null -> items += RecapSegment.TitleCard(m.memory.title, dateLabel(m), TEXT_CARD_MS)
                item.kind == MediaKind.VIDEO -> {
                    val total = item.durationMs ?: MAX_CLIP_MS
                    val len = min(total, clipMs.coerceAtLeast(MIN_CLIP_MS))
                    // Start a little in, so we skip the shaky first frames of a clip.
                    val start = if (total > len + 1_000) 500L else 0L
                    items += RecapSegment.Clip(item.id, start, start + len, caption, date)
                }
                else -> {
                    val zoomIn = nextRandom() > 0.5f
                    val cx = 0.5f + (nextRandom() - 0.5f) * 0.2f
                    val cy = 0.5f + (nextRandom() - 0.5f) * 0.2f
                    val a = Framing(cx, cy, if (zoomIn) 1.0f else 1.18f)
                    val b = Framing(1f - cx, 1f - cy, if (zoomIn) 1.18f else 1.0f)
                    items += RecapSegment.Photo(item.id, if (cappedPhotoMs == 0L) DEFAULT_PHOTO_MS else cappedPhotoMs, a, b, caption, date)
                }
            }
        }
        items += RecapSegment.TitleCard(outroText, outroSubtitle, OUTRO_MS, isOutro = true)
        return RecapPlan(theme, items, videoBitrate = length.videoBitrate, audioBitrate = if (length.hardCap) STATUS_AUDIO_BITRATE else null)
    }
}
