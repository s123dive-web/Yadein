package com.yaadein.data.lock

import com.yaadein.data.crypto.FakeKdf
import com.yaadein.data.crypto.FakeKeyWrapper
import com.yaadein.data.crypto.InMemorySecretStore
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.PinResult
import com.yaadein.data.crypto.VaultKeys
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultSessionTest {
    private val store = InMemorySecretStore()
    private val wrapper = FakeKeyWrapper()
    private val errors = mutableListOf<String>()

    private fun keys() = VaultKeys(wrapper, store, FakeKdf())
    private fun pins() = PinManager(store, FakeKdf(), elapsed = { 0L }, bootId = { 1 })
    private fun session(keys: VaultKeys = keys(), pins: PinManager = pins()) = VaultSession(keys, pins) { what, _ -> errors += what }

    @Test
    fun `fresh install opens without the user and the first pin binds the keys`() = runTest {
        val s = session()
        s.start()
        assertEquals(VaultSession.State.Open(migrationPending = false), s.state.value)
        s.setPin("123456")
        // Next process: locked until the PIN.
        val next = session()
        next.start()
        assertEquals(VaultSession.State.Locked, next.state.value)
        assertTrue(next.unlockWithPin("000000", false) {} is PinResult.Wrong)
        assertEquals(VaultSession.State.Locked, next.state.value)
        assertEquals(PinResult.Success, next.unlockWithPin("123456", false) {})
        assertEquals(VaultSession.State.Open(migrationPending = false), next.state.value)
    }

    @Test
    fun `an install from before pin-bound keys migrates on the next correct pin`() = runTest {
        // Old build: device-wrapped keys plus a PIN hash.
        val oldKeys = keys()
        oldKeys.openWithoutUser()
        val db = oldKeys.databaseKey().copyOf()
        pins().setPin("246810")

        val keys = keys()
        val s = session(keys)
        s.start()
        assertEquals(VaultSession.State.Open(migrationPending = true), s.state.value)
        assertTrue(s.unlockWithPin("111111", false) {} is PinResult.Wrong)
        assertEquals(VaultKeys.Protection.DEVICE, keys.protection)
        assertEquals(PinResult.Success, s.unlockWithPin("246810", false) {})
        assertEquals(VaultKeys.Protection.PIN, keys.protection)
        assertEquals(VaultSession.State.Open(migrationPending = false), s.state.value)
        assertFalse(pins().hasLegacyHash)
        assertTrue(errors.isEmpty())

        val next = session()
        next.start()
        assertEquals(VaultSession.State.Locked, next.state.value)
        assertEquals(PinResult.Success, next.unlockWithPin("246810", false) {})
        assertArrayEquals(db, keys().also { it.openWithPin("246810") }.databaseKey())
    }

    @Test
    fun `device lock forgets the pin and opens without the user`() = runTest {
        val s = session()
        s.start()
        s.setPin("123456")
        s.useDeviceLock()
        assertFalse(pins().isConfigured)
        val next = session()
        next.start()
        assertEquals(VaultSession.State.Open(migrationPending = false), next.state.value)
    }
}
