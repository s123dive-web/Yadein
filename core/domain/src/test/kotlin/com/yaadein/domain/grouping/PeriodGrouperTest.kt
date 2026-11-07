package com.yaadein.domain.grouping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset

class PeriodGrouperTest {
    private val zone = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int = 1, h: Int = 12) =
        PickedItem("$y-$m-$d-$h", LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

    private val library = listOf(
        at(2022, 12, 25), at(2022, 12, 31),
        at(2023, 1, 5), at(2023, 1, 20), at(2023, 6, 10), at(2023, 12, 24),
        at(2024, 3, 3), at(2024, 3, 4), at(2024, 3, 5), at(2024, 12, 1),
        PickedItem("undated", null),
    )

    @Test
    fun `split by year makes one memory per year, newest first`() {
        val split = PeriodGrouper.split(library.shuffled(), SplitBy.YEAR, zone = zone)
        assertEquals(listOf(2024, 2023, 2022), split.buckets.map { it.year })
        assertEquals(listOf(4, 4, 2), split.buckets.map { it.items.size })
        assertTrue(split.buckets.all { it.month == null })
        assertEquals(1, split.undated.size)
        assertEquals(0, split.filteredOut)
        assertEquals(3, split.memoryCount)
    }

    @Test
    fun `split by month makes one memory per month that has photos`() {
        val split = PeriodGrouper.split(library, SplitBy.MONTH, zone = zone)
        assertEquals(6, split.memoryCount)
        val first = split.buckets.first()
        assertEquals(2024, first.year)
        assertEquals(Month.DECEMBER, first.month)
        val march = split.buckets.single { it.year == 2024 && it.month == Month.MARCH }
        assertEquals(3, march.items.size)
        assertEquals(listOf("2024-3-3-12", "2024-3-4-12", "2024-3-5-12"), march.items.map { it.key })
    }

    @Test
    fun `year filter keeps only that year and splits it by month`() {
        val split = PeriodGrouper.split(library, SplitBy.MONTH, PeriodFilter(years = setOf(2023)), zone)
        assertEquals(listOf(Month.DECEMBER, Month.JUNE, Month.JANUARY), split.buckets.map { it.month })
        assertTrue(split.buckets.all { it.year == 2023 })
        assertEquals(6, split.filteredOut)
        assertEquals(4, split.keptCount)
    }

    @Test
    fun `month filter keeps that month across years and splits by year`() {
        val split = PeriodGrouper.split(library, SplitBy.YEAR, PeriodFilter(months = setOf(Month.DECEMBER)), zone)
        assertEquals(listOf(2024, 2023, 2022), split.buckets.map { it.year })
        assertEquals(listOf(1, 1, 2), split.buckets.map { it.items.size })
    }

    @Test
    fun `several years, and year plus month together`() {
        assertEquals(listOf(2024, 2022), PeriodGrouper.split(library, SplitBy.YEAR, PeriodFilter(years = setOf(2022, 2024)), zone).buckets.map { it.year })
        val dec2023 = PeriodGrouper.split(library, SplitBy.MONTH, PeriodFilter(years = setOf(2023), months = setOf(Month.DECEMBER)), zone)
        assertEquals(1, dec2023.memoryCount)
        assertEquals("2023-12-24-12", dec2023.buckets.single().items.single().key)
    }

    @Test
    fun `filter that matches nothing yields no memories`() {
        val split = PeriodGrouper.split(library, SplitBy.MONTH, PeriodFilter(years = setOf(2010, 2011)), zone)
        assertEquals(0, split.memoryCount)
        assertEquals(10, split.filteredOut)
        assertNull(split.buckets.firstOrNull())
    }

    @Test
    fun `years lists distinct years newest first`() {
        assertEquals(listOf(2024, 2023, 2022), PeriodGrouper.years(library, zone))
    }
}
