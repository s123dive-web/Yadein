import SwiftUI
import UIKit
import YaadeinData
import YaadeinDomain

/// Relive (ReliveScreen.kt): story prompt, Surprise me, shake tip, birthdays (with a one-tap reel), On this day widened to
/// this week / this month (Rewind), places and years as photo tiles.
struct ReliveView: View {
    let onPlace: (String) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let _ = model.revision
        let store = model.store
        let today = LocalDate.today()
        let total = store?.memoryCount ?? 0
        let countsByYear = store?.countsByYear() ?? []
        let rewind = countsByYear.map(\.year).min().flatMap { earliest in
            Rewind.pick(store?.inDayRanges(Rewind.windows(today: today, earliestYear: earliest)) ?? [], today: today)
        }
        let yearCovers = store?.yearCovers() ?? [:]
        let placeCovers = store?.placeCovers() ?? [:]
        let digest = YaadeinDomain.Reminders.build(cards: [], people: store?.persons() ?? [], nowMillis: 0)
        let birthdays = YaadeinDomain.Reminders.birthdays(digest, today: today, daysAhead: 30)
        let places = store?.topPlaces() ?? []
        let years = countsByYear
        let birthdaysFirst = (birthdays.first?.inDays ?? Int.max) <= 1
        ScrollView {
            if total == 0 {
                EmptyState(illustration: .kite, title: L("relive_empty_title"), message: L("relive_empty_message"))
            } else {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    if birthdaysFirst { birthdaysSection(birthdays, store) }
                    promptCard(digest, today)
                    FlowLayout(spacing: Spacing.sm) {
                        NavigationLink(value: Route.randomMemory) { Label(L("relive_surprise"), systemImage: "dice") }
                            .buttonStyle(FilledButtonStyle(tonal: true))
                        NavigationLink(value: Route.recap(.all)) { Label(L("relive_make_recap"), systemImage: "film") }
                            .buttonStyle(FilledButtonStyle())
                        NavigationLink(value: Route.reels) { Label(L("relive_your_reels"), systemImage: "film.stack") }
                            .buttonStyle(FilledButtonStyle(tonal: true))
                    }
                    if model.settings.shakeForMemory && !model.settings.shakeHintDismissed && total >= 3 {
                        shakeHint
                    }
                    if !birthdaysFirst { birthdaysSection(birthdays, store) }
                    SectionHeader(title: L(rewind?.scope == .WEEK ? "relive_this_week" : rewind?.scope == .MONTH ? "relive_this_month" : "relive_on_this_day"))
                    if let rewind {
                        ForEach(rewind.items, id: \.card.memory.id) { item in
                            VStack(alignment: .leading, spacing: Spacing.xs) {
                                Text(LP("relive_years_ago", item.yearsAgo)).font(look.type.labelLarge).foregroundStyle(look.colors.primary)
                                NavigationLink(value: Route.memory(item.card.memory.id)) { MemoryCardView(card: item.card, compact: true) }.buttonStyle(.plain)
                            }
                        }
                    } else {
                        Text(L("relive_on_this_day_empty")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    }
                    if !places.isEmpty {
                        SectionHeader(title: L("relive_places"))
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(places.indices, id: \.self) { i in
                                    let p = places[i]
                                    Button { onPlace(p.place) } label: {
                                        PhotoTile(title: p.place, subtitle: LP("memory_count", p.count),
                                                  cover: placeCovers[p.place.trimmingCharacters(in: .whitespaces).lowercased()], pin: true)
                                    }
                                    .buttonStyle(.plain)
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
                                        PhotoTile(title: String(y.year), subtitle: LP("memory_count", y.count), cover: yearCovers[y.year])
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
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

    /// Upcoming birthdays as cards; within a week, one tap makes a reel of that person.
    @ViewBuilder private func birthdaysSection(_ birthdays: [UpcomingBirthday], _ store: VaultStore?) -> some View {
        if !birthdays.isEmpty {
            SectionHeader(title: L("relive_birthdays"))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: Spacing.sm) {
                    ForEach(birthdays, id: \.person.id) { b in
                        if let p = store?.person(id: b.person.id) {
                            VStack(alignment: .leading, spacing: Spacing.sm) {
                                NavigationLink(value: Route.person(p.id)) {
                                    HStack(spacing: Spacing.sm) {
                                        PersonAvatar(person: p, size: look.sizes.avatarMd)
                                        Text(b.inDays == 0 ? L("relive_birthday_today", p.displayName) : L("relive_birthday_in", p.displayName, b.inDays))
                                            .font(look.type.titleSmall).lineLimit(2).multilineTextAlignment(.leading)
                                    }
                                }
                                .buttonStyle(.plain)
                                if b.inDays <= 7 {
                                    NavigationLink(value: Route.recap(.person(p.id))) { Label(L("relive_birthday_reel"), systemImage: "film") }
                                        .buttonStyle(FilledButtonStyle())
                                }
                            }
                            .padding(Spacing.md)
                            .frame(width: 220, alignment: .leading)
                            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(b.inDays == 0 ? look.colors.tertiaryContainer : look.colors.secondaryContainer))
                            .foregroundStyle(b.inDays == 0 ? look.colors.onTertiaryContainer : look.colors.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }

    /// One-time tip that shaking the phone opens a random memory.
    private var shakeHint: some View {
        HStack(alignment: .top, spacing: Spacing.md) {
            Image(systemName: "iphone.radiowaves.left.and.right").font(.title3)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(L("relive_shake_hint_title")).font(look.type.titleSmall)
                Text(L("shake_hint")).font(look.type.bodyMedium)
                NavigationLink(value: Route.randomMemory) { Text(L("relive_surprise")) }.font(look.type.labelLarge)
            }
            Spacer(minLength: 0)
            Button { withAnimation(Motion.standard) { model.updateSettings { $0.shakeHintDismissed = true } } } label: {
                Image(systemName: "xmark").frame(width: 44, height: 44)
            }
            .accessibilityLabel(L("relive_got_it"))
        }
        .padding(.leading, Spacing.lg).padding(.vertical, Spacing.sm)
        .foregroundStyle(look.colors.onSecondaryContainer)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.secondaryContainer))
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
                    // Each new pick pops in, so "Another one" feels like drawing a card rather than a refresh.
                    NavigationLink(value: Route.memory(card.memory.id)) { MemoryCardView(card: card) }.buttonStyle(.plain)
                        .id(card.memory.id)
                        .transition(.scale(scale: 0.85).combined(with: .opacity))
                } else {
                    EmptyState(illustration: .kite, title: L("relive_empty_title"), message: L("relive_empty_message"))
                }
                Button { withAnimation(.spring(response: 0.45, dampingFraction: 0.7)) { card = nextRandom() } } label: {
                    Label(L("relive_another"), systemImage: "shuffle")
                }
                .buttonStyle(FilledButtonStyle(tonal: true))
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("relive_random_title"))
        .onAppear { if card == nil { card = model.store?.random() } }
        .sensoryFeedback(.impact(weight: .medium), trigger: card?.memory.id)
    }

    /// A random memory, not the one on screen when there are others to pick from.
    private func nextRandom() -> MemoryCard? {
        let next = model.store?.random()
        if let next, next.memory.id == card?.memory.id, (model.store?.memoryCount ?? 0) > 1 { return model.store?.random() ?? next }
        return next
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

/// A year or a place: its photo (or cover art) with the name and count on a scrim (PhotoTile in ReliveScreen.kt).
private struct PhotoTile: View {
    let title: String
    let subtitle: String
    let cover: CoverPick?
    var pin = false
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            SwiftUI.Group {
                if let id = cover?.mediaId, let media = model.store?.media(id: id) {
                    VaultImage(path: media.thumbPath)
                } else {
                    CoverArt(style: cover?.coverStyle ?? CoverStyle.defaultStyle, seed: cover?.memoryId ?? 0)
                }
            }
            .frame(width: 148, height: 112)
            .clipped()
            LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 2) {
                    if pin { Image(systemName: "mappin").font(.caption) }
                    Text(title).font(look.type.titleMedium).lineLimit(1)
                }
                Text(subtitle).font(look.type.labelMedium).opacity(0.9).lineLimit(1)
            }
            .foregroundStyle(.white)
            .padding(Spacing.sm)
        }
        .frame(width: 148, height: 112)
        .clipShape(RoundedRectangle(cornerRadius: Radius.md))
        .accessibilityElement(children: .combine)
    }
}
