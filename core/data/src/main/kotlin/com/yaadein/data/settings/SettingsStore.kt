package com.yaadein.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.yaadein.data.crypto.KeyWrapper
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.DarkMode
import com.yaadein.domain.settings.LockMethod
import com.yaadein.domain.settings.LockTimeout
import com.yaadein.domain.settings.TimelineLayout
import com.yaadein.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Bumped when a stored default needs a one-time migration. Version 1 files were written before the
 * lock timeout default moved from "immediately" to one minute.
 */
internal const val SETTINGS_VERSION = 2

/** Serializable mirror of [UserSettings] with defaults so new fields are backward compatible. */
@Serializable
internal data class SettingsDto(
    val settingsVersion: Int = 1,
    val theme: String = AppTheme.WARM_ALBUM.name,
    val darkMode: String = DarkMode.SYSTEM.name,
    val easyMode: Boolean = false,
    val secureScreen: Boolean = true,
    val lockTimeout: String = LockTimeout.ONE_MINUTE.name,
    val biometricEnabled: Boolean = true,
    val wipeAfterTenFailures: Boolean = false,
    val onThisDayEnabled: Boolean = true,
    val onThisDayMinuteOfDay: Int = 9 * 60,
    val shakeForMemory: Boolean = true,
    val faceSuggestions: Boolean = false,
    val smartPolish: Boolean = false,
    val storeOptimizedCopies: Boolean = true,
    val onboardingDone: Boolean = false,
    val pinConfigured: Boolean = false,
    val demoMode: Boolean = false,
    val lockMethod: String = LockMethod.PIN.name,
    val timelineLayout: String = TimelineLayout.CARDS.name,
    val birthdayReminders: Boolean = true,
    val storyPrompts: Boolean = true,
    val widgetShowsMemories: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupUri: String? = null,
    val lastBackupAtMillis: Long = 0L,
    val backupNudgeDismissedAtMillis: Long = 0L,
    val autoBackupFailed: Boolean = false,
)

internal fun SettingsDto.toDomain() = UserSettings(
    theme = enumOr(theme, AppTheme.WARM_ALBUM),
    darkMode = enumOr(darkMode, DarkMode.SYSTEM),
    easyMode = easyMode,
    secureScreen = secureScreen,
    lockTimeout = migratedLockTimeout(),
    biometricEnabled = biometricEnabled,
    wipeAfterTenFailures = wipeAfterTenFailures,
    onThisDayEnabled = onThisDayEnabled,
    onThisDayMinuteOfDay = onThisDayMinuteOfDay,
    shakeForMemory = shakeForMemory,
    faceSuggestions = faceSuggestions,
    smartPolish = smartPolish,
    storeOptimizedCopies = storeOptimizedCopies,
    onboardingDone = onboardingDone,
    pinConfigured = pinConfigured,
    demoMode = demoMode,
    lockMethod = enumOr(lockMethod, LockMethod.PIN),
    timelineLayout = enumOr(timelineLayout, TimelineLayout.CARDS),
    birthdayReminders = birthdayReminders,
    storyPrompts = storyPrompts,
    widgetShowsMemories = widgetShowsMemories,
    autoBackupEnabled = autoBackupEnabled,
    autoBackupUri = autoBackupUri,
    lastBackupAtMillis = lastBackupAtMillis,
    backupNudgeDismissedAtMillis = backupNudgeDismissedAtMillis,
    autoBackupFailed = autoBackupFailed,
)

/** Files saved by version 1 carried IMMEDIATELY only because it was the default; lift them to the new default. */
private fun SettingsDto.migratedLockTimeout(): LockTimeout {
    val stored = enumOr(lockTimeout, LockTimeout.ONE_MINUTE)
    return if (settingsVersion < 2 && stored == LockTimeout.IMMEDIATELY) LockTimeout.ONE_MINUTE else stored
}

internal fun UserSettings.toDto() = SettingsDto(
    settingsVersion = SETTINGS_VERSION,
    theme = theme.name,
    darkMode = darkMode.name,
    easyMode = easyMode,
    secureScreen = secureScreen,
    lockTimeout = lockTimeout.name,
    biometricEnabled = biometricEnabled,
    wipeAfterTenFailures = wipeAfterTenFailures,
    onThisDayEnabled = onThisDayEnabled,
    onThisDayMinuteOfDay = onThisDayMinuteOfDay,
    shakeForMemory = shakeForMemory,
    faceSuggestions = faceSuggestions,
    smartPolish = smartPolish,
    storeOptimizedCopies = storeOptimizedCopies,
    onboardingDone = onboardingDone,
    pinConfigured = pinConfigured,
    demoMode = demoMode,
    lockMethod = lockMethod.name,
    timelineLayout = timelineLayout.name,
    birthdayReminders = birthdayReminders,
    storyPrompts = storyPrompts,
    widgetShowsMemories = widgetShowsMemories,
    autoBackupEnabled = autoBackupEnabled,
    autoBackupUri = autoBackupUri,
    lastBackupAtMillis = lastBackupAtMillis,
    backupNudgeDismissedAtMillis = backupNudgeDismissedAtMillis,
    autoBackupFailed = autoBackupFailed,
)

private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/**
 * DataStore serializer that encrypts the settings JSON with the Keystore-wrapped master key
 * (AES-256-GCM via [KeyWrapper]), so even preferences never sit in plaintext on disk.
 */
class EncryptedSettingsSerializer(private val wrapper: KeyWrapper) : Serializer<UserSettings> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: UserSettings = UserSettings()

    override suspend fun readFrom(input: InputStream): UserSettings {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            val plain = wrapper.unwrap(bytes)
            json.decodeFromString<SettingsDto>(plain.decodeToString()).toDomain()
        } catch (e: Exception) {
            throw CorruptionException("Settings could not be decrypted", e)
        }
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        val plain = json.encodeToString(t.toDto()).encodeToByteArray()
        output.write(wrapper.wrap(plain))
    }
}

class DataStoreSettingsRepository(private val store: DataStore<UserSettings>) : SettingsRepository {
    override val settings: Flow<UserSettings> = store.data
    override suspend fun current(): UserSettings = store.data.first()
    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        store.updateData(transform)
    }
}
