import SwiftUI
import UIKit
import YaadeinDomain

/// Relive (ReliveScreen.kt): story prompt, Surprise me, birthdays, On this day, places, years.
struct ReliveView: View {
    let onPlace: (String) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let _ = model.revision
        let store = model.store
        let today = LocalDate.today()
        let total = store?.memoryCount ?? 0
        let onThisDay = store?.onThisDay(monthDay: Dates.monthDayKey(today), excludeYear: today.year) ?? []
        let digest = YaadeinDomain.Reminders.build(cards: [], people: store?.persons() ?? [], nowMillis: 0)
        let birthdays = YaadeinDomain.Reminders.birthdays(digest, today: today, daysAhead: 30)
        let places = store?.topPlaces() ?? []
        let years = store?.countsByYear() ?? []
        ScrollView {
            if total == 0 {
                EmptyState(illustration: .kite, title: L("relive_empty_title"), message: L("relive_empty_message"))
            } else {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    promptCard(digest, today)
                    FlowLayout(spacing: Spacing.sm) {
                        NavigationLink(value: Route.randomMemory) { Label(L("relive_surprise"), systemImage: "dice") }
                            .buttonStyle(FilledButtonStyle(tonal: true))
                        NavigationLink(value: Route.recap(.all)) { Label(L("relive_make_recap"), systemImage: "film") }
                            .buttonStyle(FilledButtonStyle())
                        NavigationLink(value: Route.reels) { Label(L("relive_your_reels"), systemImage: "film.stack") }
                            .buttonStyle(FilledButtonStyle(tonal: true))
                    }
                    if !birthdays.isEmpty {
                        SectionHeader(title: L("relive_birthdays"))
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(birthdays, id: \.person.id) { b in
                                    if let p = store?.person(id: b.person.id) {
                                        NavigationLink(value: Route.person(p.id)) {
                                            HStack(spacing: Spacing.sm) {
                                                PersonAvatar(person: p, size: look.sizes.avatarSm)
                                                Text(b.inDays == 0 ? L("relive_birthday_today", p.displayName) : L("relive_birthday_in", p.displayName, b.inDays))
                                                    .font(look.type.labelLarge)
                                            }
                                            .padding(.horizontal, Spacing.md).padding(.vertical, Spacing.sm)
                                            .background(Capsule().fill(look.colors.secondaryContainer))
                                            .foregroundStyle(look.colors.onSecondaryContainer)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                    SectionHeader(title: L("relive_on_this_day"))
                    if onThisDay.isEmpty {
                        Text(L("relive_on_this_day_empty")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    } else {
                        ForEach(onThisDay) { c in
                            NavigationLink(value: Route.memory(c.memory.id)) { MemoryCardView(card: c, compact: true) }.buttonStyle(.plain)
                        }
                    }
                    if !places.isEmpty {
                        SectionHeader(title: L("relive_places"))
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(places.indices, id: \.self) { i in
                                    let p = places[i]
                                    Button { onPlace(p.place) } label: { ChipLabel(text: "\(p.place) · \(p.count)", icon: "mappin") }.buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    if !years.isEmpty {
                        SectionHeader(title: L("relive_years"))
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(years.indices, id: \.self) { i in
                                    let y = years[i]
                                    NavigationLink(value: Route.yearly(personId: 0, year: y.year)) {
                                        VStack(spacing: 2) {
                                            Text(String(y.year)).font(look.type.titleLarge).foregroundStyle(look.colors.primary)
                                            Text(LP("memory_count", y.count)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                                        }
                                        .padding(Spacing.md)
                                        .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    if model.settings.shakeForMemory {
                        Text(L("shake_hint")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xxxl)
            }
        }
        .background(look.colors.surface.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarLeading) { Text(L("relive_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader) } }
        .toolbarBackground(look.colors.surface, for: .navigationBar)
    }

    /// This week's story prompt (PromptCard): about a family member, a friend, or the user.
    @ViewBuilder private func promptCard(_ digest: ReminderDigest, _ today: LocalDate) -> some View {
        let pick = YaadeinDomain.Reminders.weeklyPrompt(digest, today: today)
        let text = pick.person.map { LA($0.relation == .FRIEND ? "prompts_friend" : "prompts_family", pick.index, $0.name) } ?? LA("prompts_self", pick.index)
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("notification_prompt_title")).font(look.type.labelLarge).foregroundStyle(look.colors.onPrimaryContainer.opacity(0.8))
            Text(text).font(look.type.titleLarge).foregroundStyle(look.colors.onPrimaryContainer)
            NavigationLink(value: Route.voiceCapture(memoryId: nil, prompt: text)) { Text(L("relive_record_story")) }.buttonStyle(FilledButtonStyle())
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.primaryContainer))
    }
}

/// "Surprise me": a random memory and "Another one" (RandomMemory on Android). Also opened by shaking the phone.
struct RandomMemoryView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var card: MemoryCard?

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                if let card {
                    NavigationLink(value: Route.memory(card.memory.id)) { MemoryCardView(card: card) }.buttonStyle(.plain)
                } else {
                    EmptyState(illustration: .kite, title: L("relive_empty_title"), message: L("relive_empty_message"))
                }
                Button(L("relive_another")) { withAnimation(Motion.standard) { card = model.store?.random() } }
                    .buttonStyle(FilledButtonStyle(tonal: true))
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("relive_random_title"))
        .onAppear { if card == nil { card = model.store?.random() } }
    }
}

/// "Your 2024" / "Your 2024 with Rahul" (YearlySummaryScreen.kt).
struct YearlySummaryView: View {
    let personId: Int64
    let year: Int
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let _ = model.revision
        let store = model.store
        let person = personId == 0 ? nil : store?.person(id: personId)
        let cards = store?.cardsForYear(year, personId: personId == 0 ? nil : personId) ?? []
        let title = person.map { L("yearly_title", year, $0.displayName) } ?? L("yearly_all_title", year)
        let photos = cards.reduce(0) { $0 + $1.mediaCount }
        let places = Set(cards.map(\.memory.place).filter { !$0.isEmpty })
        var counts: [Int64: Int] = [:]
        for c in cards { for p in c.people where p.id != personId { counts[p.id, default: 0] += 1 } }
        let top = counts.sorted { $0.value > $1.value }.prefix(5).compactMap { store?.person(id: $0.key) }
        return ScrollView {
            if cards.isEmpty {
                EmptyState(illustration: .calendarStar, title: L("yearly_empty", year), message: "")
            } else {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    Text(String(year)).font(look.type.displayLarge).foregroundStyle(look.colors.primary)
                    Text(title).font(look.type.headlineSmall)
                    FlowLayout(spacing: Spacing.sm) {
                        ChipLabel(text: L("yearly_memories", cards.count))
                        ChipLabel(text: L("yearly_photos", photos))
                        ChipLabel(text: L("yearly_places", places.count))
                    }
                    if !top.isEmpty {
                        Text(L("yearly_top_people")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                        HStack(spacing: Spacing.md) {
                            ForEach(top) { p in
                                NavigationLink(value: Route.person(p.id)) {
                                    VStack { PersonAvatar(person: p, size: look.sizes.avatarLg); Text(p.displayName).font(look.type.labelMedium).lineLimit(1) }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    NavigationLink(value: Route.recap(personId == 0 ? .year(year) : .person(personId))) { Text(L("relive_make_recap")) }
                        .buttonStyle(FilledButtonStyle())
                    if let first = cards.first {
                        Text(L("yearly_first")).font(look.type.titleMedium)
                        NavigationLink(value: Route.memory(first.memory.id)) { MemoryCardView(card: first, compact: true) }.buttonStyle(.plain)
                    }
                    if cards.count > 1, let last = cards.last {
                        Text(L("yearly_last")).font(look.type.titleMedium)
                        NavigationLink(value: Route.memory(last.memory.id)) { MemoryCardView(card: last, compact: true) }.buttonStyle(.plain)
                    }
                    ForEach(cards.dropFirst().dropLast()) { c in
                        NavigationLink(value: Route.memory(c.memory.id)) { CompactMemoryRow(card: c) }.buttonStyle(.plain)
                    }
                }
                .padding(Spacing.gutter)
            }
        }
        .screenTitle(title)
    }
}

/// Bubble map of who appears with whom (RelationshipsMapScreen.kt): deterministic force layout, bubble size =
/// memories, line weight = shared memories, tap → person.
struct RelationshipsMapView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let store = model.store
        let people = store?.persons() ?? []
        let counts = store?.memoryCounts() ?? [:]
        let pairs = store?.coAppearances() ?? []
        GeometryReader { geo in
            let positions = layout(people.map(\.id), pairs: pairs, size: geo.size)
            let maxCount = max(1, counts.values.max() ?? 1)
            let maxPair = max(1, pairs.map { $0.count }.max() ?? 1)
            ZStack {
                Canvas { ctx, _ in
                    for p in pairs {
                        guard let a = positions[p.a], let b = positions[p.b] else { continue }
                        var path = Path()
                        path.move(to: a)
                        path.addLine(to: b)
                        ctx.stroke(path, with: .color(look.colors.primary.opacity(0.35)), lineWidth: 1 + 5 * CGFloat(p.count) / CGFloat(maxPair))
                    }
                }
                ForEach(people) { person in
                    if let pos = positions[person.id] {
                        let size = look.sizes.avatarMd + 32 * CGFloat(counts[person.id] ?? 0) / CGFloat(maxCount)
                        NavigationLink(value: Route.person(person.id)) {
                            VStack(spacing: 2) {
                                PersonAvatar(person: person, size: size)
                                Text(person.displayName).font(look.type.labelSmall).foregroundStyle(look.colors.onSurface).lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                        .position(pos)
                    }
                }
            }
        }
        .overlay(alignment: .bottom) {
            Text(L("relationships_hint")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant).padding()
        }
        .screenTitle(L("relationships_title"))
    }

    /// Fruchterman–Reingold from a circle, fixed iterations: same input, same picture.
    private func layout(_ ids: [Int64], pairs: [(a: Int64, b: Int64, count: Int)], size: CGSize) -> [Int64: CGPoint] {
        guard !ids.isEmpty, size.width > 0 else { return [:] }
        let w = Double(size.width) - 80, h = Double(size.height) - 120
        var pos: [Int64: (Double, Double)] = [:]
        for (i, id) in ids.enumerated() {
            let a = 2 * Double.pi * Double(i) / Double(ids.count)
            pos[id] = (w / 2 + w / 3 * cos(a), h / 2 + h / 3 * sin(a))
        }
        let k = (w * h / Double(ids.count)).squareRoot() * 0.6
        var temp = w / 10
        for _ in 0..<120 {
            var disp: [Int64: (Double, Double)] = Dictionary(uniqueKeysWithValues: ids.map { ($0, (0.0, 0.0)) })
            for a in ids { for b in ids where a != b {
                let dx = pos[a]!.0 - pos[b]!.0, dy = pos[a]!.1 - pos[b]!.1
                let d = max(0.01, (dx * dx + dy * dy).squareRoot())
                disp[a]!.0 += dx / d * k * k / d
                disp[a]!.1 += dy / d * k * k / d
            } }
            for p in pairs {
                guard let pa = pos[p.a], let pb = pos[p.b] else { continue }
                let dx = pa.0 - pb.0, dy = pa.1 - pb.1
                let d = max(0.01, (dx * dx + dy * dy).squareRoot())
                let f = d * d / k * Double(p.count).squareRoot()
                disp[p.a]!.0 -= dx / d * f; disp[p.a]!.1 -= dy / d * f
                disp[p.b]!.0 += dx / d * f; disp[p.b]!.1 += dy / d * f
            }
            for id in ids {
                let (dx, dy) = disp[id]!
                let d = max(0.01, (dx * dx + dy * dy).squareRoot())
                var x = pos[id]!.0 + dx / d * min(d, temp), y = pos[id]!.1 + dy / d * min(d, temp)
                x = min(w, max(0, x)); y = min(h, max(0, y))
                pos[id] = (x, y)
            }
            temp *= 0.96
        }
        return pos.mapValues { CGPoint(x: $0.0 + 40, y: $0.1 + 50) }
    }
}

/// Shake for a random memory (ShakeLogic: three spikes within 600 ms, 2 s cooldown — iOS reports the shake gesture).
extension UIWindow {
    open override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake { NotificationCenter.default.post(name: .yaadeinShake, object: nil) }
        super.motionEnded(motion, with: event)
    }
}

extension Notification.Name {
    static let yaadeinShake = Notification.Name("yaadein.shake")
}
