package com.yaadein.data.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Fast, deterministic stand-in for Argon2 so tests stay quick. */
private class FakeHasher : PinHasher {
    override fun hash(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
}

class PinManagerTest {
    private lateinit var store: InMemorySecretStore
    private var now = 1_000_000L
    private var boot = 1
    private lateinit var pin: PinManager
    private var wiped = false

    private fun manager() = PinManager(store, FakeHasher(), elapsed = { now }, bootId = { boot })

    @BeforeEach
    fun setUp() {
        store = InMemorySecretStore()
        pin = manager()
        wiped = false
    }

    @Test
    fun `not configured until a pin is set`() {
        assertFalse(pin.isConfigured)
        pin.setPin("123456")
        assertTrue(pin.isConfigured)
        assertTrue(pin.hasLegacyHash)
    }

    @Test
    fun `vault-bound pin stays configured without a hash`() {
        pin.setPin("123456")
        pin.markVaultBound()
        assertTrue(pin.isConfigured)
        assertFalse(pin.hasLegacyHash)
    }

    @Test
    fun `correct pin succeeds and resets failures`() {
        pin.setPin("123456")
        pin.verify("000000", false) { wiped = true }
        assertEquals(1, pin.failures())
        assertEquals(PinResult.Success, pin.verify("123456", false) { wiped = true })
        assertEquals(0, pin.failures())
    }

    @Test
    fun `a custom check decides success`() {
        pin.setPin("123456")
        pin.markVaultBound()
        assertEquals(PinResult.Success, pin.verify("999999", false, {}) { it == "999999" })
        assertTrue(pin.verify("123456", false, {}) { it == "999999" } is PinResult.Wrong)
    }

    @Test
    fun `fifth wrong pin starts a 30 second lockout that survives restarts`() {
        pin.setPin("123456")
        repeat(4) { assertTrue(pin.verify("111111", false) { } is PinResult.Wrong) }
        val fifth = pin.verify("111111", false) { } as PinResult.Wrong
        assertEquals(5, fifth.failures)
        assertEquals(30_000L, fifth.lockedForMs)
        val restarted = manager()
        assertTrue(restarted.verify("123456", false) { } is PinResult.LockedOut)
        now += 30_000L
        assertEquals(PinResult.Success, restarted.verify("123456", false) { })
    }

    @Test
    fun `changing the wall clock does not shorten the lockout`() {
        pin.setPin("123456")
        repeat(5) { pin.verify("111111", false) { } }
        // Only the monotonic clock counts; nothing here moves it, so the lock holds no matter the date.
        assertEquals(30_000L, pin.remainingLockMs())
        now += 10_000L
        assertEquals(20_000L, pin.remainingLockMs())
    }

    @Test
    fun `after a reboot the full delay applies again`() {
        pin.setPin("123456")
        repeat(5) { pin.verify("111111", false) { } }
        now += 25_000L
        boot = 2
        now = 500L // monotonic clock restarted
        assertEquals(30_000L, pin.remainingLockMs())
        now += 30_000L
        assertEquals(0L, pin.remainingLockMs())
    }

    @Test
    fun `tenth wrong pin wipes when enabled`() {
        pin.setPin("123456")
        repeat(9) {
            pin.verify("111111", true) { wiped = true }
            now += 3_600_000L
        }
        assertFalse(wiped)
        assertEquals(PinResult.Wiped, pin.verify("111111", true) { wiped = true })
        assertTrue(wiped)
    }

    @Test
    fun `wipe disabled never wipes`() {
        pin.setPin("123456")
        repeat(12) {
            pin.verify("111111", false) { wiped = true }
            now += 3_600_000L
        }
        assertFalse(wiped)
    }

    @Test
    fun `pin must be six digits`() {
        assertFalse(PinManager.isValidPin("12345"))
        assertFalse(PinManager.isValidPin("12345a"))
        assertTrue(PinManager.isValidPin("123456"))
    }
}
