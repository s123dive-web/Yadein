import XCTest
import YaadeinCrypto
import YaadeinDomain
@testable import YaadeinData

final class VaultSessionTests: XCTestCase {
    var folder: URL!
    var uptime: Int64 = 1_000_000
    var boot = "boot-1"
    let device = InMemoryDeviceKey()

    override func setUp() { folder = FileManager.default.temporaryDirectory.appendingPathComponent("secrets-\(UUID().uuidString)") }
    override func tearDown() { try? FileManager.default.removeItem(at: folder) }

    func makeSession() -> VaultSession {
        let files = SecretFiles(folder: folder)
        let lockout = LockoutTracker(files: files, device: device, uptimeMs: { self.uptime }, bootId: { self.boot })
        // Fast stand-in KDF: the real Argon2id is covered by Argon2idTests.
        return VaultSession(files: files, device: device, aead: SoftwareAESGCM(), lockout: lockout,
                            kdf: { pin, salt in Array(SHA256.hex(Array(pin.utf8) + salt).utf8.prefix(32)) })
    }

    func testOnboardingPinUnlockAndLockout() {
        let s = makeSession()
        XCTAssertFalse(s.isSetUp)
        XCTAssertNoThrow(try s.createVault())
        let keys = try! s.currentKeys()
        XCTAssertEqual(.device, s.protection)
        XCTAssertNoThrow(try s.setPin("123456"))
        XCTAssertEqual(.pin, s.protection)
        s.lockNow()
        XCTAssertThrowsError(try s.currentKeys())

        let fresh = makeSession() // a new process
        XCTAssertFalse(fresh.isOpen)
        for i in 1...4 {
            XCTAssertEqual(.wrongPin(failures: i, retryAfterMs: 0, attemptsBeforeWipe: nil), fresh.unlock(pin: "000000", wipeAfterTen: false, onWipe: {}))
        }
        XCTAssertEqual(.wrongPin(failures: 5, retryAfterMs: 30_000, attemptsBeforeWipe: nil), fresh.unlock(pin: "000000", wipeAfterTen: false, onWipe: {}))
        XCTAssertEqual(.lockedOut(retryAfterMs: 30_000), fresh.unlock(pin: "123456", wipeAfterTen: false, onWipe: {}))
        uptime += 30_000
        XCTAssertEqual(.unlocked, fresh.unlock(pin: "123456", wipeAfterTen: false, onWipe: {}))
        XCTAssertEqual(keys, try fresh.currentKeys())
        XCTAssertEqual(0, fresh.lockout.failures)
    }

    func testRebootDoesNotShortenALockout() {
        let s = makeSession()
        try! s.createVault()
        try! s.setPin("111111")
        for _ in 1...6 { _ = s.unlock(pin: "x", wipeAfterTen: false, onWipe: {}) }
        uptime = 5 // rebooted: uptime restarted
        boot = "boot-2"
        // The 6th try was refused (locked out), so 5 failures: the full 30 s starts again after the reboot.
        XCTAssertEqual(.lockedOut(retryAfterMs: 30_000), makeSession().unlock(pin: "111111", wipeAfterTen: false, onWipe: {}))
    }

    func testWipeAfterTen() {
        let s = makeSession()
        try! s.createVault()
        try! s.setPin("111111")
        var wiped = false
        var result: VaultSession.UnlockResult = .unlocked
        for _ in 1...10 {
            uptime += 10_000_000
            result = s.unlock(pin: "x", wipeAfterTen: true, onWipe: { wiped = true })
        }
        XCTAssertEqual(.wiped, result)
        XCTAssertTrue(wiped)
    }

    func testDatabaseFileAndSettings() throws {
        var db = VaultDatabase()
        db.persons = [Person(id: 1, name: "Aai")]
        let key = ChunkedAEAD.randomBytes(32)
        let sealed = try DatabaseFile.seal(db, key: key, aead: SoftwareAESGCM())
        XCTAssertEqual(db, try DatabaseFile.open(sealed, key: key, aead: SoftwareAESGCM()))
        XCTAssertThrowsError(try DatabaseFile.open(sealed, key: ChunkedAEAD.randomBytes(32), aead: SoftwareAESGCM()))

        let settings = SettingsStore(files: SecretFiles(folder: folder), device: device)
        XCTAssertEqual(UserSettings(), settings.load())
        var s = UserSettings()
        s.theme = .FESTIVE
        s.easyMode = true
        try settings.save(s)
        XCTAssertEqual(s, settings.load())
        // Older files without newer keys still load with defaults.
        XCTAssertEqual(.ONE_MINUTE, try JSONDecoder().decode(UserSettings.self, from: Data(#"{"theme":"MIDNIGHT"}"#.utf8)).lockTimeout)
    }
}
