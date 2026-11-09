import XCTest
import YaadeinCrypto
import YaadeinData
@testable import Yaadein

/// Checks that only make sense on Apple platforms: CryptoKit agrees with the portable AES-GCM (so files written by
/// either open with the other, and with Android), and every string the app asks for exists in all eight languages.
final class AppTests: XCTestCase {
    func testCryptoKitMatchesPortableAESGCM() throws {
        let key = (0..<32).map(UInt8.init), nonce = (0..<12).map(UInt8.init), aad = Array("YDN2".utf8)
        for size in [0, 1, 15, 16, 17, 1000, 65_536] {
            let plain = (0..<size).map { UInt8(truncatingIfNeeded: $0 &* 7) }
            let a = try CryptoKitAEAD().seal(plain, key: key, nonce: nonce, aad: aad)
            let b = try SoftwareAESGCM().seal(plain, key: key, nonce: nonce, aad: aad)
            XCTAssertEqual(a, b, "size \(size)")
            XCTAssertEqual(plain, try CryptoKitAEAD().open(b, key: key, nonce: nonce, aad: aad))
        }
        var bad = try CryptoKitAEAD().seal([1, 2, 3], key: key, nonce: nonce, aad: aad)
        bad[0] ^= 1
        XCTAssertThrowsError(try CryptoKitAEAD().open(bad, key: key, nonce: nonce, aad: aad))
    }

    func testAndroidBackupFixtureOpensWithCryptoKit() throws {
        // Same chunked stream check as the package tests, through the production AEAD.
        let plain = (0..<150_000).map { UInt8(truncatingIfNeeded: $0 * 7 + 3) }
        let prefix: [UInt8] = [1, 2, 3, 4, 5, 6, 7]
        let sink = MemorySink()
        let enc = ChunkedEncryptor(out: sink, aead: CryptoKitAEAD(), key: (0..<32).map(UInt8.init), prefix: prefix, aad: Array("YDN2".utf8) + prefix)
        try enc.write(plain)
        try enc.finish()
        XCTAssertEqual("cfa0672c493ea4b26b4d21ad199765038de7608d7df3bdd95267f7d8e7f189d3", SHA256.hex(sink.bytes))
    }

    func testStringsExistInEveryLanguage() throws {
        let keys = ["lock_title", "timeline_title", "nav_people", "hub_title", "onboarding_1_title", "settings_title", "date_sometime_in"]
        for lang in ["en", "hi", "mr", "gu", "ta", "te", "bn", "kn"] {
            let path = try XCTUnwrap(Bundle(for: AppModel.self).path(forResource: lang, ofType: "lproj"), lang)
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                XCTAssertNotEqual(key, bundle.localizedString(forKey: key, value: nil, table: nil), "\(lang): \(key)")
            }
        }
    }

    func testFontsAreRegistered() {
        for name in ["Fraunces-SemiBold", "Fraunces-Regular", "ManropeExtraLight-Regular", "ManropeExtraLight-SemiBold"] {
            XCTAssertNotNil(UIFont(name: name, size: 12), name)
        }
    }
}
