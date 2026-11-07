import XCTest
@testable import YaadeinDomain

/// Mirrors ExportParsingTest.kt.
final class ExportParsingTests: XCTestCase {
    func testWhatsappAndCameraNamesCarryTheDate() {
        XCTAssertEqual(LocalDate.of(2024, 1, 17), ExportParsing.dateFromName("WhatsApp Chat/IMG-20240117-WA0003.jpg"))
        XCTAssertEqual(LocalDate.of(2023, 12, 31), ExportParsing.dateFromName("VID-20231231-WA0010.mp4"))
        XCTAssertEqual(LocalDate.of(2025, 3, 2), ExportParsing.dateFromName("PXL_20250302_101010123.jpg"))
        XCTAssertNil(ExportParsing.dateFromName("holiday.jpg"))
    }

    func testTakeoutAlbumsAndSidecars() {
        XCTAssertEqual("Goa 2024", ExportParsing.albumOf("Takeout/Google Photos/Goa 2024/IMG_1.jpg"))
        XCTAssertNil(ExportParsing.albumOf("Takeout/Google Photos/Photos from 2023/IMG_1.jpg"))
        XCTAssertEqual("Takeout/Google Photos/Goa 2024/IMG_1.jpg", ExportParsing.sidecarTarget("Takeout/Google Photos/Goa 2024/IMG_1.jpg.supplemental-metadata.json"))
        XCTAssertEqual("a/IMG_2.jpg", ExportParsing.sidecarTarget("a/IMG_2.jpg.json"))
        XCTAssertNil(ExportParsing.sidecarTarget("a/metadata.json"))
        XCTAssertEqual(1_700_000_000_000, ExportParsing.takenFromSidecar(#"{"title":"x","photoTakenTime":{"timestamp":"1700000000","formatted":"..."}}"#))
    }

    func testChatNameComesFromTheZipName() {
        XCTAssertEqual("Aai", ExportParsing.chatName("WhatsApp Chat with Aai.zip"))
        XCTAssertNil(ExportParsing.chatName("photos.zip"))
    }

    func testAlbumsBecomeOneMemoryLoosePhotosOnePerDay() {
        let items = [
            ExportParsing.Item(path: "Takeout/Google Photos/Goa/a.jpg", kind: .PHOTO, takenAtMillis: 1_700_000_000_000, album: "Goa"),
            ExportParsing.Item(path: "Takeout/Google Photos/Goa/b.jpg", kind: .PHOTO, takenAtMillis: 1_700_200_000_000, album: "Goa"),
            ExportParsing.Item(path: "IMG-20240117-WA0003.jpg", kind: .PHOTO, takenAtMillis: nil, album: nil),
            ExportParsing.Item(path: "IMG-20240117-WA0004.jpg", kind: .PHOTO, takenAtMillis: nil, album: nil),
            ExportParsing.Item(path: "IMG-20240118-WA0001.jpg", kind: .PHOTO, takenAtMillis: nil, album: nil),
        ]
        let groups = ExportParsing.groups(items, zone: TimeZone(identifier: "UTC")!, fallback: .of(2026, 1, 1))
        XCTAssertEqual(3, groups.count)
        let goa = groups.first { $0.title == "Goa" }!
        XCTAssertEqual(2, goa.items.count)
        XCTAssertEqual(LocalDate.of(2023, 11, 14), goa.start)
        XCTAssertEqual(2, groups.first { $0.start == .of(2024, 1, 17) }!.items.count)
    }
}
