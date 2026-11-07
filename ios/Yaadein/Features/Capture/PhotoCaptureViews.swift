import PhotosUI
import SwiftUI
import YaadeinDomain

private func monthYearTitle(_ ym: YearMonth) -> String { "\(Dates.monthName(ym.month)) \(ym.year)" }

// MARK: - Photos first

/// Photo-first (PhotoFirstScreen.kt): pick → grouped by day / consecutive days (PhotoGrouper) → rename, change date,
/// tag people, merge with the one above, split, remove → save all.
struct PhotoFirstView: View {
    struct Draft: Identifiable {
        let id = UUID()
        var title: String
        var date: MemoryDate
        var items: [PickedMedia]
        var people: [Int64] = []
    }

    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var picked: [PhotosPickerItem] = []
    @State private var showPicker = false
    @State private var all: [PickedMedia] = []
    @State private var drafts: [Draft] = []
    @State private var inspecting = false
    @State private var saving = false
    @State private var rejected: [ImportRejection] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if inspecting {
                    HStack(spacing: Spacing.md) { ProgressView(); Text(L("photos_inspecting")).font(look.type.bodyMedium) }
                } else if drafts.isEmpty {
                    EmptyState(illustration: .photoStack, title: L("photos_empty_title"), message: L("photos_empty_message"),
                               actionLabel: L("photos_pick")) { showPicker = true }
                } else {
                    Text(LP("photos_grouped", drafts.count)).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    ForEach($drafts) { $d in draftCard($d) }
                    Button(L("photos_pick_more")) { showPicker = true }.buttonStyle(TextButtonStyle())
                    Button(L("photos_save_all", drafts.count)) { Task { await saveAll() } }
                        .buttonStyle(FilledButtonStyle(fullWidth: true)).disabled(saving)
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("photos_title"))
        .photosPicker(isPresented: $showPicker, selection: $picked, matching: .any(of: [.images, .videos]))
        .onAppear { if drafts.isEmpty && all.isEmpty { showPicker = true } }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            inspecting = true
            Task {
                let (ok, bad) = await MediaImporter.inspect(items, optimize: model.settings.storeOptimizedCopies)
                all += ok
                rejected = bad
                regroup()
                inspecting = false
            }
        }
        .rejectedAlert($rejected)
    }

    private func regroup() {
        let byKey = Dictionary(uniqueKeysWithValues: all.map { ($0.id.uuidString, $0) })
        drafts = PhotoGrouper.group(all.map(\.pickedItem), zone: .current).map { g in
            let date: MemoryDate
            if let s = g.startDate {
                date = g.isMultiDay ? Dates.exact(s, end: g.endDate) : Dates.exact(s)
            } else {
                date = Dates.exact(.today())
            }
            let title: String
            if let s = g.startDate {
                title = g.isMultiDay ? monthYearTitle(s.yearMonth) : "\(s.day) \(Dates.monthName(s.month)) \(s.year)"
            } else {
                title = ""
            }
            return Draft(title: title, date: date, items: g.items.compactMap { byKey[$0.key] })
        }
    }

    private func dateOf(_ items: [PickedMedia], fallback: MemoryDate) -> MemoryDate {
        let days = items.compactMap(\.takenAtMillis).map { LocalDate(epochMillis: $0, zone: .current) }
        guard let lo = days.min(), let hi = days.max() else { return fallback }
        return Dates.exact(lo, end: hi)
    }

    private func draftCard(_ d: Binding<Draft>) -> some View {
        let index = drafts.firstIndex { $0.id == d.wrappedValue.id } ?? 0
        return VStack(alignment: .leading, spacing: Spacing.md) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.xs) {
                    ForEach(d.wrappedValue.items) { m in
                        SwiftUI.Group {
                            if let p = m.preview { Image(uiImage: p).resizable().scaledToFill() } else { Rectangle().fill(look.colors.surfaceContainerHigh) }
                        }
                        .frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                    }
                }
            }
            OutlinedField(label: L("event_title"), text: d.title)
            DateChips(value: d.date)
            PeopleChips(selected: d.people)
            HStack {
                if index > 0 {
                    Button(L("photos_merge_up")) {
                        let cur = drafts.remove(at: index)
                        drafts[index - 1].items += cur.items
                        drafts[index - 1].date = dateOf(drafts[index - 1].items, fallback: drafts[index - 1].date)
                        drafts[index - 1].people = Array(Set(drafts[index - 1].people + cur.people))
                    }
                    .buttonStyle(TextButtonStyle())
                }
                if d.wrappedValue.items.count > 1 {
                    Button(L("photos_split")) {
                        var cur = drafts[index]
                        let half = cur.items.count / 2
                        let second = Array(cur.items[half...])
                        cur.items = Array(cur.items[..<half])
                        cur.date = dateOf(cur.items, fallback: cur.date)
                        drafts[index] = cur
                        drafts.insert(Draft(title: cur.title, date: dateOf(second, fallback: cur.date), items: second, people: cur.people), at: index + 1)
                    }
                    .buttonStyle(TextButtonStyle())
                }
                Spacer()
                Button(L("photos_remove_group"), role: .destructive) {
                    drafts[index].items.forEach { $0.discard() }
                    drafts.remove(at: index)
                }
                .buttonStyle(TextButtonStyle())
            }
        }
        .padding(Spacing.lg)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainerLow))
    }

    private func saveAll() async {
        saving = true
        var failures: [ImportRejection] = []
        for d in drafts {
            let polished = PolishEngine.polish(d.title.isEmpty ? "memory" : d.title)
            let memory = Memory(type: .EVENT, title: d.title.isEmpty ? polished.title : d.title, date: d.date, place: polished.place,
                                mood: polished.mood, coverStyle: polished.coverStyle)
            let (_, f) = await saveMemory(memory, people: d.people, media: d.items, keepOriginal: !model.settings.storeOptimizedCopies, model: model)
            failures += f
        }
        drafts = []
        all = []
        saving = false
        rejected = failures
        if failures.isEmpty { dismiss() }
    }
}

// MARK: - Bulk upload

/// Bulk upload by year or month (BulkUploadScreen.kt): many picks, filter by years and months, one memory per split.
struct BulkUploadView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var picked: [PhotosPickerItem] = []
    @State private var showPicker = false
    @State private var all: [PickedMedia] = []
    @State private var splitBy: SplitBy = .MONTH
    @State private var filter = PeriodFilter()
    @State private var titles: [String: String] = [:]
    @State private var removed: Set<String> = []
    @State private var includeUndated = false
    @State private var undatedTitle = ""
    @State private var undatedDate = Dates.exact(.today())
    @State private var people: [Int64] = []
    @State private var inspecting = false
    @State private var saving = false
    @State private var rejected: [ImportRejection] = []

    private func key(_ b: PeriodBucket) -> String { b.yearMonth.map { String(format: "%04d-%02d", $0.year, $0.month) } ?? String(b.year) }
    private func suggested(_ b: PeriodBucket) -> String { b.yearMonth.map(monthYearTitle) ?? String(b.year) }

    var body: some View {
        let items = all.map(\.pickedItem)
        let split = PeriodGrouper.split(items, splitBy: splitBy, filter: filter, zone: .current)
        let buckets = split.buckets.filter { !removed.contains(key($0)) }
        let years = PeriodGrouper.years(items, zone: .current)
        let byKey = Dictionary(uniqueKeysWithValues: all.map { ($0.id.uuidString, $0) })
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if all.isEmpty && !inspecting {
                    EmptyState(illustration: .photoStack, title: L("bulk_title"), message: L("bulk_empty_message"), actionLabel: L("photos_pick")) { showPicker = true }
                } else {
                    if inspecting { ProgressView() }
                    Button(L("bulk_add_more")) { showPicker = true }.buttonStyle(TextButtonStyle())
                    Text(L("bulk_split_label")).font(look.type.titleSmall)
                    Picker(L("bulk_split_label"), selection: $splitBy) {
                        Text(L("bulk_split_month")).tag(SplitBy.MONTH)
                        Text(L("bulk_split_year")).tag(SplitBy.YEAR)
                    }
                    .pickerStyle(.segmented)
                    if !years.isEmpty {
                        Text(L("bulk_filter_years")).font(look.type.titleSmall)
                        FlowLayout(spacing: Spacing.sm) {
                            ForEach(years, id: \.self) { y in
                                Chip(text: String(y), selected: filter.years.contains(y)) {
                                    if filter.years.contains(y) { filter.years.remove(y) } else { filter.years.insert(y) }
                                }
                            }
                        }
                    }
                    Text(L("bulk_filter_months")).font(look.type.titleSmall)
                    FlowLayout(spacing: Spacing.sm) {
                        ForEach(1...12, id: \.self) { m in
                            Chip(text: Dates.monthName(m), selected: filter.months.contains(m)) {
                                if filter.months.contains(m) { filter.months.remove(m) } else { filter.months.insert(m) }
                            }
                        }
                    }
                    Text(L("bulk_filter_hint")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                    HStack {
                        Text(LP("bulk_will_create", buckets.count + (includeUndated && !split.undated.isEmpty ? 1 : 0))).font(look.type.titleMedium)
                        Spacer()
                        if !removed.isEmpty || !filter.isEmpty {
                            Button(L("bulk_reset")) {
                                removed = []
                                filter = PeriodFilter()
                            }
                            .buttonStyle(TextButtonStyle())
                        }
                    }
                    if split.filteredOut > 0 { Text(LP("bulk_filtered_out", split.filteredOut)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant) }
                    if buckets.isEmpty && !all.isEmpty { Text(L("bulk_nothing_matches")).font(look.type.bodyMedium) }
                    ForEach(buckets, id: \.self) { b in
                        let k = key(b)
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            HStack {
                                Text(suggested(b)).font(look.type.titleMedium)
                                Text(LP("media_count", b.items.count)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                                Spacer()
                                Button { removed.insert(k) } label: { Image(systemName: "xmark") }.accessibilityLabel(L("action_delete"))
                            }
                            HStack(spacing: Spacing.xs) {
                                ForEach(b.items.prefix(5), id: \.key) { item in
                                    if let p = byKey[item.key]?.preview {
                                        Image(uiImage: p).resizable().scaledToFill().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: Radius.sm))
                                    }
                                }
                                if b.items.count > 5 { Text(L("bulk_more_count", b.items.count - 5)).font(look.type.labelLarge) }
                            }
                            TextField(suggested(b), text: Binding(get: { titles[k] ?? suggested(b) }, set: { titles[k] = $0 }))
                                .font(look.type.bodyLarge)
                                .padding(.horizontal, Spacing.md).frame(minHeight: look.sizes.touchTarget)
                                .background(RoundedRectangle(cornerRadius: Radius.sm).stroke(look.colors.outline))
                        }
                        .padding(Spacing.lg)
                        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainerLow))
                    }
                    if !split.undated.isEmpty {
                        Toggle(isOn: $includeUndated) {
                            VStack(alignment: .leading) {
                                Text(LP("bulk_undated", split.undated.count)).font(look.type.titleSmall)
                                Text(L("bulk_undated_sub")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                            }
                        }
                        if includeUndated {
                            OutlinedField(label: L("event_title"), text: $undatedTitle)
                            DateChips(value: $undatedDate)
                        }
                    }
                    PeopleChips(selected: $people, title: L("bulk_people"))
                    Button(L("photos_save_all", buckets.count + (includeUndated && !split.undated.isEmpty ? 1 : 0))) {
                        Task { await save(buckets: buckets, undated: split.undated, byKey: byKey) }
                    }
                    .buttonStyle(FilledButtonStyle(fullWidth: true))
                    .disabled(saving || (buckets.isEmpty && !(includeUndated && !split.undated.isEmpty)))
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("bulk_title"))
        .photosPicker(isPresented: $showPicker, selection: $picked, maxSelectionCount: nil, matching: .any(of: [.images, .videos]))
        .onAppear { if all.isEmpty { showPicker = true } }
        .onChange(of: picked) { _, items in
            guard !items.isEmpty else { return }
            picked = []
            inspecting = true
            Task {
                let (ok, bad) = await MediaImporter.inspect(items, optimize: model.settings.storeOptimizedCopies)
                all += ok
                rejected = bad
                inspecting = false
            }
        }
        .rejectedAlert($rejected)
    }

    private func save(buckets: [PeriodBucket], undated: [PickedItem], byKey: [String: PickedMedia]) async {
        saving = true
        var groups: [(String, MemoryDate, [PickedMedia])] = buckets.map { b in
            let date = b.yearMonth.map { Dates.month($0) } ?? Dates.year(b.year)
            return (titles[key(b)] ?? suggested(b), date, b.items.compactMap { byKey[$0.key] })
        }
        if includeUndated && !undated.isEmpty { groups.append((undatedTitle, undatedDate, undated.compactMap { byKey[$0.key] })) }
        var failures: [ImportRejection] = []
        for (title, date, media) in groups {
            let polished = PolishEngine.polish(title.isEmpty ? "memory" : title)
            let memory = Memory(type: .EVENT, title: title.isEmpty ? polished.title : title, date: date, place: polished.place,
                                mood: polished.mood, coverStyle: polished.coverStyle)
            let (_, f) = await saveMemory(memory, people: people, media: media, keepOriginal: !model.settings.storeOptimizedCopies, model: model)
            failures += f
        }
        all = []
        saving = false
        rejected = failures
        if failures.isEmpty { dismiss() }
    }
}

// MARK: - Fill in the past

/// "Anything memorable in 2019?" year by year (PastWizardScreen.kt), each line polished into a text memory.
struct PastWizardView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var index = 0
    @State private var input = ""
    @State private var people: [Int64] = []
    @State private var added = 0
    private let years: [Int] = { let y = LocalDate.today().year; return Array(stride(from: y - 1, through: y - 25, by: -1)) }()

    var body: some View {
        let _ = model.revision
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if index >= years.count {
                    EmptyState(illustration: .calendarStar, title: L("past_done_title"), message: LP("past_added", added),
                               actionLabel: L("action_done")) { dismiss() }
                } else {
                    let year = years[index]
                    let existing = model.store?.countsByYear().first { $0.year == year }?.count ?? 0
                    ProgressView(value: Double(index), total: Double(years.count))
                    Text(L("past_question", year)).font(look.type.headlineMedium)
                    Text(existing > 0 ? LP("past_existing", existing) : L("past_nothing_yet")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    TextField(L("past_placeholder"), text: $input, axis: .vertical)
                        .font(look.type.titleMedium).lineLimit(2...4)
                        .padding(Spacing.lg)
                        .background(RoundedRectangle(cornerRadius: Radius.md).stroke(look.colors.outline))
                    PeopleChips(selected: $people)
                    Button(L("past_save_more")) { save(year, next: false) }.buttonStyle(FilledButtonStyle(fullWidth: true, tonal: true)).disabled(input.isBlank)
                    Button(L("past_save_next")) { save(year, next: true) }.buttonStyle(FilledButtonStyle(fullWidth: true)).disabled(input.isBlank)
                    HStack {
                        if index > 0 { Button(L("past_previous")) { index -= 1; input = "" }.buttonStyle(TextButtonStyle()) }
                        Spacer()
                        Button(L("past_skip_year")) { skip() }.buttonStyle(TextButtonStyle())
                    }
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("past_title"))
    }

    private func skip() {
        index += 1
        input = ""
        people = []
    }

    private func save(_ year: Int, next: Bool) {
        guard let store = model.store, !input.isBlank else { return }
        let p = PolishEngine.polish(input)
        // Keep the parsed date only if it falls inside the year being asked about.
        let date = p.dateDetected && Dates.year(of: p.date) == year ? p.date : Dates.year(year)
        let title = p.dateDetected ? p.title : "\(p.title), \(year)"
        _ = try? store.createMemory(Memory(type: .TEXT_ONLY, title: title, description: p.description, date: date, place: p.place,
                                           mood: p.mood, coverStyle: p.coverStyle), personIds: people)
        added += 1
        input = ""
        people = []
        if next { skip() }
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
