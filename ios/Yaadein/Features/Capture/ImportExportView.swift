import SwiftUI
import UniformTypeIdentifiers
import YaadeinCrypto
import YaadeinData
import YaadeinDomain

/// "Bring in an export" (ImportExportScreen.kt): a Google Photos Takeout or WhatsApp chat zip becomes memories with
/// real dates (albums → one memory each, loose photos → one per day), using the shared ExportParsing rules.
struct ImportExportView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var choosing = false
    @State private var status: String?
    @State private var result: String?
    @State private var working = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L("import_intro")).font(look.type.bodyLarge)
                Text(L("import_takeout_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Text(L("import_whatsapp_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Button(L("import_choose")) { choosing = true }.buttonStyle(FilledButtonStyle(fullWidth: true)).disabled(working)
                if let status { HStack(spacing: Spacing.md) { ProgressView(); Text(status).font(look.type.bodyMedium) } }
                if let result { Text(result).font(look.type.titleMedium) }
                Text(L("import_privacy")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("import_title"))
        .fileImporter(isPresented: $choosing, allowedContentTypes: [.zip]) { r in
            if case let .success(url) = r { Task { await importZip(url) } }
        }
    }

    private func importZip(_ url: URL) async {
        guard let store = model.store, let vault = model.vault else { return }
        working = true
        result = nil
        status = L("import_reading", 0)
        let staging = FileManager.default.temporaryDirectory.appendingPathComponent("import-\(UUID().uuidString)")
        defer {
            try? FileManager.default.removeItem(at: staging)
            working = false
            status = nil
        }
        // 1. Stream the zip: media to staging files, sidecar dates remembered.
        let read: (items: [ExportParsing.Item], files: [String: URL], chat: String?)? = await Task.detached(priority: .userInitiated) {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            try? FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
            guard let source = try? FileSource(url: url) else { return nil }
            defer { try? source.close() }
            var files: [String: URL] = [:]
            var taken: [String: Int64] = [:]
            var counter = 0
            do {
                try ZipStreamReader(source).forEachEntry { name, readEntry in
                    if let target = ExportParsing.sidecarTarget(name) {
                        var bytes: [UInt8] = []
                        try readEntry { bytes += $0 }
                        if let t = ExportParsing.takenFromSidecar(String(decoding: bytes, as: UTF8.self)) { taken[target] = t }
                    } else if ExportParsing.kindOf(name) != nil {
                        counter += 1
                        let ext = (name as NSString).pathExtension.lowercased()
                        let out = staging.appendingPathComponent("\(counter).\(ext)")
                        let sink = try FileSink(url: out)
                        defer { try? sink.close() }
                        try readEntry { try sink.write($0) }
                        files[name] = out
                    }
                }
            } catch {
                if files.isEmpty { return nil }
            }
            let items = files.keys.sorted().compactMap { path -> ExportParsing.Item? in
                guard let kind = ExportParsing.kindOf(path) else { return nil }
                return ExportParsing.Item(path: path, kind: kind, takenAtMillis: taken[path], album: ExportParsing.albumOf(path))
            }
            return (items, files, ExportParsing.chatName(url.lastPathComponent))
        }.value
        guard let read, !read.items.isEmpty else {
            result = read == nil ? L("import_failed") : L("import_not_export")
            return
        }
        // 2. Group with the shared rules and save each group as a memory.
        let groups = ExportParsing.groups(read.items, zone: .current, fallback: .today())
        var added = 0, skipped = 0, memories = 0
        let total = read.items.count
        for g in groups {
            let start = g.start, end = g.end
            let date = start == end ? Dates.exact(start) : Dates.exact(start, end: end)
            let dayText = memoryDateText(Dates.exact(start))
            let title = g.title ?? (read.chat.map { L("import_chat_title", $0, dayText) } ?? L("import_untitled", dayText))
            let polished = PolishEngine.polish(title)
            guard let memoryId = try? store.createMemory(Memory(type: .EVENT, title: title, date: date, place: polished.place,
                                                                mood: polished.mood, coverStyle: polished.coverStyle), personIds: []) else { continue }
            memories += 1
            for item in g.items {
                status = L("import_importing", added + skipped + 1, total)
                guard let file = read.files[item.path] else { continue }
                do {
                    var media: PickedMedia
                    if item.kind == .VIDEO {
                        media = try await MediaImporter.inspectVideo(file)
                    } else {
                        media = try MediaImporter.inspectPhoto(try Data(contentsOf: file))
                    }
                    if let t = item.takenAtMillis ?? ExportParsing.dateFromName(item.path).map({ $0.epochDay * 86_400_000 }) {
                        media = PickedMedia(kind: media.kind, data: media.data, fileURL: media.fileURL, takenAtMillis: t, sizeBytes: media.sizeBytes,
                                            width: media.width, height: media.height, durationMs: media.durationMs, preview: media.preview)
                    }
                    try await MediaImporter.save(media, memoryId: memoryId, keepOriginal: !model.settings.storeOptimizedCopies, store: store, vault: vault)
                    added += 1
                } catch {
                    skipped += 1
                }
            }
        }
        result = L("import_done", added, memories) + (skipped > 0 ? "\n" + L("import_skipped", skipped) : "")
        Notifier.reschedule(model)
    }
}
