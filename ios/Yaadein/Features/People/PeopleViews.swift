import SwiftUI
import YaadeinDomain

/// People grid with the groups row (PeopleScreen.kt).
struct PeopleView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let _ = model.revision
        let store = model.store
        let people = store?.persons() ?? []
        let counts = store?.memoryCounts() ?? [:]
        SwiftUI.Group {
            if people.isEmpty {
                ScrollView {
                    EmptyState(illustration: .peopleCircle, title: L("people_empty_title"), message: L("people_empty_message"))
                    NavigationLink(value: Route.personEdit(nil)) { Text(L("people_add")) }.buttonStyle(FilledButtonStyle())
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: Spacing.md) {
                        GroupsRow(groups: store?.groups() ?? [])
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 112), spacing: Spacing.md)], spacing: Spacing.md) {
                            ForEach(people) { p in
                                NavigationLink(value: Route.person(p.id)) { PersonTile(person: p, memoryCount: counts[p.id] ?? 0) }
                                    .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.gutter)
                    .padding(.top, Spacing.sm)
                    .padding(.bottom, Spacing.xxxl + look.sizes.fabSize)
                }
            }
        }
        .background(look.colors.surface.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Text(L("people_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader) }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if people.count >= 2 {
                    NavigationLink(value: Route.relationships) { Image(systemName: "point.3.connected.trianglepath.dotted") }
                        .accessibilityLabel(L("people_relationships"))
                }
                NavigationLink(value: Route.personEdit(nil)) { Image(systemName: "person.badge.plus") }.accessibilityLabel(L("people_add"))
            }
        }
        .toolbarBackground(look.colors.surface, for: .navigationBar)
    }
}

private struct GroupsRow: View {
    let groups: [YaadeinDomain.Group]
    @Environment(\.look) private var look

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(L("people_groups")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    ForEach(groups) { g in
                        NavigationLink(value: Route.groupEdit(g.id)) { ChipLabel(text: "\(g.emoji) \(g.name)") }.buttonStyle(.plain)
                    }
                    NavigationLink(value: Route.groupEdit(nil)) { ChipLabel(text: L("people_new_group"), icon: "plus") }.buttonStyle(.plain)
                }
            }
        }
    }
}

/// Non-interactive chip look for links (AssistChip).
struct ChipLabel: View {
    let text: String
    var icon: String? = nil
    @Environment(\.look) private var look
    var body: some View {
        HStack(spacing: Spacing.xs) {
            if let icon { Image(systemName: icon).font(.system(size: 13)) }
            Text(text).font(look.type.labelLarge)
        }
        .padding(.horizontal, Spacing.md)
        .frame(minHeight: 32)
        .foregroundStyle(look.colors.onSurface)
        .overlay(Capsule().stroke(look.colors.outline))
        .frame(minHeight: look.sizes.touchTarget)
        .contentShape(Rectangle())
    }
}

struct PersonTile: View {
    let person: Person
    let memoryCount: Int
    @Environment(\.look) private var look

    var body: some View {
        VStack(spacing: 0) {
            PersonAvatar(person: person, size: look.sizes.avatarLg)
            Spacer().frame(height: Spacing.sm)
            Text(person.displayName).font(look.type.titleSmall).lineLimit(1).foregroundStyle(look.colors.onSurface)
            Text(LP("memory_count", memoryCount)).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
        }
        .padding(.vertical, Spacing.lg)
        .padding(.horizontal, Spacing.sm)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainerLow))
        .accessibilityElement(children: .combine)
    }
}

/// A person's story (PersonDetailScreen.kt): avatar, relation, stats, birthday, groups, Together, years, memories.
struct PersonDetailView: View {
    let personId: Int64
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let _ = model.revision
        if let store = model.store, let story = store.story(personId: personId, today: .today()) {
            let cards = store.cards(TimelineFilter(personIds: [personId]))
            let years = Array(Set(cards.map { Dates.start($0.memory.date).year })).sorted(by: >)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    header(story)
                    if !years.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: Spacing.sm) {
                                ForEach(years, id: \.self) { y in
                                    NavigationLink(value: Route.yearly(personId: personId, year: y)) {
                                        ChipLabel(text: L("person_year_with", y, story.person.displayName))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    if cards.isEmpty {
                        EmptyState(illustration: .boxClosed, title: L("person_empty_title", story.person.displayName), message: L("person_empty_message"))
                    } else {
                        LazyVStack(spacing: Spacing.md) {
                            ForEach(cards) { c in
                                NavigationLink(value: Route.memory(c.memory.id)) { MemoryCardView(card: c, compact: true) }.buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, Spacing.gutter)
                .padding(.bottom, Spacing.xxxl)
            }
            .background(look.colors.surface.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.personEdit(personId)) { Image(systemName: "pencil") }.accessibilityLabel(L("action_edit"))
                }
            }
        } else {
            Color.clear.onAppear { dismiss() }
        }
    }

    private func header(_ story: PersonStory) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            HStack { Spacer(); PersonAvatar(person: story.person, size: look.sizes.avatarXl); Spacer() }
            Text(story.person.name).font(look.type.headlineMedium).frame(maxWidth: .infinity).accessibilityAddTraits(.isHeader)
            Text(([relationLabel(story.person.relation)] + (story.person.nickname.map { [$0] } ?? [])).joined(separator: " · "))
                .font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).frame(maxWidth: .infinity)
            HStack(spacing: Spacing.sm) {
                StatCard(value: String(story.memoryCount), label: L("person_stat_memories"))
                StatCard(value: story.firstMemory.map { Dates.label($0.date).text } ?? "—", label: L("person_first_memory"))
                StatCard(value: story.lastMemory.map { Dates.label($0.date).text } ?? "—", label: L("person_last_memory"))
            }
            if let days = story.daysToBirthday {
                Label(days == 0 ? L("person_birthday_today") : days == 1 ? L("person_birthday_tomorrow") : L("person_birthday_in_days", days),
                      systemImage: "gift")
                    .font(look.type.bodyMedium).foregroundStyle(look.colors.secondary)
            }
            FlowLayout(spacing: Spacing.sm) {
                ForEach(story.groups) { g in ChipLabel(text: "\(g.emoji) \(g.name)") }
                NavigationLink(value: Route.together([personId])) { ChipLabel(text: L("person_together"), icon: "person.2") }.buttonStyle(.plain)
                NavigationLink(value: Route.recap(.person(personId))) { ChipLabel(text: L("person_recap"), icon: "film") }.buttonStyle(.plain)
            }
        }
        .padding(.top, Spacing.md)
    }
}

private struct StatCard: View {
    let value: String
    let label: String
    @Environment(\.look) private var look
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(look.type.titleMedium).foregroundStyle(look.colors.primary).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(look.type.labelMedium).foregroundStyle(look.colors.onSurfaceVariant).lineLimit(1)
        }
        .padding(Spacing.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
        .accessibilityElement(children: .combine)
    }
}

/// Group editor (GroupEditScreen.kt): name, emoji presets, members, delete.
struct GroupEditView: View {
    let groupId: Int64?
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var emoji = "👨‍👩‍👧"
    @State private var members: Set<Int64> = []
    @State private var loaded = false
    private let emojis = ["👨‍👩‍👧", "🏠", "🎓", "💼", "🧳", "🎉", "❤️", "⚽", "🎶", "🙏", "🌸", "🍲"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                OutlinedField(label: L("group_name"), text: $name)
                Text(L("group_emoji")).font(look.type.titleMedium)
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(emojis, id: \.self) { e in Chip(text: e, selected: emoji == e) { emoji = e } }
                }
                Text(L("group_members")).font(look.type.titleMedium)
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(model.store?.persons() ?? []) { p in
                        Chip(text: p.displayName, selected: members.contains(p.id)) {
                            if members.contains(p.id) { members.remove(p.id) } else { members.insert(p.id) }
                        }
                    }
                }
                Button(L("action_save")) {
                    var g = groupId.flatMap { model.store?.group(id: $0) } ?? YaadeinDomain.Group(name: "")
                    g.name = name.trimmingCharacters(in: .whitespaces)
                    g.emoji = emoji
                    g.memberIds = Array(members).sorted()
                    _ = try? model.store?.upsertGroup(g)
                    dismiss()
                }
                .buttonStyle(FilledButtonStyle(fullWidth: true))
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                if let groupId {
                    Button(L("group_delete"), role: .destructive) {
                        try? model.store?.deleteGroup(id: groupId)
                        dismiss()
                    }
                    .buttonStyle(TextButtonStyle()).frame(maxWidth: .infinity)
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L(groupId == nil ? "group_new_title" : "group_edit_title"))
        .onAppear {
            guard !loaded else { return }
            loaded = true
            if let groupId, let g = model.store?.group(id: groupId) {
                name = g.name
                emoji = g.emoji
                members = Set(g.memberIds)
            }
        }
    }
}

/// Together (TogetherScreen): pick two or more people, see only memories where all of them appear.
struct TogetherView: View {
    let initialIds: [Int64]
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var selected: Set<Int64> = []

    var body: some View {
        let _ = model.revision
        let cards = selected.count >= 2 ? (model.store?.cards(TimelineFilter(personIds: selected, requireAllPeople: true)) ?? []) : []
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(L("together_pick")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(model.store?.persons() ?? []) { p in
                        Chip(text: p.displayName, selected: selected.contains(p.id)) {
                            if selected.contains(p.id) { selected.remove(p.id) } else { selected.insert(p.id) }
                        }
                    }
                }
                if selected.count >= 2 && cards.isEmpty {
                    EmptyState(illustration: .peopleCircle, title: L("together_empty_title"), message: L("together_empty_message"))
                }
                LazyVStack(spacing: Spacing.md) {
                    ForEach(cards) { c in
                        NavigationLink(value: Route.memory(c.memory.id)) { MemoryCardView(card: c, compact: true) }.buttonStyle(.plain)
                    }
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("together_title"))
        .onAppear { if selected.isEmpty { selected = Set(initialIds) } }
    }
}
