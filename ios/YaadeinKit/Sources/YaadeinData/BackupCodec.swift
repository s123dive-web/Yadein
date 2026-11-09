import Foundation
import YaadeinCrypto
import YaadeinDomain

/// The `.yaadein` backup / family pack file, byte-compatible with Android's BackupManager (format 2):
/// `"YDNB" ‖ 0x02 ‖ salt(16) ‖ prefix(7) ‖ ChunkedAEAD(zip)`, key = Argon2id(password, salt), header = AAD.
/// The zip holds manifest.json, data.json (all tables, kotlinx.serialization field names) and the decrypted media
/// (`media/<id>.bin`, `thumbs/<id>.jpg`, `voice/<memoryId>.m4a`). A backup made on either phone restores on the other.
public enum BackupCodec {
    public static let magic: [UInt8] = Array("YDNB".utf8)
    public static let format: UInt8 = 2
    public static let minPasswordLength = 6
    public static let fileExtension = "yaadein"

    public enum BackupError: Error, Equatable {
        case wrongPassword
        case newerVersion
        case notABackup
        /// The file is a family pack (some memories): it can only be added, not restored over the vault.
        case packNotBackup
        case manifestMismatch
    }

    // MARK: JSON rows (BackupManager.Manifest / Dump)

    public struct Manifest: Codable, Equatable, Sendable {
        public var app = "yaadein"
        public var format = 2
        public var createdAt: Int64
        public var persons: Int
        public var memories: Int
        public var media: Int
        public var pack = false

        public init(createdAt: Int64, persons: Int, memories: Int, media: Int, pack: Bool) {
            self.createdAt = createdAt
            self.persons = persons
            self.memories = memories
            self.media = media
            self.pack = pack
        }

        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            app = try c.decodeIfPresent(String.self, forKey: .app) ?? "yaadein"
            format = try c.decodeIfPresent(Int.self, forKey: .format) ?? 2
            createdAt = try c.decode(Int64.self, forKey: .createdAt)
            persons = try c.decode(Int.self, forKey: .persons)
            memories = try c.decode(Int.self, forKey: .memories)
            media = try c.decode(Int.self, forKey: .media)
            pack = try c.decodeIfPresent(Bool.self, forKey: .pack) ?? false
        }
    }

    public struct PersonRow: Codable, Equatable, Sendable {
        var id: Int64; var name: String; var nickname: String?; var relation: String; var birthdayEpochDay: Int64?
        var profileMediaId: Int64?; var notes: String; var avatarColorIndex: Int; var createdAt: Int64

        public func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id); try c.encode(name, forKey: .name); try c.encode(nickname, forKey: .nickname)
            try c.encode(relation, forKey: .relation); try c.encode(birthdayEpochDay, forKey: .birthdayEpochDay)
            try c.encode(profileMediaId, forKey: .profileMediaId); try c.encode(notes, forKey: .notes)
            try c.encode(avatarColorIndex, forKey: .avatarColorIndex); try c.encode(createdAt, forKey: .createdAt)
        }
    }

    public struct GroupRow: Codable, Equatable, Sendable {
        var id: Int64; var name: String; var emoji: String; var createdAt: Int64
    }

    public struct MemoryRow: Codable, Equatable, Sendable {
        var id: Int64; var type: String; var title: String; var description: String; var dateKind: String
        var startEpochDay: Int64; var endEpochDay: Int64?; var fuzzyLabel: String?; var place: String; var mood: String?
        var coverMediaId: Int64?; var coverStyle: String; var createdAt: Int64; var updatedAt: Int64; var voice: Bool

        enum CodingKeys: String, CodingKey {
            case id, type, title, description, dateKind, startEpochDay, endEpochDay, fuzzyLabel, place, mood, coverMediaId, coverStyle, createdAt, updatedAt, voice
        }

        init(id: Int64, type: String, title: String, description: String, dateKind: String, startEpochDay: Int64, endEpochDay: Int64?,
             fuzzyLabel: String?, place: String, mood: String?, coverMediaId: Int64?, coverStyle: String, createdAt: Int64, updatedAt: Int64, voice: Bool) {
            self.id = id; self.type = type; self.title = title; self.description = description; self.dateKind = dateKind
            self.startEpochDay = startEpochDay; self.endEpochDay = endEpochDay; self.fuzzyLabel = fuzzyLabel; self.place = place
            self.mood = mood; self.coverMediaId = coverMediaId; self.coverStyle = coverStyle; self.createdAt = createdAt
            self.updatedAt = updatedAt; self.voice = voice
        }

        public init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = try c.decode(Int64.self, forKey: .id); type = try c.decode(String.self, forKey: .type)
            title = try c.decode(String.self, forKey: .title); description = try c.decode(String.self, forKey: .description)
            dateKind = try c.decode(String.self, forKey: .dateKind); startEpochDay = try c.decode(Int64.self, forKey: .startEpochDay)
            endEpochDay = try c.decodeIfPresent(Int64.self, forKey: .endEpochDay); fuzzyLabel = try c.decodeIfPresent(String.self, forKey: .fuzzyLabel)
            place = try c.decode(String.self, forKey: .place); mood = try c.decodeIfPresent(String.self, forKey: .mood)
            coverMediaId = try c.decodeIfPresent(Int64.self, forKey: .coverMediaId); coverStyle = try c.decode(String.self, forKey: .coverStyle)
            createdAt = try c.decode(Int64.self, forKey: .createdAt); updatedAt = try c.decode(Int64.self, forKey: .updatedAt)
            voice = try c.decodeIfPresent(Bool.self, forKey: .voice) ?? false
        }

        public func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id); try c.encode(type, forKey: .type); try c.encode(title, forKey: .title)
            try c.encode(description, forKey: .description); try c.encode(dateKind, forKey: .dateKind)
            try c.encode(startEpochDay, forKey: .startEpochDay); try c.encode(endEpochDay, forKey: .endEpochDay)
            try c.encode(fuzzyLabel, forKey: .fuzzyLabel); try c.encode(place, forKey: .place); try c.encode(mood, forKey: .mood)
            try c.encode(coverMediaId, forKey: .coverMediaId); try c.encode(coverStyle, forKey: .coverStyle)
            try c.encode(createdAt, forKey: .createdAt); try c.encode(updatedAt, forKey: .updatedAt); try c.encode(voice, forKey: .voice)
        }
    }

    public struct MediaRow: Codable, Equatable, Sendable {
        var id: Int64; var memoryId: Int64?; var kind: String; var file: String; var thumb: String; var takenAt: Int64?
        var durationMs: Int64?; var sizeBytes: Int64; var caption: String; var width: Int; var height: Int; var sha256: String
        var position: Int; var createdAt: Int64

        public func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id); try c.encode(memoryId, forKey: .memoryId); try c.encode(kind, forKey: .kind)
            try c.encode(file, forKey: .file); try c.encode(thumb, forKey: .thumb); try c.encode(takenAt, forKey: .takenAt)
            try c.encode(durationMs, forKey: .durationMs); try c.encode(sizeBytes, forKey: .sizeBytes); try c.encode(caption, forKey: .caption)
            try c.encode(width, forKey: .width); try c.encode(height, forKey: .height); try c.encode(sha256, forKey: .sha256)
            try c.encode(position, forKey: .position); try c.encode(createdAt, forKey: .createdAt)
        }
    }

    public struct Dump: Codable, Equatable, Sendable {
        var persons: [PersonRow]; var groups: [GroupRow]; var groupMembers: [Link]
        var memories: [MemoryRow]; var media: [MediaRow]; var mediaPersons: [Link]; var memoryPersons: [Link]
    }

    // MARK: Conversion

    /// Rows for everything, or (family pack) only `onlyMemoryIds`, their media and the people in them.
    static func dump(_ d: VaultDatabase, onlyMemoryIds: Set<Int64>? = nil) -> Dump {
        let memories = d.memories.filter { onlyMemoryIds?.contains($0.id) ?? true }
        let memoryIds = Set(memories.map(\.id))
        let media = d.media.filter { onlyMemoryIds == nil || ($0.memoryId.map(memoryIds.contains) ?? false) }
        let mediaIds = Set(media.map(\.id))
        let memoryPersons = d.memoryPersons.filter { memoryIds.contains($0.first) }
        let mediaPersons = d.mediaPersons.filter { mediaIds.contains($0.first) }
        let packPeople: Set<Int64>? = onlyMemoryIds == nil ? nil : Set(memoryPersons.map(\.second) + mediaPersons.map(\.second))
        let persons = d.persons.filter { packPeople?.contains($0.id) ?? true }.map { p in
            PersonRow(id: p.id, name: p.name, nickname: p.nickname, relation: p.relation.rawValue, birthdayEpochDay: p.birthdayEpochDay,
                      profileMediaId: onlyMemoryIds == nil ? p.profileMediaId : nil, notes: p.notes, avatarColorIndex: p.avatarColorIndex, createdAt: p.createdAt)
        }
        let groups = onlyMemoryIds == nil ? d.groups : []
        return Dump(
            persons: persons,
            groups: groups.map { GroupRow(id: $0.id, name: $0.name, emoji: $0.emoji, createdAt: $0.createdAt) },
            groupMembers: groups.flatMap { g in g.memberIds.map { Link(g.id, $0) } },
            memories: memories.map { m in
                MemoryRow(id: m.id, type: m.type.rawValue, title: m.title, description: m.description, dateKind: m.date.kind.rawValue,
                          startEpochDay: m.date.startEpochDay, endEpochDay: m.date.endEpochDay, fuzzyLabel: m.date.label, place: m.place,
                          mood: m.mood?.rawValue, coverMediaId: m.coverMediaId, coverStyle: m.coverStyle, createdAt: m.createdAt,
                          updatedAt: m.updatedAt, voice: m.voiceNotePath != nil)
            },
            media: media.map { r in
                MediaRow(id: r.id, memoryId: r.memoryId, kind: r.kind.rawValue, file: r.encryptedPath, thumb: r.thumbPath, takenAt: r.takenAt,
                         durationMs: r.durationMs, sizeBytes: r.sizeBytes, caption: r.caption, width: r.width, height: r.height,
                         sha256: r.sha256, position: r.position, createdAt: r.createdAt)
            },
            mediaPersons: mediaPersons, memoryPersons: memoryPersons)
    }

    // MARK: Writing

    /// Writes a backup (or, with `onlyMemoryIds`, a family pack) of `database`. `mediaFile` streams a vault file's plaintext.
    public static func write(database: VaultDatabase, to sink: ByteSink, key: [UInt8], salt: [UInt8], aead: AEAD,
                             onlyMemoryIds: Set<Int64>? = nil, now: Int64,
                             prefix: [UInt8] = ChunkedAEAD.randomBytes(ChunkedAEAD.prefixBytes),
                             mediaFile: @escaping (String, ([UInt8]) throws -> Void) throws -> Void,
                             exists: (String) -> Bool = { _ in true },
                             progress: (Int, Int) -> Void = { _, _ in }) throws {
        precondition(salt.count == 16 && prefix.count == ChunkedAEAD.prefixBytes)
        let d = dump(database, onlyMemoryIds: onlyMemoryIds)
        let manifest = Manifest(createdAt: now, persons: d.persons.count, memories: d.memories.count, media: d.media.count, pack: onlyMemoryIds != nil)
        let header = magic + [format] + salt + prefix
        try sink.write(header)
        let enc = ChunkedEncryptor(out: sink, aead: aead, key: key, prefix: prefix, aad: header)
        let zip = ZipStreamWriter(enc)
        let json = JSONEncoder()
        json.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        try zip.add(name: "manifest.json", bytes: Array(try json.encode(manifest)))
        try zip.add(name: "data.json", bytes: Array(try json.encode(d)))
        for (i, row) in d.media.enumerated() {
            progress(i, d.media.count)
            if exists(row.file) { try zip.add(name: "media/\(row.id).bin") { write in try mediaFile(row.file, write) } }
            if exists(row.thumb) { try zip.add(name: "thumbs/\(row.id).jpg") { write in try mediaFile(row.thumb, write) } }
        }
        for m in database.memories where d.memories.contains(where: { $0.id == m.id }) {
            if let voice = m.voiceNotePath, exists(voice) {
                try zip.add(name: "voice/\(m.id).m4a") { write in try mediaFile(voice, write) }
            }
        }
        try zip.finish()
        try enc.finish()
    }

    // MARK: Reading

    public struct Header: Sendable {
        public let salt: [UInt8]
        public let prefix: [UInt8]
        public let bytes: [UInt8]
    }

    public static func readHeader(_ source: ByteSource) throws -> Header {
        var h: [UInt8] = []
        while h.count < 5 {
            let r = try source.read(max: 5 - h.count)
            if r.isEmpty { throw BackupError.notABackup }
            h += r
        }
        guard Array(h.prefix(4)) == magic else { throw BackupError.notABackup }
        guard h[4] <= format else { throw BackupError.newerVersion }
        // Format 1 (one GCM pass over the whole zip) could not be written for real libraries on Android; not supported.
        guard h[4] == format else { throw BackupError.notABackup }
        while h.count < 5 + 16 + ChunkedAEAD.prefixBytes {
            let r = try source.read(max: 5 + 16 + ChunkedAEAD.prefixBytes - h.count)
            if r.isEmpty { throw BackupError.notABackup }
            h += r
        }
        return Header(salt: Array(h[5..<21]), prefix: Array(h[21...]), bytes: h)
    }

    public struct Archive {
        public let manifest: Manifest
        public let dump: Dump
        /// Zip entry name ("media/9.bin") → plaintext file in the staging folder.
        public let files: [String: URL]
    }

    private static let safeName = try! NSRegularExpression(pattern: "^[a-z]+_[0-9]+\\.[a-z0-9]+$")

    /// Decrypts the whole archive into `staging`. A wrong password fails on the first chunk with `.wrongPassword`.
    /// `manifestOnly` stops after manifest.json (the quick "this backup has 120 memories" check).
    public static func read(_ source: ByteSource, header: Header, key: [UInt8], aead: AEAD, staging: URL?,
                            manifestOnly: Bool = false) throws -> (Manifest?, Archive?) {
        let dec = ChunkedDecryptor(input: source, aead: aead, key: key, prefix: header.prefix, aad: header.bytes)
        var manifest: Manifest?
        var dump: Dump?
        var files: [String: URL] = [:]
        struct Stop: Error {}
        do {
            try ZipStreamReader(dec).forEachEntry { name, read in
                switch name {
                case "manifest.json":
                    var bytes: [UInt8] = []
                    try read { bytes += $0 }
                    manifest = try JSONDecoder().decode(Manifest.self, from: Data(bytes))
                    if manifestOnly { throw Stop() }
                case "data.json":
                    var bytes: [UInt8] = []
                    try read { bytes += $0 }
                    dump = try JSONDecoder().decode(Dump.self, from: Data(bytes))
                default:
                    guard name.hasPrefix("media/") || name.hasPrefix("thumbs/") || name.hasPrefix("voice/"), let staging else { return }
                    // Entry names come from the file: keep only the expected "dir/<id>.<ext>" shape (no path tricks).
                    let flat = name.replacingOccurrences(of: "/", with: "_")
                    guard safeName.firstMatch(in: flat, range: NSRange(location: 0, length: (flat as NSString).length)) != nil else { return }
                    let url = staging.appendingPathComponent(flat)
                    let sink = try FileSink(url: url)
                    defer { try? sink.close() }
                    try read { try sink.write($0) }
                    files[name] = url
                }
            }
        } catch is Stop {
        } catch CryptoError.authenticationFailed {
            if manifest == nil { throw BackupError.wrongPassword }
            throw CryptoError.authenticationFailed
        }
        guard let m = manifest else { throw BackupError.wrongPassword }
        guard m.app == "yaadein", m.format <= Int(format) else { throw BackupError.newerVersion }
        if manifestOnly { return (m, nil) }
        guard let d = dump else { throw BackupError.notABackup }
        return (m, Archive(manifest: m, dump: d, files: files))
    }

    /// Builds the database a restore installs. `storeFile(stagingURL, folder, ext)` re-encrypts a staged plaintext into
    /// the vault and returns its new relative path. Ids are kept, so links stay valid.
    public static func restoredDatabase(_ a: Archive, storeFile: (URL, MediaVault.Folder, String) throws -> String) throws -> VaultDatabase {
        guard !a.manifest.pack else { throw BackupError.packNotBackup }
        guard a.dump.media.count == a.manifest.media, a.dump.memories.count == a.manifest.memories else { throw BackupError.manifestMismatch }
        var db = VaultDatabase()
        var paths: [Int64: (String, String)] = [:]
        for row in a.dump.media {
            guard let plain = a.files["media/\(row.id).bin"] else { continue }
            let file = try storeFile(plain, .media, row.kind == "VIDEO" ? "mp4" : "jpg")
            let thumb = try a.files["thumbs/\(row.id).jpg"].map { try storeFile($0, .thumbs, "jpg") } ?? ""
            paths[row.id] = (file, thumb)
        }
        var voice: [Int64: String] = [:]
        for row in a.dump.memories where row.voice {
            if let plain = a.files["voice/\(row.id).m4a"] { voice[row.id] = try storeFile(plain, .voice, "m4a") }
        }
        let mediaIds = Set(paths.keys)
        db.persons = a.dump.persons.map { p in
            Person(id: p.id, name: p.name, nickname: p.nickname, relation: Relation(rawValue: p.relation) ?? .OTHER, birthdayEpochDay: p.birthdayEpochDay,
                   profileMediaId: p.profileMediaId.flatMap { mediaIds.contains($0) ? $0 : nil }, notes: p.notes,
                   avatarColorIndex: p.avatarColorIndex, createdAt: p.createdAt)
        }
        let members = Dictionary(grouping: a.dump.groupMembers, by: \.first)
        db.groups = a.dump.groups.map { g in
            Group(id: g.id, name: g.name, emoji: g.emoji, memberIds: (members[g.id] ?? []).map(\.second), createdAt: g.createdAt)
        }
        db.memories = a.dump.memories.map { memory(from: $0, voice: voice[$0.id], mediaIds: mediaIds) }
        db.media = a.dump.media.compactMap { r in paths[r.id].map { media(from: r, memoryId: r.memoryId, paths: $0) } }
        let personIds = Set(db.persons.map(\.id)), memoryIds = Set(db.memories.map(\.id))
        db.memoryPersons = a.dump.memoryPersons.filter { memoryIds.contains($0.first) && personIds.contains($0.second) }
        db.mediaPersons = a.dump.mediaPersons.filter { mediaIds.contains($0.first) && personIds.contains($0.second) }
        let maxId = ([0] + db.persons.map(\.id) + db.groups.map(\.id) + db.memories.map(\.id) + db.media.map(\.id)).max()!
        db.nextId = maxId + 1
        return db
    }

    static func memory(from mm: MemoryRow, voice: String?, mediaIds: Set<Int64>) -> Memory {
        Memory(id: mm.id, type: MemoryType(rawValue: mm.type) ?? .EVENT, title: mm.title, description: mm.description,
               date: MemoryDate(kind: DateKind(rawValue: mm.dateKind) ?? .EXACT, startEpochDay: mm.startEpochDay, endEpochDay: mm.endEpochDay, label: mm.fuzzyLabel),
               place: mm.place, mood: mm.mood.flatMap(Mood.init(rawValue:)), coverMediaId: mm.coverMediaId.flatMap { mediaIds.contains($0) ? $0 : nil },
               coverStyle: mm.coverStyle, voiceNotePath: voice, createdAt: mm.createdAt, updatedAt: mm.updatedAt)
    }

    static func media(from r: MediaRow, memoryId: Int64?, paths: (String, String)) -> Media {
        Media(id: r.id, memoryId: memoryId, kind: MediaKind(rawValue: r.kind) ?? .PHOTO, encryptedPath: paths.0, thumbPath: paths.1,
              takenAt: r.takenAt, durationMs: r.durationMs, sizeBytes: r.sizeBytes, caption: r.caption, width: r.width, height: r.height,
              sha256: r.sha256, position: r.position, createdAt: r.createdAt)
    }

    /// Adds a family pack to `database`: people matched by name (case and spaces ignored), everything else added with
    /// new ids. Returns the new database, how many memories were added, how many people were new and the first new memory.
    public static func merged(_ a: Archive, into database: VaultDatabase,
                              storeFile: (URL, MediaVault.Folder, String) throws -> String) throws -> (VaultDatabase, Int, Int, Int64?) {
        var db = database
        func newId() -> Int64 { defer { db.nextId += 1 }; return db.nextId }
        func norm(_ n: String) -> String {
            n.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
        }
        var personIds: [Int64: Int64] = [:]
        var newPeople = 0
        for p in a.dump.persons {
            if let existing = db.persons.first(where: { norm($0.name) == norm(p.name) }) {
                personIds[p.id] = existing.id
            } else {
                let id = newId()
                newPeople += 1
                db.persons.append(Person(id: id, name: p.name, nickname: p.nickname, relation: Relation(rawValue: p.relation) ?? .OTHER,
                                         birthdayEpochDay: p.birthdayEpochDay, notes: p.notes, avatarColorIndex: p.avatarColorIndex, createdAt: p.createdAt))
                personIds[p.id] = id
            }
        }
        var memoryIds: [Int64: Int64] = [:]
        var mediaIds: [Int64: Int64] = [:]
        var newMemories: [Memory] = []
        for mm in a.dump.memories {
            let id = newId()
            memoryIds[mm.id] = id
            let voice = try a.files["voice/\(mm.id).m4a"].map { try storeFile($0, .voice, "m4a") }
            var m = memory(from: mm, voice: mm.voice ? voice : nil, mediaIds: [])
            m.id = id
            newMemories.append(m)
        }
        for r in a.dump.media {
            guard let plain = a.files["media/\(r.id).bin"] else { continue }
            let file = try storeFile(plain, .media, r.kind == "VIDEO" ? "mp4" : "jpg")
            let thumb = try a.files["thumbs/\(r.id).jpg"].map { try storeFile($0, .thumbs, "jpg") } ?? ""
            var row = media(from: r, memoryId: r.memoryId.flatMap { memoryIds[$0] }, paths: (file, thumb))
            row.id = newId()
            mediaIds[r.id] = row.id
            db.media.append(row)
        }
        for (i, mm) in a.dump.memories.enumerated() { newMemories[i].coverMediaId = mm.coverMediaId.flatMap { mediaIds[$0] } }
        db.memories += newMemories
        var seen = Set(db.memoryPersons)
        for l in a.dump.memoryPersons {
            if let m = memoryIds[l.first], let p = personIds[l.second], seen.insert(Link(m, p)).inserted { db.memoryPersons.append(Link(m, p)) }
        }
        var seenMedia = Set(db.mediaPersons)
        for l in a.dump.mediaPersons {
            if let m = mediaIds[l.first], let p = personIds[l.second], seenMedia.insert(Link(m, p)).inserted { db.mediaPersons.append(Link(m, p)) }
        }
        return (db, a.dump.memories.count, newPeople, newMemories.first?.id)
    }
}
