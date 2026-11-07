import AVKit
import PhotosUI
import SwiftUI
import YaadeinData
import YaadeinDomain

func moodLabel(_ m: Mood) -> String {
    switch m {
    case .JOYFUL: return L("mood_joyful")
    case .CALM: return L("mood_calm")
    case .NOSTALGIC: return L("mood_nostalgic")
    case .PROUD: return L("mood_proud")
    case .FUNNY: return L("mood_funny")
    case .SAD: return L("mood_sad")
    case .CELEBRATION: return L("mood_celebration")
    }
}

/// Memory detail (MemoryDetailScreen.kt): 4:3 hero with actions, title, date, place, mood, description, people,
/// media grid (long-press → set as cover), add photos, delete.
struct MemoryDetailView: View {
    let memoryId: Int64
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false
    @State private var picking: [PhotosPickerItem] = []
    @State private var showPicker = false
    @State private var importing = false
    @State private var rejected: [ImportRejection] = []

    var body: some View {
        let _ = model.revision
        if let detail = model.store?.detail(id: memoryId) {
            content(detail)
        } else {
            Color.clear.onAppear { dismiss() }
        }
    }

    private func content(_ d: MemoryDetail) -> some View {
        let cover = d.media.first { $0.id == d.memory.coverMediaId } ?? d.media.first
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    if let cover {
                        NavigationLink(value: Route.mediaViewer(memoryId: memoryId, index: d.media.firstIndex(of: cover) ?? 0)) {
                            VaultImage(path: cover.thumbPath)
                        }
                        .accessibilityLabel(L("memory_cover_cd", d.memory.title))
                    } else {
                        CoverArt(style: d.memory.coverStyle, seed: d.memory.id)
                    }
                }
                .frame(maxWidth: .infinity)
                .aspectRatio(4 / 3, contentMode: .fit)
                .clipped()

                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: Spacing.lg)
                    Text(d.memory.title).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader)
                    Spacer().frame(height: Spacing.xs)
                    HStack(spacing: Spacing.sm) {
                        Text(memoryDateText(d.memory.date)).font(look.type.labelLarge).foregroundStyle(look.colors.primary)
                        if !d.memory.place.isEmpty {
                            Image(systemName: "mappin.and.ellipse").foregroundStyle(look.colors.onSurfaceVariant)
                            Text(d.memory.place).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                        }
                        if let mood = d.memory.mood {
                            Text(moodLabel(mood))
                                .font(look.type.labelMedium)
                                .padding(.horizontal, Spacing.sm).padding(.vertical, Spacing.xxs)
                                .background(Capsule().fill((look.colors.moodColors[mood] ?? look.colors.primary).opacity(0.18)))
                                .foregroundStyle(look.colors.moodColors[mood] ?? look.colors.primary)
                        }
                    }
                    if !d.memory.description.isEmpty {
                        Spacer().frame(height: Spacing.lg)
                        Text(d.memory.description).font(look.type.bodyLarge)
                    }
                    if let voice = d.memory.voiceNotePath {
                        Spacer().frame(height: Spacing.lg)
                        VoiceNoteCard(memoryId: memoryId, path: voice)
                    }
                    if !d.people.isEmpty {
                        Spacer().frame(height: Spacing.xl)
                        Text(L("memory_who_was_there")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                        Spacer().frame(height: Spacing.sm)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.md) {
                                ForEach(d.people) { p in
                                    NavigationLink(value: Route.person(p.id)) {
                                        VStack(spacing: Spacing.xs) {
                                            PersonAvatar(person: p, size: look.sizes.avatarLg)
                                            Text(p.displayName).font(look.type.labelMedium).lineLimit(1).foregroundStyle(look.colors.onSurface)
                                        }
                                        .padding(Spacing.xs)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    Spacer().frame(height: Spacing.xl)
                    HStack {
                        Text(d.media.isEmpty ? L("memory_photos") : LP("media_count", d.media.count))
                            .font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                        Spacer()
                        Button(L("memory_add_photos")) { showPicker = true }.buttonStyle(TextButtonStyle())
                    }
                    if importing { ProgressView().frame(maxWidth: .infinity) }
                    Spacer().frame(height: Spacing.sm)
                    if d.media.isEmpty {
                        Button { showPicker = true } label: {
                            Text(L("memory_no_photos_hint")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                                .padding(Spacing.lg).frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainerLow))
                        }
                        .buttonStyle(.plain)
                    } else {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.xs), count: 3), spacing: Spacing.xs) {
                            ForEach(Array(d.media.enumerated()), id: \.element.id) { index, media in
                                NavigationLink(value: Route.mediaViewer(memoryId: memoryId, index: index)) {
                                    MediaThumb(media: media, isCover: media.id == d.memory.coverMediaId)
                                }
                                .buttonStyle(.plain)
                                .contextMenu {
                                    if media.kind == .PHOTO {
                                        Button(L("memory_set_cover")) { try? model.store?.setCover(memoryId: memoryId, mediaId: media.id) }
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xxxl)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(look.colors.surface.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink(value: Route.eventCapture(memoryId: memoryId)) { Image(systemName: "pencil") }
                    .accessibilityLabel(L("action_edit"))
                MemoryShareMenu(detail: d)
                NavigationLink(value: Route.voiceCapture(memoryId: memoryId, prompt: nil)) { Image(systemName: "mic") }
                    .accessibilityLabel(L(d.memory.voiceNotePath == nil ? "memory_record_voice" : "memory_rerecord_voice"))
                NavigationLink(value: Route.recap(.memory(memoryId))) { Image(systemName: "film") }
                    .accessibilityLabel(L("memory_make_recap"))
                Menu {
                    Button(L("memory_add_photos"), systemImage: "photo.badge.plus") { showPicker = true }
                    Button(L("action_delete"), systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityLabel(L("action_more"))
            }
        }
        .photosPicker(isPresented: $showPicker, selection: $picking, matching: .any(of: [.images, .videos]))
        .onChange(of: picking) { _, items in
            guard !items.isEmpty else { return }
            picking = []
            Task { await addMedia(items) }
        }
        .alert(L("memory_delete_title"), isPresented: $confirmDelete) {
            Button(L("action_delete"), role: .destructive) { delete() }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { Text(L("memory_delete_message")) }
        .alert(L("reject_title"), isPresented: Binding(get: { !rejected.isEmpty }, set: { if !$0 { rejected = [] } })) {
            Button(L("action_done")) { rejected = [] }
        } message: { Text(rejected.map(\.message).joined(separator: "\n")) }
    }

    private func addMedia(_ items: [PhotosPickerItem]) async {
        guard let store = model.store, let vault = model.vault else { return }
        importing = true
        let (ok, bad) = await MediaImporter.inspect(items, optimize: model.settings.storeOptimizedCopies)
        var failures = bad
        for media in ok {
            do { try await MediaImporter.save(media, memoryId: memoryId, keepOriginal: !model.settings.storeOptimizedCopies, store: store, vault: vault) }
            catch let r as ImportRejection { failures.append(r) }
            catch { failures.append(.unreadable) }
        }
        importing = false
        rejected = failures
    }

    private func delete() {
        guard let store = model.store, let vault = model.vault else { return }
        if let paths = try? store.deleteMemory(id: memoryId) { paths.forEach(vault.delete) }
        dismiss()
    }
}

struct MediaThumb: View {
    let media: Media
    let isCover: Bool
    @Environment(\.look) private var look

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay(VaultImage(path: media.thumbPath))
            .overlay(alignment: .bottomLeading) {
                if media.kind == .VIDEO {
                    Image(systemName: "play.circle.fill").font(.system(size: 22)).foregroundStyle(.white).padding(Spacing.xs).shadow(radius: 2)
                }
            }
            .overlay(alignment: .topTrailing) {
                if isCover {
                    Text(L("memory_cover_badge")).font(look.type.labelSmall).foregroundStyle(.white)
                        .padding(.horizontal, Spacing.xs).padding(.vertical, 2)
                        .background(Capsule().fill(.black.opacity(0.5))).padding(Spacing.xs)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            .accessibilityElement()
            .accessibilityLabel(media.kind == .VIDEO ? L("memory_play_video") : (media.caption.isEmpty ? L("memory_photos") : media.caption))
    }
}

// MARK: - Viewer

/// Full-screen pager (MediaViewerScreen.kt): pinch-zoom photos, videos streamed from the vault, captions, tag people,
/// remove, previous/next.
struct MediaViewerView: View {
    let memoryId: Int64
    let startIndex: Int
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var index = 0
    @State private var caption = ""
    @State private var tagging = false
    @State private var confirmRemove = false

    var body: some View {
        let _ = model.revision
        let media = model.store?.media(forMemory: memoryId) ?? []
        VStack(spacing: 0) {
            if media.isEmpty {
                Color.clear.onAppear { dismiss() }
            } else {
                TabView(selection: $index) {
                    ForEach(Array(media.enumerated()), id: \.element.id) { i, m in
                        SwiftUI.Group {
                            if m.kind == .VIDEO { EncryptedVideoPlayer(path: m.encryptedPath) } else { ZoomableVaultImage(path: m.encryptedPath) }
                        }
                        .tag(i)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .background(Color.black)
                let current = media[min(index, media.count - 1)]
                VStack(spacing: Spacing.sm) {
                    HStack {
                        Button { withAnimation { index = max(0, index - 1) } } label: { Image(systemName: "chevron.left") }
                            .disabled(index == 0).accessibilityLabel(L("viewer_previous"))
                        Spacer()
                        Text(L("viewer_of", index + 1, media.count)).font(look.type.labelLarge)
                        Spacer()
                        Button { withAnimation { index = min(media.count - 1, index + 1) } } label: { Image(systemName: "chevron.right") }
                            .disabled(index >= media.count - 1).accessibilityLabel(L("action_next"))
                    }
                    .frame(minHeight: look.sizes.touchTarget)
                    TextField(L("viewer_caption_hint"), text: $caption)
                        .font(look.type.bodyLarge)
                        .padding(.horizontal, Spacing.md)
                        .frame(minHeight: look.sizes.touchTarget)
                        .background(RoundedRectangle(cornerRadius: Radius.sm).fill(Color.white.opacity(0.12)))
                        .onSubmit { try? model.store?.updateCaption(mediaId: current.id, caption: caption) }
                    HStack {
                        Button(L("viewer_tag_people"), systemImage: "person.crop.circle.badge.plus") { tagging = true }
                        Spacer()
                        Button(L("viewer_remove"), systemImage: "trash", role: .destructive) { confirmRemove = true }
                    }
                    .font(look.type.labelLarge)
                }
                .padding(Spacing.lg)
                .foregroundStyle(.white)
                .background(Color.black)
                .onChange(of: index) { _, i in caption = media[min(i, media.count - 1)].caption }
                .sheet(isPresented: $tagging) {
                    PeoplePicker(selected: Set(model.store?.mediaPeople(mediaId: current.id) ?? [])) { ids in
                        try? model.store?.setMediaPeople(mediaId: current.id, personIds: Array(ids))
                    }
                    .environment(\.look, look)
                }
                .alert(L("viewer_remove"), isPresented: $confirmRemove) {
                    Button(L("action_delete"), role: .destructive) {
                        if let paths = try? model.store?.deleteMedia(id: current.id) { paths.forEach { model.vault?.delete($0) } }
                        index = max(0, index - 1)
                    }
                    Button(L("action_cancel"), role: .cancel) {}
                }
            }
        }
        .background(Color.black.ignoresSafeArea())
        .toolbarBackground(.black, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .onAppear {
            index = min(startIndex, max(0, media.count - 1))
            caption = media.indices.contains(index) ? media[index].caption : ""
        }
    }
}

/// Pinch to zoom, double tap to reset. Paging only works while not zoomed (the Android fix for "no next/previous").
struct ZoomableVaultImage: View {
    let path: String
    @State private var scale: CGFloat = 1
    @State private var last: CGFloat = 1
    @State private var offset: CGSize = .zero

    var body: some View {
        VaultImage(path: path, full: true, contentMode: .fit)
            .scaleEffect(scale)
            .offset(offset)
            .gesture(MagnifyGesture()
                .onChanged { scale = max(1, last * $0.magnification) }
                .onEnded { _ in
                    last = scale
                    if scale <= 1 { offset = .zero }
                })
            .simultaneousGesture(scale > 1 ? DragGesture().onChanged { offset = $0.translation } : nil)
            .onTapGesture(count: 2) {
                withAnimation {
                    scale = 1
                    last = 1
                    offset = .zero
                }
            }
    }
}

/// Picks people (who was there / tag people).
struct PeoplePicker: View {
    @State var selected: Set<Int64>
    let done: (Set<Int64>) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(model.store?.persons() ?? []) { p in
                        Chip(text: p.displayName, selected: selected.contains(p.id)) {
                            if selected.contains(p.id) { selected.remove(p.id) } else { selected.insert(p.id) }
                        }
                    }
                }
                .padding(Spacing.gutter)
            }
            .screenTitle(L("who_was_there"))
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_done")) {
                        done(selected)
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
