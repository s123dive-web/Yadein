import Foundation
import YaadeinCrypto
import YaadeinDomain

/// Wraps small secrets with a key that never leaves this device (on iPhone: an AES key in the Keychain with
/// "this device only, when unlocked" access; Android uses the Keystore). Everything sealed with it becomes unreadable
/// when the key is deleted (wipe) or the app is moved to another phone.
public protocol DeviceKeyWrapper: AnyObject {
    func wrap(_ plain: [UInt8]) throws -> [UInt8]
    func unwrap(_ sealed: [UInt8]) throws -> [UInt8]
    func deleteKey()
}

/// Software wrapper for tests and previews.
public final class InMemoryDeviceKey: DeviceKeyWrapper {
    private var key = ChunkedAEAD.randomBytes(32)
    private let aead: AEAD
    public init(aead: AEAD = SoftwareAESGCM()) { self.aead = aead }
    public func wrap(_ plain: [UInt8]) throws -> [UInt8] {
        let nonce = ChunkedAEAD.randomBytes(12)
        return nonce + (try aead.seal(plain, key: key, nonce: nonce, aad: []))
    }
    public func unwrap(_ sealed: [UInt8]) throws -> [UInt8] {
        guard sealed.count > 12 else { throw CryptoError.truncated }
        return try aead.open(Array(sealed[12...]), key: key, nonce: Array(sealed[..<12]), aad: [])
    }
    public func deleteKey() { key = ChunkedAEAD.randomBytes(32) }
}

/// The two vault keys: one for the database file, one for media files (as on Android).
public struct VaultKeys: Equatable, Sendable {
    public let database: [UInt8]
    public let media: [UInt8]

    public static func generate() -> VaultKeys { VaultKeys(database: ChunkedAEAD.randomBytes(32), media: ChunkedAEAD.randomBytes(32)) }

    /// Keys from their 64-byte form (database ‖ media), e.g. the biometric Keychain copy.
    public static func fromBytes(_ raw: [UInt8]) -> VaultKeys {
        precondition(raw.count == 64)
        return VaultKeys(database: Array(raw[..<32]), media: Array(raw[32...]))
    }

    var bytes: [UInt8] { database + media }

    init(database: [UInt8], media: [UInt8]) {
        self.database = database
        self.media = media
    }

    init(bytes: [UInt8]) throws {
        guard bytes.count == 64 else { throw CryptoError.invalidFormat("vault keys") }
        database = Array(bytes[..<32])
        media = Array(bytes[32...])
    }
}

/// Small files the session keeps next to the vault. Writes are atomic (temp + rename).
public final class SecretFiles: @unchecked Sendable {
    public let folder: URL
    public init(folder: URL) {
        self.folder = folder
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    }

    public func read(_ name: String) -> [UInt8]? { (try? Data(contentsOf: folder.appendingPathComponent(name))).map { [UInt8]($0) } }

    public func write(_ name: String, _ bytes: [UInt8]) throws {
        let url = folder.appendingPathComponent(name)
        try Data(bytes).write(to: url, options: [.atomic])
        #if os(iOS)
        try? (url as NSURL).setResourceValue(URLFileProtection.completeUntilFirstUserAuthentication, forKey: .fileProtectionKey)
        #endif
    }

    public func delete(_ name: String) { try? FileManager.default.removeItem(at: folder.appendingPathComponent(name)) }
}

/// Wrong-PIN counter with escalating delays (LockoutPolicy), sealed with the device key so it survives restarts and
/// cannot be edited. Time is measured on the system uptime clock, so changing the date does not shorten a lockout;
/// after a reboot (uptime restarts) the full delay applies again.
public final class LockoutTracker {
    struct State: Codable { var failures = 0; var lockedUntilUptimeMs: Int64 = 0; var bootId = "" }

    private let files: SecretFiles
    private let device: DeviceKeyWrapper
    private let uptimeMs: () -> Int64
    private let bootId: () -> String
    private static let name = "lockout.bin"

    public init(files: SecretFiles, device: DeviceKeyWrapper, uptimeMs: @escaping () -> Int64, bootId: @escaping () -> String) {
        self.files = files
        self.device = device
        self.uptimeMs = uptimeMs
        self.bootId = bootId
    }

    private func load() -> State {
        guard let sealed = files.read(Self.name), let plain = try? device.unwrap(sealed),
              var s = try? JSONDecoder().decode(State.self, from: Data(plain)) else { return State() }
        if s.bootId != bootId(), s.lockedUntilUptimeMs > 0 {
            s.lockedUntilUptimeMs = uptimeMs() + LockoutPolicy.delayAfterFailures(s.failures)
            s.bootId = bootId()
            save(s)
        }
        return s
    }

    private func save(_ s: State) {
        if let data = try? JSONEncoder().encode(s), let sealed = try? device.wrap([UInt8](data)) { try? files.write(Self.name, sealed) }
    }

    public var failures: Int { load().failures }

    /// Milliseconds until another PIN may be tried (0 = now).
    public var remainingMs: Int64 { max(0, load().lockedUntilUptimeMs - uptimeMs()) }

    /// Records a wrong PIN; returns the new failure count.
    @discardableResult
    public func recordFailure() -> Int {
        var s = load()
        s.failures += 1
        let delay = LockoutPolicy.delayAfterFailures(s.failures)
        s.lockedUntilUptimeMs = delay > 0 ? uptimeMs() + delay : 0
        s.bootId = bootId()
        save(s)
        return s.failures
    }

    public func reset() { save(State(failures: 0, lockedUntilUptimeMs: 0, bootId: bootId())) }
    public func clear() { files.delete(Self.name) }
}

/// Holds the vault keys while unlocked, and seals them (VaultKeys.kt / VaultSession.kt on Android):
/// - `.pin`: `salt ‖ DeviceKey( AES-GCM( Argon2id(PIN, salt), dbKey ‖ mediaKey ) )`. Without the PIN the keys cannot
///   be recovered, even by code running as the app.
/// - `.device`: `DeviceKey(dbKey ‖ mediaKey)`, during onboarding and in "phone passcode / Face ID" mode.
/// Every re-seal writes, verifies, then replaces.
public final class VaultSession: @unchecked Sendable {
    public enum Protection: String, Codable { case pin, device }

    public enum UnlockResult: Equatable {
        case unlocked
        /// Wrong PIN; `retryAfterMs` > 0 while locked out; `attemptsBeforeWipe` when wipe-after-ten is on.
        case wrongPin(failures: Int, retryAfterMs: Int64, attemptsBeforeWipe: Int?)
        case lockedOut(retryAfterMs: Int64)
        case wiped
    }

    struct Sealed: Codable { var protection: Protection; var salt: [UInt8]; var sealed: [UInt8] }

    public let files: SecretFiles
    public let lockout: LockoutTracker
    private let device: DeviceKeyWrapper
    private let aead: AEAD
    private let kdf: (String, [UInt8]) -> [UInt8]
    private let lock = NSLock()
    private var keys: VaultKeys?
    private static let keysFile = "vault.keys"

    /// - Parameter kdf: Argon2id with Android's parameters in the app; a fast stand-in in tests.
    public init(files: SecretFiles, device: DeviceKeyWrapper, aead: AEAD, lockout: LockoutTracker,
                kdf: @escaping (String, [UInt8]) -> [UInt8] = { Argon2id.hash(password: $0, salt: $1) }) {
        self.files = files
        self.device = device
        self.aead = aead
        self.lockout = lockout
        self.kdf = kdf
    }

    public var isSetUp: Bool { files.read(Self.keysFile) != nil }
    public var isOpen: Bool { lock.withLock { keys != nil } }
    public var protection: Protection? { load()?.protection }

    /// The open keys; throws when locked so nothing can read the vault before unlock.
    public func currentKeys() throws -> VaultKeys {
        guard let k = lock.withLock({ keys }) else { throw CryptoError.invalidFormat("vault is locked") }
        return k
    }

    private func load() -> Sealed? {
        guard let raw = files.read(Self.keysFile) else { return nil }
        return try? JSONDecoder().decode(Sealed.self, from: Data(raw))
    }

    private func write(_ s: Sealed, verify: (Sealed) throws -> VaultKeys, expected: VaultKeys) throws {
        let data = [UInt8](try JSONEncoder().encode(s))
        try files.write(Self.keysFile + ".new", data)
        guard let reread = files.read(Self.keysFile + ".new"), let parsed = try? JSONDecoder().decode(Sealed.self, from: Data(reread)),
              try verify(parsed) == expected else {
            files.delete(Self.keysFile + ".new")
            throw CryptoError.authenticationFailed
        }
        try files.write(Self.keysFile, data)
        files.delete(Self.keysFile + ".new")
    }

    private func sealWithPin(_ keys: VaultKeys, pin: String) throws -> Sealed {
        let salt = ChunkedAEAD.randomBytes(16)
        let nonce = ChunkedAEAD.randomBytes(12)
        let inner = nonce + (try aead.seal(keys.bytes, key: kdf(pin, salt), nonce: nonce, aad: Array("yaadein.vault".utf8)))
        return Sealed(protection: .pin, salt: salt, sealed: try device.wrap(inner))
    }

    private func openWithPin(_ s: Sealed, pin: String) throws -> VaultKeys {
        let inner = try device.unwrap(s.sealed)
        guard inner.count > 12 else { throw CryptoError.truncated }
        let plain = try aead.open(Array(inner[12...]), key: kdf(pin, s.salt), nonce: Array(inner[..<12]), aad: Array("yaadein.vault".utf8))
        return try VaultKeys(bytes: plain)
    }

    private func openWithDevice(_ s: Sealed) throws -> VaultKeys { try VaultKeys(bytes: try device.unwrap(s.sealed)) }

    /// First launch: new keys protected by the device key only (onboarding runs before a PIN exists).
    public func createVault() throws {
        let k = VaultKeys.generate()
        try write(Sealed(protection: .device, salt: [], sealed: try device.wrap(k.bytes)), verify: openWithDevice, expected: k)
        lock.withLock { keys = k }
    }

    /// Seals the open keys with a new PIN (setup and change PIN).
    public func setPin(_ pin: String) throws {
        let k = try currentKeys()
        try write(try sealWithPin(k, pin: pin), verify: { try self.openWithPin($0, pin: pin) }, expected: k)
        lockout.reset()
    }

    /// Switches to "phone passcode / Face ID" mode: keys sealed by the device key only.
    public func useDeviceProtection() throws {
        let k = try currentKeys()
        try write(Sealed(protection: .device, salt: [], sealed: try device.wrap(k.bytes)), verify: openWithDevice, expected: k)
    }

    /// Opens a device-protected vault (the app asks the phone's passcode / Face ID first).
    public func unlockWithDevice() throws {
        guard let s = load(), s.protection == .device else { throw CryptoError.invalidFormat("vault needs the PIN") }
        let k = try openWithDevice(s)
        lock.withLock { keys = k }
    }

    /// Opens with keys from the biometric Keychain copy.
    public func unlock(with biometricKeys: VaultKeys) throws {
        guard let s = load() else { throw CryptoError.invalidFormat("no vault") }
        // Accept only keys that are the vault's: compare with what the sealed copy opens to when it is device-only;
        // for PIN vaults the biometric copy was saved from an open session, so trust its integrity (Keychain ACL).
        if s.protection == .device, try openWithDevice(s) != biometricKeys { throw CryptoError.authenticationFailed }
        lock.withLock { keys = biometricKeys }
        lockout.reset()
    }

    public func unlock(pin: String, wipeAfterTen: Bool, onWipe: () -> Void) -> UnlockResult {
        let remaining = lockout.remainingMs
        if remaining > 0 { return .lockedOut(retryAfterMs: remaining) }
        guard let s = load() else { return .wrongPin(failures: 0, retryAfterMs: 0, attemptsBeforeWipe: nil) }
        if s.protection == .pin, let k = try? openWithPin(s, pin: pin) {
            lock.withLock { keys = k }
            lockout.reset()
            return .unlocked
        }
        let failures = lockout.recordFailure()
        if LockoutPolicy.shouldWipe(failures, wipeEnabled: wipeAfterTen) {
            onWipe()
            return .wiped
        }
        return .wrongPin(failures: failures, retryAfterMs: lockout.remainingMs,
                         attemptsBeforeWipe: wipeAfterTen ? LockoutPolicy.attemptsBeforeWipe(failures) : nil)
    }

    /// Checks a PIN without changing the session (change PIN, sensitive settings). Counts as an attempt.
    public func verify(pin: String) -> Bool {
        guard lockout.remainingMs == 0, let s = load(), s.protection == .pin else { return false }
        if (try? openWithPin(s, pin: pin)) != nil {
            lockout.reset()
            return true
        }
        lockout.recordFailure()
        return false
    }

    public func lockNow() { lock.withLock { keys = nil } }

    /// Deletes the device key (everything sealed with it becomes unrecoverable) and the key file.
    public func wipe() {
        lock.withLock { keys = nil }
        files.delete(Self.keysFile)
        lockout.clear()
        device.deleteKey()
    }
}

/// Settings sealed with the device key (DataStore + Keystore on Android): readable before unlock, so theme and lock
/// timeout apply on the lock screen too.
public final class SettingsStore: @unchecked Sendable {
    private let files: SecretFiles
    private let device: DeviceKeyWrapper
    private static let name = "settings.bin"

    public init(files: SecretFiles, device: DeviceKeyWrapper) {
        self.files = files
        self.device = device
    }

    public func load() -> UserSettings {
        guard let sealed = files.read(Self.name), let plain = try? device.unwrap(sealed),
              let s = try? JSONDecoder().decode(UserSettings.self, from: Data(plain)) else { return UserSettings() }
        return s
    }

    public func save(_ settings: UserSettings) throws {
        try files.write(Self.name, try device.wrap([UInt8](try JSONEncoder().encode(settings))))
    }

    public func clear() { files.delete(Self.name) }
}

/// The encrypted database file: `nonce(12) ‖ AES-GCM(dbKey, JSON)`, written atomically.
public enum DatabaseFile {
    public static func seal(_ db: VaultDatabase, key: [UInt8], aead: AEAD) throws -> [UInt8] {
        let nonce = ChunkedAEAD.randomBytes(12)
        return nonce + (try aead.seal([UInt8](try JSONEncoder().encode(db)), key: key, nonce: nonce, aad: Array("yaadein.db.v1".utf8)))
    }

    public static func open(_ bytes: [UInt8], key: [UInt8], aead: AEAD) throws -> VaultDatabase {
        guard bytes.count > 12 else { throw CryptoError.truncated }
        let plain = try aead.open(Array(bytes[12...]), key: key, nonce: Array(bytes[..<12]), aad: Array("yaadein.db.v1".utf8))
        return try JSONDecoder().decode(VaultDatabase.self, from: Data(plain))
    }
}
