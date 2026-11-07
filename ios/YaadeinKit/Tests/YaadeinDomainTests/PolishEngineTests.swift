import XCTest
@testable import YaadeinDomain

/// Mirrors PolishEngineTest.kt: the same line gives the same card on Android and iPhone.
final class PolishEngineTests: XCTestCase {
    let today = LocalDate.of(2026, 11, 5)
    func polish(_ s: String) -> PolishResult { PolishEngine.polish(s, today: today, locale: Locale(identifier: "en")) }

    func testGoaTrip2024BecomesAGetawayWithAYear() {
        let r = polish("goa trip 2024")
        XCTAssertEqual("Goa Getaway, 2024", r.title)
        XCTAssertEqual("beach", r.coverStyle)
        XCTAssertEqual("Goa", r.place)
        XCTAssertEqual(.YEAR, r.date.kind)
        XCTAssertEqual(2024, LocalDate(epochDay: r.date.startEpochDay).year)
        XCTAssertTrue(r.description.contains("Goa Getaway in 2024"))
        XCTAssertEqual(.JOYFUL, r.mood)
    }

    func testMonthAndYearGiveAMonthOnlyDate() {
        let r = polish("Goa trip dec 2024")
        XCTAssertEqual("Goa Getaway, December 2024", r.title)
        XCTAssertEqual(.MONTH, r.date.kind)
    }

    func testHinglishDiwaliAtHomeIn2019() {
        let r = polish("diwali ghar pe 2019")
        XCTAssertEqual("diwali", r.coverStyle)
        XCTAssertEqual("Diwali Ghar, 2019", r.title)
        XCTAssertEqual(.CELEBRATION, r.mood)
    }

    func testSeasonBecomesAFuzzyLabel() {
        let r = polish("summer 2015 mahabaleshwar")
        XCTAssertEqual(.FUZZY, r.date.kind)
        XCTAssertEqual("Summer 2015", r.date.label)
        XCTAssertEqual("Mahabaleshwar", r.place)
        XCTAssertEqual("mountain", r.coverStyle)
        XCTAssertEqual("Mahabaleshwar, Summer 2015", r.title)
    }

    func testExactNumericDateIsParsed() {
        let r = polish("priya ki shaadi 25/11/2022")
        XCTAssertEqual(.EXACT, r.date.kind)
        XCTAssertEqual(LocalDate.of(2022, 11, 25).epochDay, r.date.startEpochDay)
        XCTAssertEqual("wedding", r.coverStyle)
        XCTAssertTrue(r.title.hasPrefix("Priya Shaadi"))
    }

    func testTodayAndYesterday() {
        XCTAssertEqual(today.epochDay, polish("chai with aai today").date.startEpochDay)
        XCTAssertEqual(today.minusDays(1).epochDay, polish("cricket match yesterday").date.startEpochDay)
        XCTAssertEqual("sports", polish("cricket match yesterday").coverStyle)
    }

    func testNoDateMeansTodayAndNoSuffix() {
        let r = polish("rainy evening bhajiyas")
        XCTAssertFalse(r.dateDetected)
        XCTAssertEqual("Rainy Evening Bhajiyas", r.title)
        XCTAssertEqual("rain", r.coverStyle)
        XCTAssertEqual(today.epochDay, r.date.startEpochDay)
    }

    func testMonthWithoutYearAssumesTheMostRecentOccurrence() {
        let r = polish("trek in march")
        XCTAssertEqual(.MONTH, r.date.kind)
        XCTAssertEqual(LocalDate.of(2026, 3, 1).epochDay, r.date.startEpochDay)
        XCTAssertEqual(LocalDate.of(2025, 12, 1).epochDay, polish("trek in december").date.startEpochDay)
    }

    func testUnknownTextGetsAPlainCoverAndAWarmDescription() {
        let r = polish("random thoughts")
        XCTAssertTrue(r.coverStyle.hasPrefix("plain_"))
        XCTAssertFalse(r.description.isEmpty)
        XCTAssertEqual("Random Thoughts", r.title)
    }

    func testPlaceAfterInIsDetected() {
        let r = polish("lunch in Nagpur with cousins 2021")
        XCTAssertEqual("Nagpur", r.place)
        XCTAssertEqual("food", r.coverStyle)
    }

    func testIsDeterministic() {
        XCTAssertEqual(polish("goa trip 2024"), polish("goa trip 2024"))
    }
}
