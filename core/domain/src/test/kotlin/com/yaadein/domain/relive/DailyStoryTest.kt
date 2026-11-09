package com.yaadein.domain.relive

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DailyStoryTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun card(id: Long, photo: Boolean = false) = MemoryCard(
        memory = Memory(id = id, type = MemoryType.EVENT, title = "m$id", date = Dates.exact(LocalDate.of(2020, 11, 9))),
        people = emptyList(),
        cover = if (photo) Media(id = id * 10, memoryId = id, kind = MediaKind.PHOTO, encryptedPath = "", thumbPath = "", sizeBytes = 1, width = 1, height = 1, sha256 = "") else null,
        mediaCount = if (photo) 1 else 0,
    )

    @Test fun `memories from years ago come first, photos before text, capped`() {
        val rewind = RewindResult(RewindScope.DAY, listOf(card(1), card(2, photo = true), card(3), card(4, photo = true), card(5), card(6)).mapIndexed { i, c -> RewindItem(c, i + 1) })
        val pages = DailyStory.build(rewind, spotlight = null, memoryOfTheDay = null)
        assertEquals(listOf(2L, 4L, 1L, 3L), pages.map { (it as StoryPage.Moment).card.memory.id })
        assertEquals(listOf(2, 4, 1, 3), pages.map { (it as StoryPage.Moment).yearsAgo })
    }

    @Test fun `spotlight then memory of the day, without repeating a memory`() {
        val spotlight = StoryPage.Spotlight(Person(id = 7, name = "Nana"), 5, card(9))
        val rewind = RewindResult(RewindScope.WEEK, listOf(RewindItem(card(1), 2)))
        val pages = DailyStory.build(rewind, spotlight, memoryOfTheDay = card(8))
        assertEquals(3, pages.size)
        assertTrue(pages[1] is StoryPage.Spotlight)
        assertNull((pages[2] as StoryPage.Moment).yearsAgo)
        assertEquals(2, DailyStory.build(rewind, spotlight, memoryOfTheDay = card(1)).size)
    }

    @Test fun `empty when there is nothing to show`() {
        assertTrue(DailyStory.build(null, null, null).isEmpty())
    }

    @Test fun `spotlight rotates daily among people with enough memories`() {
        val people = listOf(Person(id = 3, name = "C"), Person(id = 1, name = "A"), Person(id = 2, name = "B"))
        val counts = mapOf(1L to 5, 2L to 1, 3L to 2)
        val picks = (0L..3L).map { DailyStory.spotlightPerson(people, counts, today.plusDays(it))!!.id }
        assertTrue(picks.all { it == 1L || it == 3L })
        assertNotEquals(picks[0], picks[1])
        assertEquals(DailyStory.spotlightPerson(people, counts, today), DailyStory.spotlightPerson(people.reversed(), counts, today))
        assertNull(DailyStory.spotlightPerson(people, mapOf(2L to 1), today))
    }

    @Test fun `memory of the day is stable for the day and changes over days`() {
        val ids = (1L..50L).toList()
        assertEquals(DailyStory.memoryOfTheDayId(ids, today), DailyStory.memoryOfTheDayId(ids.shuffled(), today))
        val week = (0L..6L).map { DailyStory.memoryOfTheDayId(ids, today.plusDays(it)) }.toSet()
        assertTrue(week.size >= 4)
        assertNull(DailyStory.memoryOfTheDayId(emptyList(), today))
    }

    @Test fun `memory of the day matches the iPhone app`() {
        // Pinned in RewindTests.swift too, so both apps pick the same memory on the same day.
        val ids = (1L..50L).toList()
        assertEquals(listOf(44L, 49L, 38L), (0L..2L).map { DailyStory.memoryOfTheDayId(ids, today.plusDays(it)) })
    }
}
