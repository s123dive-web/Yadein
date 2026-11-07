import Foundation
import Observation
import SwiftUI
import YaadeinCrypto
import YaadeinData
import YaadeinDomain

/// App-wide state: settings, the lock, and the open vault (YaadeinApp + SessionViewModel + AppLockState on Android).
/// Nothing reads the database or media before the vault is unlocked; while locked only the lock screen is shown.
@MainActor
@Observable
final class AppModel {
    enum Phase: Equatable { case onboarding, firstPerson, lockSetup, locked, home }

    private(set) var settings: UserSettings
    private(set) var phase: Phase = .onboarding
    /// Bumped on every vault change; views read it so they recompute from the store.
    private(set) var revision = 0
    private(set) var store: VaultStore?
    private(set) var vault: MediaVault?
    /// One-shot navigation requests from outside a screen (e.g. "open this memory" after unlock).
    var pendingMemoryId: Int64?

    let aead: AEAD = CryptoKitAEAD()
    let session: VaultSession
    private let settingsStore: SettingsStore
    private let files: SecretFiles
    private let root: URL
    private var backgroundedAtUptime: Int64?
    private var observer: UUID?

    init() {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Yaadein", isDirectory: true)
        root = support
        try? FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
        // The vault leaves the phone only as a .yaadein backup the user makes, never in iCloud/iTunes device backups.
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = support
        try? url.setResourceValues(values)

        files = SecretFiles(folder: support.appendingPathComponent("secrets", isDirectory: true))
        let device = KeychainDeviceKey()
        settingsStore = SettingsStore(files: files, device: device)
        let lockout = LockoutTracker(files: files, device: device, uptimeMs: { SystemClock.uptimeMs }, bootId: { SystemClock.bootId })
        session = VaultSession(files: files, device: device, aead: aead, lockout: lockout)
        settings = settingsStore.load()
        start()
    }

    /// Decides the first screen (onboarding, PIN setup, lock or home) on a cold start.
    private func start() {
        if !session.isSetUp {
            // A fresh install (or after erase): Keychain items survive uninstall on iOS, so drop stale ones.
            BiometricKeys.clear()
            settingsStore.clear()
            settings = UserSettings()
            try? session.createVault()
            openVault()
        } else if session.protection == .device && settings.lockMethod != .DEVICE {
            // Onboarding not finished yet: the vault has no PIN, open it.
            try? session.unlockWithDevice()
            openVault()
        }
        phase = nextPhase()
    }

    private func nextPhase() -> Phase {
        if !settings.onboardingDone { return .onboarding }
        if !settings.pinConfigured { return session.isOpen ? .lockSetup : .locked }
        return session.isOpen ? .home : .locked
    }

    // MARK: Settings

    func updateSettings(_ change: (inout UserSettings) -> Void) {
        var s = settings
        change(&s)
        guard s != settings else { return }
        settings = s
        try? settingsStore.save(s)
    }

    // MARK: Vault

    private var databaseURL: URL { root.appendingPathComponent("vault/db.enc") }

    private func openVault() {
        guard let keys = try? session.currentKeys() else { return }
        let aead = self.aead
        let dbURL = databaseURL
        try? FileManager.default.createDirectory(at: dbURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        var database = VaultDatabase()
        if let bytes = try? Data(contentsOf: dbURL) {
            do {
                database = try DatabaseFile.open([UInt8](bytes), key: keys.database, aead: aead)
            } catch {
                // Never overwrite a database we could not read: keep it aside for "Check my memories" / support.
                try? FileManager.default.moveItem(at: dbURL, to: dbURL.appendingPathExtension("unreadable-\(Int(Date().timeIntervalSince1970))"))
            }
        }
        let newStore = VaultStore(database: database) { db in
            let sealed = try DatabaseFile.seal(db, key: keys.database, aead: aead)
            try Data(sealed).write(to: dbURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
        let session = self.session
        let newVault = MediaVault(root: root.appendingPathComponent("vault", isDirectory: true), aead: aead) { try session.currentKeys().media }
        observer = newStore.observe { [weak self] _ in
            Task { @MainActor in self?.revision += 1 }
        }
        store = newStore
        vault = newVault
        ThumbnailCache.shared.removeAll()
        // Finish or discard imports cut short last time (files nothing refers to).
        let referenced = Set(database.media.flatMap { [$0.encryptedPath, $0.thumbPath] } + database.memories.compactMap(\.voiceNotePath))
        DispatchQueue.global(qos: .utility).async { newVault.pruneOrphans(keeping: referenced) }
        revision += 1
        if settings.onboardingDone { Notifier.reschedule(self) }
    }

    private func closeVault() {
        if let observer { store?.removeObserver(observer) }
        store = nil
        vault = nil
        ThumbnailCache.shared.removeAll()
        session.lockNow()
    }

    // MARK: Onboarding and setup

    func finishOnboarding() {
        updateSettings { $0.onboardingDone = true }
        phase = .firstPerson
    }

    func finishFirstPerson() { phase = settings.pinConfigured ? .home : .lockSetup }

    /// Seals the vault keys with the new PIN (Argon2id + device key) and unlocks.
    func setPin(_ pin: String) async throws {
        let session = self.session
        try await Task.detached(priority: .userInitiated) { try session.setPin(pin) }.value
        updateSettings {
            $0.pinConfigured = true
            $0.lockMethod = .PIN
        }
    }

    /// Optional Face ID copy of the keys, then home.
    func finishLockSetup(useBiometric: Bool) {
        var sealed = false
        if useBiometric, let keys = try? session.currentKeys() {
            sealed = BiometricKeys.save(keys.database + keys.media)
        }
        if !sealed { BiometricKeys.clear() }
        updateSettings { $0.biometricEnabled = sealed }
        phase = .home
    }

    /// "Use my phone's screen lock instead".
    func chooseDeviceLock() async -> Bool {
        guard await DeviceAuth.authenticate(reason: L("lock_biometric_title")) else { return false }
        try? session.useDeviceProtection()
        BiometricKeys.clear()
        updateSettings {
            $0.pinConfigured = true
            $0.lockMethod = .DEVICE
            $0.biometricEnabled = false
        }
        phase = .home
        return true
    }

    // MARK: Lock and unlock

    func unlock(pin: String) async -> VaultSession.UnlockResult {
        let session = self.session
        let wipe = settings.wipeAfterTenFailures
        let result = await Task.detached(priority: .userInitiated) { session.unlock(pin: pin, wipeAfterTen: wipe, onWipe: {}) }.value
        switch result {
        case .unlocked:
            openVault()
            phase = nextPhase()
        case .wiped:
            eraseEverything()
        default:
            break
        }
        return result
    }

    /// Face ID / Touch ID: true when it opened the vault.
    func unlockWithBiometrics() async -> Bool {
        guard settings.biometricEnabled, let raw = await BiometricKeys.load(prompt: L("lock_biometric_subtitle")),
              raw.count == 64 else { return false }
        guard (try? session.unlock(with: VaultKeysBridge.make(raw))) != nil else { return false }
        openVault()
        phase = nextPhase()
        return true
    }

    func unlockWithDevice() async -> Bool {
        guard await DeviceAuth.authenticate(reason: L("lock_biometric_title")) else { return false }
        guard (try? session.unlockWithDevice()) != nil else { return false }
        openVault()
        phase = nextPhase()
        return true
    }

    func lockNow() {
        guard settings.pinConfigured else { return }
        closeVault()
        phase = .locked
    }

    /// Lock timeout (AppLockState): lock when the app was in the background longer than the chosen time.
    func scenePhaseChanged(_ newPhase: ScenePhase) {
        switch newPhase {
        case .background:
            backgroundedAtUptime = SystemClock.uptimeMs
            if settings.lockTimeout == .IMMEDIATELY { lockNow() }
        case .active:
            if let at = backgroundedAtUptime, SystemClock.uptimeMs - at >= settings.lockTimeout.millis { lockNow() }
            backgroundedAtUptime = nil
        default:
            break
        }
    }

    /// Settings → Storage → Erase everything (and wipe after ten wrong PINs).
    func eraseEverything() {
        closeVault()
        vault?.wipe()
        try? FileManager.default.removeItem(at: root.appendingPathComponent("vault"))
        BiometricKeys.clear()
        session.wipe()
        settingsStore.clear()
        settings = UserSettings()
        try? session.createVault()
        openVault()
        phase = .onboarding
    }

    // MARK: Biometric toggle from Settings

    func setBiometric(_ on: Bool) {
        var sealed = false
        if on, let keys = try? session.currentKeys() { sealed = BiometricKeys.save(keys.database + keys.media) }
        if !on || !sealed { BiometricKeys.clear() }
        updateSettings { $0.biometricEnabled = sealed }
    }
}

/// VaultKeys' byte initialiser is internal to YaadeinData; this goes through the public session API.
enum VaultKeysBridge {
    static func make(_ raw: [UInt8]) -> VaultKeys { VaultKeys.fromBytes(raw) }
}
