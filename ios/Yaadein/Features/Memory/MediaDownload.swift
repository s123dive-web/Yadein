import Photos
import SwiftUI
import YaadeinData
import YaadeinDomain

/// Saves photos and videos out of the vault into the iPhone's Photos library, keeping the date they were taken
/// (MediaDownloader.kt on Android). Only add-only access is asked for: Yaadein never reads the library.
enum MediaDownload {
    struct Result {
        let saved: Int
        let total: Int

        var message: String {
            if total == 0 { return L("download_none") }
            if saved == 0 { return L("share_failed") }
            if saved < total { return L("download_partial", saved, total) }
            return L("download_saved_gallery")
        }
    }

    static func save(_ media: [Media], vault: MediaVault) async -> Result {
        guard !media.isEmpty else { return Result(saved: 0, total: 0) }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { return Result(saved: 0, total: media.count) }
        var saved = 0
        for m in media {
            if await saveOne(m, vault: vault) { saved += 1 }
        }
        return Result(saved: saved, total: media.count)
    }

    private static func saveOne(_ m: Media, vault: MediaVault) async -> Bool {
        // The stored name is "<uuid>.<ext>.enc"; keep the original extension so Photos knows the type.
        let stored = (m.encryptedPath as NSString).deletingPathExtension
        let ext = (stored as NSString).pathExtension.isEmpty ? (m.kind == .VIDEO ? "mp4" : "jpg") : (stored as NSString).pathExtension
        guard let url = try? vault.decryptToTemporaryFile(m.encryptedPath, ext: ext) else { return false }
        defer { try? FileManager.default.removeItem(at: url) }
        let taken = m.takenAt.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                request.addResource(with: m.kind == .VIDEO ? .video : .photo, fileURL: url, options: nil)
                if let taken { request.creationDate = taken }
            }
            return true
        } catch {
            return false
        }
    }
}

/// Button that saves the given media and reports the outcome (used by the viewer and the memory screen).
struct MediaDownloadButton: View {
    let label: String
    let media: () -> [Media]
    @Environment(AppModel.self) private var model
    @State private var working = false
    @State private var message: String?

    var body: some View {
        Button(label, systemImage: "arrow.down.circle") { start() }
            .disabled(working)
            .overlay { if working { ProgressView() } }
            .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button(L("action_done")) { message = nil }
            }
    }

    private func start() {
        guard let vault = model.vault else { return }
        let items = media()
        guard !items.isEmpty else { message = L("download_none"); return }
        working = true
        Task {
            let r = await MediaDownload.save(items, vault: vault)
            working = false
            message = r.message
        }
    }
}
