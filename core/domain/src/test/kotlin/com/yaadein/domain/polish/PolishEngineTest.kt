package com.yaadein.domain.polish

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Mood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

class PolishEngineTest {
    private val today = LocalDate.of(2026, 11, 5)
    private fun polish(s: String) = PolishEngine.polish(s, today, Locale.ENGLISH)

    @Test
    fun `goa trip 2024 becomes a getaway with a year`() {
        val r = polish("goa trip 2024")
        assertEquals("Goa Getaway, 2024", r.title)
        assertEquals("beach", r.coverStyle)
        assertEquals("Goa", r.place)
        assertEquals(DateKind.YEAR, r.date.kind)
        assertEquals(2024, java.time.LocalDate.ofEpochDay(r.date.startEpochDay).year)
        assertTrue(r.description.contains("Goa Getaway in 2024"))
        assertEquals(Mood.JOYFUL, r.mood)
    }

    @Test
    fun `month and year give a month-only date`() {
        val r = polish("Goa trip dec 2024")
        assertEquals("Goa Getaway, December 2024", r.title)
        assertEquals(DateKind.MONTH, r.date.kind)
    }

    @Test
    fun `hinglish diwali at home in 2019`() {
        val r = polish("diwali ghar pe 2019")
        assertEquals("diwali", r.coverStyle)
        assertEquals("Diwali Ghar, 2019", r.title)
        assertEquals(Mood.CELEBRATION, r.mood)
    }

    @Test
    fun `season becomes a fuzzy label`() {
        val r = polish("summer 2015 mahabaleshwar")
        assertEquals(DateKind.FUZZY, r.date.kind)
        assertEquals("Summer 2015", r.date.label)
        assertEquals("Mahabaleshwar", r.place)
        assertEquals("mountain", r.coverStyle)
        assertEquals("Mahabaleshwar, Summer 2015", r.title)
    }

    @Test
    fun `exact numeric date is parsed`() {
        val r = polish("priya ki shaadi 25/11/2022")
        assertEquals(DateKind.EXACT, r.date.kind)
        assertEquals(LocalDate.of(2022, 11, 25).toEpochDay(), r.date.startEpochDay)
        assertEquals("wedding", r.coverStyle)
        assertTrue(r.title.startsWith("Priya Shaadi"))
    }

    @Test
    fun `today and yesterday`() {
        assertEquals(today.toEpochDay(), polish("chai with aai today").date.startEpochDay)
        assertEquals(today.minusDays(1).toEpochDay(), polish("cricket match yesterday").date.startEpochDay)
        assertEquals("sports", polish("cricket match yesterday").coverStyle)
    }

    @Test
    fun `no date means today and no suffix`() {
        val r = polish("rainy evening bhajiyas")
        assertFalse(r.dateDetected)
        assertEquals("Rainy Evening Bhajiyas", r.title)
        assertEquals("rain", r.coverStyle)
        assertEquals(today.toEpochDay(), r.date.startEpochDay)
    }

    @Test
    fun `month without year assumes the most recent occurrence`() {
        val r = polish("trek in march")
        assertEquals(DateKind.MONTH, r.date.kind)
        assertEquals(LocalDate.of(2026, 3, 1).toEpochDay(), r.date.startEpochDay)
        val r2 = polish("trek in december")
        assertEquals(LocalDate.of(2025, 12, 1).toEpochDay(), r2.date.startEpochDay)
    }

    @Test
    fun `unknown text gets a plain cover and a warm description`() {
        val r = polish("random thoughts")
        assertTrue(r.coverStyle.startsWith("plain_"))
        assertTrue(r.description.isNotBlank())
        assertEquals("Random Thoughts", r.title)
    }

    @Test
    fun `place after in is detected`() {
        val r = polish("lunch in Nagpur with cousins 2021")
        assertEquals("Nagpur", r.place)
        assertEquals("food", r.coverStyle)
    }

    @Test
    fun `is deterministic`() {
        assertEquals(polish("goa trip 2024"), polish("goa trip 2024"))
    }

    @Test
    fun `titleFor builds photo-first titles from date, people, festival and place`() {
        val month = com.yaadein.domain.dates.Dates.month(java.time.YearMonth.of(2024, 10))
        assertEquals("October 2024 with Aaji", PolishEngine.titleFor(month, peopleNames = listOf("Aaji"), locale = Locale.ENGLISH))
        assertEquals("October 2024 with Aaji and Rahul", PolishEngine.titleFor(month, peopleNames = listOf("Aaji", "Rahul"), locale = Locale.ENGLISH))
        assertEquals("October 2024 with Aaji, Rahul and others", PolishEngine.titleFor(month, peopleNames = listOf("Aaji", "Rahul", "Priya", "Neha"), locale = Locale.ENGLISH))
        assertEquals("Diwali 2024 with Aaji", PolishEngine.titleFor(month, festival = "Diwali", peopleNames = listOf("Aaji"), locale = Locale.ENGLISH))
        val day = com.yaadein.domain.dates.Dates.exact(LocalDate.of(2024, 12, 20))
        assertEquals("20 Dec 2024 in Pune", PolishEngine.titleFor(day, place = "Pune", locale = Locale.ENGLISH))
        assertEquals("20 Dec 2024 with Aaji", PolishEngine.titleFor(day, place = "Pune", peopleNames = listOf(" Aaji ", "Aaji"), locale = Locale.ENGLISH))
        assertEquals("Summer 2018", PolishEngine.titleFor(com.yaadein.domain.dates.Dates.fuzzy("Summer 2018", 2018, 5), locale = Locale.ENGLISH))
    }
}
