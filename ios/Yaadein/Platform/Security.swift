import CryptoKit
import Foundation
import LocalAuthentication
import Security
import YaadeinCrypto
import YaadeinData

/// AES-256-GCM through CryptoKit (hardware accelerated, constant time). Same bytes as `SoftwareAESGCM` and javax.crypto.
struct CryptoKitAEAD: AEAD {
    func seal(_ plaintext: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8] {
        let box = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), nonce: try AES.GCM.Nonce(data: nonce), authenticating: aad)
        return [UInt8](box.ciphertext) + [UInt8](box.tag)
    }

    func open(_ sealed: [UInt8], key: [UInt8], nonce: [UInt8], aad: [UInt8]) throws -> [UInt8] {
        guard sealed.count >= 16 else { throw CryptoError.truncated }
        do {
            let box = try AES.GCM.SealedBox(nonce: try AES.GCM.Nonce(data: nonce), ciphertext: sealed.dropLast(16), tag: sealed.suffix(16))
            return [UInt8](try AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad))
        } catch {
            throw CryptoError.authenticationFailed
        }
    }
}

enum Keychain {
    static let service = "com.yaadein.vault"

    static func read(_ account: String, context: LAContext? = nil, prompt: String? = nil) -> Data? {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        if let context { q[kSecUseAuthenticationContext as String] = context }
        if let prompt { q[kSecUseOperationPrompt as String] = prompt }
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    @discardableResult
    static func write(_ account: String, _ data: Data, access: SecAccessControl? = nil) -> Bool {
        delete(account)
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                                kSecAttrAccount as String: account, kSecValueData as String: data]
        if let access {
            q[kSecAttrAccessControl as String] = access
        } else {
            // Never in iCloud Keychain or device backups: a restored phone cannot open the vault without the backup file.
            q[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        }
        return SecItemAdd(q as CFDictionary, nil) == errSecSuccess
    }

    static func delete(_ account: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(q as CFDictionary)
    }
}

/// The device-bound wrapping key (Android: `yaadein_master` in the Keystore). 32 random bytes in the Keychain, this
/// device only; deleting it makes everything sealed with it unrecoverable (wipe).
final class KeychainDeviceKey: DeviceKeyWrapper {
    private let account = "device.key"
    private let aead = CryptoKitAEAD()
    private let lock = NSLock()

    private func key() throws -> [UInt8] {
        try lock.withLock {
            if let d = Keychain.read(account), d.count == 32 { return [UInt8](d) }
            let fresh = ChunkedAEAD.randomBytes(32)
            guard Keychain.write(account, Data(fresh)) else { throw CryptoError.invalidFormat("Keychain unavailable") }
            return fresh
        }
    }

    func wrap(_ plain: [UInt8]) throws -> [UInt8] {
        let nonce = ChunkedAEAD.randomBytes(12)
        return nonce + (try aead.seal(plain, key: try key(), nonce: nonce, aad: Array("yaadein.device".utf8)))
    }

    func unwrap(_ sealed: [UInt8]) throws -> [UInt8] {
        guard sealed.count > 12 else { throw CryptoError.truncated }
        return try aead.open(Array(sealed[12...]), key: try key(), nonce: Array(sealed[..<12]), aad: Array("yaadein.device".utf8))
    }

    func deleteKey() { Keychain.delete(account) }
}

/// A second copy of the vault keys that only Face ID / Touch ID can release (BiometricKeys.kt): the Keychain item is
/// bound to the current biometric enrolment, so adding a new face or finger invalidates it and the PIN is needed.
enum BiometricKeys {
    private static let account = "vault.biometric"

    static var isAvailable: Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    static var kind: LABiometryType {
        let c = LAContext()
        var error: NSError?
        _ = c.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        return c.biometryType
    }

    static var hasCopy: Bool {
        let c = LAContext()
        c.interactionNotAllowed = true
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: Keychain.service,
                                kSecAttrAccount as String: account, kSecUseAuthenticationContext as String: c]
        q[kSecReturnAttributes as String] = true
        let status = SecItemCopyMatching(q as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    static func save(_ keys: [UInt8]) -> Bool {
        guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .biometryCurrentSet, nil) else { return false }
        return Keychain.write(account, Data(keys), access: access)
    }

    /// Shows Face ID / Touch ID and returns the keys, or nil if cancelled, failed or invalidated.
    static func load(prompt: String) async -> [UInt8]? {
        await Task.detached {
            let context = LAContext()
            context.localizedCancelTitle = L("lock_biometric_negative")
            return Keychain.read(account, context: context, prompt: prompt).map { [UInt8]($0) }
        }.value
    }

    static func clear() { Keychain.delete(account) }
}

/// "Use my phone's screen lock instead": the passcode / Face ID prompt of the phone itself.
enum DeviceAuth {
    static var isAvailable: Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
    }

    static func authenticate(reason: String) async -> Bool {
        await withCheckedContinuation { cont in
            LAContext().evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, _ in cont.resume(returning: ok) }
        }
    }
}

enum SystemClock {
    /// Milliseconds since boot (does not change with the date).
    static var uptimeMs: Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }

    /// Changes on every reboot: the boot time rounded to the minute.
    static var bootId: String {
        let boot = Date().timeIntervalSince1970 - ProcessInfo.processInfo.systemUptime
        return String(Int64(boot / 60))
    }
}
