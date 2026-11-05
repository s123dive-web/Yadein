package com.yaadein.domain.recap

import com.yaadein.domain.model.MediaKind
import kotlinx.serialization.Serializable
import com.yaadein.domain.model.MemoryDetail
import kotlin.math.max
import kotlin.math.min

enum class RecapTheme { WARM, MINIMAL, FESTIVE, CLASSIC_FILM }

/** Target length presets; the planner trims or pads segment durations to land near the target. */
enum class RecapLength(val targetSeconds: Int) { SHORT(30), MEDIUM(60), LONG(120) }

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
    val width: Int = 1920,
    val height: Int = 1080,
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
    const val OUTRO_MS = 3_000L
    const val MIN_PHOTO_MS = 1_800L
    const val MAX_PHOTO_MS = 4_500L
    const val DEFAULT_PHOTO_MS = 3_000L
    const val MAX_CLIP_MS = 6_000L
    const val MIN_CLIP_MS = 2_000L
    const val MAX_ITEMS_PER_MEMORY = 8

    fun plan(
        memories: List<MemoryDetail>,
        theme: RecapTheme,
        length: RecapLength,
        title: String,
        subtitle: String,
        outroText: String,
        dateLabel: (MemoryDetail) -> String,
        includeVideos: Boolean = true,
    ): RecapPlan {
        val ordered = memories.sortedWith(compareBy({ it.memory.date.startEpochDay }, { it.memory.id }))
        val items = ArrayList<RecapSegment>()
        var seed = 17L
        fun nextRandom(): Float { seed = (seed * 6364136223846793005L + 1442695040888963407L); return ((seed ushr 33) % 1000) / 1000f }

        // Collect candidate media, capped per memory so one huge album does not dominate.
        val candidates = ArrayList<Triple<MemoryDetail, com.yaadein.domain.model.Media, Boolean>>()
        for (m in ordered) {
            val media = m.media.filter { includeVideos || it.kind == MediaKind.PHOTO }
            val chosen = if (media.size <= MAX_ITEMS_PER_MEMORY) media else {
                // Evenly spaced picks keep the beginning, middle and end of the album.
                (0 until MAX_ITEMS_PER_MEMORY).map { i -> media[i * (media.size - 1) / (MAX_ITEMS_PER_MEMORY - 1)] }
            }
            chosen.forEachIndexed { i, md -> candidates += Triple(m, md, i == 0) }
        }
        // Text-only memories without media still get a card so the story is complete.
        val textOnly = ordered.filter { it.media.isEmpty() }

        val targetMs = length.targetSeconds * 1000L - INTRO_MS - OUTRO_MS
        val textCardsMs = textOnly.size * 2_500L
        val mediaBudget = max(0L, targetMs - textCardsMs)
        val clipCount = candidates.count { it.second.kind == MediaKind.VIDEO }
        val photoCount = candidates.size - clipCount
        // Clips take up to MAX_CLIP_MS; photos share what is left, within bounds.
        val clipMs = if (clipCount == 0) 0L else min(MAX_CLIP_MS, max(MIN_CLIP_MS, mediaBudget / max(1, (photoCount + clipCount * 2)) * 2))
        val photoMs = if (photoCount == 0) 0L else (mediaBudget - clipMs * clipCount).let { left -> (left / photoCount).coerceIn(MIN_PHOTO_MS, MAX_PHOTO_MS) }

        items += RecapSegment.TitleCard(title, subtitle, INTRO_MS)
        var lastMemoryId = -1L
        val merged = ArrayList<Pair<MemoryDetail, Any>>() // Any = Media or Unit (text card)
        for (m in ordered) {
            if (m.media.isEmpty()) merged += m to Unit
            else candidates.filter { it.first.memory.id == m.memory.id }.forEach { merged += m to it.second }
        }
        for ((m, item) in merged) {
            val firstOfMemory = m.memory.id != lastMemoryId
            lastMemoryId = m.memory.id
            val caption = if (firstOfMemory) m.memory.title else null
            val date = if (firstOfMemory) dateLabel(m) else null
            when (item) {
                is Unit -> items += RecapSegment.TitleCard(m.memory.title, dateLabel(m), 2_500L)
                is com.yaadein.domain.model.Media -> if (item.kind == MediaKind.VIDEO) {
                    val total = item.durationMs ?: MAX_CLIP_MS
                    val len = min(total, clipMs.coerceAtLeast(MIN_CLIP_MS))
                    // Start a little in, so we skip the shaky first frames of a clip.
                    val start = if (total > len + 1_000) 500L else 0L
                    items += RecapSegment.Clip(item.id, start, start + len, caption, date)
                } else {
                    val zoomIn = nextRandom() > 0.5f
                    val cx = 0.5f + (nextRandom() - 0.5f) * 0.2f
                    val cy = 0.5f + (nextRandom() - 0.5f) * 0.2f
                    val a = Framing(cx, cy, if (zoomIn) 1.0f else 1.18f)
                    val b = Framing(1f - cx, 1f - cy, if (zoomIn) 1.18f else 1.0f)
                    items += RecapSegment.Photo(item.id, if (photoMs == 0L) DEFAULT_PHOTO_MS else photoMs, a, b, caption, date)
                }
            }
        }
        items += RecapSegment.TitleCard(outroText, "", OUTRO_MS, isOutro = true)
        return RecapPlan(theme, items)
    }
}
