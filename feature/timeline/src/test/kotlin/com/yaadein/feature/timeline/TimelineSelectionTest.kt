package com.yaadein.feature.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineSelectionTest {
    @Test
    fun `ticking adds in order and ticking again removes`() {
        var s = TimelineSelection()
        assertFalse(s.active)
        s = s.toggle(5).toggle(2).toggle(9)
        assertEquals(listOf(5L, 2L, 9L), s.ids)
        assertTrue(s.active)
        s = s.toggle(2)
        assertEquals(listOf(5L, 9L), s.ids)
        assertFalse(s.toggle(5).toggle(9).active)
    }

    @Test
    fun `select all keeps earlier ticks, skips duplicates and stops at the limit`() {
        val some = TimelineSelection().toggle(3).addAll(listOf(1, 2, 3, 4))
        assertEquals(listOf(3L, 1L, 2L, 4L), some.ids)
        assertFalse(some.limitHit)
        val full = TimelineSelection().addAll((1L..TimelineSelection.LIMIT + 10L).toList())
        assertEquals(TimelineSelection.LIMIT, full.ids.size)
        assertTrue(full.limitHit)
        // One more tick is refused, an untick clears the warning.
        val refused = full.toggle(99_999)
        assertEquals(TimelineSelection.LIMIT, refused.ids.size)
        assertTrue(refused.limitHit)
        assertFalse(refused.toggle(1).limitHit)
    }
}
