package com.yaadein.domain.reminders

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import com.yaadein.domain.relive.Festival
import com.yaadein.domain.relive.Festivals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RemindersTest {
    private fun card(id: Long, title: String, date: LocalDate, exact: Boolean = true) = MemoryCard(
        memory = Memory(id = id, type = MemoryType.EVENT, title = title, date = if (exact) Dates.exact(date) else Dates.year(date.year)),
        people = emptyList(), cover = null, mediaCount = 0,
    )

    @Test
    fun `on this day finds earlier years only, most recent first`() {
        val cards = listOf(
            card(1, "Goa", LocalDate.of(2021, 11, 7)),
            card(2, "Diwali", LocalDate.of(2024, 11, 7)),
            card(3, "Today", LocalDate.of(2026, 11, 7)),
            card(4, "Other day", LocalDate.of(2024, 11, 8)),
            card(5, "Just a year", LocalDate.of(2020, 11, 7), exact = false),
        )
        val digest = Reminders.build(cards, emptyList(), 0L)
        val matches = Reminders.onThisDay(digest, LocalDate.of(2026, 11, 7))
        assertEquals(listOf("Diwali" to 2, "Goa" to 5), matches.map { it.memory.title to it.yearsAgo })
    }

    @Test
    fun `at most three memories are kept per calendar day`() {
        val cards = (2010..2020).map { card(it.toLong(), "Y$it", LocalDate.of(it, 3, 1)) }
        val digest = Reminders.build(cards, emptyList(), 0L)
        assertEquals(listOf("Y2020", "Y2019", "Y2018"), digest.memories.map { it.title })
    }

    @Test
    fun `birthdays today and tomorrow with age`() {
        val people = listOf(
            Person(id = 1, name = "Rahul", birthdayEpochDay = LocalDate.of(1990, 11, 8).toEpochDay()),
            Person(id = 2, name = "Aai", birthdayEpochDay = LocalDate.of(1960, 11, 7).toEpochDay()),
            Person(id = 3, name = "Far", birthdayEpochDay = LocalDate.of(1990, 12, 25).toEpochDay()),
            Person(id = 4, name = "Unknown"),
        )
        val digest = Reminders.build(emptyList(), people, 0L)
        val upcoming = Reminders.birthdays(digest, LocalDate.of(2026, 11, 7), daysAhead = 1)
        assertEquals(listOf("Aai" to 0, "Rahul" to 1), upcoming.map { it.person.name to it.inDays })
        assertEquals(66, upcoming.first().turning)
    }

    @Test
    fun `a 29 February birthday falls on 28 February in other years`() {
        assertEquals(LocalDate.of(2027, 2, 28), Reminders.nextBirthday(LocalDate.of(2000, 2, 29), LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), Reminders.nextBirthday(LocalDate.of(2000, 2, 29), LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun `weekly prompt is deterministic and usually about family`() {
        val people = listOf(Person(id = 1, name = "Aaji", relation = Relation.FAMILY), Person(id = 2, name = "Neha", relation = Relation.FRIEND))
        val digest = Reminders.build(emptyList(), people, 0L)
        val sunday = LocalDate.of(2026, 11, 8)
        assertTrue(Reminders.isPromptDay(sunday))
        assertEquals(Reminders.weeklyPrompt(digest, sunday), Reminders.weeklyPrompt(digest, sunday))
        val picks = (0 until 8).map { Reminders.weeklyPrompt(digest, sunday.plusWeeks(it.toLong())) }
        assertTrue(picks.count { it.person?.relation == Relation.FAMILY } >= 4)
        assertTrue(picks.any { it.person == null })
        assertTrue(picks.all { it.index >= 0 })
    }

    @Test
    fun `festival reminder fires on the exact day only`() {
        val festivals = Festivals.parse("diwali,2024,2024-10-31\nchristmas,2024,2024-12-25")
        assertEquals(Festival.DIWALI, Reminders.festivalToday(festivals, LocalDate.of(2024, 10, 31))?.festival)
        assertNull(Reminders.festivalToday(festivals, LocalDate.of(2024, 10, 30))) // inside the window, but not the day
        assertNull(Reminders.festivalToday(festivals, LocalDate.of(2025, 10, 31))) // the table has no 2025 row
        assertEquals(Festival.CHRISTMAS, Reminders.festivalToday(Festivals.bundled(), LocalDate.of(2030, 12, 25))?.festival)
    }

    @Test
    fun `with nobody added prompts are about the user`() {
        val pick = Reminders.weeklyPrompt(Reminders.build(emptyList(), emptyList(), 0L), LocalDate.of(2026, 11, 8))
        assertNull(pick.person)
        assertNotNull(pick)
    }
}
