import PhotosUI
import SwiftUI
import YaadeinDomain

func relationLabel(_ r: Relation) -> String {
    switch r {
    case .FAMILY: return L("relation_family")
    case .FRIEND: return L("relation_friend")
    case .COLLEAGUE: return L("relation_colleague")
    case .OTHER: return L("relation_other")
    }
}

/// Add or edit a person (PersonEditScreen.kt): photo, name, nickname, relation, birthday, notes, delete.
/// Also the "Add your first person" step after onboarding.
struct PersonEditView: View {
    let personId: Int64?
    let firstRun: Bool
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var nickname = ""
    @State private var relation: Relation = .FAMILY
    @State private var hasBirthday = false
    @State private var birthday = Date()
    @State private var notes = ""
    @State private var profileMediaId: Int64?
    @State private var photoItem: PhotosPickerItem?
    @State private var loaded = false
    @State private var confirmDelete = false
    @State private var saving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                HStack(spacing: Spacing.lg) {
                    let preview = Person(id: personId ?? 0, name: name.isEmpty ? "?" : name, profileMediaId: profileMediaId,
                                         avatarColorIndex: avatarIndex)
                    PersonAvatar(person: preview, size: look.sizes.avatarXl)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(L("person_photo")).font(look.type.titleMedium)
                        PhotosPicker(selection: $photoItem, matching: .images) {
                            Text(L("person_photo_pick")).font(look.type.labelLarge)
                        }
                        .buttonStyle(TextButtonStyle())
                        if profileMediaId != nil {
                            Button(L("person_photo_remove")) { profileMediaId = nil }.buttonStyle(TextButtonStyle())
                        }
                    }
                }
                OutlinedField(label: L("person_name"), text: $name)
                OutlinedField(label: L("person_nickname"), text: $nickname)
                Text(L("person_relation")).font(look.type.titleMedium)
                FlowLayout(spacing: Spacing.sm) {
                    ForEach(Relation.allCases, id: \.self) { r in Chip(text: relationLabel(r), selected: relation == r) { relation = r } }
                }
                Toggle(L("person_birthday"), isOn: $hasBirthday).font(look.type.titleMedium).frame(minHeight: look.sizes.touchTarget)
                if hasBirthday {
                    DatePicker(L("person_birthday"), selection: $birthday, in: ...Date(), displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .labelsHidden()
                }
                OutlinedField(label: L("person_notes"), text: $notes, axis: .vertical)
                Button(L("action_save")) { save() }
                    .buttonStyle(FilledButtonStyle(fullWidth: true))
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || saving)
                if let personId, !firstRun {
                    Button(L("person_delete"), role: .destructive) { confirmDelete = true }
                        .buttonStyle(TextButtonStyle())
                        .frame(maxWidth: .infinity)
                        .confirmationDialog(L("person_delete_confirm"), isPresented: $confirmDelete, titleVisibility: .visible) {
                            Button(L("action_delete"), role: .destructive) {
                                try? model.store?.deletePerson(id: personId)
                                dismiss()
                            }
                        }
                }
                if firstRun {
                    Button(L("action_skip")) { model.finishFirstPerson() }.buttonStyle(TextButtonStyle()).frame(maxWidth: .infinity)
                }
            }
            .padding(Spacing.gutter)
        }
        .scrollDismissesKeyboard(.interactively)
        .screenTitle(L(personId == nil ? "person_new_title" : "person_edit_title"))
        .onAppear(perform: load)
        .onChange(of: photoItem) { _, item in
            guard let item, let store = model.store, let vault = model.vault else { return }
            Task { if let id = await MediaImporter.saveProfilePhoto(item, store: store, vault: vault) { profileMediaId = id } }
        }
    }

    private var avatarIndex: Int {
        if let personId, let p = model.store?.person(id: personId) { return p.avatarColorIndex }
        return (model.store?.persons().count ?? 0) % 8
    }

    private func load() {
        guard !loaded else { return }
        loaded = true
        guard let personId, let p = model.store?.person(id: personId) else { return }
        name = p.name
        nickname = p.nickname ?? ""
        relation = p.relation
        notes = p.notes
        profileMediaId = p.profileMediaId
        if let b = p.birthdayEpochDay {
            hasBirthday = true
            birthday = LocalDate(epochDay: b).date(in: .current)
        }
    }

    private func save() {
        guard let store = model.store else { return }
        saving = true
        var p = personId.flatMap { store.person(id: $0) } ?? Person(name: "", avatarColorIndex: avatarIndex)
        p.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let nick = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        p.nickname = nick.isEmpty ? nil : nick
        p.relation = relation
        p.notes = notes
        p.profileMediaId = profileMediaId
        p.birthdayEpochDay = hasBirthday ? LocalDate(epochMillis: Int64(birthday.timeIntervalSince1970 * 1000), zone: .current).epochDay : nil
        _ = try? store.upsertPerson(p)
        saving = false
        if firstRun { model.finishFirstPerson() } else { dismiss() }
    }
}
