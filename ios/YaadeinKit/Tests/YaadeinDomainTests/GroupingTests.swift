import XCTest
@testable import YaadeinDomain

/// Mirrors PhotoGrouperTest.kt and PeriodGrouperTest.kt.
final class GroupingTests: XCTestCase {
    let utc = TimeZone(identifier: "UTC")!

    func at(_ y: Int, _ m: Int, _ d: Int = 1, _ h: Int = 12, faces: Set<Int64> = []) -> PickedItem {
        PickedItem(key: "\(y)-\(m)-\(d)-\(h)", takenAtMillis: (LocalDate.of(y, m, d).epochDay * 86_400 + Int64(h) * 3600) * 1000, faces: faces)
    }

    func testSameDayAndConsecutiveDaysGroupTogetherGapsSplit() {
        let items = [at(2024, 12, 20, 9), at(2024, 12, 20, 18), at(2024, 12, 21, 10), at(2024, 12, 22, 12),
                     at(2024, 10, 31, 19), at(2024, 10, 31, 21), at(2024, 9, 9, 17)]
        let groups = PhotoGrouper.group(items.shuffled(), zone: utc)
        XCTAssertEqual(3, groups.count)
        XCTAssertEqual(LocalDate.of(2024, 12, 20), groups[0].startDate)
        XCTAssertEqual(LocalDate.of(2024, 12, 22), groups[0].endDate)
        XCTAssertTrue(groups[0].isMultiDay)
        XCTAssertEqual(4, groups[0].items.count)
        XCTAssertEqual(LocalDate.of(2024, 10, 31), groups[1].startDate)
        XCTAssertEqual(2, groups[1].items.count)
        XCTAssertEqual(1, groups[2].items.count)
    }

    func testUndatedItemsFormATrailingGroup() {
        let groups = PhotoGrouper.group([at(2024, 1, 1, 9), PickedItem(key: "x", takenAtMillis: nil), PickedItem(key: "y", takenAtMillis: nil)], zone: utc)
        XCTAssertEqual(2, groups.count)
        XCTAssertTrue(groups.last!.isUndated)
        XCTAssertEqual(2, groups.last!.items.count)
        XCTAssertNil(groups.last!.startDate)
    }

    func testFacesSplitADayIntoTwoGatheringsWhenEnabled() {
        let items = [at(2024, 5, 5, 10, faces: [1, 2]), at(2024, 5, 5, 11, faces: [2]),
                     at(2024, 5, 5, 19, faces: [7, 8]), at(2024, 5, 5, 20, faces: [8])]
        XCTAssertEqual(1, PhotoGrouper.group(items, zone: utc, useFaces: false).count)
        XCTAssertEqual(2, PhotoGrouper.group(items, zone: utc, useFaces: true).count)
    }

    func testMergeAndSplitRoundTrip() {
        let groups = PhotoGrouper.group([at(2024, 1, 1, 9), at(2024, 1, 5, 9)], zone: utc)
        XCTAssertEqual(2, groups.count)
        let merged = PhotoGrouper.merge(groups[0], groups[1])
        XCTAssertEqual(2, merged.items.count)
        XCTAssertEqual(LocalDate.of(2024, 1, 1), merged.startDate)
        XCTAssertEqual(LocalDate.of(2024, 1, 5), merged.endDate)
        let (a, b) = PhotoGrouper.split(merged, at: 1, zone: utc)
        XCTAssertEqual(1, a.items.count)
        XCTAssertEqual(1, b.items.count)
        XCTAssertEqual(LocalDate.of(2024, 1, 5), b.startDate)
    }

    lazy var library: [PickedItem] = [
        at(2022, 12, 25), at(2022, 12, 31),
        at(2023, 1, 5), at(2023, 1, 20), at(2023, 6, 10), at(2023, 12, 24),
        at(2024, 3, 3), at(2024, 3, 4), at(2024, 3, 5), at(2024, 12, 1),
        PickedItem(key: "undated", takenAtMillis: nil),
    ]

    func testSplitByYearMakesOneMemoryPerYearNewestFirst() {
        let split = PeriodGrouper.split(library.shuffled(), splitBy: .YEAR, zone: utc)
        XCTAssertEqual([2024, 2023, 2022], split.buckets.map(\.year))
        XCTAssertEqual([4, 4, 2], split.buckets.map(\.items.count))
        XCTAssertTrue(split.buckets.allSatisfy { $0.month == nil })
        XCTAssertEqual(1, split.undated.count)
        XCTAssertEqual(0, split.filteredOut)
        XCTAssertEqual(3, split.memoryCount)
    }

    func testSplitByMonthMakesOneMemoryPerMonthThatHasPhotos() {
        let split = PeriodGrouper.split(library, splitBy: .MONTH, zone: utc)
        XCTAssertEqual(6, split.memoryCount)
        XCTAssertEqual(2024, split.buckets.first!.year)
        XCTAssertEqual(12, split.buckets.first!.month)
        let march = split.buckets.first { $0.year == 2024 && $0.month == 3 }!
        XCTAssertEqual(["2024-3-3-12", "2024-3-4-12", "2024-3-5-12"], march.items.map(\.key))
    }

    func testYearFilterKeepsOnlyThatYearAndSplitsItByMonth() {
        let split = PeriodGrouper.split(library, splitBy: .MONTH, filter: PeriodFilter(years: [2023]), zone: utc)
        XCTAssertEqual([12, 6, 1], split.buckets.map(\.month))
        XCTAssertTrue(split.buckets.allSatisfy { $0.year == 2023 })
        XCTAssertEqual(6, split.filteredOut)
        XCTAssertEqual(4, split.keptCount)
    }

    func testMonthFilterKeepsThatMonthAcrossYearsAndSplitsByYear() {
        let split = PeriodGrouper.split(library, splitBy: .YEAR, filter: PeriodFilter(months: [12]), zone: utc)
        XCTAssertEqual([2024, 2023, 2022], split.buckets.map(\.year))
        XCTAssertEqual([1, 1, 2], split.buckets.map(\.items.count))
    }

    func testSeveralYearsAndYearPlusMonthTogether() {
        XCTAssertEqual([2024, 2022], PeriodGrouper.split(library, splitBy: .YEAR, filter: PeriodFilter(years: [2022, 2024]), zone: utc).buckets.map(\.year))
        let dec2023 = PeriodGrouper.split(library, splitBy: .MONTH, filter: PeriodFilter(years: [2023], months: [12]), zone: utc)
        XCTAssertEqual(1, dec2023.memoryCount)
        XCTAssertEqual("2023-12-24-12", dec2023.buckets.first!.items.first!.key)
    }

    func testFilterThatMatchesNothingYieldsNoMemories() {
        let split = PeriodGrouper.split(library, splitBy: .MONTH, filter: PeriodFilter(years: [2010, 2011]), zone: utc)
        XCTAssertEqual(0, split.memoryCount)
        XCTAssertEqual(10, split.filteredOut)
    }

    func testYearsListsDistinctYearsNewestFirst() {
        XCTAssertEqual([2024, 2023, 2022], PeriodGrouper.years(library, zone: utc))
    }
}
