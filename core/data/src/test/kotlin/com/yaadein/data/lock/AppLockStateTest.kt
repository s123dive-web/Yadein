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

    @Test
    fun `returning from a system screen the app opened does not lock, once`() {
        val s = AppLockState()
        s.unlock()
        s.allowExternalScreen(now = 0)
        s.onBackground(now = 1_000)
        s.onForeground(now = 120_000, timeout = LockTimeout.IMMEDIATELY)
        assertFalse(s.locked.value)
        s.onBackground(now = 130_000)
        s.onForeground(now = 131_000, timeout = LockTimeout.IMMEDIATELY)
        assertTrue(s.locked.value, "grace is used up")
    }

    @Test
    fun `knows whether the app is on screen so heavy jobs can wait`() {
        val s = AppLockState()
        // A process Android started only to run a worker never comes to the foreground.
        assertFalse(s.inForeground)
        s.onForeground(now = 1, timeout = LockTimeout.ONE_MINUTE)
        assertTrue(s.inForeground)
        s.onBackground(now = 2)
        assertFalse(s.inForeground)
        // Locked or not makes no difference: it is about the screen, not the vault.
        s.unlock()
        s.onForeground(now = 3, timeout = LockTimeout.ONE_MINUTE)
        assertTrue(s.inForeground)
    }
}
