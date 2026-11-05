package com.yaadein.data.lock

import com.yaadein.domain.settings.LockTimeout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLockStateTest {
    @Test
    fun `starts locked and unlock clears it`() {
        val s = AppLockState()
        assertTrue(s.locked.value)
        s.unlock()
        assertFalse(s.locked.value)
    }

    @Test
    fun `re-locks only after the timeout elapses in background`() {
        val s = AppLockState()
        s.unlock()
        s.onBackground(now = 1_000)
        s.onForeground(now = 20_000, timeout = LockTimeout.THIRTY_SECONDS)
        assertFalse(s.locked.value)
        s.onBackground(now = 30_000)
        s.onForeground(now = 61_000, timeout = LockTimeout.THIRTY_SECONDS)
        assertTrue(s.locked.value)
    }

    @Test
    fun `immediately timeout locks on any background`() {
        val s = AppLockState()
        s.unlock()
        s.onBackground(now = 5)
        s.onForeground(now = 6, timeout = LockTimeout.IMMEDIATELY)
        assertTrue(s.locked.value)
    }

    @Test
    fun `foreground without a prior background is a no-op`() {
        val s = AppLockState()
        s.unlock()
        s.onForeground(now = 999_999, timeout = LockTimeout.IMMEDIATELY)
        assertFalse(s.locked.value)
    }
}
