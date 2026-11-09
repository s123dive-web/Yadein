import Foundation

/// Human label for a memory date (`DateLabel` in Dates.kt).
public enum DateLabel: Hashable, Sendable {
    case exact(String)
    case month(String)
    case year(String)
    case fuzzy(String)

    public var text: String {
        switch self {
        case let .exact(t), let .month(t), let .year(t), let .fuzzy(t): return t
        }
    }
}

/// Pure date helpers (Dates.kt). Everything on the timeline is keyed by epoch day so exact, month-only, year-only and
/// fuzzy memories interleave in a sensible order.
public enum Dates {
    public static func today() -> LocalDate { LocalDate.today() }
    public static func todayEpochDay() -> Int64 { today().epochDay }

    public static func exact(_ date: LocalDate, end: LocalDate? = nil) -> MemoryDate {
        MemoryDate(kind: .EXACT, startEpochDay: date.epochDay,
                   endEpochDay: end.flatMap { $0.isAfter(date) ? $0.epochDay : nil })
    }

    public static func month(_ ym: YearMonth) -> MemoryDate {
        MemoryDate(kind: .MONTH, startEpochDay: ym.atDay(1).epochDay, endEpochDay: ym.atEndOfMonth.epochDay)
    }

    public static func year(_ year: Int) -> MemoryDate {
        MemoryDate(kind: .YEAR, startEpochDay: LocalDate.of(year, 1, 1).epochDay, endEpochDay: LocalDate.of(year, 12, 31).epochDay)
    }

    /// A fuzzy label such as "Summer 2015" anchored to the season's first day.
    public static func fuzzy(_ label: String, anchorYear: Int, anchorMonth: Int = 1) -> MemoryDate {
        MemoryDate(kind: .FUZZY, startEpochDay: LocalDate.of(anchorYear, min(max(anchorMonth, 1), 12), 1).epochDay,
                   endEpochDay: nil, label: label.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    public static func start(_ date: MemoryDate) -> LocalDate { LocalDate(epochDay: date.startEpochDay) }
    public static func year(of date: MemoryDate) -> Int { start(date).year }

    private static func format(_ date: LocalDate, _ pattern: String, _ locale: Locale) -> String {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = TimeZone(identifier: "UTC")
        f.locale = locale
        f.dateFormat = pattern
        return f.string(from: date.date())
    }

    /// Same patterns as Android: "d MMM yyyy", "d MMM" and "MMMM yyyy".
    public static func label(_ date: MemoryDate, locale: Locale = .current) -> DateLabel {
        let s = start(date)
        switch date.kind {
        case .EXACT:
            guard let endDay = date.endEpochDay, endDay != date.startEpochDay else {
                return .exact(format(s, "d MMM yyyy", locale))
            }
            let end = LocalDate(epochDay: endDay)
            if end.year == s.year {
                return .exact(format(s, "d MMM", locale) + " – " + format(end, "d MMM yyyy", locale))
            }
            return .exact(format(s, "d MMM yyyy", locale) + " – " + format(end, "d MMM yyyy", locale))
        case .MONTH:
            return .month(format(s, "MMMM yyyy", locale))
        case .YEAR:
            return .year(String(s.year))
        case .FUZZY:
            return .fuzzy(date.label ?? String(s.year))
        }
    }

    /// Stand-alone month name ("December"), localised.
    public static func monthName(_ month: Int, locale: Locale = .current) -> String {
        format(LocalDate.of(2000, month, 1), "LLLL", locale)
    }

    public static func daysUntilBirthday(_ birthdayEpochDay: Int64, today: LocalDate) -> Int {
        let birthday = LocalDate(epochDay: birthdayEpochDay)
        var next = birthday.withYear(today.year)
        if next.isBefore(today) { next = next.plusYears(1) }
        return Int(next.epochDay - today.epochDay)
    }

    public static func age(_ birthdayEpochDay: Int64, today: LocalDate) -> Int {
        let birthday = LocalDate(epochDay: birthdayEpochDay)
        var age = today.year - birthday.year
        if today.withYear(birthday.year).isBefore(birthday) { age -= 1 }
        return age
    }

    /// Month-day key (MMDD) used by "On this day".
    public static func monthDayKey(_ date: LocalDate) -> Int { date.month * 100 + date.day }

    public static func isValidYear(_ year: Int) -> Bool { (1900...(LocalDate.today().year + 1)).contains(year) }
}

/// Escalating delay after wrong PIN attempts (LockoutPolicy.kt).
/// Attempts 1–4: no delay. 5th wrong: 30 s, then 1 m, 5 m, 15 m, 1 h for every further failure.
public enum LockoutPolicy {
    public static let freeAttempts = 4
    public static let wipeAt = 10
    private static let delaysMs: [Int64] = [30_000, 60_000, 300_000, 900_000, 3_600_000]

    public static func delayAfterFailures(_ failures: Int) -> Int64 {
        if failures <= freeAttempts { return 0 }
        let index = min(max(failures - freeAttempts - 1, 0), delaysMs.count - 1)
        return delaysMs[index]
    }

    public static func shouldWipe(_ failures: Int, wipeEnabled: Bool) -> Bool { wipeEnabled && failures >= wipeAt }

    public static func attemptsBeforeWipe(_ failures: Int) -> Int { max(wipeAt - failures, 0) }
}
