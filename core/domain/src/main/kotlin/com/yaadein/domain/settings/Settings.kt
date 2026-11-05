package com.yaadein.domain.settings

enum class AppTheme { WARM_ALBUM, MIDNIGHT, MONSOON, FESTIVE, MINIMAL_MONO, DYNAMIC }

enum class DarkMode { SYSTEM, LIGHT, DARK }

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
    val lockTimeout: LockTimeout = LockTimeout.THIRTY_SECONDS,
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
)
