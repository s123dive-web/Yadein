package com.yaadein.domain.relive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestonesTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `the tenth, fiftieth and hundredth memory are milestones for a little while`() {
        assertNull(Milestones.memoryCount(0))
        assertNull(Milestones.memoryCount(9))
        assertEquals(Milestone.MemoryCount(10), Milestones.memoryCount(10))
        assertEquals(Milestone.MemoryCount(10), Milestones.memoryCount(12))
        assertNull(Milestones.memoryCount(13))
        assertEquals(Milestone.MemoryCount(50), Milestones.memoryCount(50))
        assertEquals(Milestone.MemoryCount(50), Milestones.memoryCount(54))
        assertNull(Milestones.memoryCount(55))
        assertEquals(Milestone.MemoryCount(100), Milestones.memoryCount(109))
        assertNull(Milestones.memoryCount(110))
        assertEquals(Milestone.MemoryCount(1000), Milestones.memoryCount(1001))
    }

    @Test
    fun `years with someone count whole years from the first memory`() {
        assertNull(Milestones.yearsWith(1, null, today))
        assertNull(Milestones.yearsWith(1, LocalDate.of(2026, 1, 1).toEpochDay(), today))
        assertEquals(Milestone.YearsWith(1, 1), Milestones.yearsWith(1, LocalDate.of(2025, 11, 10).toEpochDay(), today))
        assertEquals(Milestone.YearsWith(7, 19), Milestones.yearsWith(7, LocalDate.of(2006, 12, 1).toEpochDay(), today))
    }

    @Test
    fun `the today story mentions the years only around the anniversary`() {
        val first = LocalDate.of(2016, 11, 12).toEpochDay()
        assertEquals(Milestone.YearsWith(3, 10), Milestones.anniversaryToday(3, first, today)) // two days before
        assertEquals(Milestone.YearsWith(3, 10), Milestones.anniversaryToday(3, first, LocalDate.of(2026, 11, 15)))
        assertNull(Milestones.anniversaryToday(3, first, LocalDate.of(2026, 11, 16)))
        assertNull(Milestones.anniversaryToday(3, first, LocalDate.of(2016, 11, 13)), "the first memory itself is not an anniversary")
        // Across the new year: first memory on 30 December, today 1 January.
        assertEquals(Milestone.YearsWith(3, 5), Milestones.anniversaryToday(3, LocalDate.of(2020, 12, 30).toEpochDay(), LocalDate.of(2026, 1, 1)))
        assertNull(Milestones.anniversaryToday(3, null, today))
    }
}
