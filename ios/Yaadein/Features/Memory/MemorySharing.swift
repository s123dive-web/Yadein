import SwiftUI
import UIKit
import YaadeinCrypto
import YaadeinData
import YaadeinDomain

/// The picture card (MemoryExporter.kt): cover, title, date, place, who was there and the "Kept with Yaadein" footer.
struct ShareCardView: View {
    let detail: MemoryDetail
    let cover: UIImage?
    let look: Look

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                if let cover {
                    Image(uiImage: cover).resizable().scaledToFill()
                } else {
                    CoverArt(style: detail.memory.coverStyle, seed: detail.memory.id)
                }
            }
            .frame(width: 1080, height: 810)
            .clipped()
            VStack(alignment: .leading, spacing: 18) {
                Text(detail.memory.title).font(.custom("Fraunces-SemiBold", size: 64)).foregroundStyle(look.colors.onSurface)
                Text(([memoryDateText(detail.memory.date)] + (detail.memory.place.isEmpty ? [] : [detail.memory.place])).joined(separator: " · "))
                    .font(.custom("ManropeExtraLight-SemiBold", size: 34)).foregroundStyle(look.colors.primary)
                if !detail.memory.description.isEmpty {
                    Text(detail.memory.description).font(.custom("ManropeExtraLight-Regular", size: 32)).foregroundStyle(look.colors.onSurfaceVariant).lineLimit(5)
                }
                if !detail.people.isEmpty {
                    Text(detail.people.map(\.displayName).joined(separator: ", ")).font(.custom("ManropeExtraLight-Medium", size: 30))
                        .foregroundStyle(look.colors.onSurfaceVariant)
                }
                Text(L("share_footer")).font(.custom("Fraunces-SemiBoldItalic", size: 28)).foregroundStyle(look.colors.secondary).padding(.top, 12)
            }
            .padding(64)
        }
        .frame(width: 1080)
        .background(look.colors.surface)
        .environment(\.look, look)
    }
}

/// Share actions on a memory: picture card and encrypted family pack.
struct MemoryShareMenu: View {
    let detail: MemoryDetail
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var shareItems: [Any]?
    @State private var askPassword = false
    @State private var working = false
    @State private var failed = false

    var body: some View {
        Menu {
            Button(L("share_card"), systemImage: "photo") { shareCard() }
            Button(L("share_pdf"), systemImage: "doc.richtext") {
                if let vault = model.vault, let url = PdfBook.make(detail, look: look, vault: vault) { shareItems = [url] } else { failed = true }
            }
            Button(L("share_pack"), systemImage: "lock.doc") { askPassword = true }
        } label: { Image(systemName: "square.and.arrow.up") }
            .accessibilityLabel(L("share_card_chooser"))
            .sheet(isPresented: Binding(get: { shareItems != nil }, set: { if !$0 { cleanup() } })) {
                ActivityView(items: shareItems ?? [])
            }
            .sheet(isPresented: $askPassword) {
                PasswordSheet(title: L("share_pack_password_title"), message: L("share_pack_password_help"), confirm: true) { pw in
                    askPassword = false
                    sendPack(pw)
                }
                .environment(\.look, look)
            }
            .overlay { if working { ProgressView() } }
            .alert(L("share_failed"), isPresented: $failed) { Button(L("action_done")) {} }
    }

    private func shareCard() {
        let cover = (detail.media.first { $0.id == detail.memory.coverMediaId } ?? detail.media.first { $0.kind == .PHOTO })
            .flatMap { try? model.vault?.read($0.kind == .PHOTO ? $0.encryptedPath : $0.thumbPath) }
            .flatMap { UIImage(data: Data($0)) }
        let renderer = ImageRenderer(content: ShareCardView(detail: detail, cover: cover, look: look))
        renderer.scale = 1
        if let image = renderer.uiImage { shareItems = [image] } else { failed = true }
    }

    private func sendPack(_ password: String) {
        guard let store = model.store, let vault = model.vault else { return }
        working = true
        let aead = model.aead
        let db = store.snapshot
        let id = detail.memory.id
        let name = detail.memory.title.filter { $0.isLetter || $0.isNumber || $0 == " " }.prefix(40)
        Task.detached(priority: .userInitiated) {
            let out = FileManager.default.temporaryDirectory.appendingPathComponent("\(name.isEmpty ? "yaadein" : String(name)).\(BackupCodec.fileExtension)")
            do {
                let salt = ChunkedAEAD.randomBytes(16)
                let sink = try FileSink(url: out)
                try BackupCodec.write(database: db, to: sink, key: Argon2id.hash(password: password, salt: salt), salt: salt, aead: aead,
                                      onlyMemoryIds: [id], now: Int64(Date().timeIntervalSince1970 * 1000),
                                      mediaFile: { path, write in try vault.stream(path, to: write) }, exists: { vault.exists($0) })
                try sink.close()
                await MainActor.run {
                    working = false
                    shareItems = [out]
                }
            } catch {
                try? FileManager.default.removeItem(at: out)
                await MainActor.run {
                    working = false
                    failed = true
                }
            }
        }
    }

    private func cleanup() {
        for case let url as URL in shareItems ?? [] { try? FileManager.default.removeItem(at: url) }
        shareItems = nil
    }
}
