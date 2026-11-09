package com.yaadein.domain.relive

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class RewindTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun card(id: Long, date: MemoryDate) = MemoryCard(
        memory = Memory(id = id, type = MemoryType.EVENT, title = "m$id", date = date),
        people = emptyList(), cover = null, mediaCount = 0,
    )
    private fun exact(id: Long, y: Int, m: Int, d: Int) = card(id, Dates.exact(LocalDate.of(y, m, d)))

    @Test fun `same day wins over week and month`() {
        val result = Rewind.pick(listOf(exact(1, 2020, 11, 9), exact(2, 2023, 11, 10), exact(3, 2024, 11, 25)), today)!!
        assertEquals(RewindScope.DAY, result.scope)
        assertEquals(listOf(1L), result.items.map { it.card.memory.id })
        assertEquals(6, result.items.single().yearsAgo)
    }

    @Test fun `this year is never on this day`() {
        assertNull(Rewind.pick(listOf(exact(1, 2026, 11, 9), exact(2, 2026, 11, 2)), today))
    }

    @Test fun `falls back to the same week, newest first`() {
        val result = Rewind.pick(listOf(exact(1, 2019, 11, 6), exact(2, 2024, 11, 12), exact(3, 2024, 11, 13), exact(4, 2022, 11, 20)), today)!!
        assertEquals(RewindScope.WEEK, result.scope)
        assertEquals(listOf(2L, 1L), result.items.map { it.card.memory.id })
        assertEquals(listOf(2, 7), result.items.map { it.yearsAgo })
    }

    @Test fun `week crosses the new year`() {
        val jan2 = LocalDate.of(2026, 1, 2)
        val result = Rewind.pick(listOf(exact(1, 2023, 12, 30)), jan2)!!
        assertEquals(RewindScope.WEEK, result.scope)
        assertEquals(2, result.items.single().yearsAgo) // 30 Dec 2023 → 30 Dec 2025 is 2 years later
    }

    @Test fun `last week of December this year is not years ago`() {
        assertNull(Rewind.pick(listOf(exact(1, 2025, 12, 30)), LocalDate.of(2026, 1, 2)))
    }

    @Test fun `falls back to the month, including month-only dates`() {
        val result = Rewind.pick(listOf(exact(1, 2021, 11, 28), card(2, Dates.month(YearMonth.of(2018, 11))), card(3, Dates.year(2015))), today)!!
        assertEquals(RewindScope.MONTH, result.scope)
        assertEquals(listOf(1L, 2L), result.items.map { it.card.memory.id })
    }

    @Test fun `year-only and fuzzy dates never match`() {
        assertNull(Rewind.pick(listOf(card(1, Dates.year(2015)), card(2, Dates.fuzzy("Summer", 2018, 11))), today))
    }

    @Test fun `29 February shows on 28 February in other years`() {
        val result = Rewind.pick(listOf(exact(1, 2020, 2, 29)), LocalDate.of(2027, 2, 28))!!
        assertEquals(RewindScope.DAY, result.scope)
    }

    @Test fun `windows cover the month and the week around today in each earlier year`() {
        val windows = Rewind.windows(LocalDate.of(2026, 11, 29), earliestYear = 2024)
        assertEquals(2, windows.size)
        val w2025 = windows.first()
        assertEquals(LocalDate.of(2025, 11, 1).toEpochDay(), w2025.first)
        assertEquals(LocalDate.of(2025, 12, 2).toEpochDay(), w2025.last)
        assertTrue(Rewind.windows(today, earliestYear = 2026).isEmpty())
    }

    @Test fun `results are capped`() {
        val cards = (1..30).map { exact(it.toLong(), 2000 + it % 20, 11, 9) }
        assertEquals(Rewind.MAX_ITEMS, Rewind.pick(cards, today)!!.items.size)
    }

    @Test fun `covers prefer a row with media, otherwise the first row`() {
        val picks = Covers.pick(
            listOf(
                2025 to CoverPick(1, null, "beach"),
                2025 to CoverPick(2, 20, "plain_plum"),
                2025 to CoverPick(3, 30, "plain_plum"),
                2024 to CoverPick(4, null, "rain"),
            ),
        )
        assertEquals(20L, picks[2025]!!.mediaId)
        assertEquals(CoverPick(4, null, "rain"), picks[2024])
    }
}
