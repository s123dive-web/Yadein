import XCTest
@testable import YaadeinCrypto

/// The chunked formats and zip against output of the real Java/Android code paths (values printed by jshell running
/// the same javax.crypto / java.util.zip calls as ChunkedAead and BackupManager).
final class FormatTests: XCTestCase {
    let aead = SoftwareAESGCM()
    let key = (0..<32).map(UInt8.init)
    let prefix: [UInt8] = [1, 2, 3, 4, 5, 6, 7]
    func hex(_ b: [UInt8]) -> String { b.map { String(format: "%02x", $0) }.joined() }
    func bytes(_ s: String) -> [UInt8] { stride(from: 0, to: s.count, by: 2).map { UInt8(s.dropFirst($0).prefix(2), radix: 16)! } }

    func testAESGCMKnownAnswer() throws {
        // NIST GCM test case 16 (AES-256, 60-byte plaintext, 20-byte AAD).
        let k = bytes("feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308")
        let iv = bytes("cafebabefacedbaddecaf888")
        let p = bytes("d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39")
        let a = bytes("feedfacedeadbeeffeedfacedeadbeefabaddad2")
        let sealed = try aead.seal(p, key: k, nonce: iv, aad: a)
        XCTAssertEqual("522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662", hex(Array(sealed.prefix(60))))
        XCTAssertEqual("76fc6ece0f4e1768cddf8853bb2d551b", hex(Array(sealed.suffix(16))))
        XCTAssertEqual(p, try aead.open(sealed, key: k, nonce: iv, aad: a))
        var bad = sealed
        bad[3] ^= 1
        XCTAssertThrowsError(try aead.open(bad, key: k, nonce: iv, aad: a))
    }

    func testHashes() {
        XCTAssertEqual("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SHA256.hex(Array("abc".utf8)))
        XCTAssertEqual("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", SHA256.hex([]))
        XCTAssertEqual(0xCBF4_3926, CRC32.checksum(Array("123456789".utf8)))
    }

    func chunked(_ plain: [UInt8]) throws -> [UInt8] {
        let sink = MemorySink()
        let header = Array("YDN2".utf8) + prefix
        let enc = ChunkedEncryptor(out: sink, aead: aead, key: key, prefix: prefix, aad: header)
        // Write in odd-sized pieces to exercise the chunk boundaries.
        var i = 0
        while i < plain.count {
            let n = min(10_007, plain.count - i)
            try enc.write(Array(plain[i..<(i + n)]))
            i += n
        }
        try enc.finish()
        return sink.bytes
    }

    func testChunkedStreamMatchesAndroid() throws {
        let plain = (0..<150_000).map { UInt8(truncatingIfNeeded: $0 * 7 + 3) }
        XCTAssertEqual("cfa0672c493ea4b26b4d21ad199765038de7608d7df3bdd95267f7d8e7f189d3", SHA256.hex(try chunked(plain)))
        XCTAssertEqual("83e510b37d3962e55f2f9cc3bdad31cc5d21b9f2d5a844310e44ba3c6f560f08", SHA256.hex(try chunked(Array(plain.prefix(65_536)))))
        XCTAssertEqual("27c8b389753c5652ba4451de2b13c68ef78ecb9b42eeb6f54c62271e7b56a451", SHA256.hex(try chunked([])))
    }

    func testChunkedRoundTripAndTamperDetection() throws {
        let header = Array("YDN2".utf8) + prefix
        for size in [0, 1, 65_535, 65_536, 65_537, 200_000] {
            let plain = (0..<size).map { UInt8(truncatingIfNeeded: $0 &* 31) }
            let sealed = try chunked(plain)
            let dec = ChunkedDecryptor(input: MemorySource(sealed), aead: aead, key: key, prefix: prefix, aad: header)
            XCTAssertEqual(plain, try readAll(dec), "size \(size)")
        }
        let sealed = try chunked((0..<150_000).map { UInt8(truncatingIfNeeded: $0) })
        // Cut off at a chunk boundary: the new last chunk was not sealed as last.
        let cut = Array(sealed.prefix(65_536 + 16))
        XCTAssertThrowsError(try readAll(ChunkedDecryptor(input: MemorySource(cut), aead: aead, key: key, prefix: prefix, aad: header)))
        var flipped = sealed
        flipped[70_000] ^= 0x40
        XCTAssertThrowsError(try readAll(ChunkedDecryptor(input: MemorySource(flipped), aead: aead, key: key, prefix: prefix, aad: header)))
        XCTAssertThrowsError(try readAll(ChunkedDecryptor(input: MemorySource(sealed), aead: aead, key: Array(key.reversed()), prefix: prefix, aad: header)))
    }

    func testMediaFileFormat() throws {
        let plain = (0..<100_000).map { UInt8(truncatingIfNeeded: $0 / 3) }
        let sink = MemorySink()
        let sha = try MediaFileFormat.encrypt(MemorySource(plain), to: sink, aead: aead, key: key, prefix: prefix)
        XCTAssertEqual(SHA256.hex(plain), sha)
        XCTAssertEqual(Array("YDN2".utf8), Array(sink.bytes.prefix(4)))
        XCTAssertEqual(Int64(plain.count), MediaFileFormat.plainLength(fileLength: Int64(sink.bytes.count)))
        XCTAssertEqual(plain, try readAll(try MediaFileFormat.decryptor(MemorySource(sink.bytes), aead: aead, key: key)))
    }

    func testZipRoundTripWithStoredEntries() throws {
        let sink = MemorySink()
        let zip = ZipStreamWriter(sink)
        let big = (0..<70_000).map { UInt8(truncatingIfNeeded: $0) }
        try zip.add(name: "manifest.json", bytes: Array("{}".utf8))
        try zip.add(name: "media/1.bin") { write in try write(Array(big.prefix(1000))); try write(Array(big.dropFirst(1000))) }
        try zip.add(name: "empty", bytes: [])
        try zip.finish()
        var seen: [String: [UInt8]] = [:]
        try ZipStreamReader(MemorySource(sink.bytes)).forEachEntry { name, read in
            var b: [UInt8] = []
            try read { b += $0 }
            seen[name] = b
        }
        XCTAssertEqual(["empty", "manifest.json", "media/1.bin"], seen.keys.sorted())
        XCTAssertEqual(big, seen["media/1.bin"])
    }
}
