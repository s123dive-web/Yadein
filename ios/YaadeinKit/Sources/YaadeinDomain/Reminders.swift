import Foundation

/// What reminders and the widget need, kept outside the PIN-locked vault (Reminders.kt). Titles and first names only.
public struct ReminderDigest: Hashable, Codable, Sendable {
    public let generatedAtMillis: Int64
    public let memories: [DigestMemory]
    public let people: [DigestPerson]
}

public struct DigestMemory: Hashable, Codable, Sendable {
    public let id: Int64
    public let title: String
    public let epochDay: Int64
}

public struct DigestPerson: Hashable, Codable, Sendable {
    public let id: Int64
    public let name: String
    public let relation: Relation
    public let birthdayEpochDay: Int64?
}

public struct OnThisDayMatch: Hashable, Sendable {
    public let memory: DigestMemory
    public let yearsAgo: Int
}

public struct UpcomingBirthday: Hashable, Sendable {
    public let person: DigestPerson
    public let inDays: Int
    public let turning: Int?
}

/// A story prompt: `index` into the prompt list for the person's relation, about `person` (nil = about the user).
public struct PromptPick: Hashable, Sendable {
    public let index: Int
    public let person: DigestPerson?
}

public enum Reminders {
    /// Memories kept per calendar day; the notification shows one, the widget a few.
    public static let maxPerDay = 3
    /// Prompt texts per relation (string arrays `prompts_family/friend/self`). Sizes must match the resources.
    public static let familyPrompts = 12
    public static let friendPrompts = 6
    public static let selfPrompts = 8

    public static func build(cards: [MemoryCard], people: [Person], nowMillis: Int64) -> ReminderDigest {
        var order: [Int] = []
        var byDay: [Int: [MemoryCard]] = [:]
        for c in cards where c.memory.date.kind == .EXACT {
            let key = Dates.monthDayKey(LocalDate(epochDay: c.memory.date.startEpochDay))
            if byDay[key] == nil { order.append(key) }
            byDay[key, default: []].append(c)
        }
        let memories = order.flatMap { key in
            byDay[key]!.stableSorted { $0.memory.date.startEpochDay > $1.memory.date.startEpochDay }.prefix(maxPerDay)
        }.map { DigestMemory(id: $0.memory.id, title: $0.memory.title, epochDay: $0.memory.date.startEpochDay) }
        let persons = people.map { DigestPerson(id: $0.id, name: $0.displayName, relation: $0.relation, birthdayEpochDay: $0.birthdayEpochDay) }
        return ReminderDigest(generatedAtMillis: nowMillis, memories: memories, people: persons)
    }

    /// Exact-day memories from this calendar day in earlier years, most recent first.
    public static func onThisDay(_ digest: ReminderDigest, today: LocalDate) -> [OnThisDayMatch] {
        digest.memories.compactMap { m -> OnThisDayMatch? in
            let d = LocalDate(epochDay: m.epochDay)
            guard d.month == today.month, d.day == today.day, d.year < today.year else { return nil }
            return OnThisDayMatch(memory: m, yearsAgo: today.year - d.year)
        }.stableSorted { $0.yearsAgo < $1.yearsAgo }
    }

    /// Birthdays today (0) up to `daysAhead` days away. 29 February birthdays fall on 28 February in other years.
    public static func birthdays(_ digest: ReminderDigest, today: LocalDate, daysAhead: Int) -> [UpcomingBirthday] {
        digest.people.compactMap { p -> UpcomingBirthday? in
            guard let b = p.birthdayEpochDay else { return nil }
            let born = LocalDate(epochDay: b)
            let next = nextBirthday(born: born, today: today)
            let inDays = Int(next.epochDay - today.epochDay)
            guard (0...daysAhead).contains(inDays) else { return nil }
            let turning = next.year - born.year
            return UpcomingBirthday(person: p, inDays: inDays, turning: born.year > 1900 && turning > 0 ? turning : nil)
        }.stableSorted { $0.inDays < $1.inDays }
    }

    public static func nextBirthday(born: LocalDate, today: LocalDate) -> LocalDate {
        func inYear(_ y: Int) -> LocalDate {
            (born.month == 2 && born.day == 29 && !LocalDate.isLeap(y)) ? LocalDate.of(y, 2, 28) : born.withYear(y)
        }
        let thisYear = inYear(today.year)
        return thisYear.isBefore(today) ? inYear(today.year + 1) : thisYear
    }

    /// Prompts go out on Sundays.
    public static func isPromptDay(_ today: LocalDate) -> Bool { today.dayOfWeek == 7 }

    /// The prompt for `today`'s week: family first, then friends, then the user's own life. Deterministic.
    public static func weeklyPrompt(_ digest: ReminderDigest, today: LocalDate) -> PromptPick {
        let iso = today.isoWeek
        let week = iso.week + iso.year * 53
        let family = digest.people.filter { $0.relation == .FAMILY }.stableSorted { $0.id < $1.id }
        let friends = digest.people.filter { $0.relation == .FRIEND }.stableSorted { $0.id < $1.id }
        let slot = week % 4
        if slot != 3 && !family.isEmpty && (slot != 2 || friends.isEmpty) {
            return PromptPick(index: (week / 4) % familyPrompts, person: family[(week / 4 + slot) % family.count])
        }
        if slot == 2 && !friends.isEmpty {
            return PromptPick(index: (week / 4) % friendPrompts, person: friends[(week / 4) % friends.count])
        }
        return PromptPick(index: (week / 4) % selfPrompts, person: nil)
    }
}
