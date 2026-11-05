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
    fun `large albums are capped and text-only memories get a card`() {
        val album = memory(1, LocalDate.of(2024, 1, 1), (1..30).map { media(it.toLong(), 1) })
        val text = memory(2, LocalDate.of(2024, 2, 1), emptyList(), "Just words")
        val p = plan(listOf(album, text))
        assertEquals(RecapPlanner.MAX_ITEMS_PER_MEMORY, p.segments.filterIsInstance<RecapSegment.Photo>().size)
        assertTrue(p.segments.any { it is RecapSegment.TitleCard && it.title == "Just words" })
        val ids = p.segments.filterIsInstance<RecapSegment.Photo>().map { it.mediaId }
        assertEquals(1L, ids.first()); assertEquals(30L, ids.last())
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
}
