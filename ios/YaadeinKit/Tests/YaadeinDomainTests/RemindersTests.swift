import XCTest
@testable import YaadeinDomain

/// Mirrors RemindersTest.kt.
final class RemindersTests: XCTestCase {
    func card(_ id: Int64, _ title: String, _ date: LocalDate, exact: Bool = true) -> MemoryCard {
        MemoryCard(memory: Memory(id: id, type: .EVENT, title: title, date: exact ? Dates.exact(date) : Dates.year(date.year)),
                   people: [], cover: nil, mediaCount: 0)
    }

    func testOnThisDayFindsEarlierYearsOnlyMostRecentFirst() {
        let cards = [card(1, "Goa", .of(2021, 11, 7)), card(2, "Diwali", .of(2024, 11, 7)), card(3, "Today", .of(2026, 11, 7)),
                     card(4, "Other day", .of(2024, 11, 8)), card(5, "Just a year", .of(2020, 11, 7), exact: false)]
        let digest = Reminders.build(cards: cards, people: [], nowMillis: 0)
        let matches = Reminders.onThisDay(digest, today: .of(2026, 11, 7))
        XCTAssertEqual(["Diwali", "Goa"], matches.map(\.memory.title))
        XCTAssertEqual([2, 5], matches.map(\.yearsAgo))
    }

    func testAtMostThreeMemoriesAreKeptPerCalendarDay() {
        let cards = (2010...2020).map { card(Int64($0), "Y\($0)", .of($0, 3, 1)) }
        XCTAssertEqual(["Y2020", "Y2019", "Y2018"], Reminders.build(cards: cards, people: [], nowMillis: 0).memories.map(\.title))
    }

    func testBirthdaysTodayAndTomorrowWithAge() {
        let people = [Person(id: 1, name: "Rahul", birthdayEpochDay: LocalDate.of(1990, 11, 8).epochDay),
                      Person(id: 2, name: "Aai", birthdayEpochDay: LocalDate.of(1960, 11, 7).epochDay),
                      Person(id: 3, name: "Far", birthdayEpochDay: LocalDate.of(1990, 12, 25).epochDay),
                      Person(id: 4, name: "Unknown")]
        let digest = Reminders.build(cards: [], people: people, nowMillis: 0)
        let upcoming = Reminders.birthdays(digest, today: .of(2026, 11, 7), daysAhead: 1)
        XCTAssertEqual(["Aai", "Rahul"], upcoming.map(\.person.name))
        XCTAssertEqual([0, 1], upcoming.map(\.inDays))
        XCTAssertEqual(66, upcoming.first?.turning)
    }

    func testA29FebruaryBirthdayFallsOn28FebruaryInOtherYears() {
        XCTAssertEqual(LocalDate.of(2027, 2, 28), Reminders.nextBirthday(born: .of(2000, 2, 29), today: .of(2027, 1, 1)))
        XCTAssertEqual(LocalDate.of(2028, 2, 29), Reminders.nextBirthday(born: .of(2000, 2, 29), today: .of(2028, 1, 1)))
    }

    func testWeeklyPromptIsDeterministicAndUsuallyAboutFamily() {
        let people = [Person(id: 1, name: "Aaji", relation: .FAMILY), Person(id: 2, name: "Neha", relation: .FRIEND)]
        let digest = Reminders.build(cards: [], people: people, nowMillis: 0)
        let sunday = LocalDate.of(2026, 11, 8)
        XCTAssertTrue(Reminders.isPromptDay(sunday))
        XCTAssertEqual(Reminders.weeklyPrompt(digest, today: sunday), Reminders.weeklyPrompt(digest, today: sunday))
        let picks = (0..<8).map { Reminders.weeklyPrompt(digest, today: sunday.plusWeeks(Int64($0))) }
        XCTAssertGreaterThanOrEqual(picks.filter { $0.person?.relation == .FAMILY }.count, 4)
        XCTAssertTrue(picks.contains { $0.person == nil })
        XCTAssertTrue(picks.allSatisfy { $0.index >= 0 })
    }

    func testWithNobodyAddedPromptsAreAboutTheUser() {
        XCTAssertNil(Reminders.weeklyPrompt(Reminders.build(cards: [], people: [], nowMillis: 0), today: .of(2026, 11, 8)).person)
    }
}
