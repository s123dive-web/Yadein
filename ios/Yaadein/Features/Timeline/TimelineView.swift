import SwiftUI
import YaadeinDomain

/// Timeline items: year and month headers between cards (TimelineItem / TimelineSeparator on Android).
enum TimelineItem: Identifiable {
    case year(Int, firstMonth: Int)
    case month(Int, Int)
    case card(MemoryCard)

    var id: String {
        switch self {
        case let .year(y, _): return "year-\(y)"
        case let .month(y, m): return "month-\(y)-\(m)"
        case let .card(c): return "memory-\(c.memory.id)"
        }
    }

    static func build(_ cards: [MemoryCard]) -> [TimelineItem] {
        var out: [TimelineItem] = []
        var prev: LocalDate?
        for c in cards {
            let d = Dates.start(c.memory.date)
            if prev == nil || prev!.year != d.year {
                out.append(.year(d.year, firstMonth: d.month))
            } else if prev!.month != d.month {
                out.append(.month(d.year, d.month))
            }
            out.append(.card(c))
            prev = d
        }
        return out
    }
}

/// Home river of time (TimelineScreen.kt): layout switch (cards / compact / grid), search, filters, empty states.
struct TimelineView: View {
    let onCreate: () -> Void
    @Binding var placeFilter: String?
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var filter = TimelineFilter()
    @State private var searchActive = false
    @State private var filtersOpen = false

    var body: some View {
        let _ = model.revision
        let store = model.store
        let cards = store?.cards(filter) ?? []
        let total = store?.memoryCount ?? 0
        let layout = model.settings.timelineLayout
        VStack(spacing: 0) {
            if searchActive {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(look.colors.onSurfaceVariant)
                    TextField(L("timeline_search_hint"), text: $filter.query).font(look.type.bodyLarge).submitLabel(.search)
                }
                .padding(.horizontal, Spacing.lg)
                .frame(minHeight: look.sizes.touchTarget)
                .background(RoundedRectangle(cornerRadius: Radius.sm).stroke(look.colors.outline))
                .padding(.horizontal, Spacing.gutter)
                .padding(.vertical, Spacing.sm)
                .transition(.opacity)
            }
            activeFilterRow
            if cards.isEmpty && total == 0 {
                ScrollView {
                    EmptyState(illustration: .photoStack, title: L("timeline_empty_title"), message: L("timeline_empty_message"),
                               actionLabel: L("timeline_empty_action"), action: onCreate)
                }
            } else if cards.isEmpty {
                ScrollView {
                    EmptyState(illustration: .searchGlass, title: L("timeline_no_results_title"), message: L("timeline_no_results_message"),
                               actionLabel: L("timeline_clear_filters")) {
                        filter = TimelineFilter()
                        searchActive = false
                    }
                }
            } else if layout == .GRID {
                grid(cards)
            } else {
                list(cards, dense: layout == .COMPACT)
            }
        }
        .background(look.colors.surface.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Text(L("timeline_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader)
            }
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { cycleLayout() } label: {
                    Image(systemName: layout == .CARDS ? "rectangle.grid.1x2" : layout == .COMPACT ? "list.bullet" : "square.grid.3x3")
                }
                .accessibilityLabel(L("timeline_layout", L(layout == .CARDS ? "timeline_layout_cards" : layout == .COMPACT ? "timeline_layout_compact" : "timeline_layout_grid")))
                Button {
                    withAnimation(Motion.standard) {
                        searchActive.toggle()
                        if !searchActive { filter.query = "" }
                    }
                } label: { Image(systemName: searchActive ? "xmark" : "magnifyingglass") }
                    .accessibilityLabel(L(searchActive ? "action_close" : "timeline_search"))
                Button { filtersOpen = true } label: {
                    Image(systemName: "line.3.horizontal.decrease")
                        .overlay(alignment: .topTrailing) {
                            if !filterWithoutQuery.isEmpty { Circle().fill(look.colors.secondary).frame(width: 8, height: 8) }
                        }
                }
                .accessibilityLabel(L("timeline_filters"))
            }
        }
        .toolbarBackground(look.colors.surface, for: .navigationBar)
        .sheet(isPresented: $filtersOpen) {
            FilterSheet(filter: filter) { filter = $0 }
                .environment(\.look, look)
                .presentationDetents([.large])
        }
        .onAppear(perform: consumePlaceFilter)
        .onChange(of: placeFilter) { _, _ in consumePlaceFilter() }
    }

    private var filterWithoutQuery: TimelineFilter {
        var f = filter
        f.query = ""
        return f
    }

    private func consumePlaceFilter() {
        if let p = placeFilter {
            filter = TimelineFilter(place: p)
            placeFilter = nil
        }
    }

    private func cycleLayout() {
        model.updateSettings { s in
            switch s.timelineLayout {
            case .CARDS: s.timelineLayout = .COMPACT
            case .COMPACT: s.timelineLayout = .GRID
            case .GRID: s.timelineLayout = .CARDS
            }
        }
    }

    @ViewBuilder private var activeFilterRow: some View {
        let f = filterWithoutQuery
        if !f.isEmpty {
            HStack(spacing: Spacing.sm) {
                let people = (model.store?.persons() ?? []).filter { f.personIds.contains($0.id) }
                ForEach(people.prefix(3)) { p in Chip(text: p.displayName, selected: true) { filter = TimelineFilter() } }
                if let place = f.place { Chip(text: place, selected: true) { filter = TimelineFilter() } }
                if !f.types.isEmpty || f.fromEpochDay != nil || f.toEpochDay != nil || f.groupId != nil {
                    Chip(text: L("timeline_filters"), selected: true) { filter = TimelineFilter() }
                }
                Spacer()
                Button { filter = TimelineFilter() } label: { Image(systemName: "xmark") }
                    .frame(width: look.sizes.touchTarget, height: look.sizes.touchTarget)
                    .accessibilityLabel(L("timeline_clear_filters"))
            }
            .padding(.horizontal, Spacing.gutter)
            .padding(.vertical, Spacing.xs)
        }
    }

    private func list(_ cards: [MemoryCard], dense: Bool) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: dense ? Spacing.xs : Spacing.md) {
                ForEach(TimelineItem.build(cards)) { item in
                    switch item {
                    case let .year(y, firstMonth):
                        VStack(alignment: .leading, spacing: 0) {
                            Text(String(y)).font(look.type.displayMedium).foregroundStyle(look.colors.timelineYear).accessibilityAddTraits(.isHeader)
                            Text(Dates.monthName(firstMonth)).font(look.type.titleMedium).foregroundStyle(look.colors.onSurfaceVariant)
                        }
                        .padding(.top, Spacing.lg)
                        .padding(.bottom, Spacing.xs)
                    case let .month(_, m):
                        Text(Dates.monthName(m)).font(look.type.titleMedium).foregroundStyle(look.colors.onSurfaceVariant)
                            .padding(.top, Spacing.sm).accessibilityAddTraits(.isHeader)
                    case let .card(card):
                        NavigationLink(value: Route.memory(card.memory.id)) {
                            if dense {
                                CompactMemoryRow(card: card)
                            } else {
                                HStack(alignment: .top, spacing: 0) {
                                    Circle().fill(look.colors.primary).frame(width: look.sizes.timelineDot, height: look.sizes.timelineDot)
                                        .padding(.top, Spacing.lg)
                                        .frame(width: look.sizes.timelineDot + Spacing.md, alignment: .leading)
                                    MemoryCardView(card: card)
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, Spacing.gutter)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.xxxl + look.sizes.fabSize)
        }
    }

    private struct MonthGroup: Identifiable {
        let year: Int
        let month: Int
        let newYear: Bool
        var cards: [MemoryCard]
        var id: String { "\(year)-\(month)" }
    }

    private func monthGroups(_ cards: [MemoryCard]) -> [MonthGroup] {
        var out: [MonthGroup] = []
        for c in cards {
            let d = Dates.start(c.memory.date)
            if let last = out.last, last.year == d.year, last.month == d.month {
                out[out.count - 1].cards.append(c)
            } else {
                out.append(MonthGroup(year: d.year, month: d.month, newYear: out.last?.year != d.year, cards: [c]))
            }
        }
        return out
    }

    private func grid(_ cards: [MemoryCard]) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.xs) {
                ForEach(monthGroups(cards)) { g in
                    if g.newYear {
                        Text(String(g.year)).font(look.type.headlineMedium).foregroundStyle(look.colors.timelineYear)
                            .padding(.top, Spacing.lg).padding(.bottom, Spacing.xs).accessibilityAddTraits(.isHeader)
                    }
                    Text(Dates.monthName(g.month)).font(look.type.titleSmall).foregroundStyle(look.colors.onSurfaceVariant)
                        .padding(.top, g.newYear ? 0 : Spacing.sm)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.xs), count: 3), spacing: Spacing.xs) {
                        ForEach(g.cards) { card in
                            NavigationLink(value: Route.memory(card.memory.id)) { GridTile(card: card) }.buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.gutter)
            .padding(.top, Spacing.sm)
            .padding(.bottom, Spacing.xxxl + look.sizes.fabSize)
        }
    }
}

/// One memory per line (CompactMemoryRow).
struct CompactMemoryRow: View {
    let card: MemoryCard
    @Environment(\.look) private var look

    var body: some View {
        HStack(spacing: Spacing.md) {
            MemoryCover(card: card).frame(width: 64, height: 64).clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            VStack(alignment: .leading, spacing: 2) {
                Text(card.memory.title).font(look.type.titleMedium).lineLimit(1).foregroundStyle(look.colors.onSurface)
                Text(([memoryDateText(card.memory.date)] + (card.memory.place.isEmpty ? [] : [card.memory.place])).joined(separator: " · "))
                    .font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant).lineLimit(1)
            }
            Spacer(minLength: 0)
            if !card.people.isEmpty { FaceStack(people: card.people, max: 3) }
        }
        .padding(Spacing.sm)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
        .accessibilityElement(children: .combine)
    }
}

/// Three covers per row (GridTile).
struct GridTile: View {
    let card: MemoryCard
    @Environment(\.look) private var look

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay(MemoryCover(card: card))
            .overlay(alignment: .bottomLeading) {
                Text(card.memory.title)
                    .font(look.type.labelMedium).foregroundStyle(.white).lineLimit(2)
                    .padding(Spacing.xs)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .top, endPoint: .bottom))
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            .accessibilityElement(children: .combine)
    }
}

struct MemoryCover: View {
    let card: MemoryCard
    var body: some View {
        if let cover = card.cover {
            VaultImage(path: cover.thumbPath)
        } else {
            CoverArt(style: card.memory.coverStyle, seed: card.memory.id)
        }
    }
}

/// Filters (FilterSheet.kt): people (optionally all together), groups, places, kind, from/to dates.
struct FilterSheet: View {
    let initial: TimelineFilter
    let apply: (TimelineFilter) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var f: TimelineFilter
    @State private var useFrom = false
    @State private var useTo = false
    @State private var from = Date()
    @State private var to = Date()

    init(filter: TimelineFilter, apply: @escaping (TimelineFilter) -> Void) {
        initial = filter
        self.apply = apply
        _f = State(initialValue: filter)
    }

    var body: some View {
        let store = model.store
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    let people = store?.persons() ?? []
                    if !people.isEmpty {
                        SectionHeader(title: L("filter_people"))
                        FlowLayout(spacing: Spacing.sm) {
                            ForEach(people) { p in
                                Chip(text: p.displayName, selected: f.personIds.contains(p.id)) {
                                    if f.personIds.contains(p.id) { f.personIds.remove(p.id) } else { f.personIds.insert(p.id) }
                                }
                            }
                        }
                        if f.personIds.count > 1 {
                            Toggle(L("filter_all_together"), isOn: $f.requireAllPeople).font(look.type.bodyLarge)
                        }
                    }
                    let groups = store?.groups() ?? []
                    if !groups.isEmpty {
                        SectionHeader(title: L("filter_groups"))
                        FlowLayout(spacing: Spacing.sm) {
                            ForEach(groups) { g in
                                Chip(text: "\(g.emoji) \(g.name)", selected: f.groupId == g.id) { f.groupId = f.groupId == g.id ? nil : g.id }
                            }
                        }
                    }
                    let places = store?.distinctPlaces() ?? []
                    if !places.isEmpty {
                        SectionHeader(title: L("filter_places"))
                        FlowLayout(spacing: Spacing.sm) {
                            ForEach(places, id: \.self) { p in Chip(text: p, selected: f.place == p) { f.place = f.place == p ? nil : p } }
                        }
                    }
                    SectionHeader(title: L("filter_type"))
                    FlowLayout(spacing: Spacing.sm) {
                        typeChip(L("sidebar_photos"), .EVENT)
                        typeChip(L("sidebar_stories"), .TEXT_ONLY)
                        typeChip(L("filter_quick"), .QUICK)
                    }
                    SectionHeader(title: L("filter_dates"))
                    Toggle(L("filter_from"), isOn: $useFrom).font(look.type.bodyLarge)
                    if useFrom { DatePicker(L("filter_from"), selection: $from, displayedComponents: .date).labelsHidden() }
                    Toggle(L("filter_to"), isOn: $useTo).font(look.type.bodyLarge)
                    if useTo { DatePicker(L("filter_to"), selection: $to, displayedComponents: .date).labelsHidden() }
                }
                .padding(Spacing.gutter)
            }
            .background(look.colors.surface)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("filter_apply")) {
                        var out = f
                        out.fromEpochDay = useFrom ? LocalDate(epochMillis: Int64(from.timeIntervalSince1970 * 1000), zone: .current).epochDay : nil
                        out.toEpochDay = useTo ? LocalDate(epochMillis: Int64(to.timeIntervalSince1970 * 1000), zone: .current).epochDay : nil
                        apply(out)
                        dismiss()
                    }
                }
            }
            .screenTitle(L("timeline_filters"))
        }
        .onAppear {
            if let d = initial.fromEpochDay { useFrom = true; from = LocalDate(epochDay: d).date(in: .current) }
            if let d = initial.toEpochDay { useTo = true; to = LocalDate(epochDay: d).date(in: .current) }
        }
    }

    private func typeChip(_ text: String, _ type: MemoryType) -> some View {
        Chip(text: text, selected: f.types.contains(type)) {
            if f.types.contains(type) { f.types.remove(type) } else { f.types.insert(type) }
        }
    }
}

/// Wrapping row of chips (FlowRow).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > width {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            maxX = max(maxX, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for s in subviews {
            let size = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
