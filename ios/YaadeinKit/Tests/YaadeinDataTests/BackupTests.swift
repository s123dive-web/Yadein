import XCTest
import YaadeinCrypto
import YaadeinDomain
@testable import YaadeinData

final class BackupTests: XCTestCase {
    let aead = SoftwareAESGCM()
    /// Argon2id("secret1", salt 0..15) with Android's parameters; checked separately in Argon2idTests.
    let key: [UInt8] = stride(from: 0, to: 64, by: 2).map { i in
        UInt8("9611f29c848bf09198cccaf16e09079fc855b410d1566c2417e968829a0b4e08".dropFirst(i).prefix(2), radix: 16)!
    }

    var staging: URL!
    var vaultRoot: URL!

    override func setUp() {
        staging = FileManager.default.temporaryDirectory.appendingPathComponent("stage-\(UUID().uuidString)")
        vaultRoot = FileManager.default.temporaryDirectory.appendingPathComponent("vault-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: staging)
        try? FileManager.default.removeItem(at: vaultRoot)
    }

    func fixture() throws -> URL {
        try XCTUnwrap(Bundle.module.url(forResource: "android-backup", withExtension: "yaadein", subdirectory: "Fixtures"))
    }

    /// A file written exactly like Android's BackupManager (java.util.zip deflate + javax.crypto GCM) restores here.
    func testRestoresABackupMadeOnAndroid() throws {
        let source = try FileSource(url: try fixture())
        let header = try BackupCodec.readHeader(source)
        XCTAssertEqual((0..<16).map(UInt8.init), header.salt)
        let (manifest, archive) = try BackupCodec.read(source, header: header, key: key, aead: aead, staging: staging)
        XCTAssertEqual(1, manifest?.memories)
        let a = try XCTUnwrap(archive)
        let vault = MediaVault(root: vaultRoot, aead: aead, key: { (0..<32).map(UInt8.init) })
        let db = try BackupCodec.restoredDatabase(a) { url, folder, ext in try vault.store(file: url, folder: folder, ext: ext).path }
        XCTAssertEqual(["Aaji"], db.persons.map(\.name))
        XCTAssertEqual(-3000, db.persons[0].birthdayEpochDay)
        XCTAssertEqual(["Family"], db.groups.map(\.name))
        XCTAssertEqual([1], db.groups[0].memberIds)
        let m = try XCTUnwrap(db.memories.first)
        XCTAssertEqual("Goa Getaway, 2024", m.title)
        XCTAssertEqual(.YEAR, m.date.kind)
        XCTAssertEqual(.JOYFUL, m.mood)
        XCTAssertEqual(9, m.coverMediaId)
        XCTAssertNotNil(m.voiceNotePath)
        XCTAssertEqual([Link(7, 1)], db.memoryPersons)
        XCTAssertEqual([Link(9, 1)], db.mediaPersons)
        let photo = try XCTUnwrap(db.media.first)
        XCTAssertEqual("1a30606485db064b096234e62251582c1df2a03388118482cfc7334d4f61efb2", SHA256.hex(try vault.read(photo.encryptedPath)))
        XCTAssertEqual("3bba62c69c190288a9b3027f8d94b7703e0509b0b0009b89b4a73eac1295a6a9", SHA256.hex(try vault.read(photo.thumbPath)))
        XCTAssertEqual([1, 2, 3], try vault.read(m.voiceNotePath!))
        XCTAssertEqual(10, db.nextId)
    }

    func testWrongPasswordIsReportedAsSuch() throws {
        let source = try FileSource(url: try fixture())
        let header = try BackupCodec.readHeader(source)
        XCTAssertThrowsError(try BackupCodec.read(source, header: header, key: Array(key.reversed()), aead: aead, staging: staging)) {
            XCTAssertEqual($0 as? BackupCodec.BackupError, .wrongPassword)
        }
    }

    /// iPhone → file → iPhone, and the JSON keeps Kotlin's field names and explicit nulls (kotlinx needs them).
    func testRoundTripAndAndroidCompatibleJson() throws {
        let vault = MediaVault(root: vaultRoot, aead: aead, key: { (0..<32).map(UInt8.init) })
        let store = VaultStore { _ in }
        let rahul = try store.upsertPerson(Person(name: "Rahul", relation: .FRIEND))
        let memoryId = try store.createMemory(Memory(type: .TEXT_ONLY, title: "Chai", date: Dates.year(2020)), personIds: [rahul])
        let stored = try vault.store(bytes: [9, 9, 9], folder: .media, ext: "jpg")
        let thumb = try vault.store(bytes: [8], folder: .thumbs, ext: "jpg")
        let mediaId = try store.addMedia(Media(memoryId: memoryId, kind: .PHOTO, encryptedPath: stored.path, thumbPath: thumb.path,
                                               sizeBytes: 3, width: 1, height: 1, sha256: stored.sha256))
        XCTAssertEqual(.EVENT, store.memory(id: memoryId)?.type) // a text card with a photo becomes an event
        XCTAssertEqual(mediaId, store.memory(id: memoryId)?.coverMediaId)

        let sink = MemorySink()
        try BackupCodec.write(database: store.snapshot, to: sink, key: key, salt: (0..<16).map(UInt8.init), aead: aead, now: 1,
                              mediaFile: { path, write in try vault.stream(path, to: write) })
        let source = MemorySource(sink.bytes)
        let header = try BackupCodec.readHeader(source)
        let (_, archive) = try BackupCodec.read(source, header: header, key: key, aead: aead, staging: staging)
        let a = try XCTUnwrap(archive)
        let json = String(decoding: try JSONEncoder().encode(a.dump), as: UTF8.self)
        XCTAssertTrue(json.contains("\"nickname\":null"))
        XCTAssertTrue(json.contains("\"memoryPersons\":[{\"first\":"))
        let restored = try BackupCodec.restoredDatabase(a) { url, folder, ext in try vault.store(file: url, folder: folder, ext: ext).path }
        XCTAssertEqual(store.snapshot.memories.map(\.title), restored.memories.map(\.title))
        XCTAssertEqual([9, 9, 9], try vault.read(restored.media[0].encryptedPath))
    }

    func testFamilyPackMergesPeopleByNameAndCannotBeRestored() throws {
        let vault = MediaVault(root: vaultRoot, aead: aead, key: { (0..<32).map(UInt8.init) })
        let source = try FileSource(url: try fixture())
        let header = try BackupCodec.readHeader(source)
        let (_, archive) = try BackupCodec.read(source, header: header, key: key, aead: aead, staging: staging)
        var mine = VaultDatabase()
        mine.persons = [Person(id: 1, name: "  aaji "), Person(id: 2, name: "Rahul")]
        mine.nextId = 3
        let (merged, memories, newPeople, first) = try BackupCodec.merged(XCTUnwrap(archive), into: mine) { url, folder, ext in
            try vault.store(file: url, folder: folder, ext: ext).path
        }
        XCTAssertEqual(1, memories)
        XCTAssertEqual(0, newPeople)
        XCTAssertEqual(2, merged.persons.count)
        let m = try XCTUnwrap(merged.memories.first { $0.id == first })
        XCTAssertTrue(merged.memoryPersons.contains(Link(m.id, 1)))
        XCTAssertNotNil(m.coverMediaId)
    }
}

extension BackupTests {
    /// Writes a backup for an external reader check (java.util.zip + javax.crypto, i.e. Android's code path).
    func testWriteBackupForAndroidReaderCheck() throws {
        guard let out = ProcessInfo.processInfo.environment["YAADEIN_EXPORT_BACKUP"] else { throw XCTSkip("set YAADEIN_EXPORT_BACKUP") }
        let vault = MediaVault(root: vaultRoot, aead: aead, key: { (0..<32).map(UInt8.init) })
        let store = VaultStore { _ in }
        let p = try store.upsertPerson(Person(name: "Aaji", nickname: nil, relation: .FAMILY))
        let m = try store.createMemory(Memory(type: .EVENT, title: "Diwali ✨", date: Dates.exact(.of(2024, 10, 31)), place: "Pune"), personIds: [p])
        let photo = (0..<150_000).map { UInt8(truncatingIfNeeded: $0 * 13) }
        let s = try vault.store(bytes: photo, folder: .media, ext: "jpg")
        let t = try vault.store(bytes: [1, 2], folder: .thumbs, ext: "jpg")
        _ = try store.addMedia(Media(memoryId: m, kind: .PHOTO, encryptedPath: s.path, thumbPath: t.path, sizeBytes: 150_000, width: 4, height: 3, sha256: s.sha256))
        let sink = try FileSink(url: URL(fileURLWithPath: out))
        try BackupCodec.write(database: store.snapshot, to: sink, key: key, salt: (0..<16).map(UInt8.init), aead: aead, now: 1,
                              mediaFile: { path, write in try vault.stream(path, to: write) })
        try sink.close()
    }
}
