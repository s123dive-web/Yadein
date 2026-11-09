package com.yaadein.feature.timeline

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

class TimelineSeparatorTest {
    private fun card(date: LocalDate) = TimelineItem.Card(
        MemoryCard(Memory(id = date.toEpochDay(), type = MemoryType.EVENT, title = "m", date = Dates.exact(date)), emptyList(), null, 0),
    )

    @Test
    fun `first card gets a year header`() {
        val sep = TimelineViewModel.separatorBetween(null, card(LocalDate.of(2024, 12, 3)))
        assertEquals(TimelineItem.YearHeader(2024, Month.DECEMBER), sep)
    }

    @Test
    fun `year change inserts year header, month change inserts month header`() {
        assertEquals(
            TimelineItem.YearHeader(2023, Month.NOVEMBER),
            TimelineViewModel.separatorBetween(card(LocalDate.of(2024, 1, 3)), card(LocalDate.of(2023, 11, 3))),
        )
        assertEquals(
            TimelineItem.MonthHeader(2024, Month.OCTOBER),
            TimelineViewModel.separatorBetween(card(LocalDate.of(2024, 12, 3)), card(LocalDate.of(2024, 10, 3))),
        )
    }

    @Test
    fun `same month gets no separator and trailing null gets none`() {
        assertNull(TimelineViewModel.separatorBetween(card(LocalDate.of(2024, 12, 3)), card(LocalDate.of(2024, 12, 1))))
        assertNull(TimelineViewModel.separatorBetween(card(LocalDate.of(2024, 12, 3)), null))
    }

    @Test
    fun `year-only memory sits at the start of its year`() {
        val yearOnly = TimelineItem.Card(MemoryCard(Memory(id = 1, type = MemoryType.TEXT_ONLY, title = "y", date = Dates.year(2015)), emptyList(), null, 0))
        val sep = TimelineViewModel.separatorBetween(card(LocalDate.of(2016, 2, 1)), yearOnly)
        assertEquals(TimelineItem.YearHeader(2015, Month.JANUARY), sep)
    }
}
