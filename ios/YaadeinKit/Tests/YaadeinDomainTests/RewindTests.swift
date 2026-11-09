import XCTest
@testable import YaadeinDomain

/// Mirrors RewindTest.kt.
final class RewindTests: XCTestCase {
    let today = LocalDate.of(2026, 11, 9)

    func card(_ id: Int64, _ date: MemoryDate) -> MemoryCard {
        MemoryCard(memory: Memory(id: id, type: .EVENT, title: "m\(id)", date: date), people: [], cover: nil, mediaCount: 0)
    }
    func exact(_ id: Int64, _ y: Int, _ m: Int, _ d: Int) -> MemoryCard { card(id, Dates.exact(.of(y, m, d))) }

    func testSameDayWinsOverWeekAndMonth() {
        let r = Rewind.pick([exact(1, 2020, 11, 9), exact(2, 2023, 11, 10), exact(3, 2024, 11, 25)], today: today)!
        XCTAssertEqual(.DAY, r.scope)
        XCTAssertEqual([1], r.items.map(\.card.memory.id))
        XCTAssertEqual(6, r.items[0].yearsAgo)
    }

    func testThisYearIsNeverOnThisDay() {
        XCTAssertNil(Rewind.pick([exact(1, 2026, 11, 9), exact(2, 2026, 11, 2)], today: today))
    }

    func testFallsBackToTheSameWeekNewestFirst() {
        let r = Rewind.pick([exact(1, 2019, 11, 6), exact(2, 2024, 11, 12), exact(3, 2024, 11, 13), exact(4, 2022, 11, 20)], today: today)!
        XCTAssertEqual(.WEEK, r.scope)
        XCTAssertEqual([2, 1], r.items.map(\.card.memory.id))
        XCTAssertEqual([2, 7], r.items.map(\.yearsAgo))
    }

    func testWeekCrossesTheNewYear() {
        let r = Rewind.pick([exact(1, 2023, 12, 30)], today: .of(2026, 1, 2))!
        XCTAssertEqual(.WEEK, r.scope)
        XCTAssertEqual(2, r.items[0].yearsAgo)
        XCTAssertNil(Rewind.pick([exact(1, 2025, 12, 30)], today: .of(2026, 1, 2)))
    }

    func testFallsBackToTheMonthIncludingMonthOnlyDates() {
        let r = Rewind.pick([exact(1, 2021, 11, 28), card(2, Dates.month(YearMonth(year: 2018, month: 11))), card(3, Dates.year(2015))], today: today)!
        XCTAssertEqual(.MONTH, r.scope)
        XCTAssertEqual([1, 2], r.items.map(\.card.memory.id))
    }

    func testYearOnlyAndFuzzyDatesNeverMatch() {
        XCTAssertNil(Rewind.pick([card(1, Dates.year(2015)), card(2, Dates.fuzzy("Summer", anchorYear: 2018, anchorMonth: 11))], today: today))
    }

    func testLeapDayShowsOn28February() {
        XCTAssertEqual(.DAY, Rewind.pick([exact(1, 2020, 2, 29)], today: .of(2027, 2, 28))?.scope)
    }

    func testWindows() {
        let w = Rewind.windows(today: .of(2026, 11, 29), earliestYear: 2024)
        XCTAssertEqual(2, w.count)
        XCTAssertEqual(LocalDate.of(2025, 11, 1).epochDay, w[0].lowerBound)
        XCTAssertEqual(LocalDate.of(2025, 12, 2).epochDay, w[0].upperBound)
        XCTAssertTrue(Rewind.windows(today: today, earliestYear: 2026).isEmpty)
    }

    func testResultsAreCapped() {
        let cards = (1...30).map { exact(Int64($0), 2000 + $0 % 20, 11, 9) }
        XCTAssertEqual(Rewind.maxItems, Rewind.pick(cards, today: today)!.items.count)
    }

    func testCoversPreferARowWithMedia() {
        let picks = Covers.pick([
            (2025, CoverPick(memoryId: 1, mediaId: nil, coverStyle: "beach")),
            (2025, CoverPick(memoryId: 2, mediaId: 20, coverStyle: "plain_plum")),
            (2025, CoverPick(memoryId: 3, mediaId: 30, coverStyle: "plain_plum")),
            (2024, CoverPick(memoryId: 4, mediaId: nil, coverStyle: "rain")),
        ])
        XCTAssertEqual(20, picks[2025]?.mediaId)
        XCTAssertEqual(CoverPick(memoryId: 4, mediaId: nil, coverStyle: "rain"), picks[2024])
    }
}
