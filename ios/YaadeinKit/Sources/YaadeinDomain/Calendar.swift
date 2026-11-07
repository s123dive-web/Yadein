import Foundation

/// Time-zone free calendar date, the Swift twin of `java.time.LocalDate`. Android stores every timeline date as an
/// epoch day (days since 1970-01-01), so the iPhone app does the same and the two can exchange backups.
public struct LocalDate: Hashable, Comparable, Codable, CustomStringConvertible, Sendable {
    public let year: Int
    public let month: Int
    public let day: Int

    /// Fails like `LocalDate.of` for impossible dates (31 February, month 13...).
    public init?(_ year: Int, _ month: Int, _ day: Int) {
        guard (1...12).contains(month), day >= 1, day <= LocalDate.lengthOfMonth(year, month) else { return nil }
        self.year = year
        self.month = month
        self.day = day
    }

    /// For literals that are known to be valid.
    public static func of(_ year: Int, _ month: Int, _ day: Int) -> LocalDate {
        guard let d = LocalDate(year, month, day) else { preconditionFailure("Invalid date \(year)-\(month)-\(day)") }
        return d
    }

    /// Howard Hinnant's days-from-civil algorithm (same result as `LocalDate.toEpochDay`).
    public var epochDay: Int64 {
        let y = Int64(month <= 2 ? year - 1 : year)
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let m = Int64(month)
        let doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + Int64(day) - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    public init(epochDay: Int64) {
        let z = epochDay + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let doe = z - era * 146_097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365
        let y = yoe + era * 400
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        self.year = Int(m <= 2 ? y + 1 : y)
        self.month = Int(m)
        self.day = Int(d)
    }

    /// The local date of an instant (epoch millis) in [zone], like `Instant.atZone(zone).toLocalDate()`.
    public init(epochMillis: Int64, zone: TimeZone) {
        let seconds = Double(epochMillis) / 1000
        let offset = Int64(zone.secondsFromGMT(for: Date(timeIntervalSince1970: seconds)))
        let localSeconds = Int64((seconds).rounded(.down)) + offset
        self.init(epochDay: localSeconds >= 0 ? localSeconds / 86_400 : (localSeconds - 86_399) / 86_400)
    }

    public static func today(in zone: TimeZone = .current) -> LocalDate {
        LocalDate(epochMillis: Int64(Date().timeIntervalSince1970 * 1000), zone: zone)
    }

    public static func isLeap(_ year: Int) -> Bool { (year % 4 == 0 && year % 100 != 0) || year % 400 == 0 }

    public static func lengthOfMonth(_ year: Int, _ month: Int) -> Int {
        switch month {
        case 2: return isLeap(year) ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }

    public func plusDays(_ n: Int64) -> LocalDate { LocalDate(epochDay: epochDay + n) }
    public func minusDays(_ n: Int64) -> LocalDate { plusDays(-n) }
    public func plusWeeks(_ n: Int64) -> LocalDate { plusDays(n * 7) }

    /// Like `withYear`: 29 February becomes 28 February in a non-leap year.
    public func withYear(_ y: Int) -> LocalDate {
        LocalDate(y, month, day) ?? LocalDate.of(y, month, LocalDate.lengthOfMonth(y, month))
    }

    public func plusYears(_ n: Int) -> LocalDate { withYear(year + n) }

    /// ISO day of week: 1 = Monday ... 7 = Sunday.
    public var dayOfWeek: Int {
        let r = (epochDay + 3) % 7
        return Int(r < 0 ? r + 7 : r) + 1
    }

    public var dayOfYear: Int { Int(epochDay - LocalDate.of(year, 1, 1).epochDay) + 1 }

    /// ISO-8601 week-based year and week (`IsoFields.WEEK_BASED_YEAR`, `WEEK_OF_WEEK_BASED_YEAR`).
    public var isoWeek: (year: Int, week: Int) {
        let thursday = plusDays(Int64(4 - dayOfWeek))
        return (thursday.year, (thursday.dayOfYear - 1) / 7 + 1)
    }

    public var yearMonth: YearMonth { YearMonth(year: year, month: month) }

    public func isBefore(_ other: LocalDate) -> Bool { self < other }
    public func isAfter(_ other: LocalDate) -> Bool { self > other }

    public static func < (a: LocalDate, b: LocalDate) -> Bool { a.epochDay < b.epochDay }

    public var description: String { String(format: "%04d-%02d-%02d", year, month, day) }

    /// Midnight of this date in [zone] as a `Date`, for formatting with Foundation.
    public func date(in zone: TimeZone = TimeZone(identifier: "UTC")!) -> Date {
        let utcMidnight = Double(epochDay) * 86_400
        let offset = Double(zone.secondsFromGMT(for: Date(timeIntervalSince1970: utcMidnight)))
        return Date(timeIntervalSince1970: utcMidnight - offset)
    }
}

/// `java.time.YearMonth`.
public struct YearMonth: Hashable, Comparable, Codable, Sendable {
    public let year: Int
    /// 1...12
    public let month: Int

    public init(year: Int, month: Int) {
        precondition((1...12).contains(month))
        self.year = year
        self.month = month
    }

    public func atDay(_ day: Int) -> LocalDate { LocalDate.of(year, month, day) }
    public var atEndOfMonth: LocalDate { LocalDate.of(year, month, LocalDate.lengthOfMonth(year, month)) }
    public func minusYears(_ n: Int) -> YearMonth { YearMonth(year: year - n, month: month) }

    public static func < (a: YearMonth, b: YearMonth) -> Bool { (a.year, a.month) < (b.year, b.month) }
}

/// English month names as `java.time.Month` prints them (FULL / SHORT, `Locale.ENGLISH`).
public enum Months {
    public static let full = ["January", "February", "March", "April", "May", "June", "July", "August", "September",
                              "October", "November", "December"]
    public static let short = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
}

// MARK: - JVM compatibility helpers

extension String {
    /// `java.lang.String.hashCode()`: Android uses it to pick cover colours, descriptions and reel signatures, so the
    /// iPhone app must compute the same value to give the same result for the same text.
    public var javaHashCode: Int32 {
        var h: Int32 = 0
        for unit in utf16 { h = 31 &* h &+ Int32(unit) }
        return h
    }
}

/// `kotlin.math.abs(Int)` (which leaves Int.MIN_VALUE negative) followed by a non-negative modulo.
func kotlinAbsMod(_ value: Int32, _ n: Int) -> Int {
    let a = value == Int32.min ? Int(value) : Int(abs(value))
    let r = a % n
    return r < 0 ? r + n : r
}

/// `kotlin.random.Random(seed: Long)`: the XorWow generator Kotlin uses, so seeded shuffles pick the same photos on
/// both platforms.
public struct KotlinRandom {
    private var x: Int32, y: Int32, z: Int32, w: Int32, v: Int32, addend: Int32

    public init(seed: Int64) {
        let seed1 = Int32(truncatingIfNeeded: seed)
        let seed2 = Int32(truncatingIfNeeded: seed >> 32)
        x = seed1
        y = seed2
        z = 0
        w = 0
        v = ~seed1
        addend = (seed1 << 10) ^ Int32(bitPattern: UInt32(bitPattern: seed2) >> 4)
        for _ in 0..<64 { _ = nextInt() }
    }

    public mutating func nextInt() -> Int32 {
        var t = x
        t = t ^ Int32(bitPattern: UInt32(bitPattern: t) >> 2)
        x = y
        y = z
        z = w
        let v0 = v
        w = v0
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
        v = t
        addend = addend &+ 362_437
        return t &+ addend
    }

    private mutating func nextBits(_ bitCount: Int) -> Int32 {
        let r = UInt32(bitPattern: nextInt()) >> UInt32(32 - bitCount)
        return bitCount == 0 ? 0 : Int32(bitPattern: r)
    }

    /// `Random.nextInt(until)`.
    public mutating func nextInt(until n: Int32) -> Int32 {
        precondition(n > 0)
        if n & -n == n {
            let bitCount = 31 - n.leadingZeroBitCount
            return nextBits(bitCount)
        }
        var v: Int32
        var bits: Int32
        repeat {
            bits = Int32(bitPattern: UInt32(bitPattern: nextInt()) >> 1)
            v = bits % n
        } while bits &- v &+ (n - 1) < 0
        return v
    }
}

extension Array {
    /// `List.shuffled(Random(seed))` from the Kotlin standard library.
    public func kotlinShuffled(seed: Int64) -> [Element] {
        var random = KotlinRandom(seed: seed)
        var list = self
        var i = list.count - 1
        while i >= 1 {
            let j = Int(random.nextInt(until: Int32(i + 1)))
            list.swapAt(i, j)
            i -= 1
        }
        return list
    }

    /// A sort that keeps equal elements in their original order (Kotlin's `sortedBy` is stable).
    func stableSorted(by areInIncreasingOrder: (Element, Element) -> Bool) -> [Element] {
        enumerated().sorted { a, b in
            if areInIncreasingOrder(a.element, b.element) { return true }
            if areInIncreasingOrder(b.element, a.element) { return false }
            return a.offset < b.offset
        }.map(\.element)
    }
}

/// Thin wrapper over NSRegularExpression (ICU, the same dialect as java.util.regex for everything used here).
struct Rx {
    let regex: NSRegularExpression

    init(_ pattern: String, caseInsensitive: Bool = false) {
        // Patterns are literals in this module; a bad one is a programming error caught by the tests.
        regex = try! NSRegularExpression(pattern: pattern, options: caseInsensitive ? [.caseInsensitive] : [])
    }

    /// First match: the whole match followed by each group ("" for a group that did not take part).
    func find(_ s: String) -> [String]? {
        let ns = s as NSString
        guard let m = regex.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return (0..<m.numberOfRanges).map { i in
            let r = m.range(at: i)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
    }

    func matchesEntire(_ s: String) -> Bool {
        let ns = s as NSString
        guard let m = regex.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return false }
        return m.range.location == 0 && m.range.length == ns.length
    }

    func replaceAll(_ s: String, with template: String) -> String {
        let ns = s as NSString
        return regex.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: ns.length), withTemplate: template)
    }

    func split(_ s: String) -> [String] {
        let ns = s as NSString
        var parts: [String] = []
        var last = 0
        for m in regex.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
            parts.append(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
            last = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: last))
        return parts
    }
}
