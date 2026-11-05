package com.yaadein.domain.dates

import com.yaadein.domain.model.DateKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class DatesTest {
    @Test
    fun `year-only memory anchors to 1 January`() {
        val d = Dates.year(2015)
        assertEquals(LocalDate.of(2015, 1, 1), Dates.start(d))
        assertEquals(DateKind.YEAR, d.kind)
        assertEquals("2015", Dates.label(d, Locale.ENGLISH).text)
    }

    @Test
    fun `month-only memory anchors to the first of the month`() {
        val d = Dates.month(YearMonth.of(2024, 12))
        assertEquals(LocalDate.of(2024, 12, 1), Dates.start(d))
        assertEquals("December 2024", Dates.label(d, Locale.ENGLISH).text)
    }

    @Test
    fun `fuzzy label is preserved`() {
        val d = Dates.fuzzy("Summer 2015", 2015, 5)
        assertEquals("Summer 2015", Dates.label(d, Locale.ENGLISH).text)
        assertEquals(LocalDate.of(2015, 5, 1), Dates.start(d))
    }

    @Test
    fun `exact range formats compactly in the same year`() {
        val d = Dates.exact(LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 24))
        assertEquals("20 Dec – 24 Dec 2024", Dates.label(d, Locale.ENGLISH).text)
    }

    @Test
    fun `exact end before start is ignored`() {
        val d = Dates.exact(LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 1))
        assertNull(d.endEpochDay)
    }

    @Test
    fun `days until birthday wraps to next year`() {
        val birthday = LocalDate.of(1990, 3, 10).toEpochDay()
        assertEquals(0, Dates.daysUntilBirthday(birthday, LocalDate.of(2026, 3, 10)))
        assertEquals(364, Dates.daysUntilBirthday(birthday, LocalDate.of(2026, 3, 11)))
        assertEquals(9, Dates.daysUntilBirthday(birthday, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `age counts completed years`() {
        val birthday = LocalDate.of(1990, 3, 10).toEpochDay()
        assertEquals(35, Dates.age(birthday, LocalDate.of(2026, 3, 9)))
        assertEquals(36, Dates.age(birthday, LocalDate.of(2026, 3, 10)))
    }
}
