import Foundation

/// Google Photos Takeout and WhatsApp "Export chat" understanding (ExportParsing.kt). Pure; zip reading lives in the app.
public enum ExportParsing {
    public enum Kind: String, Sendable { case PHOTO, VIDEO }

    public struct Item: Hashable, Sendable {
        public let path: String
        public let kind: Kind
        public let takenAtMillis: Int64?
        public let album: String?

        public init(path: String, kind: Kind, takenAtMillis: Int64?, album: String?) {
            self.path = path
            self.kind = kind
            self.takenAtMillis = takenAtMillis
            self.album = album
        }
    }

    /// A memory to create: `title` is the album name (nil: build one from the date), dates inclusive.
    public struct Group: Hashable, Sendable {
        public let title: String?
        public let items: [Item]
        public let start: LocalDate
        public let end: LocalDate
    }

    private static let photoExt: Set<String> = ["jpg", "jpeg", "png", "webp", "heic", "heif", "gif"]
    private static let videoExt: Set<String> = ["mp4", "3gp", "mov", "m4v", "webm", "mkv"]

    private static func afterLast(_ s: String, _ c: Character, missing: String) -> String {
        guard let i = s.lastIndex(of: c) else { return missing }
        return String(s[s.index(after: i)...])
    }

    public static func kindOf(_ path: String) -> Kind? {
        let ext = afterLast(path, ".", missing: "").lowercased()
        if photoExt.contains(ext) { return .PHOTO }
        if videoExt.contains(ext) { return .VIDEO }
        return nil
    }

    private static let whatsApp = Rx("(?:IMG|VID|PTT|AUD|STK)-(\\d{4})(\\d{2})(\\d{2})-WA\\d+", caseInsensitive: true)
    private static let cameraName = Rx("(?:IMG|VID|PXL|Screenshot)[_-](\\d{4})(\\d{2})(\\d{2})[_-]", caseInsensitive: true)
    private static let yearFolder = Rx("^photos from \\d{4}$", caseInsensitive: true)
    private static let takenTime = Rx("\"photoTakenTime\"\\s*:\\s*\\{[^}]*\"timestamp\"\\s*:\\s*\"?(\\d+)")
    private static let chat = Rx("WhatsApp Chat (?:with|-)\\s*(.+)", caseInsensitive: true)

    /// Date encoded in a WhatsApp or camera file name.
    public static func dateFromName(_ path: String) -> LocalDate? {
        let name = afterLast(path, "/", missing: path)
        guard let m = whatsApp.find(name) ?? cameraName.find(name),
              let y = Int(m[1]), let mo = Int(m[2]), let d = Int(m[3]), let date = LocalDate(y, mo, d) else { return nil }
        return (1990...2100).contains(date.year) ? date : nil
    }

    /// Takeout album folder ("Takeout/Google Photos/Goa 2024/x.jpg" → "Goa 2024"); year folders are not albums.
    public static func albumOf(_ path: String) -> String? {
        let parts = path.split(separator: "/").map(String.init).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let i = parts.firstIndex { $0.caseInsensitiveCompare("Google Photos") == .orderedSame || $0.caseInsensitiveCompare("Google Foto") == .orderedSame }
        let folder: String
        if let i, i + 2 < parts.count {
            folder = parts[i + 1]
        } else if parts.count >= 2 {
            folder = parts[parts.count - 2]
        } else {
            return nil
        }
        if yearFolder.matchesEntire(folder) { return nil }
        if folder.caseInsensitiveCompare("Takeout") == .orderedSame || folder.lowercased().hasPrefix("whatsapp") { return nil }
        return String(folder.prefix(60))
    }

    /// "IMG_1.jpg.json" / "IMG_1.jpg.supplemental-metadata.json" → "IMG_1.jpg".
    public static func sidecarTarget(_ path: String) -> String? {
        guard path.lowercased().hasSuffix(".json") else { return nil }
        var base = path
        if base.hasSuffix(".json") { base.removeLast(5) } else if base.hasSuffix(".JSON") { base.removeLast(5) }
        if let r = base.range(of: ".supplemental", options: .caseInsensitive) { return String(base[..<r.lowerBound]) }
        return kindOf(base) != nil ? base : nil
    }

    public static func takenFromSidecar(_ json: String) -> Int64? {
        guard let m = takenTime.find(json), let seconds = Int64(m[1]) else { return nil }
        return seconds * 1000
    }

    /// "WhatsApp Chat with Aai.zip" → "Aai".
    public static func chatName(_ fileName: String?) -> String? {
        guard let fileName else { return nil }
        let n = fileName.lastIndex(of: ".").map { String(fileName[..<$0]) } ?? fileName
        guard let m = chat.find(n) else { return nil }
        let name = m[1].trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? nil : String(name.prefix(40))
    }

    /// Albums become one memory each; everything else is grouped by day. Undated items go into one group on `fallback`.
    public static func groups(_ items: [Item], zone: TimeZone, fallback: LocalDate) -> [Group] {
        func dateOf(_ it: Item) -> LocalDate? {
            it.takenAtMillis.map { LocalDate(epochMillis: $0, zone: zone) } ?? dateFromName(it.path)
        }
        var result: [Group] = []
        var albumOrder: [String] = []
        var albums: [String: [Item]] = [:]
        var dayOrder: [LocalDate?] = []
        var days: [LocalDate?: [Item]] = [:]
        for it in items {
            if let album = it.album {
                if albums[album] == nil { albumOrder.append(album) }
                albums[album, default: []].append(it)
            } else {
                let d = dateOf(it)
                if days[d] == nil { dayOrder.append(d) }
                days[d, default: []].append(it)
            }
        }
        for album in albumOrder {
            let list = albums[album]!
            let dates = list.compactMap(dateOf)
            result.append(Group(title: album, items: list, start: dates.min() ?? fallback, end: dates.max() ?? fallback))
        }
        for day in dayOrder {
            result.append(Group(title: nil, items: days[day]!, start: day ?? fallback, end: day ?? fallback))
        }
        return result.stableSorted { $0.start < $1.start }
    }
}
