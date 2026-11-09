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

    // MARK: - DailyStory (mirrors DailyStoryTest.kt)

    func photoCard(_ id: Int64) -> MemoryCard {
        MemoryCard(memory: Memory(id: id, type: .EVENT, title: "m\(id)", date: Dates.exact(.of(2020, 11, 9))), people: [],
                   cover: Media(id: id * 10, memoryId: id, kind: .PHOTO, encryptedPath: "", thumbPath: "", sizeBytes: 1, width: 1, height: 1, sha256: ""),
                   mediaCount: 1)
    }

    func testStoryPutsPhotosFirstAndCaps() {
        let cards = [exact(1, 2020, 11, 9), photoCard(2), exact(3, 2020, 11, 9), photoCard(4), exact(5, 2020, 11, 9), exact(6, 2020, 11, 9)]
        let rewind = RewindResult(scope: .DAY, items: cards.enumerated().map { RewindItem(card: $1, yearsAgo: $0 + 1) })
        let pages = DailyStory.build(rewind: rewind, spotlight: nil, memoryOfTheDay: nil)
        XCTAssertEqual(["m-2-2", "m-4-4", "m-1-1", "m-3-3"], pages.map(\.key))
    }

    func testStorySpotlightThenMemoryOfTheDayWithoutRepeats() {
        let spotlight = StoryPage.spotlight(person: Person(id: 7, name: "Nana"), memoryCount: 5, card: exact(9, 2020, 1, 1))
        let rewind = RewindResult(scope: .WEEK, items: [RewindItem(card: exact(1, 2024, 11, 10), yearsAgo: 2)])
        XCTAssertEqual(["m-1-2", "p-7", "m-8-day"], DailyStory.build(rewind: rewind, spotlight: spotlight, memoryOfTheDay: exact(8, 2020, 1, 1)).map(\.key))
        XCTAssertEqual(2, DailyStory.build(rewind: rewind, spotlight: spotlight, memoryOfTheDay: exact(1, 2024, 11, 10)).count)
        XCTAssertTrue(DailyStory.build(rewind: nil, spotlight: nil, memoryOfTheDay: nil).isEmpty)
    }

    func testSpotlightRotatesAmongPeopleWithEnoughMemories() {
        let people = [Person(id: 3, name: "C"), Person(id: 1, name: "A"), Person(id: 2, name: "B")]
        let counts: [Int64: Int] = [1: 5, 2: 1, 3: 2]
        let picks = (0..<4).map { DailyStory.spotlightPerson(people, memoryCounts: counts, today: today.plusDays(Int64($0)))!.id }
        XCTAssertTrue(picks.allSatisfy { $0 == 1 || $0 == 3 })
        XCTAssertNotEqual(picks[0], picks[1])
        XCTAssertNil(DailyStory.spotlightPerson(people, memoryCounts: [2: 1], today: today))
    }

    func testMemoryOfTheDayMatchesAndroid() {
        let ids = Array(Int64(1)...Int64(50))
        XCTAssertEqual([44, 49, 38], (0..<3).map { DailyStory.memoryOfTheDayId(ids, today: today.plusDays(Int64($0))) })
        XCTAssertEqual(DailyStory.memoryOfTheDayId(ids, today: today), DailyStory.memoryOfTheDayId(ids.shuffled(), today: today))
        XCTAssertNil(DailyStory.memoryOfTheDayId([], today: today))
    }
}
