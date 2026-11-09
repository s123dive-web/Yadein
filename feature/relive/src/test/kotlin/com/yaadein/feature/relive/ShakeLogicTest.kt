package com.yaadein.feature.relive

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShakeLogicTest {
    @Test
    fun `three strong spikes inside the window count as a shake`() {
        val logic = ShakeLogic()
        assertFalse(logic.onSample(3f, 0))
        assertFalse(logic.onSample(3f, 100))
        assertTrue(logic.onSample(3f, 200))
    }

    @Test
    fun `gentle movement never triggers`() {
        val logic = ShakeLogic()
        repeat(50) { assertFalse(logic.onSample(1.2f, it * 20L)) }
    }

    @Test
    fun `spikes spread over time do not accumulate and cooldown suppresses repeats`() {
        val logic = ShakeLogic()
        assertFalse(logic.onSample(3f, 0))
        assertFalse(logic.onSample(3f, 1_000))
        assertFalse(logic.onSample(3f, 2_000))
        assertFalse(logic.onSample(3f, 2_100))
        assertTrue(logic.onSample(3f, 2_200))
        // Immediately after, within cooldown:
        assertFalse(logic.onSample(3f, 2_300)); assertFalse(logic.onSample(3f, 2_400)); assertFalse(logic.onSample(3f, 2_500))
    }
}
