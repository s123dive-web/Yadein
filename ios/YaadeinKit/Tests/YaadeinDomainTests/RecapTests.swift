import XCTest
@testable import YaadeinDomain

/// Mirrors RecapPlannerTest.kt and AutoReelPickerTest.kt.
final class RecapTests: XCTestCase {
    func media(_ id: Int64, _ memoryId: Int64, _ kind: MediaKind = .PHOTO, duration: Int64? = nil, position: Int = 0) -> Media {
        Media(id: id, memoryId: memoryId, kind: kind, encryptedPath: "", thumbPath: "", durationMs: duration, sizeBytes: 1,
              width: 1, height: 1, sha256: "", position: position)
    }

    func memory(_ id: Int64, _ date: LocalDate, _ media: [Media], title: String? = nil, people: [Person] = []) -> MemoryDetail {
        MemoryDetail(memory: Memory(id: id, type: .EVENT, title: title ?? "m\(id)", date: Dates.exact(date)), people: people, media: media)
    }

    func plan(_ memories: [MemoryDetail], _ length: RecapLength = .MEDIUM) -> RecapPlan {
        RecapPlanner.plan(memories, theme: .WARM, length: length, title: "Title", subtitle: "Sub", outroText: "Made with Yaadein", dateLabel: { _ in "date" })
    }

    func photos(_ p: RecapPlan) -> [(id: Int64, ms: Int64, caption: String?, start: Framing, end: Framing)] {
        p.segments.compactMap { if case let .photo(id, ms, s, e, c, _) = $0 { return (id, ms, c, s, e) } else { return nil } }
    }

    func testOrdersChronologicallyWithIntroAndOutroAndCaptionsOnFirstItemOfEachMemory() {
        let m2 = memory(2, .of(2024, 5, 1), [media(21, 2), media(22, 2)])
        let m1 = memory(1, .of(2023, 1, 1), [media(11, 1)])
        let p = plan([m2, m1])
        guard case .titleCard = p.segments.first!, case let .titleCard(_, _, _, isOutro) = p.segments.last! else { return XCTFail() }
        XCTAssertTrue(isOutro)
        XCTAssertEqual([11, 21, 22], photos(p).map(\.id))
        XCTAssertEqual(["m1", "m2", nil], photos(p).map(\.caption))
    }

    func testVideosBecomeTrimmedClipsAndPhotosKenBurns() {
        let p = plan([memory(1, .of(2024, 1, 1), [media(1, 1, .VIDEO, duration: 30_000), media(2, 1)])])
        let clips = p.segments.compactMap { if case let .clip(_, s, e, _, _) = $0 { return (s, e) } else { return nil } }
        XCTAssertEqual(1, clips.count)
        XCTAssertTrue((RecapPlanner.minClipMs...RecapPlanner.maxClipMs).contains(clips[0].1 - clips[0].0))
        XCTAssertEqual(500, clips[0].0)
        XCTAssertNotEqual(photos(p)[0].start.zoom, photos(p)[0].end.zoom)
    }

    func testLengthPresetBoundsPhotoDurationsAndTotal() {
        let many = (1...40).map { memory(Int64($0), .of(2020, 1, $0 % 28 + 1), [media(Int64($0) * 10, Int64($0))]) }
        let short = plan(many, .SHORT)
        let long = plan(many, .LONG)
        photos(short).forEach { XCTAssertEqual(RecapPlanner.minPhotoMs, $0.ms) }
        XCTAssertGreaterThan(long.totalDurationMs, short.totalDurationMs)
        photos(long).forEach { XCTAssertTrue((RecapPlanner.minPhotoMs...RecapPlanner.maxPhotoMs).contains($0.ms)) }
    }

    func testABigAlbumIsUsedWholeWhenItFitsAndTextOnlyMemoriesGetACard() {
        let album = memory(1, .of(2024, 1, 1), (1...30).map { media(Int64($0), 1) })
        let text = memory(2, .of(2024, 2, 1), [], title: "Just words")
        let p = plan([album, text])
        XCTAssertEqual(Array(1...30).map(Int64.init), photos(p).map(\.id))
        XCTAssertTrue(p.segments.contains { if case let .titleCard(t, _, _, _) = $0 { return t == "Just words" } else { return false } })
    }

    func testAHugeAlbumIsSampledEvenlyUpToTheLengthsDefaultCount() {
        let album = memory(1, .of(2024, 1, 1), (1...300).map { media(Int64($0), 1) })
        let ids = photos(plan([album], .LONG)).map(\.id)
        XCTAssertEqual(RecapPlanner.defaultItemCount(.LONG), ids.count)
        XCTAssertEqual(1, ids.first)
        XCTAssertEqual(300, ids.last)
        XCTAssertEqual(ids.sorted(), ids)
    }

    func testDefaultSelectionSharesTheBudgetFairlyBetweenAlbums() {
        let small = memory(1, .of(2024, 1, 1), (1...3).map { media(Int64($0), 1) })
        let big = memory(2, .of(2024, 2, 1), (101...200).map { media(Int64($0), 2) })
        let picked = RecapPlanner.defaultSelection([big, small], includeVideos: true, maxItems: 20)
        XCTAssertEqual(20, picked.count)
        XCTAssertTrue(Set(picked).isSuperset(of: [1, 2, 3]))
        XCTAssertEqual(17, picked.filter { $0 > 100 }.count)
    }

    func testAnExplicitSelectionIsUsedAsIsHoweverLong() {
        let album = memory(1, .of(2024, 1, 1), (1...200).map { media(Int64($0), 1) })
        let other = memory(2, .of(2024, 3, 1), [media(500, 2)])
        let chosen = Set((1...120).map(Int64.init))
        let p = RecapPlanner.plan([album, other], theme: .WARM, length: .SHORT, title: "t", subtitle: "s", outroText: "o", dateLabel: { _ in "d" }, selectedMediaIds: chosen)
        XCTAssertEqual((1...120).map(Int64.init), photos(p).map(\.id))
        XCTAssertFalse(p.segments.contains { if case let .titleCard(t, _, _, _) = $0 { return t == "m2" } else { return false } })
        XCTAssertGreaterThanOrEqual(p.totalDurationMs, 120 * RecapPlanner.minPhotoMs)
    }

    func testOutroCarriesTheMakerCreditSubtitle() {
        let p = RecapPlanner.plan([memory(1, .of(2024, 1, 1), [media(1, 1)])], theme: .WARM, length: .SHORT, title: "t", subtitle: "s",
                                  outroText: "Made with Yaadein", dateLabel: { _ in "d" }, outroSubtitle: "credit")
        XCTAssertEqual(.titleCard(title: "Made with Yaadein", subtitle: "credit", durationMs: RecapPlanner.outroMs, isOutro: true), p.segments.last)
    }

    func testIsDeterministic() {
        let m = memory(1, .of(2024, 1, 1), [media(1, 1), media(2, 1), media(3, 1)])
        XCTAssertEqual(plan([m]), plan([m]))
    }

    func testVideosCanBeExcluded() {
        let m = memory(1, .of(2024, 1, 1), [media(1, 1, .VIDEO, duration: 5000), media(2, 1)])
        let p = RecapPlanner.plan([m], theme: .MINIMAL, length: .SHORT, title: "t", subtitle: "s", outroText: "o", dateLabel: { _ in "d" }, includeVideos: false)
        XCTAssertFalse(p.segments.contains { if case .clip = $0 { return true } else { return false } })
    }

    func testPhotoDurationsSnapToWholeBeatsOfTheSoundtrack() {
        XCTAssertEqual(2_000, RecapPlanner.snapToBeat(2_100, beatMs: 500))
        XCTAssertEqual(2_500, RecapPlanner.snapToBeat(2_300, beatMs: 500))
        XCTAssertEqual(1_000, RecapPlanner.snapToBeat(300, beatMs: 500))
        XCTAssertEqual(2_345, RecapPlanner.snapToBeat(2_345, beatMs: nil))
    }

    // The Ken Burns framings come from the same LCG as Android: the first photo of every plan starts here.
    func testKenBurnsFramingsMatchAndroid() {
        let p = plan([memory(1, .of(2024, 1, 1), [media(1, 1)])])
        let first = photos(p)[0]
        XCTAssertEqual(first.start.zoom == 1.0, first.end.zoom == 1.18)
        XCTAssertEqual(first.start.cx + first.end.cx, 1, accuracy: 0.0001)
    }

    // MARK: AutoReelPicker

    let today = LocalDate.of(2026, 11, 7)
    let rahul = Person(id: 1, name: "Rahul", relation: .FAMILY, avatarColorIndex: 0)
    let asha = Person(id: 2, name: "Asha", relation: .FAMILY, avatarColorIndex: 1)

    func reelMemory(_ id: Int64, _ date: LocalDate, _ photos: Int, _ people: [Person] = [], video: Bool = false) -> MemoryDetail {
        var media = photos == 0 ? [] : (1...photos).map { self.media(id * 100 + Int64($0), id, position: $0) }
        if video { media.append(self.media(id * 100 + 99, id, .VIDEO)) }
        return memory(id, date, media, people: people)
    }

    func testFillsAllThreeSlotsForAVariedLibrary() {
        let library = [reelMemory(1, .of(2020, 11, 2), 4, [rahul]), reelMemory(2, .of(2022, 3, 9), 5, [rahul, asha]),
                       reelMemory(3, .of(2024, 6, 1), 3, [asha]), reelMemory(4, .of(2026, 10, 20), 6, [rahul]),
                       reelMemory(5, .of(2026, 11, 1), 2)]
        let picks = AutoReelPicker.pick(library, today: today)
        XCTAssertEqual([.RECENT, .PERSON, .THROWBACK], picks.map(\.slot))
        XCTAssertEqual(.RECENT_DAYS, picks[0].variant)
        XCTAssertEqual([4, 5], picks[0].memories.map(\.memory.id))
        XCTAssertEqual(.PERSON, picks[1].variant)
        XCTAssertEqual(rahul, picks[1].person)
        XCTAssertEqual(.THIS_MONTH_YEARS_AGO, picks[2].variant)
        XCTAssertEqual([1], picks[2].memories.map(\.memory.id))
        picks.forEach { XCTAssertLessThanOrEqual($0.mediaIds.count, AutoReelPicker.itemsPerReel) }
    }

    func testFallsBackWhenNothingIsRecentNobodyIsTaggedAndNoMemoryIsFromThisMonth() {
        let library = [reelMemory(1, .of(2019, 1, 5), 3), reelMemory(2, .of(2021, 4, 5), 3), reelMemory(3, .of(2023, 8, 5), 30)]
        let picks = AutoReelPicker.pick(library, today: today)
        XCTAssertEqual([.LATEST, .EVERYTHING, .THROUGH_THE_YEARS], picks.map(\.variant))
        XCTAssertEqual(AutoReelPicker.itemsPerReel, picks[1].mediaIds.count)
    }

    func testNeverMakesTheSameReelTwiceAndSkipsVideos() {
        let library = [reelMemory(1, .of(2026, 11, 1), 3, [rahul], video: true)]
        let picks = AutoReelPicker.pick(library, today: today)
        XCTAssertEqual(1, picks.count)
        XCTAssertFalse(picks[0].mediaIds.contains { $0 % 100 == 99 })
        XCTAssertEqual(AutoReelPicker.pick(library, today: today)[0].signature, picks[0].signature)
    }

    func testAnEmptyOrWordsOnlyLibraryHasNoAutomaticReels() {
        XCTAssertTrue(AutoReelPicker.pick([], today: today).isEmpty)
        XCTAssertTrue(AutoReelPicker.pick([reelMemory(1, today, 0)], today: today).isEmpty)
    }
}
