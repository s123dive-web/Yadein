package com.yaadein.domain.settings

enum class AppTheme { WARM_ALBUM, MIDNIGHT, MONSOON, FESTIVE, MINIMAL_MONO, DYNAMIC }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** How the vault is unlocked: a Yaadein PIN (keys are encrypted with it) or the phone's own screen lock (Android 11+). */
enum class LockMethod { PIN, DEVICE }

/** Timeline density: big cards, a compact list, or a photo grid. */
enum class TimelineLayout { CARDS, COMPACT, GRID }

/** How long the app may stay in the background before it locks again. */
enum class LockTimeout(val millis: Long) {
    IMMEDIATELY(0),
    THIRTY_SECONDS(30_000),
    ONE_MINUTE(60_000),
    FIVE_MINUTES(300_000),
}

data class UserSettings(
    val theme: AppTheme = AppTheme.WARM_ALBUM,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val easyMode: Boolean = false,
    val secureScreen: Boolean = true,
    val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    val biometricEnabled: Boolean = true,
    val wipeAfterTenFailures: Boolean = false,
    val onThisDayEnabled: Boolean = true,
    /** Minutes after midnight for the daily "On this day" reminder. */
    val onThisDayMinuteOfDay: Int = 9 * 60,
    val shakeForMemory: Boolean = true,
    val faceSuggestions: Boolean = false,
    val smartPolish: Boolean = false,
    val storeOptimizedCopies: Boolean = true,
    val onboardingDone: Boolean = false,
    val pinConfigured: Boolean = false,
    val demoMode: Boolean = false,
    val lockMethod: LockMethod = LockMethod.PIN,
    val timelineLayout: TimelineLayout = TimelineLayout.CARDS,
    /** "Rahul's birthday is tomorrow" notifications. */
    val birthdayReminders: Boolean = true,
    /** Weekly "Ask Aaji how she met Ajoba" prompt notification. */
    val storyPrompts: Boolean = true,
    /** Home-screen widget may show memory titles and photos (off: it only says how many memories are waiting). */
    val widgetShowsMemories: Boolean = false,
    /** Weekly automatic backup to [autoBackupUri] (a document the user picked once). */
    val autoBackupEnabled: Boolean = false,
    val autoBackupUri: String? = null,
    val lastBackupAtMillis: Long = 0L,
    /** When the "back up your memories" banner was last dismissed. */
    val backupNudgeDismissedAtMillis: Long = 0L,
    /** The last automatic backup failed (target deleted or access revoked); shown on the Backup screen. */
    val autoBackupFailed: Boolean = false,
)
