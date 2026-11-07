import XCTest
import YaadeinDomain
@testable import YaadeinData

/// Mirrors the repository behaviour MemoryRepositoryTest checks on Android.
final class VaultStoreTests: XCTestCase {
    var writes = 0
    lazy var store = VaultStore(clock: { 1000 }) { [unowned self] _ in self.writes += 1 }

    func memory(_ title: String, _ date: MemoryDate, place: String = "", type: MemoryType = .EVENT, description: String = "") -> Memory {
        Memory(type: type, title: title, description: description, date: date, place: place)
    }

    func testOrderingFiltersSearchAndTogether() throws {
        let rahul = try store.upsertPerson(Person(name: "Rahul"))
        let asha = try store.upsertPerson(Person(name: "Asha", nickname: "Ashu"))
        let goa = try store.createMemory(memory("Goa Getaway", Dates.year(2024), place: "Goa"), personIds: [rahul, asha])
        let diwali = try store.createMemory(memory("Diwali at home", Dates.exact(.of(2024, 10, 31))), personIds: [rahul])
        let old = try store.createMemory(memory("School days", Dates.fuzzy("Summer 2005", anchorYear: 2005, anchorMonth: 5), type: .TEXT_ONLY), personIds: [])
        XCTAssertEqual([diwali, goa, old], store.cards().map(\.memory.id))
        XCTAssertEqual([diwali, goa], store.cards(TimelineFilter(personIds: [rahul])).map(\.memory.id))
        XCTAssertEqual([goa], store.cards(TimelineFilter(personIds: [rahul, asha], requireAllPeople: true)).map(\.memory.id))
        XCTAssertEqual([goa], store.cards(TimelineFilter(place: "go")).map(\.memory.id))
        XCTAssertEqual([old], store.cards(TimelineFilter(types: [.TEXT_ONLY])).map(\.memory.id))
        XCTAssertEqual([goa], store.search("goa get").map(\.memory.id))
        XCTAssertEqual([goa], store.search("ashu").map(\.memory.id)) // tagged nicknames are searchable
        XCTAssertEqual([old], store.search("summer").map(\.memory.id)) // fuzzy label is indexed
        XCTAssertEqual([diwali], store.search("rahul's diwali").map(\.memory.id)) // the "s" fragment is dropped
        XCTAssertTrue(store.search("  ").isEmpty)
        XCTAssertEqual([2024, 2005], store.countsByYear().map(\.year))
        XCTAssertEqual([diwali], store.onThisDay(monthDay: 1031).map(\.memory.id))
        XCTAssertTrue(store.onThisDay(monthDay: 1031, excludeYear: 2024).isEmpty)
        XCTAssertEqual(2, store.memoryCounts()[rahul])
        XCTAssertEqual(1, store.coAppearances().first?.count)
        XCTAssertEqual(["Asha", "Rahul"], store.card(id: goa)?.people.map(\.name))
    }

    func testFailedWriteChangesNothing() throws {
        struct Boom: Error {}
        var fail = false
        let s = VaultStore { _ in if fail { throw Boom() } }
        _ = try s.createMemory(memory("A", Dates.year(2020)), personIds: [])
        fail = true
        XCTAssertThrowsError(try s.createMemory(memory("B", Dates.year(2021)), personIds: []))
        XCTAssertEqual(["A"], s.cards().map(\.memory.title))
    }

    func testDeletingCascadesAndCoverFallsBack() throws {
        let id = try store.createMemory(memory("Trip", Dates.year(2022)), personIds: [])
        let a = try store.addMedia(Media(memoryId: id, kind: .PHOTO, encryptedPath: "media/a", thumbPath: "thumbs/a", sizeBytes: 1, width: 1, height: 1, sha256: ""))
        let b = try store.addMedia(Media(memoryId: id, kind: .PHOTO, encryptedPath: "media/b", thumbPath: "thumbs/b", sizeBytes: 1, width: 1, height: 1, sha256: ""))
        XCTAssertEqual(a, store.memory(id: id)?.coverMediaId)
        XCTAssertEqual(["media/a", "thumbs/a"], try store.deleteMedia(id: a))
        XCTAssertEqual(b, store.memory(id: id)?.coverMediaId)
        XCTAssertEqual(["media/b", "thumbs/b"], try store.deleteMemory(id: id))
        XCTAssertEqual(0, store.mediaCount)
    }

    func testPersonStory() throws {
        let p = try store.upsertPerson(Person(name: "Aai", birthdayEpochDay: LocalDate.of(1960, 11, 9).epochDay))
        _ = try store.createMemory(memory("First", Dates.year(2001)), personIds: [p])
        _ = try store.createMemory(memory("Last", Dates.year(2021)), personIds: [p])
        _ = try store.upsertGroup(Group(name: "Family", memberIds: [p]))
        let story = try XCTUnwrap(store.story(personId: p, today: .of(2026, 11, 7)))
        XCTAssertEqual(2, story.memoryCount)
        XCTAssertEqual("First", story.firstMemory?.title)
        XCTAssertEqual("Last", story.lastMemory?.title)
        XCTAssertEqual(["Family"], story.groups.map(\.name))
        XCTAssertEqual(2, story.daysToBirthday)
    }
}

extension VaultStoreTests {
    func testReelRetentionAndSlots() throws {
        for i in 0..<12 { _ = try store.addReel(ReelEntry(title: "r\(i)", path: "reels/\(i)", createdAt: Int64(i), durationMs: 1)) }
        XCTAssertEqual(VaultStore.maxUserReels, store.reels().count)
        XCTAssertEqual("r11", store.reels().first?.title)
        XCTAssertTrue(try store.addReel(ReelEntry(title: "a", path: "reels/r-slot", createdAt: 1, durationMs: 1, slot: "recent")).isEmpty)
        XCTAssertEqual(["reels/r-slot"], try store.addReel(ReelEntry(title: "b", path: "reels/r2", createdAt: 2, durationMs: 1, slot: "recent")))
        XCTAssertEqual(1, store.reels().filter { $0.slot == "recent" }.count)
    }

    func testOldDatabaseWithoutReelsStillDecodes() throws {
        let json = #"{"version":1,"nextId":3,"persons":[],"groups":[],"memories":[],"media":[],"memoryPersons":[],"mediaPersons":[]}"#
        let db = try JSONDecoder().decode(VaultDatabase.self, from: Data(json.utf8))
        XCTAssertEqual(3, db.nextId)
        XCTAssertTrue(db.reels.isEmpty)
    }
}
