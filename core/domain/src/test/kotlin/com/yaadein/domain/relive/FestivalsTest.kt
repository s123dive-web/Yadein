package com.yaadein.domain.relive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FestivalsTest {
    private val csv = """
        # festival,year,date
        diwali,2023,2023-11-12
        diwali,2024,2024-10-31
        diwali,2025,2025-10-20
        holi,2024,2024-03-25
        holi,2025,2025-03-14
        christmas,2024,2024-12-25
        bogus,2024,2024-01-01
        diwali,2026,not-a-date
        diwali,2027,2026-11-08
    """.trimIndent()
    private val f = Festivals.parse(csv)

    @Test
    fun `parses valid rows and drops bad ones`() {
        assertEquals(6, f.inYear(2024).size + f.inYear(2023).size + f.inYear(2025).size)
        assertEquals(2023..2025, f.years)
        assertEquals(listOf(Festival.HOLI, Festival.DIWALI, Festival.CHRISTMAS), f.inYear(2024).map { it.festival })
    }

    @Test
    fun `windows are plus or minus two days, three for Diwali, with exact edges`() {
        val diwali = f.date(Festival.DIWALI, 2024)!!
        assertEquals(1, f.on(LocalDate.of(2024, 10, 28)).size)
        assertEquals(1, f.on(LocalDate.of(2024, 11, 3)).size)
        assertTrue(f.on(LocalDate.of(2024, 10, 27)).isEmpty())
        assertTrue(f.on(LocalDate.of(2024, 11, 4)).isEmpty())
        assertEquals(diwali, f.primary(LocalDate.of(2024, 11, 1)))
        val holi = f.date(Festival.HOLI, 2024)!!
        assertEquals(holi, f.primary(LocalDate.of(2024, 3, 27)))
        assertNull(f.primary(LocalDate.of(2024, 3, 28)))
    }

    @Test
    fun `rewind gives earlier years of the current festival, newest first`() {
        val r = f.rewind(LocalDate.of(2025, 10, 22))!!
        assertEquals(Festival.DIWALI, r.festival)
        assertEquals(2025, r.year)
        assertEquals(2, r.yearsBack.size)
        assertEquals(LocalDate.of(2024, 10, 28).toEpochDay(), r.yearsBack[0].first)
        assertEquals(LocalDate.of(2024, 11, 3).toEpochDay(), r.yearsBack[0].last)
        assertEquals(LocalDate.of(2023, 11, 9).toEpochDay(), r.yearsBack[1].first)
        assertNull(f.rewind(LocalDate.of(2025, 6, 1)))
    }

    @Test
    fun `next festivals and filter windows`() {
        assertEquals(listOf(Festival.HOLI, Festival.DIWALI), f.next(LocalDate.of(2025, 1, 1), limit = 2).map { it.festival })
        assertEquals(3, f.windows(Festival.DIWALI).size)
        assertTrue(Festivals.parse("").years.isEmpty())
    }

    @Test
    fun `the bundled table covers 2010 to 2035 for every festival`() {
        val bundled = Festivals.bundled()
        assertEquals(2010..2035, bundled.years)
        for (year in 2010..2035) assertEquals(Festival.entries.size, bundled.inYear(year).size, "year $year")
        assertEquals(LocalDate.of(2024, 10, 31), bundled.date(Festival.DIWALI, 2024)!!.date)
        assertEquals(LocalDate.of(2025, 3, 14), bundled.date(Festival.HOLI, 2025)!!.date)
        assertEquals(LocalDate.of(2026, 9, 14), bundled.date(Festival.GANESH_CHATURTHI, 2026)!!.date)
    }
}
