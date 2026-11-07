package com.yaadein.domain.recap

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RecapPlannerTest {
    private fun media(id: Long, memoryId: Long, kind: MediaKind = MediaKind.PHOTO, duration: Long? = null) =
        Media(id = id, memoryId = memoryId, kind = kind, encryptedPath = "", thumbPath = "", sizeBytes = 1, width = 1, height = 1, sha256 = "", durationMs = duration)
    private fun memory(id: Long, date: LocalDate, media: List<Media>, title: String = "m$id") =
        MemoryDetail(Memory(id = id, type = MemoryType.EVENT, title = title, date = Dates.exact(date)), emptyList(), media)

    private fun plan(memories: List<MemoryDetail>, length: RecapLength = RecapLength.MEDIUM) =
        RecapPlanner.plan(memories, RecapTheme.WARM, length, "Title", "Sub", "Made with Yaadein", { "date" })

    @Test
    fun `orders chronologically with intro and outro and captions on first item of each memory`() {
        val m2 = memory(2, LocalDate.of(2024, 5, 1), listOf(media(21, 2), media(22, 2)))
        val m1 = memory(1, LocalDate.of(2023, 1, 1), listOf(media(11, 1)))
        val p = plan(listOf(m2, m1))
        assertTrue(p.segments.first() is RecapSegment.TitleCard)
        assertTrue((p.segments.last() as RecapSegment.TitleCard).isOutro)
        val photos = p.segments.filterIsInstance<RecapSegment.Photo>()
        assertEquals(listOf(11L, 21L, 22L), photos.map { it.mediaId })
        assertEquals(listOf("m1", "m2", null), photos.map { it.caption })
    }

    @Test
    fun `videos become trimmed clips and photos ken burns`() {
        val m = memory(1, LocalDate.of(2024, 1, 1), listOf(media(1, 1, MediaKind.VIDEO, 30_000), media(2, 1)))
        val p = plan(listOf(m))
        val clip = p.segments.filterIsInstance<RecapSegment.Clip>().single()
        assertTrue(clip.durationMs in RecapPlanner.MIN_CLIP_MS..RecapPlanner.MAX_CLIP_MS)
        assertEquals(500L, clip.startMs)
        val photo = p.segments.filterIsInstance<RecapSegment.Photo>().single()
        assertTrue(photo.start.zoom != photo.end.zoom)
    }

    @Test
    fun `length preset bounds photo durations and total`() {
        val many = (1..40).map { memory(it.toLong(), LocalDate.of(2020, 1, it % 28 + 1), listOf(media(it.toLong() * 10, it.toLong())), "m$it") }
        val short = plan(many, RecapLength.SHORT)
        val long = plan(many, RecapLength.LONG)
        short.segments.filterIsInstance<RecapSegment.Photo>().forEach { assertEquals(RecapPlanner.MIN_PHOTO_MS, it.durationMs) }
        assertTrue(long.totalDurationMs > short.totalDurationMs)
        long.segments.filterIsInstance<RecapSegment.Photo>().forEach { assertTrue(it.durationMs in RecapPlanner.MIN_PHOTO_MS..RecapPlanner.MAX_PHOTO_MS) }
    }

    @Test
    fun `a big album is used whole when it fits and text-only memories get a card`() {
        val album = memory(1, LocalDate.of(2024, 1, 1), (1..30).map { media(it.toLong(), 1) })
        val text = memory(2, LocalDate.of(2024, 2, 1), emptyList(), "Just words")
        val p = plan(listOf(album, text))
        assertEquals((1L..30L).toList(), p.segments.filterIsInstance<RecapSegment.Photo>().map { it.mediaId })
        assertTrue(p.segments.any { it is RecapSegment.TitleCard && it.title == "Just words" })
    }

    @Test
    fun `a huge album is sampled evenly up to the length's default count`() {
        val album = memory(1, LocalDate.of(2024, 1, 1), (1..300).map { media(it.toLong(), 1) })
        val ids = plan(listOf(album), RecapLength.LONG).segments.filterIsInstance<RecapSegment.Photo>().map { it.mediaId }
        assertEquals(RecapPlanner.defaultItemCount(RecapLength.LONG), ids.size)
        assertEquals(1L, ids.first()); assertEquals(300L, ids.last())
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun `default selection shares the budget fairly between albums`() {
        val small = memory(1, LocalDate.of(2024, 1, 1), (1..3).map { media(it.toLong(), 1) })
        val big = memory(2, LocalDate.of(2024, 2, 1), (101..200).map { media(it.toLong(), 2) })
        val picked = RecapPlanner.defaultSelection(listOf(big, small), includeVideos = true, maxItems = 20)
        assertEquals(20, picked.size)
        assertTrue(picked.containsAll(listOf(1L, 2L, 3L)))
        assertEquals(17, picked.count { it > 100 })
    }

    @Test
    fun `an explicit selection is used as is, however long`() {
        val album = memory(1, LocalDate.of(2024, 1, 1), (1..200).map { media(it.toLong(), 1) })
        val other = memory(2, LocalDate.of(2024, 3, 1), listOf(media(500, 2)))
        val chosen = (1L..120L).toSet()
        val p = RecapPlanner.plan(listOf(album, other), RecapTheme.WARM, RecapLength.SHORT, "t", "s", "o", { "d" }, selectedMediaIds = chosen)
        val ids = p.segments.filterIsInstance<RecapSegment.Photo>().map { it.mediaId }
        assertEquals(chosen.toList(), ids)
        // The memory with photos but none chosen is left out entirely (no text card either).
        assertTrue(p.segments.none { it is RecapSegment.TitleCard && it.title == "m2" })
        assertTrue(p.totalDurationMs >= 120 * RecapPlanner.MIN_PHOTO_MS)
    }

    @Test
    fun `outro carries the maker credit subtitle`() {
        val m = memory(1, LocalDate.of(2024, 1, 1), listOf(media(1, 1)))
        val p = RecapPlanner.plan(listOf(m), RecapTheme.WARM, RecapLength.SHORT, "t", "s", "Made with Yaadein", { "d" }, outroSubtitle = "credit")
        val outro = p.segments.last() as RecapSegment.TitleCard
        assertTrue(outro.isOutro); assertEquals("Made with Yaadein", outro.title); assertEquals("credit", outro.subtitle)
    }

    @Test
    fun `is deterministic`() {
        val m = memory(1, LocalDate.of(2024, 1, 1), listOf(media(1, 1), media(2, 1), media(3, 1)))
        assertEquals(plan(listOf(m)), plan(listOf(m)))
    }

    @Test
    fun `videos can be excluded`() {
        val m = memory(1, LocalDate.of(2024, 1, 1), listOf(media(1, 1, MediaKind.VIDEO, 5000), media(2, 1)))
        val p = RecapPlanner.plan(listOf(m), RecapTheme.MINIMAL, RecapLength.SHORT, "t", "s", "o", { "d" }, includeVideos = false)
        assertTrue(p.segments.none { it is RecapSegment.Clip })
    }

    @Test
    fun `photo durations snap to whole beats of the soundtrack`() {
        assertEquals(2_000L, RecapPlanner.snapToBeat(2_100L, 500L))
        assertEquals(2_500L, RecapPlanner.snapToBeat(2_300L, 500L))
        // At least two beats, so very fast tracks never flash a photo for one beat.
        assertEquals(1_000L, RecapPlanner.snapToBeat(300L, 500L))
        assertEquals(2_345L, RecapPlanner.snapToBeat(2_345L, null))
    }
}
