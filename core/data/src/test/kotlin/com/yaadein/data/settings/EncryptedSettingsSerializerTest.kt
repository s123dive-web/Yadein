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
}
