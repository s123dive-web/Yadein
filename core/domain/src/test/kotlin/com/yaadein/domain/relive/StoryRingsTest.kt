package com.yaadein.domain.relive

import com.yaadein.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StoryRingsTest {
    private val day = LocalDate.of(2026, 11, 10)
    private val people = (1L..6L).map { Person(id = it, name = "Person $it") }
    private fun counts(vararg pairs: Pair<Long, Int>) = mapOf(*pairs)

    @Test
    fun `people need three memories with photos and at most three are shown`() {
        val few = StoryRings.people(people, counts(1L to 3, 2L to 2, 3L to 9), day)
        assertEquals(listOf(1L, 3L), few.map { it.id })

        val all = counts(1L to 3, 2L to 4, 3L to 5, 4L to 6, 5L to 7, 6L to 8)
        val today = StoryRings.people(people, all, day)
        assertEquals(StoryRings.MAX_PEOPLE, today.size)
        assertEquals(today, StoryRings.people(people.shuffled(java.util.Random(1)), all, day), "same all day, whatever the input order")
    }

    @Test
    fun `the people window moves on by one person each day and reaches everyone`() {
        val all = people.associate { it.id to 5 }
        val today = StoryRings.people(people, all, day).map { it.id }
        val tomorrow = StoryRings.people(people, all, day.plusDays(1)).map { it.id }
        assertEquals(today.drop(1), tomorrow.take(2))
        val seen = (0L until people.size).flatMap { d -> StoryRings.people(people, all, day.plusDays(d)).map { it.id } }.toSet()
        assertEquals(people.map { it.id }.toSet(), seen)
    }

    @Test
    fun `a place needs two memories and rotates daily among the top places`() {
        assertNull(StoryRings.place(listOf(RingPlace("Pune", 1), RingPlace(" ", 9)), day))
        val places = listOf(RingPlace("Pune", 12), RingPlace("Goa", 5), RingPlace("Nashik", 2), RingPlace("Shimla", 1))
        val picks = (0L..5L).map { StoryRings.place(places, day.plusDays(it)) }
        assertEquals(setOf("Pune", "Goa", "Nashik"), picks.toSet())
        assertEquals(picks[0], StoryRings.place(places.reversed(), day))
    }

    @Test
    fun `a festival ring appears while it is on and up to a fortnight before`() {
        val festivals = Festivals.parse("diwali,2026,2026-11-08\nchristmas,2026,2026-12-25\nholi,2027,2027-03-22")
        assertEquals(Festival.DIWALI, StoryRings.festival(festivals, LocalDate.of(2026, 11, 10))?.festival) // inside Diwali's ±3 days
        assertNull(StoryRings.festival(festivals, LocalDate.of(2026, 11, 20)))
        assertEquals(Festival.CHRISTMAS, StoryRings.festival(festivals, LocalDate.of(2026, 12, 11))?.festival)
        assertNull(StoryRings.festival(festivals, LocalDate.of(2026, 12, 10)))
    }

    @Test
    fun `rings come in a fixed order and only when they have something to show`() {
        val festival = FestivalDate(Festival.CHRISTMAS, LocalDate.of(2026, 12, 25))
        val rings = StoryRings.build(
            today = LocalDate.of(2026, 12, 20), hasToday = true, people = people, photoMemoryCounts = counts(2L to 4, 5L to 3),
            memoriesThisYear = 7, festival = festival, festivalHasMemories = true, places = listOf(RingPlace("Pune", 4)),
        )
        assertEquals(listOf(RingKind.TODAY, RingKind.PERSON, RingKind.PERSON, RingKind.YEAR, RingKind.FESTIVAL, RingKind.PLACE), rings.map { it.kind })
        assertEquals(listOf("today", "person-2", "person-5", "year-2026", "festival-christmas", "place-pune"), rings.map { it.key })
        assertEquals(5, rings.first { it.kind == RingKind.FESTIVAL }.festivalInDays)
        assertEquals(rings.size, rings.map { it.key }.toSet().size)

        val empty = StoryRings.build(
            today = day, hasToday = false, people = people, photoMemoryCounts = emptyMap(),
            memoriesThisYear = 0, festival = festival, festivalHasMemories = false, places = emptyList(),
        )
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `a ring is unseen until it is watched and unseen again the next day`() {
        var stored = ""
        assertFalse(StorySeen.isSeen(stored, "today", day))
        stored = StorySeen.mark(stored, "today", day)
        stored = StorySeen.mark(stored, "person-2", day)
        stored = StorySeen.mark(stored, "today", day)
        assertEquals(setOf("today", "person-2"), StorySeen.seenToday(stored, day))
        assertFalse(StorySeen.isSeen(stored, "year-2026", day))
        assertTrue(StorySeen.seenToday(stored, day.plusDays(1)).isEmpty())
        // Marking on a new day forgets yesterday.
        assertEquals(setOf("place-pune"), StorySeen.seenToday(StorySeen.mark(stored, "place-pune", day.plusDays(1)), day.plusDays(1)))
        assertTrue(StorySeen.seenToday("garbage", day).isEmpty())
    }
}
