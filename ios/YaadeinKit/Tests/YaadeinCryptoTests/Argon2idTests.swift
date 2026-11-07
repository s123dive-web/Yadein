import XCTest
@testable import YaadeinCrypto

final class Argon2idTests: XCTestCase {
    func hex(_ b: [UInt8]) -> String { b.map { String(format: "%02x", $0) }.joined() }
    func bytes(_ s: String) -> [UInt8] { stride(from: 0, to: s.count, by: 2).map { UInt8(s.dropFirst($0).prefix(2), radix: 16)! } }

    func testBlake2bKnownAnswers() {
        // RFC 7693 Appendix A ("abc", 64 bytes) and the empty string.
        XCTAssertEqual("ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
                       hex(Blake2b.hash(Array("abc".utf8), outputLength: 64)))
        XCTAssertEqual("786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce",
                       hex(Blake2b.hash([], outputLength: 64)))
        // Longer than one block.
        XCTAssertEqual(32, Blake2b.hash([UInt8](repeating: 7, count: 300), outputLength: 32).count)
    }

    /// RFC 9106 §5.3 test vector (with secret and associated data replaced: reference implementation output).
    func testMatchesReferenceImplementation() {
        // Expected values from argon2-cffi (the same reference C code argon2kt wraps on Android).
        let small = Argon2id.hash(password: Array("password".utf8), salt: Array("somesalt12345678".utf8),
                                  parameters: .init(iterations: 3, memoryKiB: 64, parallelism: 2, length: 32))
        XCTAssertEqual("7ad81051e93c71dd6e2a472c021e737475f25f7765cdf8d184d9f6d1910ab114", hex(small))
        let long = Argon2id.hash(password: Array("pw".utf8), salt: Array("saltsaltsaltsalt".utf8),
                                 parameters: .init(iterations: 1, memoryKiB: 32, parallelism: 1, length: 100))
        XCTAssertEqual("0ba9b32752e3cf68cda12912d3b6005c8bb0d59010b1e01ff09613c83425e7eb70c244a1d6307b181e58c3a8df8dffe82f6bb047523309659c2c1b01641012b99b41ebaf6390a71391e79f2f41f581082c3fc74b0e3841a0fe3ab5a4d7ac3f122ead0b29", hex(long))
    }

    /// Android's real parameters (t=3, m=64 MiB, p=2). Slow in debug builds, so CI runs it in release.
    func testYaadeinParametersMatchAndroid() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["YAADEIN_SLOW_TESTS"] == "1", "set YAADEIN_SLOW_TESTS=1")
        let key = Argon2id.hash(password: "secret1", salt: Array(0..<16))
        XCTAssertEqual("9611f29c848bf09198cccaf16e09079fc855b410d1566c2417e968829a0b4e08", hex(key))
    }
}
