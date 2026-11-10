package com.yaadein.domain.relive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ThenAndNowTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun photo(id: Long, memory: Long, date: LocalDate) = DatedPhoto(id, memory, date.toEpochDay())

    @Test
    fun `pairs an early photo with a recent one at least three years apart`() {
        val photos = listOf(
            photo(1, 10, LocalDate.of(2012, 5, 1)),
            photo(2, 11, LocalDate.of(2019, 8, 15)),
            photo(3, 12, LocalDate.of(2026, 3, 2)),
        )
        val pair = ThenAndNow.pick(photos, today)
        assertNotNull(pair)
        assertEquals(1L, pair!!.then.mediaId)
        assertEquals(3L, pair.now.mediaId)
        assertEquals(13, pair.yearsApart)
    }

    @Test
    fun `no pair when everything is recent, from one memory, or there is one photo`() {
        assertNull(ThenAndNow.pick(emptyList(), today))
        assertNull(ThenAndNow.pick(listOf(photo(1, 10, LocalDate.of(2010, 1, 1))), today))
        assertNull(ThenAndNow.pick(listOf(photo(1, 10, LocalDate.of(2024, 1, 1)), photo(2, 11, LocalDate.of(2026, 1, 1))), today))
        // Three years to the day counts; one day short does not.
        assertNotNull(ThenAndNow.pick(listOf(photo(1, 10, LocalDate.of(2023, 6, 1)), photo(2, 11, LocalDate.of(2026, 6, 1))), today))
        assertNull(ThenAndNow.pick(listOf(photo(1, 10, LocalDate.of(2023, 6, 2)), photo(2, 11, LocalDate.of(2026, 6, 1))), today))
        // Two photos of the same memory are not a "then and now", however the memory is dated.
        assertNull(ThenAndNow.pick(listOf(photo(1, 10, LocalDate.of(2010, 1, 1)), photo(2, 10, LocalDate.of(2010, 1, 1))), today))
    }

    @Test
    fun `the pair is the same all day and varies across days when there is a choice`() {
        val photos = (1L..8L).map { photo(it, 100 + it, LocalDate.of(2010, 1, 1).plusDays(it * 20)) } +
            (9L..16L).map { photo(it, 100 + it, LocalDate.of(2026, 1, 1).plusDays(it * 10)) }
        val first = ThenAndNow.pick(photos, today)!!
        assertEquals(first, ThenAndNow.pick(photos.shuffled(java.util.Random(3)), today))
        assertTrue(first.then.mediaId <= 8 && first.now.mediaId >= 9)
        assertTrue(first.yearsApart >= ThenAndNow.MIN_YEARS)
        val pairs = (0L until 30L).map { ThenAndNow.pick(photos, today.plusDays(it))!! }.toSet()
        assertTrue(pairs.size > 3, "expected different pairs on different days, got ${pairs.size}")
        assertNotEquals(pairs.map { it.then.mediaId }.toSet().size, 1)
    }

    @Test
    fun `a recent photo without an old enough partner is skipped for one that has`() {
        // The newest photo's memory is the only old one's memory too (a memory re-dated); another recent photo still pairs.
        val photos = listOf(
            photo(1, 10, LocalDate.of(2011, 1, 1)),
            photo(2, 10, LocalDate.of(2026, 10, 1)),
            photo(3, 11, LocalDate.of(2026, 9, 1)),
        )
        for (d in 0L until 10L) {
            val pair = ThenAndNow.pick(photos, today.plusDays(d))
            assertEquals(1L to 3L, pair!!.then.mediaId to pair.now.mediaId)
        }
    }
}
