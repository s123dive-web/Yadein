package com.yaadein.domain.grouping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class PhotoGrouperTest {
    private val zone = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int, faces: Set<Long> = emptySet()) =
        PickedItem("$y-$m-$d-$h", LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), faces = faces)

    @Test
    fun `same day and consecutive days group together, gaps split`() {
        val items = listOf(
            at(2024, 12, 20, 9), at(2024, 12, 20, 18), at(2024, 12, 21, 10), at(2024, 12, 22, 12), // Goa trip
            at(2024, 10, 31, 19), at(2024, 10, 31, 21), // Diwali
            at(2024, 9, 9, 17), // birthday
        )
        val groups = PhotoGrouper.group(items.shuffled(), zone)
        assertEquals(3, groups.size)
        assertEquals(LocalDate.of(2024, 12, 20), groups[0].startDate)
        assertEquals(LocalDate.of(2024, 12, 22), groups[0].endDate)
        assertTrue(groups[0].isMultiDay)
        assertEquals(4, groups[0].items.size)
        assertEquals(LocalDate.of(2024, 10, 31), groups[1].startDate)
        assertEquals(2, groups[1].items.size)
        assertEquals(1, groups[2].items.size)
    }

    @Test
    fun `undated items form a trailing group`() {
        val groups = PhotoGrouper.group(listOf(at(2024, 1, 1, 9), PickedItem("x", null), PickedItem("y", null)), zone)
        assertEquals(2, groups.size)
        assertTrue(groups.last().isUndated)
        assertEquals(2, groups.last().items.size)
        assertNull(groups.last().startDate)
    }

    @Test
    fun `faces split a day into two gatherings when enabled`() {
        val items = listOf(
            at(2024, 5, 5, 10, setOf(1, 2)), at(2024, 5, 5, 11, setOf(2)),
            at(2024, 5, 5, 19, setOf(7, 8)), at(2024, 5, 5, 20, setOf(8)),
        )
        assertEquals(1, PhotoGrouper.group(items, zone, useFaces = false).size)
        val withFaces = PhotoGrouper.group(items, zone, useFaces = true)
        assertEquals(2, withFaces.size)
    }

    @Test
    fun `merge and split round trip`() {
        val groups = PhotoGrouper.group(listOf(at(2024, 1, 1, 9), at(2024, 1, 5, 9)), zone)
        assertEquals(2, groups.size)
        val merged = PhotoGrouper.merge(groups[0], groups[1])
        assertEquals(2, merged.items.size)
        assertEquals(LocalDate.of(2024, 1, 1), merged.startDate)
        assertEquals(LocalDate.of(2024, 1, 5), merged.endDate)
        val (a, b) = PhotoGrouper.split(merged, 1, zone)
        assertEquals(1, a.items.size); assertEquals(1, b.items.size)
        assertEquals(LocalDate.of(2024, 1, 5), b.startDate)
    }
}
