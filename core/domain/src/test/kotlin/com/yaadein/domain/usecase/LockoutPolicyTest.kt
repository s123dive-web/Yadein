package com.yaadein.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LockoutPolicyTest {
    @Test
    fun `first four failures have no delay`() {
        (0..4).forEach { assertEquals(0L, LockoutPolicy.delayAfterFailures(it)) }
    }

    @Test
    fun `fifth failure starts the escalation`() {
        assertEquals(30_000L, LockoutPolicy.delayAfterFailures(5))
        assertEquals(60_000L, LockoutPolicy.delayAfterFailures(6))
        assertEquals(300_000L, LockoutPolicy.delayAfterFailures(7))
        assertEquals(900_000L, LockoutPolicy.delayAfterFailures(8))
        assertEquals(3_600_000L, LockoutPolicy.delayAfterFailures(9))
        assertEquals(3_600_000L, LockoutPolicy.delayAfterFailures(40))
    }

    @Test
    fun `wipe only when enabled and at ten`() {
        assertFalse(LockoutPolicy.shouldWipe(10, wipeEnabled = false))
        assertFalse(LockoutPolicy.shouldWipe(9, wipeEnabled = true))
        assertTrue(LockoutPolicy.shouldWipe(10, wipeEnabled = true))
    }
}
