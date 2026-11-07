import Foundation

/// A picked photo or video before import (PhotoGrouper.kt).
public struct PickedItem: Hashable, Sendable {
    public let key: String
    public let takenAtMillis: Int64?
    public let isVideo: Bool
    /// Face-cluster ids present in the item when face suggestions are on; empty otherwise.
    public let faces: Set<Int64>

    public init(key: String, takenAtMillis: Int64?, isVideo: Bool = false, faces: Set<Int64> = []) {
        self.key = key
        self.takenAtMillis = takenAtMillis
        self.isVideo = isVideo
        self.faces = faces
    }
}

public struct SuggestedGroup: Hashable, Sendable {
    public var items: [PickedItem]
    public var startDate: LocalDate?
    public var endDate: LocalDate?

    public init(items: [PickedItem], startDate: LocalDate?, endDate: LocalDate?) {
        self.items = items
        self.startDate = startDate
        self.endDate = endDate
    }

    public var isUndated: Bool { startDate == nil }
    public var isMultiDay: Bool { startDate != nil && endDate != nil && endDate != startDate }
}

/// Groups picked media into suggested memories by date gap (PhotoGrouper.kt):
/// same day together; consecutive days chain into one trip; a gap of more than `maxGapDays` starts a new memory; with
/// faces, a same-day run splits where the faces change completely after `faceSplitGapMinutes`. Undated items trail.
public enum PhotoGrouper {
    public static func group(_ items: [PickedItem], zone: TimeZone = .current, maxGapDays: Int64 = 1,
                             useFaces: Bool = false, faceSplitGapMinutes: Int64 = 180) -> [SuggestedGroup] {
        let dated = items.filter { $0.takenAtMillis != nil }
        let undated = items.filter { $0.takenAtMillis == nil }
        let sorted = dated.stableSorted { $0.takenAtMillis! < $1.takenAtMillis! }
        var groups: [[PickedItem]] = []
        var lastDate: LocalDate?
        var lastMillis: Int64 = 0
        for item in sorted {
            let millis = item.takenAtMillis!
            let date = LocalDate(epochMillis: millis, zone: zone)
            var startNew = false
            if groups.isEmpty || lastDate == nil {
                startNew = true
            } else if date.epochDay - lastDate!.epochDay > maxGapDays {
                startNew = true
            } else if useFaces, !item.faces.isEmpty, let prev = groups.last?.last, !prev.faces.isEmpty,
                      prev.faces.isDisjoint(with: item.faces), millis - lastMillis >= faceSplitGapMinutes * 60_000 {
                startNew = true
            }
            if startNew { groups.append([]) }
            groups[groups.count - 1].append(item)
            lastDate = date
            lastMillis = millis
        }
        let result = groups.map { g in
            SuggestedGroup(items: g, startDate: LocalDate(epochMillis: g.first!.takenAtMillis!, zone: zone),
                           endDate: LocalDate(epochMillis: g.last!.takenAtMillis!, zone: zone))
        }.stableSorted { $0.startDate! > $1.startDate! }
        return undated.isEmpty ? result : result + [SuggestedGroup(items: undated, startDate: nil, endDate: nil)]
    }

    /// Merges two groups (user tapped "merge").
    public static func merge(_ a: SuggestedGroup, _ b: SuggestedGroup) -> SuggestedGroup {
        let items = (a.items + b.items).stableSorted { ($0.takenAtMillis ?? Int64.max) < ($1.takenAtMillis ?? Int64.max) }
        let dates = [a.startDate, a.endDate, b.startDate, b.endDate].compactMap { $0 }
        return SuggestedGroup(items: items, startDate: dates.min(), endDate: dates.max())
    }

    /// Splits a group at `index` (items before the index stay, the rest become a new group).
    public static func split(_ group: SuggestedGroup, at index: Int, zone: TimeZone = .current) -> (SuggestedGroup, SuggestedGroup) {
        let cut = min(max(index, 1), group.items.count - 1)
        let first = Array(group.items.prefix(cut))
        let second = Array(group.items.dropFirst(first.count))
        func dates(_ list: [PickedItem]) -> (LocalDate?, LocalDate?) {
            let d = list.compactMap(\.takenAtMillis).map { LocalDate(epochMillis: $0, zone: zone) }
            return (d.min(), d.max())
        }
        let (fs, fe) = dates(first)
        let (ss, se) = dates(second)
        return (SuggestedGroup(items: first, startDate: fs, endDate: fe), SuggestedGroup(items: second, startDate: ss, endDate: se))
    }
}

/// How a bulk upload is split into memories.
public enum SplitBy: String, Sendable { case YEAR, MONTH }

/// Which photos of a bulk upload to keep, by the date they were taken (PeriodGrouper.kt).
public struct PeriodFilter: Hashable, Sendable {
    public var years: Set<Int>
    /// 1...12
    public var months: Set<Int>

    public init(years: Set<Int> = [], months: Set<Int> = []) {
        self.years = years
        self.months = months
    }

    public var isEmpty: Bool { years.isEmpty && months.isEmpty }

    public func matches(_ ym: YearMonth) -> Bool {
        (years.isEmpty || years.contains(ym.year)) && (months.isEmpty || months.contains(ym.month))
    }
}

/// One memory-to-be: every photo of `year` (split by year) or of `year`/`month` (split by month).
public struct PeriodBucket: Hashable, Sendable {
    public let year: Int
    /// Nil when split by year.
    public let month: Int?
    public let items: [PickedItem]

    public var yearMonth: YearMonth? { month.map { YearMonth(year: year, month: $0) } }
}

public struct PeriodSplit: Hashable, Sendable {
    /// Newest first; each bucket becomes one memory.
    public let buckets: [PeriodBucket]
    /// Items without a date: they cannot be placed in a year or month.
    public let undated: [PickedItem]
    /// Dated items left out by the filter.
    public let filteredOut: Int

    public var memoryCount: Int { buckets.count }
    public var keptCount: Int { buckets.reduce(0) { $0 + $1.items.count } }
}

public enum PeriodGrouper {
    public static func split(_ items: [PickedItem], splitBy: SplitBy, filter: PeriodFilter = PeriodFilter(),
                             zone: TimeZone = .current) -> PeriodSplit {
        let dated = items.filter { $0.takenAtMillis != nil }
        let undated = items.filter { $0.takenAtMillis == nil }
        var filteredOut = 0
        var keys: [YearMonth] = []
        var byKey: [YearMonth: [PickedItem]] = [:]
        for item in dated.stableSorted(by: { $0.takenAtMillis! < $1.takenAtMillis! }) {
            let ym = LocalDate(epochMillis: item.takenAtMillis!, zone: zone).yearMonth
            if !filter.matches(ym) {
                filteredOut += 1
                continue
            }
            let key = splitBy == .YEAR ? YearMonth(year: ym.year, month: 1) : ym
            if byKey[key] == nil { keys.append(key) }
            byKey[key, default: []].append(item)
        }
        let buckets = keys.sorted(by: >).map { key in
            PeriodBucket(year: key.year, month: splitBy == .MONTH ? key.month : nil, items: byKey[key]!)
        }
        return PeriodSplit(buckets: buckets, undated: undated, filteredOut: filteredOut)
    }

    /// Years present in `items`, newest first; used to offer the year filter.
    public static func years(_ items: [PickedItem], zone: TimeZone = .current) -> [Int] {
        var seen = Set<Int>()
        return items.compactMap(\.takenAtMillis).map { LocalDate(epochMillis: $0, zone: zone).year }
            .filter { seen.insert($0).inserted }
            .sorted(by: >)
    }
}
