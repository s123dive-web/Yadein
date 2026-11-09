import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Share → Save to Yaadein (SEND / SEND_MULTIPLE + SharedMediaInbox on Android). The extension cannot open the
/// encrypted vault, so it only copies the shared photos and videos into the App Group inbox and marks the batch ready.
/// The next time Yaadein is unlocked, a new memory opens with them attached and the copies are deleted.
final class ShareViewController: UIViewController {
    private let state = ShareState()

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: ShareView(state: state) { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        })
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        Task { await copyItems() }
    }

    private func copyItems() async {
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        let fm = FileManager.default
        let batch = AppGroup.inbox.appendingPathComponent("\(Int64(Date().timeIntervalSince1970 * 1000))-\(UUID().uuidString)", isDirectory: true)
        var copied = 0
        do {
            try fm.createDirectory(at: batch, withIntermediateDirectories: true)
            for (i, provider) in providers.enumerated() {
                guard let type = [UTType.movie, UTType.image].first(where: { provider.hasItemConformingToTypeIdentifier($0.identifier) }) else { continue }
                if await copy(provider, type: type, to: batch, index: i) != nil { copied += 1 }
            }
            if copied > 0 {
                try Data().write(to: batch.appendingPathComponent("ready"))
            } else {
                try? fm.removeItem(at: batch)
            }
        } catch {
            try? fm.removeItem(at: batch)
            copied = 0
        }
        let count = copied
        await MainActor.run { state.result = count > 0 ? .saved(count) : .failed }
    }

    /// The provider's file only lives during the callback, so it is copied inside it.
    private func copy(_ provider: NSItemProvider, type: UTType, to folder: URL, index: Int) async -> URL? {
        await withCheckedContinuation { continuation in
            _ = provider.loadFileRepresentation(forTypeIdentifier: type.identifier) { url, _ in
                guard let url else { return continuation.resume(returning: nil) }
                let ext = url.pathExtension.isEmpty ? (type == .movie ? "mov" : "jpg") : url.pathExtension.lowercased()
                let target = folder.appendingPathComponent(String(format: "%03d.%@", index, ext))
                do {
                    try FileManager.default.copyItem(at: url, to: target)
                    continuation.resume(returning: target)
                } catch {
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}

@MainActor
final class ShareState: ObservableObject {
    enum Result: Equatable { case working, saved(Int), failed }
    @Published var result: Result = .working
}

struct ShareView: View {
    @ObservedObject var state: ShareState
    let done: () -> Void

    private let plum = Color(red: 0x5B / 255, green: 0x2A / 255, blue: 0x86 / 255)

    var body: some View {
        VStack(spacing: 16) {
            Text(text("share_target_label")).font(.custom("Fraunces-SemiBold", size: 26))
            switch state.result {
            case .working:
                ProgressView()
            case .saved:
                Image(systemName: "checkmark.circle.fill").font(.system(size: 48)).foregroundStyle(plum)
                Text(text("share_saved")).font(.custom("ManropeExtraLight-SemiBold", size: 18))
                Text(text("app_info_share_body")).font(.custom("ManropeExtraLight-Regular", size: 15))
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
            case .failed:
                Image(systemName: "exclamationmark.triangle").font(.system(size: 44)).foregroundStyle(.orange)
                Text(text("share_failed")).font(.custom("ManropeExtraLight-SemiBold", size: 18)).multilineTextAlignment(.center)
            }
            Button(action: done) {
                Text(text("action_done")).font(.custom("ManropeExtraLight-SemiBold", size: 16)).frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(plum)
            .disabled(state.result == .working)
        }
        .padding(24)
        .frame(maxWidth: 480)
    }
}

func text(_ key: String) -> String { Bundle.main.localizedString(forKey: key, value: nil, table: nil) }
