import Foundation

// Settings.kt and Premium.kt. Same names and defaults as Android so a setting means the same thing on both phones.

public enum AppTheme: String, Codable, CaseIterable, Sendable { case WARM_ALBUM, MIDNIGHT, MONSOON, FESTIVE, MINIMAL_MONO, DYNAMIC }

public enum DarkMode: String, Codable, CaseIterable, Sendable { case SYSTEM, LIGHT, DARK }

/// How the vault is unlocked: a Yaadein PIN (keys are encrypted with it) or the phone's own passcode / Face ID.
public enum LockMethod: String, Codable, CaseIterable, Sendable { case PIN, DEVICE }

/// Timeline density: big cards, a compact list, or a photo grid.
public enum TimelineLayout: String, Codable, CaseIterable, Sendable { case CARDS, COMPACT, GRID }

/// How long the app may stay in the background before it locks again.
public enum LockTimeout: String, Codable, CaseIterable, Sendable {
    case IMMEDIATELY, THIRTY_SECONDS, ONE_MINUTE, FIVE_MINUTES

    public var millis: Int64 {
        switch self {
        case .IMMEDIATELY: return 0
        case .THIRTY_SECONDS: return 30_000
        case .ONE_MINUTE: return 60_000
        case .FIVE_MINUTES: return 300_000
        }
    }
}

public struct UserSettings: Hashable, Codable, Sendable {
    public var theme: AppTheme = .WARM_ALBUM
    public var darkMode: DarkMode = .SYSTEM
    public var easyMode = false
    /// Hide the app content in the app switcher and blur on screen capture.
    public var secureScreen = false
    public var lockTimeout: LockTimeout = .ONE_MINUTE
    public var biometricEnabled = true
    public var wipeAfterTenFailures = false
    public var onThisDayEnabled = true
    /// Minutes after midnight for the daily "On this day" reminder.
    public var onThisDayMinuteOfDay = 9 * 60
    public var shakeForMemory = true
    public var faceSuggestions = false
    public var smartPolish = false
    public var storeOptimizedCopies = true
    public var onboardingDone = false
    public var pinConfigured = false
    public var demoMode = false
    public var lockMethod: LockMethod = .PIN
    public var timelineLayout: TimelineLayout = .CARDS
    public var birthdayReminders = true
    public var storyPrompts = true
    public var widgetShowsMemories = false
    public var autoBackupEnabled = false
    public var lastBackupAtMillis: Int64 = 0
    public var backupNudgeDismissedAtMillis: Int64 = 0
    public var autoBackupFailed = false

    public init() {}

    // Missing keys fall back to the defaults, so files written by an older build still load.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = UserSettings()
        theme = try c.decodeIfPresent(AppTheme.self, forKey: .theme) ?? d.theme
        darkMode = try c.decodeIfPresent(DarkMode.self, forKey: .darkMode) ?? d.darkMode
        easyMode = try c.decodeIfPresent(Bool.self, forKey: .easyMode) ?? d.easyMode
        secureScreen = try c.decodeIfPresent(Bool.self, forKey: .secureScreen) ?? d.secureScreen
        lockTimeout = try c.decodeIfPresent(LockTimeout.self, forKey: .lockTimeout) ?? d.lockTimeout
        biometricEnabled = try c.decodeIfPresent(Bool.self, forKey: .biometricEnabled) ?? d.biometricEnabled
        wipeAfterTenFailures = try c.decodeIfPresent(Bool.self, forKey: .wipeAfterTenFailures) ?? d.wipeAfterTenFailures
        onThisDayEnabled = try c.decodeIfPresent(Bool.self, forKey: .onThisDayEnabled) ?? d.onThisDayEnabled
        onThisDayMinuteOfDay = try c.decodeIfPresent(Int.self, forKey: .onThisDayMinuteOfDay) ?? d.onThisDayMinuteOfDay
        shakeForMemory = try c.decodeIfPresent(Bool.self, forKey: .shakeForMemory) ?? d.shakeForMemory
        faceSuggestions = try c.decodeIfPresent(Bool.self, forKey: .faceSuggestions) ?? d.faceSuggestions
        smartPolish = try c.decodeIfPresent(Bool.self, forKey: .smartPolish) ?? d.smartPolish
        storeOptimizedCopies = try c.decodeIfPresent(Bool.self, forKey: .storeOptimizedCopies) ?? d.storeOptimizedCopies
        onboardingDone = try c.decodeIfPresent(Bool.self, forKey: .onboardingDone) ?? d.onboardingDone
        pinConfigured = try c.decodeIfPresent(Bool.self, forKey: .pinConfigured) ?? d.pinConfigured
        demoMode = try c.decodeIfPresent(Bool.self, forKey: .demoMode) ?? d.demoMode
        lockMethod = try c.decodeIfPresent(LockMethod.self, forKey: .lockMethod) ?? d.lockMethod
        timelineLayout = try c.decodeIfPresent(TimelineLayout.self, forKey: .timelineLayout) ?? d.timelineLayout
        birthdayReminders = try c.decodeIfPresent(Bool.self, forKey: .birthdayReminders) ?? d.birthdayReminders
        storyPrompts = try c.decodeIfPresent(Bool.self, forKey: .storyPrompts) ?? d.storyPrompts
        widgetShowsMemories = try c.decodeIfPresent(Bool.self, forKey: .widgetShowsMemories) ?? d.widgetShowsMemories
        autoBackupEnabled = try c.decodeIfPresent(Bool.self, forKey: .autoBackupEnabled) ?? d.autoBackupEnabled
        lastBackupAtMillis = try c.decodeIfPresent(Int64.self, forKey: .lastBackupAtMillis) ?? d.lastBackupAtMillis
        backupNudgeDismissedAtMillis = try c.decodeIfPresent(Int64.self, forKey: .backupNudgeDismissedAtMillis) ?? d.backupNudgeDismissedAtMillis
        autoBackupFailed = try c.decodeIfPresent(Bool.self, forKey: .autoBackupFailed) ?? d.autoBackupFailed
    }
}

/// Features planned for the one-time "Yaadein Plus" unlock (docs/PREMIUM.md). Nothing is locked yet.
public enum PremiumFeature: String, CaseIterable, Sendable { case EXTRA_THEMES, EXTRA_MUSIC, LONG_REELS, PDF_BOOK, FAMILY_PACKS }

public protocol PremiumGate { func isUnlocked(_ feature: PremiumFeature) -> Bool }

public struct AllUnlocked: PremiumGate {
    public init() {}
    public func isUnlocked(_ feature: PremiumFeature) -> Bool { true }
}
