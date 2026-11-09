import XCTest
@testable import YaadeinDomain

/// Mirrors core/domain/src/test/.../DatesTest.kt and LockoutPolicyTest.kt.
final class DatesTests: XCTestCase {
    let en = Locale(identifier: "en")

    func testYearOnlyMemoryAnchorsTo1January() {
        let d = Dates.year(2015)
        XCTAssertEqual(LocalDate.of(2015, 1, 1), Dates.start(d))
        XCTAssertEqual(.YEAR, d.kind)
        XCTAssertEqual("2015", Dates.label(d, locale: en).text)
    }

    func testMonthOnlyMemoryAnchorsToTheFirstOfTheMonth() {
        let d = Dates.month(YearMonth(year: 2024, month: 12))
        XCTAssertEqual(LocalDate.of(2024, 12, 1), Dates.start(d))
        XCTAssertEqual("December 2024", Dates.label(d, locale: en).text)
    }

    func testFuzzyLabelIsPreserved() {
        let d = Dates.fuzzy("Summer 2015", anchorYear: 2015, anchorMonth: 5)
        XCTAssertEqual("Summer 2015", Dates.label(d, locale: en).text)
        XCTAssertEqual(LocalDate.of(2015, 5, 1), Dates.start(d))
    }

    func testExactRangeFormatsCompactlyInTheSameYear() {
        let d = Dates.exact(LocalDate.of(2024, 12, 20), end: LocalDate.of(2024, 12, 24))
        XCTAssertEqual("20 Dec – 24 Dec 2024", Dates.label(d, locale: en).text)
    }

    func testExactEndBeforeStartIsIgnored() {
        XCTAssertNil(Dates.exact(LocalDate.of(2024, 12, 20), end: LocalDate.of(2024, 12, 1)).endEpochDay)
    }

    func testDaysUntilBirthdayWrapsToNextYear() {
        let birthday = LocalDate.of(1990, 3, 10).epochDay
        XCTAssertEqual(0, Dates.daysUntilBirthday(birthday, today: LocalDate.of(2026, 3, 10)))
        XCTAssertEqual(364, Dates.daysUntilBirthday(birthday, today: LocalDate.of(2026, 3, 11)))
        XCTAssertEqual(9, Dates.daysUntilBirthday(birthday, today: LocalDate.of(2026, 3, 1)))
    }

    func testAgeCountsCompletedYears() {
        let birthday = LocalDate.of(1990, 3, 10).epochDay
        XCTAssertEqual(35, Dates.age(birthday, today: LocalDate.of(2026, 3, 9)))
        XCTAssertEqual(36, Dates.age(birthday, today: LocalDate.of(2026, 3, 10)))
    }

    // Epoch days must equal java.time.LocalDate.toEpochDay() so backups exchange dates exactly.
    func testEpochDaysMatchJavaTime() {
        XCTAssertEqual(0, LocalDate.of(1970, 1, 1).epochDay)
        XCTAssertEqual(20_089, LocalDate.of(2025, 1, 1).epochDay)
        XCTAssertEqual(-1, LocalDate.of(1969, 12, 31).epochDay)
        XCTAssertEqual(11_016, LocalDate.of(2000, 2, 29).epochDay)
        for day in Int64(-50_000)...Int64(60_000) where day % 37 == 0 {
            XCTAssertEqual(day, LocalDate(epochDay: day).epochDay)
        }
        XCTAssertEqual(4, LocalDate.of(1970, 1, 1).dayOfWeek) // Thursday
        XCTAssertEqual(7, LocalDate.of(2026, 11, 8).dayOfWeek) // Sunday
        XCTAssertEqual(53, LocalDate.of(2026, 12, 31).isoWeek.week)
        XCTAssertEqual(2026, LocalDate.of(2027, 1, 1).isoWeek.year) // 1 Jan 2027 belongs to 2026-W53
        XCTAssertEqual(1, LocalDate.of(2024, 12, 30).isoWeek.week) // ...and 30 Dec 2024 to 2025-W01
    }

    func testFirstFourFailuresHaveNoDelay() {
        (0...4).forEach { XCTAssertEqual(0, LockoutPolicy.delayAfterFailures($0)) }
    }

    func testFifthFailureStartsTheEscalation() {
        XCTAssertEqual(30_000, LockoutPolicy.delayAfterFailures(5))
        XCTAssertEqual(60_000, LockoutPolicy.delayAfterFailures(6))
        XCTAssertEqual(300_000, LockoutPolicy.delayAfterFailures(7))
        XCTAssertEqual(900_000, LockoutPolicy.delayAfterFailures(8))
        XCTAssertEqual(3_600_000, LockoutPolicy.delayAfterFailures(9))
        XCTAssertEqual(3_600_000, LockoutPolicy.delayAfterFailures(40))
    }

    func testWipeOnlyWhenEnabledAndAtTen() {
        XCTAssertFalse(LockoutPolicy.shouldWipe(10, wipeEnabled: false))
        XCTAssertFalse(LockoutPolicy.shouldWipe(9, wipeEnabled: true))
        XCTAssertTrue(LockoutPolicy.shouldWipe(10, wipeEnabled: true))
    }

    // Expected values were printed by the JVM (String.hashCode) and kotlin-stdlib (kotlin.random.Random, shuffled).
    func testJvmCompatibility() {
        XCTAssertEqual(Int32(625_209_652), "goa trip 2024".javaHashCode)
        XCTAssertEqual(Int32(0), "".javaHashCode)
        XCTAssertEqual(Int32(96_354), "abc".javaHashCode)
        XCTAssertEqual("d841670b", String(UInt32(bitPattern: "PERSON:1:101,102".javaHashCode), radix: 16))
        var r = KotlinRandom(seed: 42)
        XCTAssertEqual([972_016_666, 1_740_578_880, -408_207_414, -112_774_692, 1_162_768_683], (0..<5).map { _ in r.nextInt() })
        var r2 = KotlinRandom(seed: 38)
        XCTAssertEqual([0, 5, 9, 4, 5, 9], (0..<6).map { _ in r2.nextInt(until: 10) })
        XCTAssertEqual([7, 6, 5, 9, 3, 4, 0, 8, 1, 2], Array(0..<10).kotlinShuffled(seed: 34))
    }
}
