import Foundation

// Swift twins of core/domain/model/Models.kt. Raw values match the Kotlin enum names, which are what the Android
// database and backups store, so a backup made on one platform restores on the other.

public enum Relation: String, Codable, CaseIterable, Sendable { case FAMILY, FRIEND, COLLEAGUE, OTHER }

public enum MemoryType: String, Codable, CaseIterable, Sendable { case EVENT, QUICK, TEXT_ONLY }

/// How precisely the user knows when a memory happened.
public enum DateKind: String, Codable, CaseIterable, Sendable { case EXACT, MONTH, YEAR, FUZZY }

public enum Mood: String, Codable, CaseIterable, Sendable { case JOYFUL, CALM, NOSTALGIC, PROUD, FUNNY, SAD, CELEBRATION }

public enum MediaKind: String, Codable, CaseIterable, Sendable { case PHOTO, VIDEO }

public struct Person: Hashable, Codable, Identifiable, Sendable {
    public var id: Int64
    public var name: String
    public var nickname: String?
    public var relation: Relation
    /// Birthday as epoch day. Nil when unknown.
    public var birthdayEpochDay: Int64?
    public var profileMediaId: Int64?
    public var notes: String
    /// Index into the avatar palette so a person's colour is stable everywhere.
    public var avatarColorIndex: Int
    public var createdAt: Int64

    public init(id: Int64 = 0, name: String, nickname: String? = nil, relation: Relation = .FAMILY,
                birthdayEpochDay: Int64? = nil, profileMediaId: Int64? = nil, notes: String = "",
                avatarColorIndex: Int = 0, createdAt: Int64 = 0) {
        self.id = id
        self.name = name
        self.nickname = nickname
        self.relation = relation
        self.birthdayEpochDay = birthdayEpochDay
        self.profileMediaId = profileMediaId
        self.notes = notes
        self.avatarColorIndex = avatarColorIndex
        self.createdAt = createdAt
    }

    public var displayName: String {
        if let n = nickname, !n.trimmingCharacters(in: .whitespaces).isEmpty { return n }
        return name
    }

    public var initials: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: { $0.isWhitespace })
            .prefix(2)
            .compactMap { $0.first.map { String($0).uppercased() } }
            .joined()
    }
}

public struct Group: Hashable, Codable, Identifiable, Sendable {
    public var id: Int64
    public var name: String
    public var emoji: String
    public var memberIds: [Int64]
    public var createdAt: Int64

    public init(id: Int64 = 0, name: String, emoji: String = "👨‍👩‍👧", memberIds: [Int64] = [], createdAt: Int64 = 0) {
        self.id = id
        self.name = name
        self.emoji = emoji
        self.memberIds = memberIds
        self.createdAt = createdAt
    }
}

/// When the memory happened.
/// - EXACT: `startEpochDay` (and optional `endEpochDay` for multi-day events)
/// - MONTH: `startEpochDay` is the first of the month
/// - YEAR: `startEpochDay` is 1 January
/// - FUZZY: `startEpochDay` is an anchor day and `label` describes it ("Summer 2015")
public struct MemoryDate: Hashable, Codable, Sendable {
    public var kind: DateKind
    public var startEpochDay: Int64
    public var endEpochDay: Int64?
    public var label: String?

    public init(kind: DateKind, startEpochDay: Int64, endEpochDay: Int64? = nil, label: String? = nil) {
        self.kind = kind
        self.startEpochDay = startEpochDay
        self.endEpochDay = endEpochDay
        self.label = label
    }
}

public struct Memory: Hashable, Codable, Identifiable, Sendable {
    public var id: Int64
    public var type: MemoryType
    public var title: String
    public var description: String
    public var date: MemoryDate
    public var place: String
    public var mood: Mood?
    public var coverMediaId: Int64?
    /// Gradient / illustration key used when there is no cover photo (see `CoverStyle`).
    public var coverStyle: String
    public var voiceNotePath: String?
    public var createdAt: Int64
    public var updatedAt: Int64

    public init(id: Int64 = 0, type: MemoryType, title: String, description: String = "", date: MemoryDate,
                place: String = "", mood: Mood? = nil, coverMediaId: Int64? = nil, coverStyle: String = CoverStyle.defaultStyle,
                voiceNotePath: String? = nil, createdAt: Int64 = 0, updatedAt: Int64 = 0) {
        self.id = id
        self.type = type
        self.title = title
        self.description = description
        self.date = date
        self.place = place
        self.mood = mood
        self.coverMediaId = coverMediaId
        self.coverStyle = coverStyle
        self.voiceNotePath = voiceNotePath
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public struct Media: Hashable, Codable, Identifiable, Sendable {
    public var id: Int64
    public var memoryId: Int64?
    public var kind: MediaKind
    public var encryptedPath: String
    public var thumbPath: String
    public var takenAt: Int64?
    public var durationMs: Int64?
    public var sizeBytes: Int64
    public var caption: String
    public var width: Int
    public var height: Int
    public var sha256: String
    public var position: Int
    public var createdAt: Int64

    public init(id: Int64 = 0, memoryId: Int64? = nil, kind: MediaKind, encryptedPath: String, thumbPath: String,
                takenAt: Int64? = nil, durationMs: Int64? = nil, sizeBytes: Int64, caption: String = "", width: Int,
                height: Int, sha256: String, position: Int = 0, createdAt: Int64 = 0) {
        self.id = id
        self.memoryId = memoryId
        self.kind = kind
        self.encryptedPath = encryptedPath
        self.thumbPath = thumbPath
        self.takenAt = takenAt
        self.durationMs = durationMs
        self.sizeBytes = sizeBytes
        self.caption = caption
        self.width = width
        self.height = height
        self.sha256 = sha256
        self.position = position
        self.createdAt = createdAt
    }
}

/// A memory together with everything the timeline card needs.
public struct MemoryCard: Hashable, Identifiable, Sendable {
    public var memory: Memory
    public var people: [Person]
    public var cover: Media?
    public var mediaCount: Int
    public var id: Int64 { memory.id }

    public init(memory: Memory, people: [Person], cover: Media?, mediaCount: Int) {
        self.memory = memory
        self.people = people
        self.cover = cover
        self.mediaCount = mediaCount
    }
}

/// A memory with all of its related data for the detail screen.
public struct MemoryDetail: Hashable, Identifiable, Sendable {
    public var memory: Memory
    public var people: [Person]
    public var media: [Media]
    public var id: Int64 { memory.id }

    public init(memory: Memory, people: [Person], media: [Media]) {
        self.memory = memory
        self.people = people
        self.media = media
    }
}

public struct PersonStory: Hashable, Sendable {
    public var person: Person
    public var memoryCount: Int
    public var mediaCount: Int
    public var firstMemory: Memory?
    public var lastMemory: Memory?
    public var groups: [Group]
    /// Days until the next birthday, nil when unknown.
    public var daysToBirthday: Int?

    public init(person: Person, memoryCount: Int, mediaCount: Int, firstMemory: Memory?, lastMemory: Memory?,
                groups: [Group], daysToBirthday: Int?) {
        self.person = person
        self.memoryCount = memoryCount
        self.mediaCount = mediaCount
        self.firstMemory = firstMemory
        self.lastMemory = lastMemory
        self.groups = groups
        self.daysToBirthday = daysToBirthday
    }
}

/// Named cover styles for text-only memories. Keys are stable (stored in the database and in backups).
public enum CoverStyle {
    public static let defaultStyle = "plain_plum"
    public static let all = [
        "plain_plum", "plain_amber", "plain_teal", "plain_rose", "plain_indigo",
        "beach", "mountain", "travel", "wedding", "birthday", "diwali", "holi", "festival",
        "school", "college", "home", "food", "baby", "music", "sports", "rain", "winter",
    ]
}

/// Filters applied to the timeline (`TimelineFilter` in Repositories.kt). Empty filter = everything.
public struct TimelineFilter: Hashable, Sendable {
    public var personIds: Set<Int64>
    /// When true, every person in `personIds` must appear (Together view).
    public var requireAllPeople: Bool
    public var groupId: Int64?
    public var place: String?
    public var fromEpochDay: Int64?
    public var toEpochDay: Int64?
    public var types: Set<MemoryType>
    public var query: String

    public init(personIds: Set<Int64> = [], requireAllPeople: Bool = false, groupId: Int64? = nil, place: String? = nil,
                fromEpochDay: Int64? = nil, toEpochDay: Int64? = nil, types: Set<MemoryType> = [], query: String = "") {
        self.personIds = personIds
        self.requireAllPeople = requireAllPeople
        self.groupId = groupId
        self.place = place
        self.fromEpochDay = fromEpochDay
        self.toEpochDay = toEpochDay
        self.types = types
        self.query = query
    }

    public var isEmpty: Bool {
        personIds.isEmpty && groupId == nil && (place ?? "").trimmingCharacters(in: .whitespaces).isEmpty &&
            fromEpochDay == nil && toEpochDay == nil && types.isEmpty && query.trimmingCharacters(in: .whitespaces).isEmpty
    }
}
