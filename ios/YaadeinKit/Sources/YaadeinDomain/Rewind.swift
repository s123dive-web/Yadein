import Foundation

/// How close to today's date the memories in a `RewindResult` are (Rewind.kt).
public enum RewindScope: String, Sendable { case DAY, WEEK, MONTH }

public struct RewindItem: Hashable, Sendable {
    public let card: MemoryCard
    public let yearsAgo: Int
    public init(card: MemoryCard, yearsAgo: Int) { self.card = card; self.yearsAgo = yearsAgo }
}

/// What Relive shows under "On this day": the closest scope that has anything, newest first.
public struct RewindResult: Hashable, Sendable {
    public let scope: RewindScope
    public let items: [RewindItem]
    public init(scope: RewindScope, items: [RewindItem]) { self.scope = scope; self.items = items }
}

/// "On this day" that is rarely empty (port of Rewind.kt): the exact day first, then the same week (±3 days) in earlier
/// years, then the same month (exact and month-only dates). Fuzzy and year-only dates are left out.
public enum Rewind {
    public static let weekDays = 3
    public static let maxItems = 12
    public static let maxYearsBack = 120

    /// Epoch-day ranges, one per earlier year, covering everything `pick` could return.
    public static func windows(today: LocalDate, earliestYear: Int) -> [ClosedRange<Int64>] {
        let first = max(earliestYear, today.year - maxYearsBack)
        guard first <= today.year - 1 else { return [] }
        return stride(from: today.year - 1, through: first, by: -1).map { year in
            let month = YearMonth(year: year, month: today.month)
            let anchor = today.withYear(year)
            let from = min(month.atDay(1), anchor.minusDays(Int64(weekDays))).epochDay
            let to = max(month.atEndOfMonth, anchor.plusDays(Int64(weekDays))).epochDay
            return from...to
        }
    }

    public static func pick(_ cards: [MemoryCard], today: LocalDate) -> RewindResult? {
        var day: [RewindItem] = [], week: [RewindItem] = [], month: [RewindItem] = []
        for card in cards {
            let date = LocalDate(epochDay: card.memory.date.startEpochDay)
            let yearsAgo = today.year - date.year
            switch card.memory.date.kind {
            case .EXACT:
                if yearsAgo >= 1 && isSameDay(date, today) { day.append(RewindItem(card: card, yearsAgo: yearsAgo)) }
                if let w = weekYearsAgo(date, today) { week.append(RewindItem(card: card, yearsAgo: w)) }
                if yearsAgo >= 1 && date.month == today.month { month.append(RewindItem(card: card, yearsAgo: yearsAgo)) }
            case .MONTH:
                if yearsAgo >= 1 && date.month == today.month { month.append(RewindItem(card: card, yearsAgo: yearsAgo)) }
            case .YEAR, .FUZZY:
                break
            }
        }
        let scope: RewindScope, items: [RewindItem]
        if !day.isEmpty { (scope, items) = (.DAY, day) }
        else if !week.isEmpty { (scope, items) = (.WEEK, week) }
        else if !month.isEmpty { (scope, items) = (.MONTH, month) }
        else { return nil }
        var seen = Set<Int64>()
        let sorted = items.filter { seen.insert($0.card.memory.id).inserted }.sorted { a, b in
            if a.yearsAgo != b.yearsAgo { return a.yearsAgo < b.yearsAgo }
            if a.card.memory.date.startEpochDay != b.card.memory.date.startEpochDay { return a.card.memory.date.startEpochDay > b.card.memory.date.startEpochDay }
            return a.card.memory.id > b.card.memory.id
        }
        return RewindResult(scope: scope, items: Array(sorted.prefix(maxItems)))
    }

    private static func isSameDay(_ date: LocalDate, _ today: LocalDate) -> Bool {
        guard date.month == today.month else { return false }
        if date.day == today.day { return true }
        return date.month == 2 && date.day == 29 && today.day == 28 && !LocalDate.isLeap(today.year)
    }

    private static func weekYearsAgo(_ date: LocalDate, _ today: LocalDate) -> Int? {
        for offset in -1...1 {
            let anniversary = date.withYear(today.year + offset)
            if abs(anniversary.epochDay - today.epochDay) <= Int64(weekDays) {
                let yearsAgo = today.year + offset - date.year
                return yearsAgo >= 1 ? yearsAgo : nil
            }
        }
        return nil
    }
}

/// A memory standing for a group (a year or a place): its cover media, or its cover style when it has none.
public struct CoverPick: Hashable, Sendable {
    public let memoryId: Int64
    public let mediaId: Int64?
    public let coverStyle: String
    public init(memoryId: Int64, mediaId: Int64?, coverStyle: String) { self.memoryId = memoryId; self.mediaId = mediaId; self.coverStyle = coverStyle }
}

public enum Covers {
    /// One cover per key from rows ordered newest first: the first row with media, else the first row.
    public static func pick<K: Hashable>(_ rows: [(K, CoverPick)]) -> [K: CoverPick] {
        var result: [K: CoverPick] = [:]
        for (key, cover) in rows {
            if let current = result[key], current.mediaId != nil || cover.mediaId == nil { continue }
            result[key] = cover
        }
        return result
    }
}
