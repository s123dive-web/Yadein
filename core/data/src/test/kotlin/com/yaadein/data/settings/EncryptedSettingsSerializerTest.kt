package com.yaadein.data.settings

import com.yaadein.data.crypto.FakeKeyWrapper
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.LockTimeout
import com.yaadein.domain.settings.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class EncryptedSettingsSerializerTest {
    @Test
    fun `settings round trip through encryption`() = runTest {
        val serializer = EncryptedSettingsSerializer(FakeKeyWrapper())
        val settings = UserSettings(theme = AppTheme.MONSOON, easyMode = true, lockTimeout = LockTimeout.FIVE_MINUTES, onboardingDone = true, pinConfigured = true)
        val out = ByteArrayOutputStream()
        serializer.writeTo(settings, out)
        val bytes = out.toByteArray()
        assertFalse(String(bytes).contains("MONSOON"), "settings must not be stored in plaintext")
        assertEquals(settings, serializer.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `empty file yields defaults`() = runTest {
        val serializer = EncryptedSettingsSerializer(FakeKeyWrapper())
        assertEquals(UserSettings(), serializer.readFrom(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `unknown enum names fall back to defaults`() {
        val dto = SettingsDto(theme = "NEON", darkMode = "BLUE")
        val domain = dto.toDomain()
        assertEquals(AppTheme.WARM_ALBUM, domain.theme)
    }

    @Test
    fun `version 1 files with the old immediate lock default move to one minute`() {
        val old = SettingsDto(settingsVersion = 1, lockTimeout = LockTimeout.IMMEDIATELY.name)
        assertEquals(LockTimeout.ONE_MINUTE, old.toDomain().lockTimeout)
        // A deliberate choice made on the current version is kept.
        val chosen = SettingsDto(settingsVersion = SETTINGS_VERSION, lockTimeout = LockTimeout.IMMEDIATELY.name)
        assertEquals(LockTimeout.IMMEDIATELY, chosen.toDomain().lockTimeout)
        assertEquals(SETTINGS_VERSION, UserSettings().toDto().settingsVersion)
    }

    @Test
    fun `screenshots are allowed by default and old blocked-by-default files are switched once`() {
        assertEquals(false, UserSettings().secureScreen)
        assertEquals(false, SettingsDto(settingsVersion = 2, secureScreen = true).toDomain().secureScreen)
        // Turning blocking back on after the update is kept.
        assertEquals(true, SettingsDto(settingsVersion = SETTINGS_VERSION, secureScreen = true).toDomain().secureScreen)
    }

    @Test
    fun `settings saved before the photos-first onboarding keep their meaning`() {
        // An existing user's file has no trace of the new flags: they stay onboarded and locked, nothing is re-asked.
        val existing = SettingsDto(settingsVersion = SETTINGS_VERSION, onboardingDone = true, pinConfigured = true).toDomain()
        assertEquals(true, existing.onboardingDone)
        assertEquals(true, existing.pinConfigured)
        assertEquals(false, existing.lockPromptPostponed)
        assertEquals(false, existing.peopleNudgeDismissed)
        // The new flags survive a round trip.
        val changed = existing.copy(lockPromptPostponed = true, peopleNudgeDismissed = true)
        assertEquals(changed, changed.toDto().toDomain())
    }
}
