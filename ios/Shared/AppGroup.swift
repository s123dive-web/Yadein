import Foundation

/// The App Group shared by the app, the home-screen widget and the share extension. It holds only two things:
/// `widget.json` (the widget's lines for the coming days, already worded by the app, titles only when the user turned
/// "Show memories on the widget" on) and `shared/` (files handed over by Share → Save to Yaadein until the app takes
/// them into the encrypted vault). Unsigned builds have no group container; then the app's own folder is used, so the
/// app keeps working and only the extensions fall back to their empty states.
enum AppGroup {
    static let id = "group.com.yaadein.app"
    static let urlScheme = "yaadein"

    static var container: URL {
        if let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: id) { return url }
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("group", isDirectory: true)
    }

    static var widgetFile: URL { container.appendingPathComponent("widget.json") }
    static var inbox: URL { container.appendingPathComponent("shared", isDirectory: true) }

    /// One line of the widget for one day.
    struct WidgetDay: Codable, Hashable {
        var epochDay: Int64
        var title: String
        var text: String
        var memoryId: Int64?
    }

    struct WidgetData: Codable {
        var days: [WidgetDay]
        var addLabel: String
    }

    static func readWidget() -> WidgetData? {
        guard let data = try? Data(contentsOf: widgetFile) else { return nil }
        return try? JSONDecoder().decode(WidgetData.self, from: data)
    }

    /// Files waiting in the share inbox, oldest first. A finished hand-over is a folder holding the files and a `ready`
    /// marker, so a half-copied share is never picked up.
    static func pendingShares() -> [URL] {
        let fm = FileManager.default
        guard let batches = try? fm.contentsOfDirectory(at: inbox, includingPropertiesForKeys: [.creationDateKey]) else { return [] }
        return batches
            .filter { fm.fileExists(atPath: $0.appendingPathComponent("ready").path) }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .flatMap { batch in
                ((try? fm.contentsOfDirectory(at: batch, includingPropertiesForKeys: nil)) ?? [])
                    .filter { $0.lastPathComponent != "ready" }
                    .sorted { $0.lastPathComponent < $1.lastPathComponent }
            }
    }

    static var hasPendingShares: Bool { !pendingShares().isEmpty }

    static func clearShares() { try? FileManager.default.removeItem(at: inbox) }
}
