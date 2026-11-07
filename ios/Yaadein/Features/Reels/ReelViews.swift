import SwiftUI
import UIKit
import YaadeinData
import YaadeinDomain

/// What a reel is about (Scope on Android).
enum ReelScope: Hashable {
    case all
    case person(Int64)
    case year(Int)
    case memory(Int64)
}

func recapThemeName(_ t: RecapTheme) -> String {
    switch t {
    case .WARM: return L("theme_warm")
    case .MINIMAL: return L("theme_minimal")
    case .FESTIVE: return L("theme_festive")
    case .CLASSIC_FILM: return L("theme_classic")
    }
}

/// "Create a reel" (RecapScreen.kt): who or what, title, theme, music, length, clips → render → play / share.
struct RecapView: View {
    let initialScope: ReelScope
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var scope: ReelScope = .all
    @State private var title = ""
    @State private var titleEdited = false
    @State private var theme: RecapTheme = .WARM
    @State private var track: ReelTrack? = ReelTrack.all.first
    @State private var length: RecapLength = .SHORT
    @State private var includeVideos = true
    @State private var progress: Double?
    @State private var renderer: ReelRenderer?
    @State private var done: ReelEntry?
    @State private var failure: String?

    private func memories() -> [MemoryDetail] {
        guard let store = model.store else { return [] }
        switch scope {
        case .all: return store.details(ids: store.ids(TimelineFilter()))
        case let .person(id): return store.details(ids: store.ids(TimelineFilter(personIds: [id])))
        case let .year(y): return store.cardsForYear(y).compactMap { store.detail(id: $0.memory.id) }
        case let .memory(id): return store.detail(id: id).map { [$0] } ?? []
        }
    }

    private func defaultTitle() -> String {
        switch scope {
        case .all: return L("recap_title_all")
        case let .person(id): return L("recap_title_person", model.store?.person(id: id)?.displayName ?? "")
        case let .year(y): return L("recap_title_year", y)
        case let .memory(id): return model.store?.memory(id: id)?.title ?? L("recap_title_all")
        }
    }

    private func plan(_ memories: [MemoryDetail]) -> RecapPlan {
        RecapPlanner.plan(memories, theme: theme, length: length, title: titleEdited ? title : defaultTitle(), subtitle: "",
                          outroText: L("recap_outro"), dateLabel: { memoryDateText($0.memory.date) }, includeVideos: includeVideos,
                          outroSubtitle: L("recap_outro_credit"), beatMs: track?.beatMs)
    }

    var body: some View {
        let _ = model.revision
        let ms = memories()
        let p = plan(ms)
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if let done {
                    doneCard(done)
                } else if let progress {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text(L("recap_rendering", Int(progress * 100))).font(look.type.titleMedium)
                        ProgressView(value: progress)
                        Button(L("action_cancel")) { renderer?.cancel() }.buttonStyle(TextButtonStyle())
                    }
                } else if ms.isEmpty {
                    EmptyState(illustration: .filmReel, title: L("recap_empty_title"), message: L("recap_empty_message"))
                } else {
                    setup(p)
                }
                if let failure {
                    Text(L("recap_failed_title")).font(look.type.titleMedium).foregroundStyle(look.colors.error)
                    Text(L("recap_failed_text", failure)).font(look.type.bodyMedium)
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("recap_title"))
        .toolbar { ToolbarItem(placement: .topBarTrailing) { NavigationLink(value: Route.reels) { Image(systemName: "film.stack") }.accessibilityLabel(L("reels_library_title")) } }
        .onAppear { scope = initialScope }
    }

    @ViewBuilder private func setup(_ p: RecapPlan) -> some View {
        let people = model.store?.persons() ?? []
        let years = model.store?.countsByYear().map(\.year) ?? []
        Text(L("recap_scope")).font(look.type.titleSmall)
        FlowLayout(spacing: Spacing.sm) {
            Chip(text: L("scope_all"), selected: scope == .all) { scope = .all }
            ForEach(people) { person in Chip(text: person.displayName, selected: scope == .person(person.id)) { scope = .person(person.id) } }
            ForEach(years, id: \.self) { y in Chip(text: String(y), selected: scope == .year(y)) { scope = .year(y) } }
            if case let .memory(id) = scope, let m = model.store?.memory(id: id) { Chip(text: m.title, selected: true) {} }
        }
        OutlinedField(label: L("recap_video_title"), text: Binding(get: { titleEdited ? title : defaultTitle() }, set: { title = $0; titleEdited = true }))
        Text(L("recap_theme")).font(look.type.titleSmall)
        FlowLayout(spacing: Spacing.sm) {
            ForEach(RecapTheme.allCases, id: \.self) { t in
                Chip(text: recapThemeName(t), selected: theme == t) {
                    theme = t
                    if let match = ReelTrack.all.first(where: { $0.theme == t }) { track = match }
                }
            }
        }
        Text(L("recap_music")).font(look.type.titleSmall)
        FlowLayout(spacing: Spacing.sm) {
            ForEach(ReelTrack.all) { t in Chip(text: t.label, selected: track == t) { track = t } }
            Chip(text: L("music_none"), selected: track == nil) { track = nil }
        }
        Text(L("music_licence")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
        Text(L("recap_length")).font(look.type.titleSmall)
        Picker(L("recap_length"), selection: $length) {
            ForEach(RecapLength.allCases, id: \.self) { l in Text(L("recap_length_seconds", l.targetSeconds)).tag(l) }
        }
        .pickerStyle(.segmented)
        Toggle(L("recap_include_videos"), isOn: $includeVideos).font(look.type.bodyLarge)
        Text(L("recap_summary", p.mediaCount, "\(p.totalDurationMs / 1000) s")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
        Button(L("recap_render")) { Task { await render(p) } }
            .buttonStyle(FilledButtonStyle(fullWidth: true))
            .disabled(p.mediaCount == 0 && p.segments.count <= 2)
    }

    private func doneCard(_ reel: ReelEntry) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(L("recap_done_title")).font(look.type.headlineSmall)
            EncryptedVideoPlayer(path: reel.path).aspectRatio(9 / 16, contentMode: .fit).frame(maxHeight: 420).frame(maxWidth: .infinity)
            HStack {
                ShareReelButton(reel: reel)
                Spacer()
                Button(L("recap_make_another")) { self.done = nil }.buttonStyle(TextButtonStyle())
            }
        }
    }

    private func render(_ p: RecapPlan) async {
        guard let store = model.store, let vault = model.vault else { return }
        failure = nil
        progress = 0
        let r = ReelRenderer(plan: p, track: track, vault: vault, store: store)
        renderer = r
        let reelTitle = titleEdited ? title : defaultTitle()
        do {
            let file = try await Task.detached(priority: .userInitiated) {
                try await r.render { value in Task { @MainActor in progress = value } }
            }.value
            defer { try? FileManager.default.removeItem(at: file) }
            let stored = try vault.store(file: file, folder: .reels, ext: "mp4")
            let entry = ReelEntry(title: reelTitle, path: stored.path, createdAt: Int64(Date().timeIntervalSince1970 * 1000), durationMs: p.totalDurationMs)
            let dropped = try store.addReel(entry)
            dropped.forEach(vault.delete)
            done = entry
        } catch is CancellationError {
        } catch {
            failure = error.localizedDescription
        }
        progress = nil
        renderer = nil
    }
}

/// Decrypts a reel to a temporary file only when sharing, then the system share sheet.
struct ShareReelButton: View {
    let reel: ReelEntry
    @Environment(AppModel.self) private var model
    @State private var file: URL?

    var body: some View {
        Button(L("action_share"), systemImage: "square.and.arrow.up") {
            file = try? model.vault?.decryptToTemporaryFile(reel.path, ext: "mp4")
        }
        .buttonStyle(FilledButtonStyle())
        .accessibilityLabel(L("reel_share_named", reel.title))
        .sheet(item: Binding(get: { file.map(IdentifiedURL.init) }, set: { if $0 == nil { cleanup() } })) { item in
            ActivityView(items: [item.url]).onDisappear(perform: cleanup)
        }
    }

    private func cleanup() {
        if let file { try? FileManager.default.removeItem(at: file) }
        file = nil
    }
}

struct IdentifiedURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

/// "Your reels" (ReelsScreen.kt): made for you (automatic) and made by you, play / share / delete.
struct ReelsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var deleting: ReelEntry?

    var body: some View {
        let _ = model.revision
        let reels = model.store?.reels() ?? []
        let auto = reels.filter { $0.slot != nil }
        let mine = reels.filter { $0.slot == nil }
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                SectionHeader(title: L("reels_made_for_you"))
                Text(L("reels_made_for_you_hint")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                if auto.isEmpty { Text(L("reels_auto_empty")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant) }
                ForEach(auto) { row($0) }
                SectionHeader(title: L("reels_made_by_you"))
                Text(L("reels_made_by_you_hint", VaultStore.maxUserReels)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                if mine.isEmpty { Text(L("reels_mine_empty")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant) }
                ForEach(mine) { row($0) }
                NavigationLink(value: Route.recap(.all)) { Text(L("recap_render")) }.buttonStyle(FilledButtonStyle(fullWidth: true))
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("reels_library_title"))
        .task { await AutoReels.refresh(model) }
        .alert(L("reel_delete_title", deleting?.title ?? ""), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button(L("reel_delete"), role: .destructive) {
                if let d = deleting, let path = try? model.store?.deleteReel(id: d.id) { model.vault?.delete(path) }
                deleting = nil
            }
            Button(L("action_cancel"), role: .cancel) { deleting = nil }
        } message: { Text(L("reel_delete_text")) }
    }

    private func row(_ reel: ReelEntry) -> some View {
        HStack(spacing: Spacing.md) {
            NavigationLink(value: Route.reelPlayer(reel.id)) {
                HStack(spacing: Spacing.md) {
                    Image(systemName: "play.circle.fill").font(.system(size: 36)).foregroundStyle(look.colors.primary)
                    VStack(alignment: .leading) {
                        Text(reel.title).font(look.type.titleMedium).foregroundStyle(look.colors.onSurface).lineLimit(1)
                        Text("\(reel.durationMs / 1000) s").font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("reel_play_named", reel.title))
            Spacer()
            Button { deleting = reel } label: { Image(systemName: "trash") }.accessibilityLabel(L("reel_delete_named", reel.title))
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
    }
}

struct ReelPlayerView: View {
    let reelId: String
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        if let reel = model.store?.reels().first(where: { $0.id == reelId }) {
            VStack(spacing: Spacing.md) {
                EncryptedVideoPlayer(path: reel.path).aspectRatio(9 / 16, contentMode: .fit)
                ShareReelButton(reel: reel)
            }
            .padding(Spacing.md)
            .background(Color.black.ignoresSafeArea())
            .screenTitle(reel.title)
        } else {
            EmptyState(illustration: .filmReel, title: L("reel_missing_title"), message: L("reel_missing_text"))
        }
    }
}

/// Three reels kept ready from the library (AutoReelWorker): recent, a person, a throwback. Same picks as Android
/// (AutoReelPicker); rendered only when their photos changed.
@MainActor
enum AutoReels {
    private static var running = false

    static func refresh(_ model: AppModel) async {
        guard !running, let store = model.store, let vault = model.vault else { return }
        running = true
        defer { running = false }
        let all = store.details(ids: store.ids(TimelineFilter()))
        let picks = AutoReelPicker.pick(all, today: .today())
        for pick in picks {
            if store.reels().contains(where: { $0.slot == pick.slot.key && $0.signature == pick.signature }) { continue }
            let title: String = {
                switch pick.variant {
                case .RECENT_DAYS: return L("auto_reel_recent")
                case .LATEST: return L("auto_reel_latest")
                case .PERSON: return L("recap_title_person", pick.person?.displayName ?? "")
                case .EVERYTHING: return L("auto_reel_everything")
                case .THIS_MONTH_YEARS_AGO: return L("auto_reel_this_month")
                case .THROUGH_THE_YEARS: return L("auto_reel_years")
                }
            }()
            let track = ReelTrack.all.first { $0.theme == pick.theme }
            let plan = RecapPlanner.plan(pick.memories, theme: pick.theme, length: .SHORT, title: title, subtitle: "", outroText: L("recap_outro"),
                                         dateLabel: { memoryDateText($0.memory.date) }, includeVideos: false, selectedMediaIds: pick.mediaIds,
                                         outroSubtitle: L("recap_outro_credit"), beatMs: track?.beatMs)
            let renderer = ReelRenderer(plan: plan, track: track, vault: vault, store: store)
            guard let file = try? await Task.detached(priority: .utility, operation: { try await renderer.render { _ in } }).value else { continue }
            defer { try? FileManager.default.removeItem(at: file) }
            guard let stored = try? vault.store(file: file, folder: .reels, ext: "mp4") else { continue }
            let entry = ReelEntry(title: title, path: stored.path, createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                                  durationMs: plan.totalDurationMs, slot: pick.slot.key, signature: pick.signature)
            (try? store.addReel(entry))?.forEach(vault.delete)
        }
    }
}
