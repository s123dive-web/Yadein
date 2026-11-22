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

    @Test
    fun `the status preset never runs past 30 seconds and lowers the bitrate`() {
        val many = memory(1, LocalDate.of(2024, 1, 1), (1L..60L).map { media(it, 1) })
        val p = RecapPlanner.plan(listOf(many), RecapTheme.WARM, RecapLength.STATUS, "T", "S", "Made", { "d" }, selectedMediaIds = (1L..60L).toSet())
        assertTrue(p.totalDurationMs <= 30_000L, "status reel is ${p.totalDurationMs} ms")
        assertTrue(p.segments.filterIsInstance<RecapSegment.Photo>().all { it.durationMs >= RecapPlanner.MIN_PHOTO_MS })
        assertTrue(p.segments.filterIsInstance<RecapSegment.Photo>().size < 60)
        assertEquals(STATUS_BITRATE, p.videoBitrate)
        assertEquals(STATUS_AUDIO_BITRATE, p.audioBitrate)
        // The other presets keep the renderer's quality default and may grow with a long selection.
        val long = RecapPlanner.plan(listOf(many), RecapTheme.WARM, RecapLength.SHORT, "T", "S", "Made", { "d" }, selectedMediaIds = (1L..60L).toSet())
        assertEquals(null, long.videoBitrate)
        assertTrue(long.totalDurationMs > 30_000L)
    }

    private fun journey(memories: List<MemoryDetail>, length: RecapLength = RecapLength.MEDIUM, thenNow: String? = "Then and now") =
        RecapPlanner.plan(memories, RecapTheme.WARM, length, "Title", "Sub", "Made with Yaadein", { "long" }, journey = true, thenNowTitle = thenNow, shortDateLabel = { "d${it.memory.id}" })

    @Test
    fun `a journey reel marks every photo with its memory's step and closes on then and now`() {
        val day0 = memory(1, LocalDate.of(2026, 11, 11), listOf(media(11, 1), media(12, 1)), "Day0")
        val words = memory(2, LocalDate.of(2026, 11, 12), emptyList(), "Rest day")
        val day2 = memory(3, LocalDate.of(2026, 11, 13), listOf(media(31, 3, MediaKind.VIDEO, 9_000), media(32, 3)), "Day2")
        val p = journey(listOf(day2, words, day0))
        val marks = p.segments.mapNotNull { (it as? RecapSegment.Photo)?.journey ?: (it as? RecapSegment.Clip)?.journey }
        // Three steps (the words-only day counts), oldest first; the words-only card itself carries no rail.
        assertEquals(listOf(JourneyMark(0, 3), JourneyMark(0, 3), JourneyMark(2, 3), JourneyMark(2, 3)), marks)
        // Chapter markers use the short date.
        assertEquals(listOf("d1", null, null), p.segments.filterIsInstance<RecapSegment.Photo>().map { it.dateLabel })
        assertEquals("d3", p.segments.filterIsInstance<RecapSegment.Clip>().single().dateLabel)
        val closing = p.segments[p.segments.lastIndex - 1] as RecapSegment.TitleCard
        assertEquals(ThenNow(11, 32, "d1", "d3"), closing.thenNow)
        assertEquals("Then and now", closing.title)
        assertEquals(RecapPlanner.THEN_NOW_MS, closing.durationMs)
        assertTrue((p.segments.last() as RecapSegment.TitleCard).isOutro)
    }

    @Test
    fun `then and now shows each memory's cover and needs two memories with photos`() {
        val first = MemoryDetail(Memory(id = 1, type = MemoryType.EVENT, title = "a", date = Dates.exact(LocalDate.of(2026, 1, 1)), coverMediaId = 12), emptyList(), listOf(media(11, 1), media(12, 1)))
        val last = MemoryDetail(Memory(id = 2, type = MemoryType.EVENT, title = "b", date = Dates.exact(LocalDate.of(2026, 2, 1)), coverMediaId = 21), emptyList(), listOf(media(21, 2), media(22, 2)))
        val pair = journey(listOf(first, last)).segments.filterIsInstance<RecapSegment.TitleCard>().mapNotNull { it.thenNow }.single()
        assertEquals(12L to 21L, pair.thenMediaId to pair.nowMediaId)
        // One memory, or the ending switched off: no closing card.
        assertTrue(journey(listOf(first)).segments.filterIsInstance<RecapSegment.TitleCard>().none { it.thenNow != null })
        assertTrue(journey(listOf(first, last), thenNow = null).segments.filterIsInstance<RecapSegment.TitleCard>().none { it.thenNow != null })
    }

    @Test
    fun `an ordinary reel has no journey marks and no closing card`() {
        val a = memory(1, LocalDate.of(2026, 1, 1), listOf(media(11, 1)))
        val b = memory(2, LocalDate.of(2026, 2, 1), listOf(media(21, 2)))
        val p = plan(listOf(a, b))
        assertTrue(p.segments.filterIsInstance<RecapSegment.Photo>().all { it.journey == null })
        assertTrue(p.segments.filterIsInstance<RecapSegment.TitleCard>().all { it.thenNow == null })
    }

    @Test
    fun `a journey never skips a chosen memory and keeps each cover`() {
        val days = (1..30).map { d ->
            MemoryDetail(
                Memory(id = d.toLong(), type = MemoryType.EVENT, title = "Day$d", date = Dates.exact(LocalDate.of(2026, 1, d)), coverMediaId = d * 100L + 5),
                emptyList(), (1..6).map { media(d * 100L + it, d.toLong()) },
            )
        }
        // The status preset's budget is 12 photos; 30 chosen days still get one each, and it is the cover.
        val picked = RecapPlanner.journeySelection(days, includeVideos = true, maxItems = 12)
        assertEquals((1..30).map { it * 100L + 5 }.toSet(), picked)
        // With room for more, the cover is still among each day's photos.
        val roomy = RecapPlanner.journeySelection(days.take(4), includeVideos = true, maxItems = 8)
        assertEquals(8, roomy.size)
        (1..4).forEach { d -> assertTrue(d * 100L + 5 in roomy) }
    }

    @Test
    fun `the status preset stays within 30 seconds for a journey with its closing card`() {
        val days = (1..25).map { memory(it.toLong(), LocalDate.of(2026, 1, it), listOf(media(it * 10L, it.toLong())), "Day$it") }
        val p = journey(days, RecapLength.STATUS)
        assertTrue(p.totalDurationMs <= 30_000L)
        assertTrue(p.segments.filterIsInstance<RecapSegment.TitleCard>().any { it.thenNow != null })
    }

    @Test
    fun `a journey is named after what its memories' titles share`() {
        val fitness = (0..6).map { "Day$it - Suresh Fitness Journey" }
        assertEquals("Suresh Fitness Journey", RecapPlanner.journeyTitle(fitness))
        // One unrelated memory among them does not change the name.
        assertEquals("Suresh Fitness Journey", RecapPlanner.journeyTitle(fitness + "Watch Bag From Shraddha"))
        assertEquals("Diwali Celebration", RecapPlanner.journeyTitle(listOf("Diwali Celebration 1", "Diwali Celebration 2", "diwali celebration at home")))
        assertEquals(null, RecapPlanner.journeyTitle(listOf("Dinner with Aai", "Lunch with Baba", "Goa")))
        assertEquals(null, RecapPlanner.journeyTitle(listOf("Only one")))
    }
}
