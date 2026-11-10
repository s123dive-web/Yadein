package com.yaadein.feature.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PickMergingTest {
    @Test
    fun `fresh drops photos already on the screen and repeats within one pick, keeping the first`() {
        val picked = listOf("a", "b", "a", "c", "b", "d")
        assertEquals(listOf("c", "d"), PickMerging.fresh(picked, setOf("a", "b")) { it })
        assertEquals(listOf("a", "b", "c", "d"), PickMerging.fresh(picked, emptySet()) { it })
        assertEquals(emptyList<String>(), PickMerging.fresh(listOf("a", "a"), setOf("a")) { it })
    }

    @Test
    fun `foldTarget finds the draft whose days the new photos touch`() {
        val ranges = listOf(100L..102L, 50L..50L, null)
        assertEquals(0, PickMerging.foldTarget(ranges, 101, 101)) // inside the trip
        assertEquals(0, PickMerging.foldTarget(ranges, 103, 104)) // the day after, like PhotoGrouper's one-day gap
        assertEquals(0, PickMerging.foldTarget(ranges, 98, 99)) // the day before
        assertEquals(1, PickMerging.foldTarget(ranges, 49, 51)) // spans the single day
        assertEquals(-1, PickMerging.foldTarget(ranges, 60, 60)) // nothing nearby; undated drafts never match
        assertEquals(-1, PickMerging.foldTarget(emptyList(), 60, 60))
    }
}
