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
    private lateinit var pin: PinManager
    private var wiped = false

    @BeforeEach
    fun setUp() {
        store = InMemorySecretStore()
        pin = PinManager(store, FakeHasher(), clock = { now })
        wiped = false
    }

    @Test
    fun `not configured until a pin is set`() {
        assertFalse(pin.isConfigured)
        pin.setPin("123456")
        assertTrue(pin.isConfigured)
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
    fun `fifth wrong pin starts a 30 second lockout that survives restarts`() {
        pin.setPin("123456")
        repeat(4) { assertTrue(pin.verify("111111", false) { } is PinResult.Wrong) }
        val fifth = pin.verify("111111", false) { } as PinResult.Wrong
        assertEquals(5, fifth.failures)
        assertEquals(30_000L, fifth.lockedForMs)
        // A fresh manager over the same store still sees the lock.
        val restarted = PinManager(store, FakeHasher(), clock = { now })
        assertTrue(restarted.verify("123456", false) { } is PinResult.LockedOut)
        now += 30_000L
        assertEquals(PinResult.Success, restarted.verify("123456", false) { })
    }

    @Test
    fun `tenth wrong pin wipes when enabled`() {
        pin.setPin("123456")
        repeat(9) {
            pin.verify("111111", true) { wiped = true }
            now += 3_600_000L // skip past any lockout
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
