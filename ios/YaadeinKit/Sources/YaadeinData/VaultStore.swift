import Foundation
import YaadeinDomain

/// A link row (memory ↔ person, media ↔ person, group ↔ person). Encoded as `{"first":a,"second":b}`, the way
/// kotlinx.serialization writes Kotlin `Pair`s in Android backups.
public struct Link: Hashable, Codable, Sendable {
    public var first: Int64
    public var second: Int64
    public init(_ first: Int64, _ second: Int64) {
        self.first = first
        self.second = second
    }
}

/// Every table of the vault. On iPhone the whole database is one AES-GCM sealed file (loaded into memory after unlock
/// and written atomically on every change), instead of SQLCipher on Android: same rows, same rules.
public struct VaultDatabase: Codable, Equatable, Sendable {
    public static let currentVersion = 1

    public var version = VaultDatabase.currentVersion
    public var nextId: Int64 = 1
    public var persons: [Person] = []
    public var groups: [Group] = []
    public var memories: [Memory] = []
    public var media: [Media] = []
    public var memoryPersons: [Link] = []
    public var mediaPersons: [Link] = []
    /// Reels kept in "Your reels" (ReelLibrary on Android). Not part of backups, as on Android.
    public var reels: [ReelEntry] = []

    public init() {}

    enum CodingKeys: String, CodingKey { case version, nextId, persons, groups, memories, media, memoryPersons, mediaPersons, reels }

    // Fields added later default when absent, so a database written by an older build always opens.
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        nextId = try c.decodeIfPresent(Int64.self, forKey: .nextId) ?? 1
        persons = try c.decodeIfPresent([Person].self, forKey: .persons) ?? []
        groups = try c.decodeIfPresent([Group].self, forKey: .groups) ?? []
        memories = try c.decodeIfPresent([Memory].self, forKey: .memories) ?? []
        media = try c.decodeIfPresent([Media].self, forKey: .media) ?? []
        memoryPersons = try c.decodeIfPresent([Link].self, forKey: .memoryPersons) ?? []
        mediaPersons = try c.decodeIfPresent([Link].self, forKey: .mediaPersons) ?? []
        reels = try c.decodeIfPresent([ReelEntry].self, forKey: .reels) ?? []
    }
}

/// A rendered reel: an encrypted MP4 in the vault (`reels/…`). `slot` is set for the automatic reels.
public struct ReelEntry: Hashable, Codable, Identifiable, Sendable {
    public var id: String
    public var title: String
    public var path: String
    public var createdAt: Int64
    public var durationMs: Int64
    public var slot: String?
    public var signature: String?

    public init(id: String = UUID().uuidString, title: String, path: String, createdAt: Int64, durationMs: Int64,
                slot: String? = nil, signature: String? = nil) {
        self.id = id
        self.title = title
        self.path = path
        self.createdAt = createdAt
        self.durationMs = durationMs
        self.slot = slot
        self.signature = signature
    }
}

/// Change notification payload: lets screens refresh only when something they show changed.
public enum VaultChange: Sendable { case memories, people, groups, all }

/// The repository layer (MemoryRepositoryImpl, PersonRepositoryImpl, GroupRepositoryImpl, MediaRepositoryImpl on
/// Android) over a `VaultDatabase`. Query results, ordering and search semantics follow the Android SQL exactly.
/// Every mutation is applied to a copy and persisted before it becomes visible, so a failed write changes nothing.
public final class VaultStore: @unchecked Sendable {
    private let lock = NSRecursiveLock()
    private var db: VaultDatabase
    private let persist: (VaultDatabase) throws -> Void
    private let clock: () -> Int64
    private var observers: [UUID: (VaultChange) -> Void] = [:]

    public init(database: VaultDatabase = VaultDatabase(), clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
                persist: @escaping (VaultDatabase) throws -> Void) {
        db = database
        self.clock = clock
        self.persist = persist
    }

    public var snapshot: VaultDatabase { lock.withLock { db } }

    @discardableResult
    public func observe(_ handler: @escaping (VaultChange) -> Void) -> UUID {
        let id = UUID()
        lock.withLock { observers[id] = handler }
        return id
    }

    public func removeObserver(_ id: UUID) { _ = lock.withLock { observers.removeValue(forKey: id) } }

    /// Runs `change` on a copy, persists it, then publishes it (one transaction).
    @discardableResult
    private func transaction<T>(_ kind: VaultChange, _ change: (inout VaultDatabase) throws -> T) throws -> T {
        let (result, handlers): (T, [(VaultChange) -> Void]) = try lock.withLock {
            var copy = db
            let r = try change(&copy)
            try persist(copy)
            db = copy
            return (r, Array(observers.values))
        }
        handlers.forEach { $0(kind) }
        return result
    }

    private static func newId(_ d: inout VaultDatabase) -> Int64 {
        defer { d.nextId += 1 }
        return d.nextId
    }

    /// Replaces everything (restore). Persisted first, like Android's single restore transaction.
    public func replaceAll(_ database: VaultDatabase) throws {
        try transaction(.all) { $0 = database }
    }

    // MARK: - Cards and details

    private func sortedMedia(_ d: VaultDatabase, memoryId: Int64) -> [Media] {
        d.media.filter { $0.memoryId == memoryId }.sorted {
            ($0.position, $0.takenAt ?? Int64.max, $0.id) < ($1.position, $1.takenAt ?? Int64.max, $1.id)
        }
    }

    private func people(_ d: VaultDatabase, memoryId: Int64) -> [Person] {
        let ids = Set(d.memoryPersons.filter { $0.first == memoryId }.map(\.second))
        return d.persons.filter { ids.contains($0.id) }.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    private func card(_ d: VaultDatabase, _ m: Memory) -> MemoryCard {
        let media = sortedMedia(d, memoryId: m.id)
        let cover = media.first { $0.id == m.coverMediaId } ?? media.first
        return MemoryCard(memory: m, people: people(d, memoryId: m.id), cover: cover, mediaCount: media.count)
    }

    private func detail(_ d: VaultDatabase, _ m: Memory) -> MemoryDetail {
        MemoryDetail(memory: m, people: people(d, memoryId: m.id), media: sortedMedia(d, memoryId: m.id))
    }

    private static func newestFirst(_ a: Memory, _ b: Memory) -> Bool {
        (a.date.startEpochDay, a.id) > (b.date.startEpochDay, b.id)
    }

    /// Timeline cards, newest first (`ORDER BY start_epoch_day DESC, id DESC`).
    public func cards(_ filter: TimelineFilter = TimelineFilter()) -> [MemoryCard] {
        lock.withLock {
            let d = db
            return matching(d, filter).sorted(by: VaultStore.newestFirst).map { card(d, $0) }
        }
    }

    public func ids(_ filter: TimelineFilter) -> [Int64] { cards(filter).map(\.memory.id) }

    private func matching(_ d: VaultDatabase, _ f: TimelineFilter) -> [Memory] {
        var result = d.memories
        if !f.personIds.isEmpty {
            result = result.filter { m in
                let tagged = Set(d.memoryPersons.filter { $0.first == m.id }.map(\.second))
                return f.requireAllPeople ? f.personIds.isSubset(of: tagged) : !f.personIds.isDisjoint(with: tagged)
            }
        }
        if let g = f.groupId {
            let members = Set(d.groups.first { $0.id == g }?.memberIds ?? [])
            result = result.filter { m in d.memoryPersons.contains { $0.first == m.id && members.contains($0.second) } }
        }
        if let place = f.place?.trimmingCharacters(in: .whitespaces), !place.isEmpty {
            result = result.filter { $0.place.range(of: place, options: .caseInsensitive) != nil }
        }
        if let from = f.fromEpochDay { result = result.filter { ($0.date.endEpochDay ?? $0.date.startEpochDay) >= from } }
        if let to = f.toEpochDay { result = result.filter { $0.date.startEpochDay <= to } }
        if !f.types.isEmpty { result = result.filter { f.types.contains($0.type) } }
        if let tokens = SearchIndex.queryTokens(f.query) {
            result = result.filter { SearchIndex.matches(tokens, body: searchBody(d, $0)) }
        }
        return result
    }

    /// Same text Android puts in the FTS row: title, description, place, fuzzy label, captions and tagged names.
    private func searchBody(_ d: VaultDatabase, _ m: Memory) -> String {
        let captions = d.media.filter { $0.memoryId == m.id }.map(\.caption)
        let names = people(d, memoryId: m.id).flatMap { [$0.name, $0.nickname].compactMap { $0 } }
        return ([m.title, m.description, m.place, m.date.label ?? ""] + captions + names)
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }.joined(separator: " ")
    }

    public func card(id: Int64) -> MemoryCard? {
        lock.withLock { db.memories.first { $0.id == id }.map { card(db, $0) } }
    }

    public func detail(id: Int64) -> MemoryDetail? {
        lock.withLock { db.memories.first { $0.id == id }.map { detail(db, $0) } }
    }

    public func details(ids: [Int64]) -> [MemoryDetail] {
        lock.withLock {
            let wanted = Set(ids)
            return db.memories.filter { wanted.contains($0.id) }.map { detail(db, $0) }
        }
    }

    public func memory(id: Int64) -> Memory? { lock.withLock { db.memories.first { $0.id == id } } }

    public var memoryCount: Int { lock.withLock { db.memories.count } }
    public var mediaCount: Int { lock.withLock { db.media.count } }

    /// "On this day": exact-date memories on `monthDay` (MMDD), optionally excluding a year, newest first.
    public func onThisDay(monthDay: Int, excludeYear: Int? = nil) -> [MemoryCard] {
        lock.withLock {
            db.memories.filter { m in
                guard m.date.kind == .EXACT else { return false }
                let d = LocalDate(epochDay: m.date.startEpochDay)
                return Dates.monthDayKey(d) == monthDay && d.year != excludeYear
            }.sorted { $0.date.startEpochDay > $1.date.startEpochDay }.map { card(db, $0) }
        }
    }

    public func random() -> MemoryCard? {
        lock.withLock { db.memories.randomElement().map { card(db, $0) } }
    }

    /// Year → number of memories, newest year first.
    public func countsByYear() -> [(year: Int, count: Int)] {
        lock.withLock {
            var counts: [Int: Int] = [:]
            for m in db.memories { counts[LocalDate(epochDay: m.date.startEpochDay).year, default: 0] += 1 }
            return counts.sorted { $0.key > $1.key }.map { ($0.key, $0.value) }
        }
    }

    /// Memories of `year` (optionally with `personId`), oldest first.
    public func cardsForYear(_ year: Int, personId: Int64? = nil) -> [MemoryCard] {
        lock.withLock {
            let from = LocalDate.of(year, 1, 1).epochDay, to = LocalDate.of(year, 12, 31).epochDay
            return db.memories.filter { m in
                (from...to).contains(m.date.startEpochDay) &&
                    (personId == nil || db.memoryPersons.contains { $0.first == m.id && $0.second == personId })
            }.sorted { ($0.date.startEpochDay, $0.id) < ($1.date.startEpochDay, $1.id) }.map { card(db, $0) }
        }
    }

    public func distinctPlaces() -> [String] {
        lock.withLock {
            var seen = Set<String>()
            return db.memories.map(\.place).filter { !$0.isEmpty && seen.insert($0).inserted }
                .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        }
    }

    /// Places with the most memories, most first (case-insensitive grouping).
    public func topPlaces(limit: Int = 12) -> [(place: String, count: Int)] {
        lock.withLock {
            var counts: [String: (String, Int)] = [:]
            for m in db.memories where !m.place.isEmpty {
                let key = m.place.lowercased()
                counts[key] = (counts[key]?.0 ?? m.place, (counts[key]?.1 ?? 0) + 1)
            }
            return counts.values.sorted { $0.1 != $1.1 ? $0.1 > $1.1 : $0.0.lowercased() < $1.0.lowercased() }
                .prefix(limit).map { ($0.0, $0.1) }
        }
    }

    public func search(_ query: String, limit: Int = 50) -> [MemoryCard] {
        guard SearchIndex.queryTokens(query) != nil else { return [] }
        return Array(cards(TimelineFilter(query: query)).prefix(limit))
    }

    // MARK: - Memory writes

    @discardableResult
    public func createMemory(_ memory: Memory, personIds: [Int64]) throws -> Int64 {
        try transaction(.memories) { d in
            let id = VaultStore.newId(&d)
            var m = memory
            let now = clock()
            m.id = id
            m.createdAt = now
            m.updatedAt = now
            m.place = m.place.trimmingCharacters(in: .whitespaces)
            d.memories.append(m)
            var seen = Set<Int64>()
            d.memoryPersons += personIds.filter { seen.insert($0).inserted }.map { Link(id, $0) }
            return id
        }
    }

    public func updateMemory(_ memory: Memory, personIds: [Int64]) throws {
        try transaction(.memories) { d in
            guard let i = d.memories.firstIndex(where: { $0.id == memory.id }) else { return }
            var m = memory
            m.createdAt = d.memories[i].createdAt
            m.updatedAt = clock()
            m.place = m.place.trimmingCharacters(in: .whitespaces)
            d.memories[i] = m
            d.memoryPersons.removeAll { $0.first == memory.id }
            var seen = Set<Int64>()
            d.memoryPersons += personIds.filter { seen.insert($0).inserted }.map { Link(memory.id, $0) }
        }
    }

    /// Deletes the memory and (cascade) its media rows; returns the vault paths whose files the caller deletes.
    @discardableResult
    public func deleteMemory(id: Int64) throws -> [String] {
        try transaction(.memories) { d in
            let media = d.media.filter { $0.memoryId == id }
            let mediaIds = Set(media.map(\.id))
            var paths = media.flatMap { [$0.encryptedPath, $0.thumbPath] }
            if let voice = d.memories.first(where: { $0.id == id })?.voiceNotePath { paths.append(voice) }
            d.memories.removeAll { $0.id == id }
            d.media.removeAll { mediaIds.contains($0.id) }
            d.memoryPersons.removeAll { $0.first == id }
            d.mediaPersons.removeAll { mediaIds.contains($0.first) }
            for i in d.persons.indices where d.persons[i].profileMediaId.map(mediaIds.contains) == true { d.persons[i].profileMediaId = nil }
            return paths
        }
    }

    public func setCover(memoryId: Int64, mediaId: Int64?) throws {
        try transaction(.memories) { d in
            guard let i = d.memories.firstIndex(where: { $0.id == memoryId }) else { return }
            d.memories[i].coverMediaId = mediaId
            d.memories[i].updatedAt = clock()
        }
    }

    public func setVoiceNote(memoryId: Int64, path: String?) throws {
        try transaction(.memories) { d in
            guard let i = d.memories.firstIndex(where: { $0.id == memoryId }) else { return }
            d.memories[i].voiceNotePath = path
            d.memories[i].updatedAt = clock()
        }
    }

    // MARK: - Media

    /// Adds an imported, encrypted and verified media row. The first photo of a memory becomes its cover, and a
    /// text-only card turns into an event (as MediaImporter does on Android).
    @discardableResult
    public func addMedia(_ media: Media) throws -> Int64 {
        try transaction(.memories) { d in
            let id = VaultStore.newId(&d)
            var row = media
            row.id = id
            row.createdAt = clock()
            if let memoryId = row.memoryId {
                row.position = (d.media.filter { $0.memoryId == memoryId }.map(\.position).max() ?? -1) + 1
                if let i = d.memories.firstIndex(where: { $0.id == memoryId }) {
                    if d.memories[i].coverMediaId == nil && row.kind == .PHOTO { d.memories[i].coverMediaId = id }
                    if d.memories[i].type == .TEXT_ONLY { d.memories[i].type = .EVENT }
                    d.memories[i].updatedAt = clock()
                }
            }
            d.media.append(row)
            return id
        }
    }

    public func media(id: Int64) -> Media? { lock.withLock { db.media.first { $0.id == id } } }

    public func media(forMemory memoryId: Int64) -> [Media] { lock.withLock { sortedMedia(db, memoryId: memoryId) } }

    /// Media where `personId` is tagged, newest first.
    public func media(forPerson personId: Int64) -> [Media] {
        lock.withLock {
            let ids = Set(db.mediaPersons.filter { $0.second == personId }.map(\.first))
            return db.media.filter { ids.contains($0.id) }.sorted { ($0.takenAt ?? 0) > ($1.takenAt ?? 0) }
        }
    }

    public func updateCaption(mediaId: Int64, caption: String) throws {
        try transaction(.memories) { d in
            if let i = d.media.firstIndex(where: { $0.id == mediaId }) { d.media[i].caption = caption }
        }
    }

    public func setMediaPeople(mediaId: Int64, personIds: [Int64]) throws {
        try transaction(.memories) { d in
            d.mediaPersons.removeAll { $0.first == mediaId }
            var seen = Set<Int64>()
            d.mediaPersons += personIds.filter { seen.insert($0).inserted }.map { Link(mediaId, $0) }
        }
    }

    public func mediaPeople(mediaId: Int64) -> [Int64] {
        lock.withLock { db.mediaPersons.filter { $0.first == mediaId }.map(\.second) }
    }

    /// Removes a media row; returns its vault paths for the caller to delete. A removed cover falls back to the next photo.
    @discardableResult
    public func deleteMedia(id: Int64) throws -> [String] {
        try transaction(.memories) { d in
            guard let row = d.media.first(where: { $0.id == id }) else { return [] }
            d.media.removeAll { $0.id == id }
            d.mediaPersons.removeAll { $0.first == id }
            if let memoryId = row.memoryId, let i = d.memories.firstIndex(where: { $0.id == memoryId }), d.memories[i].coverMediaId == id {
                d.memories[i].coverMediaId = d.media.filter { $0.memoryId == memoryId && $0.kind == .PHOTO }
                    .min { ($0.position, $0.id) < ($1.position, $1.id) }?.id
            }
            for i in d.persons.indices where d.persons[i].profileMediaId == id { d.persons[i].profileMediaId = nil }
            return [row.encryptedPath, row.thumbPath]
        }
    }

    public func reorderMedia(memoryId: Int64, orderedIds: [Int64]) throws {
        try transaction(.memories) { d in
            for (pos, id) in orderedIds.enumerated() {
                if let i = d.media.firstIndex(where: { $0.id == id && $0.memoryId == memoryId }) { d.media[i].position = pos }
            }
        }
    }

    public var totalMediaBytes: Int64 { lock.withLock { db.media.reduce(0) { $0 + $1.sizeBytes } } }

    // MARK: - People

    /// Everyone, by name (`ORDER BY name COLLATE NOCASE`).
    public func persons() -> [Person] {
        lock.withLock { db.persons.sorted { $0.name.lowercased() < $1.name.lowercased() } }
    }

    public func person(id: Int64) -> Person? { lock.withLock { db.persons.first { $0.id == id } } }

    @discardableResult
    public func upsertPerson(_ person: Person) throws -> Int64 {
        try transaction(.people) { d in
            if person.id != 0, let i = d.persons.firstIndex(where: { $0.id == person.id }) {
                d.persons[i] = person
                return person.id
            }
            var p = person
            p.id = VaultStore.newId(&d)
            p.createdAt = clock()
            d.persons.append(p)
            return p.id
        }
    }

    public func deletePerson(id: Int64) throws {
        try transaction(.people) { d in
            d.persons.removeAll { $0.id == id }
            d.memoryPersons.removeAll { $0.second == id }
            d.mediaPersons.removeAll { $0.second == id }
            for i in d.groups.indices { d.groups[i].memberIds.removeAll { $0 == id } }
        }
    }

    public func story(personId: Int64, today: LocalDate) -> PersonStory? {
        lock.withLock {
            guard let person = db.persons.first(where: { $0.id == personId }) else { return nil }
            let memoryIds = Set(db.memoryPersons.filter { $0.second == personId }.map(\.first))
            let memories = db.memories.filter { memoryIds.contains($0.id) }
                .sorted { ($0.date.startEpochDay, $0.id) < ($1.date.startEpochDay, $1.id) }
            return PersonStory(
                person: person,
                memoryCount: db.memoryPersons.filter { $0.second == personId }.count,
                mediaCount: db.mediaPersons.filter { $0.second == personId }.count,
                firstMemory: memories.first,
                lastMemory: memories.last,
                groups: db.groups.filter { $0.memberIds.contains(personId) }.sorted { $0.name.lowercased() < $1.name.lowercased() },
                daysToBirthday: person.birthdayEpochDay.map { Dates.daysUntilBirthday($0, today: today) })
        }
    }

    /// Person id → number of memories they are in.
    public func memoryCounts() -> [Int64: Int] {
        lock.withLock {
            var counts: [Int64: Int] = [:]
            for l in db.memoryPersons { counts[l.second, default: 0] += 1 }
            return counts
        }
    }

    /// Pairs of people with the number of memories they share, most first (relationships map).
    public func coAppearances() -> [(a: Int64, b: Int64, count: Int)] {
        lock.withLock {
            var byMemory: [Int64: [Int64]] = [:]
            for l in db.memoryPersons { byMemory[l.first, default: []].append(l.second) }
            var counts: [Link: Int] = [:]
            for people in byMemory.values {
                let sorted = Array(Set(people)).sorted()
                for i in sorted.indices { for j in sorted.indices where j > i { counts[Link(sorted[i], sorted[j]), default: 0] += 1 } }
            }
            return counts.sorted { $0.value != $1.value ? $0.value > $1.value : ($0.key.first, $0.key.second) < ($1.key.first, $1.key.second) }
                .map { ($0.key.first, $0.key.second, $0.value) }
        }
    }

    // MARK: - Reels

    /// Newest 10 reels made by the user are kept (MAX_USER_REELS); one per automatic slot.
    public static let maxUserReels = 10

    public func reels() -> [ReelEntry] { lock.withLock { db.reels.sorted { $0.createdAt > $1.createdAt } } }

    /// Adds a reel and returns the vault paths of reels it pushed out (the caller deletes those files).
    @discardableResult
    public func addReel(_ reel: ReelEntry) throws -> [String] {
        try transaction(.all) { d in
            var dropped: [String] = []
            if let slot = reel.slot {
                dropped += d.reels.filter { $0.slot == slot }.map(\.path)
                d.reels.removeAll { $0.slot == slot }
            }
            d.reels.append(reel)
            let mine = d.reels.filter { $0.slot == nil }.sorted { $0.createdAt > $1.createdAt }
            if mine.count > VaultStore.maxUserReels {
                let old = Set(mine.dropFirst(VaultStore.maxUserReels).map(\.id))
                dropped += d.reels.filter { old.contains($0.id) }.map(\.path)
                d.reels.removeAll { old.contains($0.id) }
            }
            return dropped
        }
    }

    @discardableResult
    public func deleteReel(id: String) throws -> String? {
        try transaction(.all) { d in
            let path = d.reels.first { $0.id == id }?.path
            d.reels.removeAll { $0.id == id }
            return path
        }
    }

    // MARK: - Groups

    public func groups() -> [Group] {
        lock.withLock { db.groups.sorted { $0.name.lowercased() < $1.name.lowercased() } }
    }

    public func group(id: Int64) -> Group? { lock.withLock { db.groups.first { $0.id == id } } }

    @discardableResult
    public func upsertGroup(_ group: Group) throws -> Int64 {
        try transaction(.groups) { d in
            if group.id != 0, let i = d.groups.firstIndex(where: { $0.id == group.id }) {
                d.groups[i] = group
                return group.id
            }
            var g = group
            g.id = VaultStore.newId(&d)
            g.createdAt = clock()
            d.groups.append(g)
            return g.id
        }
    }

    public func deleteGroup(id: Int64) throws {
        try transaction(.groups) { d in d.groups.removeAll { $0.id == id } }
    }
}

/// FTS4 `unicode61` prefix search as Android does it: "goa tr" → every token must start a word of the memory's text.
public enum SearchIndex {
    private static func words(_ text: String) -> [String] {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: nil)
            .split(whereSeparator: { !($0.isLetter || $0.isNumber) }).map(String.init)
    }

    /// Tokens of the query, dropping one-letter fragments unless that is all there is. Nil for blank input.
    public static func queryTokens(_ text: String) -> [String]? {
        let all = words(text)
        let long = all.filter { $0.count > 1 }
        let tokens = long.isEmpty ? all : long
        return tokens.isEmpty ? nil : tokens
    }

    public static func matches(_ tokens: [String], body: String) -> Bool {
        let w = words(body)
        return tokens.allSatisfy { t in w.contains { $0.hasPrefix(t) } }
    }
}
