import Foundation
import SwiftUI
import UIKit
import YaadeinCrypto
import YaadeinData
import YaadeinDomain

/// "Save as PDF" (MemoryExporter.pdf on Android): an A4 page with the picture card, then pages of photos.
enum PdfBook {
    @MainActor
    static func make(_ detail: MemoryDetail, look: Look, vault: MediaVault) -> URL? {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let out = FileManager.default.temporaryDirectory.appendingPathComponent(
            "\(detail.memory.title.filter { $0.isLetter || $0.isNumber || $0 == " " }.prefix(40)).pdf")
        let photos: [UIImage] = detail.media.filter { $0.kind == .PHOTO }.compactMap { m in
            (try? vault.read(m.encryptedPath)).flatMap { UIImage(data: Data($0)) }
        }
        let renderer = ImageRenderer(content: ShareCardView(detail: detail, cover: photos.first, look: look))
        renderer.scale = 1
        guard let card = renderer.uiImage else { return nil }
        let pdf = UIGraphicsPDFRenderer(bounds: page)
        do {
            try pdf.writePDF(to: out) { ctx in
                ctx.beginPage()
                let s = min((page.width - 48) / card.size.width, (page.height - 48) / card.size.height)
                let size = CGSize(width: card.size.width * s, height: card.size.height * s)
                card.draw(in: CGRect(x: (page.width - size.width) / 2, y: 24, width: size.width, height: size.height))
                // Photos two per page, each fitted into half the page.
                for pair in stride(from: 1, to: photos.count, by: 2).map({ Array(photos[$0..<min($0 + 2, photos.count)]) }) {
                    ctx.beginPage()
                    for (i, img) in pair.enumerated() {
                        let slot = CGRect(x: 24, y: 24 + CGFloat(i) * (page.height - 48) / 2, width: page.width - 48, height: (page.height - 48) / 2 - 12)
                        let f = min(slot.width / img.size.width, slot.height / img.size.height)
                        let sz = CGSize(width: img.size.width * f, height: img.size.height * f)
                        img.draw(in: CGRect(x: slot.midX - sz.width / 2, y: slot.midY - sz.height / 2, width: sz.width, height: sz.height))
                    }
                }
            }
            return out
        } catch {
            return nil
        }
    }
}

/// Weekly automatic backup (AutoBackup.kt): the password is turned into the Argon2id key once; the key, its salt
/// and a bookmark to the chosen folder are kept sealed with the vault's media key. When a week has passed, the
/// next unlock writes `Yaadein-backup.yaadein` into that folder (for example an iCloud Drive or Google Drive folder),
/// replacing the previous one. Restores like any backup.
enum AutoBackup {
    struct Config: Codable {
        var bookmark: Data
        var salt: [UInt8]
        var key: [UInt8]
    }

    static let fileName = "Yaadein-backup.\(BackupCodec.fileExtension)"
    static let weekMs: Int64 = 7 * 86_400_000

    private static func configURL(_ vault: MediaVault) -> URL { vault.root.appendingPathComponent("autobackup.enc") }

    static func load(_ vault: MediaVault, aead: AEAD, mediaKey: [UInt8]) -> Config? {
        guard let bytes = try? Data(contentsOf: configURL(vault)), bytes.count > 12 else { return nil }
        let b = [UInt8](bytes)
        guard let plain = try? aead.open(Array(b[12...]), key: mediaKey, nonce: Array(b[..<12]), aad: Array("yaadein.autobackup".utf8)) else { return nil }
        return try? JSONDecoder().decode(Config.self, from: Data(plain))
    }

    static func save(_ config: Config, vault: MediaVault, aead: AEAD, mediaKey: [UInt8]) throws {
        let nonce = ChunkedAEAD.randomBytes(12)
        let sealed = nonce + (try aead.seal([UInt8](try JSONEncoder().encode(config)), key: mediaKey, nonce: nonce, aad: Array("yaadein.autobackup".utf8)))
        try Data(sealed).write(to: configURL(vault), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    static func disable(_ vault: MediaVault) { try? FileManager.default.removeItem(at: configURL(vault)) }

    /// Writes the backup into the bookmarked folder. Returns true on success.
    static func run(config: Config, store: VaultStore, vault: MediaVault, aead: AEAD) -> Bool {
        var stale = false
        guard let folder = try? URL(resolvingBookmarkData: config.bookmark, bookmarkDataIsStale: &stale) else { return false }
        let access = folder.startAccessingSecurityScopedResource()
        defer { if access { folder.stopAccessingSecurityScopedResource() } }
        let temp = folder.appendingPathComponent(fileName + ".part")
        let target = folder.appendingPathComponent(fileName)
        do {
            let sink = try FileSink(url: temp)
            try BackupCodec.write(database: store.snapshot, to: sink, key: config.key, salt: config.salt, aead: aead,
                                  now: Int64(Date().timeIntervalSince1970 * 1000),
                                  mediaFile: { path, write in try vault.stream(path, to: write) }, exists: { vault.exists($0) })
            try sink.close()
            _ = try? FileManager.default.removeItem(at: target)
            try FileManager.default.moveItem(at: temp, to: target)
            return true
        } catch {
            try? FileManager.default.removeItem(at: temp)
            return false
        }
    }

    /// Called after unlock: backs up when enabled and a week has passed.
    @MainActor
    static func runIfDue(_ model: AppModel) {
        guard model.settings.autoBackupEnabled, let store = model.store, let vault = model.vault,
              let keys = try? model.session.currentKeys() else { return }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        guard now - model.settings.lastBackupAtMillis >= weekMs, let config = load(vault, aead: model.aead, mediaKey: keys.media) else { return }
        let aead = model.aead
        Task.detached(priority: .background) {
            let ok = run(config: config, store: store, vault: vault, aead: aead)
            await MainActor.run {
                model.updateSettings {
                    if ok { $0.lastBackupAtMillis = now }
                    $0.autoBackupFailed = !ok
                }
            }
        }
    }
}

/// The automatic backup section of Backup and restore.
struct AutoBackupSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var pickFolder = false
    @State private var folder: URL?
    @State private var askPassword = false
    @State private var running = false

    var body: some View {
        let s = model.settings
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("autobackup_title")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
            Text(L("autobackup_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
            if s.autoBackupEnabled {
                let last = s.lastBackupAtMillis > 0
                    ? DateFormatter.localizedString(from: Date(timeIntervalSince1970: Double(s.lastBackupAtMillis) / 1000), dateStyle: .medium, timeStyle: .short)
                    : L("autobackup_never")
                Text(L("autobackup_on", last)).font(look.type.bodyMedium)
                if s.autoBackupFailed { Text(L("autobackup_failed")).font(look.type.bodySmall).foregroundStyle(look.colors.error) }
                HStack {
                    Button(running ? L("autobackup_running") : L("autobackup_now")) { backUpNow() }.buttonStyle(FilledButtonStyle(tonal: true)).disabled(running)
                    Button(L("autobackup_disable")) {
                        if let vault = model.vault { AutoBackup.disable(vault) }
                        model.updateSettings { $0.autoBackupEnabled = false }
                    }
                    .buttonStyle(TextButtonStyle())
                }
            } else {
                Button(L("autobackup_enable")) { pickFolder = true }.buttonStyle(FilledButtonStyle(tonal: true))
            }
        }
        .fileImporter(isPresented: $pickFolder, allowedContentTypes: [.folder]) { r in
            if case let .success(url) = r {
                folder = url
                askPassword = true
            }
        }
        .sheet(isPresented: $askPassword) {
            PasswordSheet(title: L("backup_set_password"), message: L("backup_password_help"), confirm: true) { pw in
                askPassword = false
                enable(password: pw)
            }
            .environment(\.look, look)
        }
    }

    private func enable(password: String) {
        guard let folder, let vault = model.vault, let keys = try? model.session.currentKeys() else { return }
        let access = folder.startAccessingSecurityScopedResource()
        defer { if access { folder.stopAccessingSecurityScopedResource() } }
        guard let bookmark = try? folder.bookmarkData() else { return }
        running = true
        let aead = model.aead
        Task.detached(priority: .userInitiated) {
            let salt = ChunkedAEAD.randomBytes(16)
            let key = Argon2id.hash(password: password, salt: salt)
            let config = AutoBackup.Config(bookmark: bookmark, salt: salt, key: key)
            try? AutoBackup.save(config, vault: vault, aead: aead, mediaKey: keys.media)
            await MainActor.run {
                model.updateSettings {
                    $0.autoBackupEnabled = true
                    $0.lastBackupAtMillis = 0
                }
                running = false
                backUpNow()
            }
        }
    }

    private func backUpNow() {
        guard let store = model.store, let vault = model.vault, let keys = try? model.session.currentKeys(),
              let config = AutoBackup.load(vault, aead: model.aead, mediaKey: keys.media) else { return }
        running = true
        let aead = model.aead
        Task.detached(priority: .userInitiated) {
            let ok = AutoBackup.run(config: config, store: store, vault: vault, aead: aead)
            await MainActor.run {
                model.updateSettings {
                    if ok { $0.lastBackupAtMillis = Int64(Date().timeIntervalSince1970 * 1000) }
                    $0.autoBackupFailed = !ok
                }
                running = false
            }
        }
    }
}
