import PhotosUI
import SwiftUI
import YaadeinDomain

// MARK: - Shared pickers

/// "When was this?" (DateChips.kt): Today, Yesterday, Pick a date, Month only, Year only, Roughly…
struct DateChips: View {
    @Binding var value: MemoryDate
    @Environment(\.look) private var look
    @State private var sheet: Sheet?

    enum Sheet: Identifiable { case day, month, year, label; var id: Int { hashValue } }

    var body: some View {
        let today = LocalDate.today()
        let isToday = value.kind == .EXACT && value.startEpochDay == today.epochDay && value.endEpochDay == nil
        let isYesterday = value.kind == .EXACT && value.startEpochDay == today.minusDays(1).epochDay && value.endEpochDay == nil
        let isPicked = value.kind == .EXACT && !isToday && !isYesterday
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("date_when")).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
            FlowLayout(spacing: Spacing.sm) {
                Chip(text: L("today"), selected: isToday) { value = Dates.exact(today) }
                Chip(text: L("yesterday"), selected: isYesterday) { value = Dates.exact(today.minusDays(1)) }
                Chip(text: isPicked ? memoryDateText(value) : L("date_pick"), selected: isPicked, icon: "calendar") { sheet = .day }
                Chip(text: value.kind == .MONTH ? memoryDateText(value) : L("date_month_only"), selected: value.kind == .MONTH) { sheet = .month }
                Chip(text: value.kind == .YEAR ? String(Dates.year(of: value)) : L("date_year_only"), selected: value.kind == .YEAR) { sheet = .year }
                Chip(text: value.kind == .FUZZY ? (value.label ?? "") : L("date_custom_label"), selected: value.kind == .FUZZY) { sheet = .label }
            }
        }
        .sheet(item: $sheet) { s in
            DatePickSheet(kind: s, initial: value) { value = $0 }.environment(\.look, look)
        }
    }
}

private struct DatePickSheet: View {
    let kind: DateChips.Sheet
    let initial: MemoryDate
    let done: (MemoryDate) -> Void
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var day = Date()
    @State private var year = LocalDate.today().year
    @State private var month = LocalDate.today().month
    @State private var label = ""
    @State private var anchorMonth = 1

    var body: some View {
        let today = LocalDate.today()
        NavigationStack {
            VStack(alignment: .leading, spacing: Spacing.md) {
                switch kind {
                case .day:
                    DatePicker(L("date_pick"), selection: $day, in: ...Date(), displayedComponents: .date).datePickerStyle(.graphical)
                case .month:
                    YearStepper(year: $year)
                    FlowLayout(spacing: Spacing.xs) {
                        ForEach(1...12, id: \.self) { m in Chip(text: Months.short[m - 1].localizedMonth(m), selected: m == month) { month = m } }
                    }
                case .year:
                    YearStepper(year: $year)
                case .label:
                    let suggestions: [(String, Int)] = [(L("date_label_summer"), 5), (L("date_label_monsoon"), 7), (L("date_label_winter"), 12),
                                                        (L("date_label_diwali"), 10), (L("date_label_early"), 2), (L("date_label_late"), 11)]
                    FlowLayout(spacing: Spacing.xs) {
                        ForEach(suggestions.indices, id: \.self) { i in
                            let s = suggestions[i]
                            Chip(text: s.0, selected: label.hasPrefix(s.0)) {
                                label = s.0
                                anchorMonth = s.1
                            }
                        }
                    }
                    OutlinedField(label: L("date_label_hint"), text: $label)
                    YearStepper(year: $year)
                }
                Spacer()
            }
            .padding(Spacing.gutter)
            .screenTitle(L(kind == .day ? "date_pick" : kind == .month ? "date_month_only" : kind == .year ? "date_year_only" : "date_custom_label"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("action_done")) {
                        switch kind {
                        case .day: done(Dates.exact(LocalDate(epochMillis: Int64(day.timeIntervalSince1970 * 1000), zone: .current)))
                        case .month: done(Dates.month(YearMonth(year: year, month: month)))
                        case .year: done(Dates.year(year))
                        case .label:
                            let t = label.trimmingCharacters(in: .whitespaces)
                            done(Dates.fuzzy(t.contains(String(year)) ? t : "\(t) \(year)", anchorYear: year, anchorMonth: anchorMonth))
                        }
                        dismiss()
                    }
                    .disabled(kind == .label && label.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents(kind == .day ? [.large] : [.medium])
        .onAppear {
            let start = Dates.start(initial)
            day = start.date(in: .current)
            year = min(start.year, today.year + 1)
            month = start.month
            label = initial.kind == .FUZZY ? (initial.label ?? "") : ""
        }
    }
}

private extension String {
    func localizedMonth(_ m: Int) -> String {
        let f = DateFormatter()
        f.locale = .current
        return f.shortStandaloneMonthSymbols[m - 1]
    }
}

struct YearStepper: View {
    @Binding var year: Int
    @Environment(\.look) private var look
    var body: some View {
        let max = LocalDate.today().year + 1
        HStack(spacing: Spacing.sm) {
            Button("−") { if year > 1900 { year -= 1 } }.buttonStyle(TextButtonStyle())
            Menu {
                ForEach((max - 61...max).reversed(), id: \.self) { y in Button(String(y)) { year = y } }
            } label: { Text(String(year)).font(look.type.titleLarge) }
            Button("+") { if year < max { year += 1 } }.buttonStyle(TextButtonStyle())
        }
    }
}

struct MoodPicker: View {
    @Binding var mood: Mood?
    @Environment(\.look) private var look
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("mood_title")).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
            FlowLayout(spacing: Spacing.sm) {
                ForEach(Mood.allCases, id: \.self) { m in Chip(text: moodLabel(m), selected: mood == m) { mood = mood == m ? nil : m } }
            }
        }
    }
}

struct CoverStylePicker: View {
    @Binding var style: String
    @Environment(\.look) private var look
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("event_cover")).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(CoverStyle.all, id: \.self) { s in
                        Button { style = s } label: {
                            CoverArt(style: s).frame(width: 72, height: 54).clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                                .overlay(RoundedRectangle(cornerRadius: Radius.sm).stroke(style == s ? look.colors.primary : .clear, lineWidth: 3))
                        }
                        .accessibilityLabel(s)
                        .accessibilityAddTraits(style == s ? .isSelected : [])
                    }
                }
            }
        }
    }
}

struct PeopleChips: View {
    @Binding var selected: [Int64]
    var title: String = L("who_was_there")
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let people = model.store?.persons() ?? []
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(title).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
            FlowLayout(spacing: Spacing.sm) {
                ForEach(people) { p in
                    Chip(text: p.displayName, selected: selected.contains(p.id)) {
                        if let i = selected.firstIndex(of: p.id) { selected.remove(at: i) } else { selected.append(p.id) }
                    }
                }
                NavigationLink(value: Route.personEdit(nil)) { ChipLabel(text: L("people_add"), icon: "plus") }.buttonStyle(.plain)
            }
        }
    }
}

/// Picked photos waiting to be saved, with remove buttons and the rejection dialog.
struct PickedMediaStrip: View {
    @Binding var items: [PickedMedia]
    @Environment(\.look) private var look
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.sm) {
                ForEach(items) { m in
                    ZStack(alignment: .topTrailing) {
                        SwiftUI.Group {
                            if let p = m.preview { Image(uiImage: p).resizable().scaledToFill() } else { Rectangle().fill(look.colors.surfaceContainerHigh) }
                        }
                        .frame(width: 88, height: 88)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                        .overlay(alignment: .bottomLeading) {
                            if m.kind == .VIDEO {
                                Text(L("event_video_badge")).font(look.type.labelSmall).foregroundStyle(.white).padding(Spacing.xxs)
                                    .background(Capsule().fill(.black.opacity(0.5))).padding(Spacing.xxs)
                            }
                        }
                        Button {
                            items.removeAll { $0.id == m.id }
                            m.discard()
                        } label: {
                            Image(systemName: "xmark.circle.fill").font(.system(size: 22)).foregroundStyle(.white, .black.opacity(0.6))
                        }
                        .accessibilityLabel(L("event_remove_photo"))
                        .padding(Spacing.xxs)
                    }
                }
            }
        }
    }
}

extension View {
    /// The "Some files were skipped" dialog (RejectedDialog).
    func rejectedAlert(_ rejected: Binding<[ImportRejection]>) -> some View {
        alert(L("reject_title"), isPresented: Binding(get: { !rejected.wrappedValue.isEmpty }, set: { if !$0 { rejected.wrappedValue = [] } })) {
            Button(L("action_done")) { rejected.wrappedValue = [] }
        } message: {
            let list = rejected.wrappedValue
            Text((list.prefix(5).map(\.message) + (list.count > 5 ? [L("reject_more", list.count - 5)] : [])).joined(separator: "\n"))
        }
    }
}

/// Saves a new memory with its picked media (create → import each item → first photo becomes the cover).
@MainActor
func saveMemory(_ memory: Memory, people: [Int64], media: [PickedMedia], keepOriginal: Bool, model: AppModel) async -> (Int64?, [ImportRejection]) {
    guard let store = model.store, let vault = model.vault, let id = try? store.createMemory(memory, personIds: people) else { return (nil, []) }
    var failures: [ImportRejection] = []
    for m in media {
        do { try await MediaImporter.save(m, memoryId: id, keepOriginal: keepOriginal, store: store, vault: vault) }
        catch let r as ImportRejection { failures.append(r) }
        catch { failures.append(.unreadable) }
    }
    Notifier.reschedule(model)
    return (id, failures)
}

// MARK: - Event-first

/// Event capture / edit (EventCaptureScreen.kt): date first, title, place, people, description, mood, media, cover.
struct EventCaptureView: View {
    let memoryId: Int64?
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var date = Dates.exact(.today())
    @State private var title = ""
    @State private var place = ""
    @State private var description = ""
    @State private var people: [Int64] = []
    @State private var mood: Mood?
    @State private var coverStyle = CoverStyle.defaultStyle
    @State private var picked: [PhotosPickerItem] = []
    @State private var media: [PickedMedia] = []
    @State private var keepOriginal = false
    @State private var titleError = false
    @State private var saving = false
    @State private var inspecting = false
    @State private var rejected: [ImportRejection] = []
    @State private var loaded = false

    var body: some View {
        let existing = memoryId.flatMap { model.store?.media(forMemory: $0).count } ?? 0
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                DateChips(value: $date)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    OutlinedField(label: L("event_title"), text: $title)
                    if titleError { Text(L("event_title_required")).font(look.type.bodySmall).foregroundStyle(look.colors.error) }
                }
                OutlinedField(label: L("event_place"), text: $place)
                PeopleChips(selected: $people)
                OutlinedField(label: L("event_description"), text: $description, axis: .vertical)
                MoodPicker(mood: $mood)
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text(L("event_media")).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
                    if existing > 0 { Text(L("event_media_existing", existing)).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant) }
                    PickedMediaStrip(items: $media)
                    if inspecting { ProgressView() }
                    PhotosPicker(selection: $picked, matching: .any(of: [.images, .videos])) {
                        Label(L("event_add_media"), systemImage: "photo.badge.plus").font(look.type.labelLarge)
                    }
                    .buttonStyle(TextButtonStyle())
                    Text(L("event_media_limits")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                    if !media.isEmpty {
                        Toggle(isOn: $keepOriginal) {
                            VStack(alignment: .leading) {
                                Text(L("event_optimize")).font(look.type.bodyLarge)
                                Text(L("event_optimize_help")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                            }
                        }
                    }
                }
                if media.isEmpty && existing == 0 { CoverStylePicker(style: $coverStyle) }
                Button(L("action_save")) { Task { await save() } }.buttonStyle(FilledButtonStyle(fullWidth: true)).disabled(saving)
            }
            .padding(Spacing.gutter)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenTitle(L(memoryId == nil ? "event_new_title" : "event_edit_title"))
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("action_save")) { Task { await save() } }.disabled(saving) } }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            inspecting = true
            Task {
                let (ok, bad) = await MediaImporter.inspect(items, optimize: model.settings.storeOptimizedCopies)
                media += ok
                rejected = bad
                inspecting = false
                if memoryId == nil, date.startEpochDay == LocalDate.today().epochDay, let first = ok.compactMap(\.takenAtMillis).min() {
                    date = Dates.exact(LocalDate(epochMillis: first, zone: .current))
                }
            }
        }
        .rejectedAlert($rejected)
        .onAppear(perform: load)
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        keepOriginal = !model.settings.storeOptimizedCopies
        if memoryId == nil, AppGroup.hasPendingShares {
            inspecting = true
            Task {
                let (ok, bad) = await MediaImporter.takeShared(optimize: model.settings.storeOptimizedCopies)
                media += ok
                rejected = bad
                inspecting = false
                if let first = ok.compactMap(\.takenAtMillis).min() { date = Dates.exact(LocalDate(epochMillis: first, zone: .current)) }
            }
        }
        guard let memoryId, let d = model.store?.detail(id: memoryId) else { return }
        date = d.memory.date
        title = d.memory.title
        place = d.memory.place
        description = d.memory.description
        people = d.people.map(\.id)
        mood = d.memory.mood
        coverStyle = d.memory.coverStyle
    }

    private func save() async {
        let t = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else {
            titleError = true
            return
        }
        guard let store = model.store, let vault = model.vault else { return }
        saving = true
        if let memoryId, var m = store.memory(id: memoryId) {
            m.title = t
            m.date = date
            m.place = place
            m.description = description
            m.mood = mood
            m.coverStyle = coverStyle
            try? store.updateMemory(m, personIds: people)
            for item in media {
                do { try await MediaImporter.save(item, memoryId: memoryId, keepOriginal: keepOriginal, store: store, vault: vault) }
                catch let r as ImportRejection { rejected.append(r) }
                catch { rejected.append(.unreadable) }
            }
            media = []
        } else {
            let memory = Memory(type: media.isEmpty ? .TEXT_ONLY : .EVENT, title: t, description: description, date: date, place: place,
                                mood: mood, coverStyle: coverStyle)
            let (_, failures) = await saveMemory(memory, people: people, media: media, keepOriginal: keepOriginal, model: model)
            media = []
            rejected = failures
        }
        saving = false
        if rejected.isEmpty { dismiss() }
    }
}

// MARK: - Text only + Polish

/// One line → a polished card (TextOnlyScreen.kt): live preview from the same PolishEngine as Android, examples, save.
struct TextOnlyView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var people: [Int64] = []

    var body: some View {
        let raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let result = raw.isEmpty ? nil : PolishEngine.polish(raw)
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L("text_hint")).font(look.type.bodyLarge).foregroundStyle(look.colors.onSurfaceVariant)
                TextField(L("text_placeholder"), text: $text, axis: .vertical)
                    .font(look.type.titleLarge)
                    .lineLimit(2...5)
                    .padding(Spacing.lg)
                    .background(RoundedRectangle(cornerRadius: Radius.md).stroke(look.colors.outline))
                if raw.isEmpty {
                    Text(L("text_examples")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                }
                if let result {
                    Text(L("text_preview")).font(look.type.titleSmall).accessibilityAddTraits(.isHeader)
                    let preview = Memory(id: -1, type: .TEXT_ONLY, title: result.title, description: result.description, date: result.date,
                                         place: result.place, mood: result.mood, coverStyle: result.coverStyle)
                    MemoryCardView(card: MemoryCard(memory: preview, people: (model.store?.persons() ?? []).filter { people.contains($0.id) },
                                                    cover: nil, mediaCount: 0))
                    if !result.dateDetected { Text(L("text_no_date")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant) }
                }
                PeopleChips(selected: $people)
                Button(L("text_save")) {
                    guard let r = result else { return }
                    Task {
                        _ = await saveMemory(Memory(type: .TEXT_ONLY, title: r.title, description: r.description, date: r.date, place: r.place,
                                                    mood: r.mood, coverStyle: r.coverStyle),
                                             people: people, media: [], keepOriginal: false, model: model)
                        dismiss()
                    }
                }
                .buttonStyle(FilledButtonStyle(fullWidth: true))
                .disabled(result == nil)
            }
            .padding(Spacing.gutter)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenTitle(L("text_title"))
    }
}

// MARK: - Quick capture

/// "Today" in one line, optional photo, people (QuickCaptureScreen.kt).
struct QuickCaptureView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var people: [Int64] = []
    @State private var picked: PhotosPickerItem?
    @State private var photo: PickedMedia?
    @State private var saving = false
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L("quick_subtitle")).font(look.type.bodyLarge).foregroundStyle(look.colors.onSurfaceVariant)
                TextField(L("quick_placeholder"), text: $text)
                    .font(look.type.titleLarge)
                    .submitLabel(.done)
                    .focused($focused)
                    .onSubmit { Task { await save() } }
                    .padding(Spacing.lg)
                    .background(RoundedRectangle(cornerRadius: Radius.md).stroke(look.colors.outline))
                HStack(spacing: Spacing.md) {
                    if let p = photo?.preview {
                        Image(uiImage: p).resizable().scaledToFill().frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    }
                    PhotosPicker(selection: $picked, matching: .any(of: [.images, .videos])) {
                        Label(L(photo == nil ? "quick_add_photo" : "quick_change_photo"), systemImage: "camera").font(look.type.labelLarge)
                    }
                    .buttonStyle(TextButtonStyle())
                }
                PeopleChips(selected: $people, title: L("quick_who"))
                Button(L("quick_save")) { Task { await save() } }
                    .buttonStyle(FilledButtonStyle(fullWidth: true))
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || saving)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("quick_title"))
        .onAppear { focused = true }
        .onChange(of: picked) { _, item in
            guard let item else { return }
            Task {
                let (ok, _) = await MediaImporter.inspect([item], optimize: model.settings.storeOptimizedCopies)
                photo?.discard()
                photo = ok.first
            }
        }
    }

    private func save() async {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !saving else { return }
        saving = true
        let memory = Memory(type: .QUICK, title: t, date: Dates.exact(.today()))
        _ = await saveMemory(memory, people: people, media: photo.map { [$0] } ?? [], keepOriginal: !model.settings.storeOptimizedCopies, model: model)
        photo = nil
        dismiss()
    }
}
