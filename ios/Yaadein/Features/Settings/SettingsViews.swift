import SwiftUI
import UniformTypeIdentifiers
import YaadeinCrypto
import YaadeinData
import YaadeinDomain

/// Settings root (SettingsScreen.kt): Easy mode switch, then the pages.
struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xs) {
                SettingsSwitchRow(icon: "figure.roll", title: L("settings_easy_mode"), subtitle: L("settings_easy_mode_subtitle"),
                                  isOn: Binding(get: { model.settings.easyMode }, set: { v in model.updateSettings { $0.easyMode = v } }))
                SettingsLink(icon: "paintpalette", title: L("settings_appearance"), subtitle: L("settings_appearance_subtitle"), page: .appearance)
                SettingsLink(icon: "lock", title: L("settings_security"), subtitle: L("settings_security_subtitle"), page: .security)
                SettingsLink(icon: "bell", title: L("settings_notifications"), subtitle: L("settings_notifications_subtitle"), page: .notifications)
                SettingsLink(icon: "face.smiling", title: L("settings_faces"), subtitle: L("settings_faces_subtitle"), page: .faces)
                SettingsLink(icon: "internaldrive", title: L("settings_storage"), subtitle: L("settings_storage_subtitle"), page: .storage)
                SettingsLink(icon: "externaldrive.badge.timemachine", title: L("settings_backup"), subtitle: L("settings_backup_subtitle"), page: .backup)
                SettingsLink(icon: "hand.raised", title: L("settings_privacy"), subtitle: L("settings_privacy_subtitle"), page: .privacy)
                SettingsLink(icon: "questionmark.circle", title: L("app_info_open"), subtitle: nil, page: .appInfo)
                SettingsLink(icon: "info.circle", title: L("settings_about"), subtitle: L("settings_about_subtitle"), page: .about)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.xxxl)
        }
        .background(look.colors.surface.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Text(L("settings_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader) }
        }
        .toolbarBackground(look.colors.surface, for: .navigationBar)
    }
}

struct SettingsLink: View {
    let icon: String
    let title: String
    let subtitle: String?
    let page: SettingsPage
    var body: some View {
        NavigationLink(value: Route.settings(page)) { SettingsRowLabel(icon: icon, title: title, subtitle: subtitle, chevron: true) }
            .buttonStyle(.plain)
    }
}

struct SettingsRowLabel: View {
    let icon: String?
    let title: String
    let subtitle: String?
    var chevron = false
    @Environment(\.look) private var look

    var body: some View {
        HStack(spacing: Spacing.lg) {
            if let icon { Image(systemName: icon).font(.system(size: look.sizes.icon * 0.85)).foregroundStyle(look.colors.primary).frame(width: look.sizes.icon) }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(look.type.titleMedium).foregroundStyle(look.colors.onSurface)
                if let subtitle { Text(subtitle).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.leading) }
            }
            Spacer(minLength: 0)
            if chevron { Image(systemName: "chevron.right").foregroundStyle(look.colors.onSurfaceVariant) }
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.md)
        .frame(minHeight: look.sizes.touchTarget + Spacing.md)
        .contentShape(Rectangle())
    }
}

struct SettingsSwitchRow: View {
    let icon: String?
    let title: String
    let subtitle: String?
    @Binding var isOn: Bool
    @Environment(\.look) private var look

    var body: some View {
        Toggle(isOn: $isOn) { SettingsRowLabel(icon: icon, title: title, subtitle: subtitle) }
            .padding(.trailing, Spacing.md)
            .tint(look.colors.primary)
    }
}

/// Routes a settings page.
struct SettingsPageView: View {
    let page: SettingsPage
    var body: some View {
        switch page {
        case .appearance: AppearanceView()
        case .security: SecurityView()
        case .notifications: NotificationsView()
        case .faces: FacesView()
        case .storage: StorageView()
        case .backup: BackupView()
        case .privacy: PrivacyView()
        case .about: AboutView()
        case .appInfo: AppInfoView()
        }
    }
}

func themeName(_ t: AppTheme) -> String {
    switch t {
    case .WARM_ALBUM: return L("theme_warm_album")
    case .MIDNIGHT: return L("theme_midnight")
    case .MONSOON: return L("theme_monsoon")
    case .FESTIVE: return L("theme_festive")
    case .MINIMAL_MONO: return L("theme_minimal_mono")
    case .DYNAMIC: return L("theme_dynamic")
    }
}

/// Appearance (AppearanceScreen.kt): language, dark mode, theme swatches, timeline layout, Easy mode.
/// "Material You" (dynamic colour) is an Android 12 feature; iOS shows the five hand-made palettes.
struct AppearanceView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                } label: { SettingsRowLabel(icon: "character.bubble", title: L("appearance_language"), subtitle: L("appearance_language_help"), chevron: true) }
                    .buttonStyle(.plain)
                Text(L("appearance_dark_mode")).font(look.type.titleMedium)
                Picker(L("appearance_dark_mode"), selection: Binding(get: { model.settings.darkMode }, set: { v in model.updateSettings { $0.darkMode = v } })) {
                    Text(L("dark_mode_system")).tag(DarkMode.SYSTEM)
                    Text(L("dark_mode_light")).tag(DarkMode.LIGHT)
                    Text(L("dark_mode_dark")).tag(DarkMode.DARK)
                }
                .pickerStyle(.segmented)
                Text(L("appearance_theme")).font(look.type.titleMedium)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: Spacing.md)], spacing: Spacing.md) {
                    ForEach([AppTheme.WARM_ALBUM, .MIDNIGHT, .MONSOON, .FESTIVE, .MINIMAL_MONO], id: \.self) { t in
                        ThemeSwatch(theme: t, selected: model.settings.theme == t) { model.updateSettings { $0.theme = t } }
                    }
                }
                Text(L("timeline_title")).font(look.type.titleMedium)
                Picker("", selection: Binding(get: { model.settings.timelineLayout }, set: { v in model.updateSettings { $0.timelineLayout = v } })) {
                    Text(L("timeline_layout_cards")).tag(TimelineLayout.CARDS)
                    Text(L("timeline_layout_compact")).tag(TimelineLayout.COMPACT)
                    Text(L("timeline_layout_grid")).tag(TimelineLayout.GRID)
                }
                .pickerStyle(.segmented)
                SettingsSwitchRow(icon: "figure.roll", title: L("settings_easy_mode"), subtitle: L("settings_easy_mode_subtitle"),
                                  isOn: Binding(get: { model.settings.easyMode }, set: { v in model.updateSettings { $0.easyMode = v } }))
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_appearance"))
    }
}

private struct ThemeSwatch: View {
    let theme: AppTheme
    let selected: Bool
    let action: () -> Void
    @Environment(\.look) private var look

    var body: some View {
        let p = YTheme(palette: YTheme.palette(for: theme), dark: look.colors.dark)
        Button(action: action) {
            VStack(spacing: Spacing.sm) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radius.md).fill(p.surface)
                    HStack(spacing: Spacing.xs) {
                        Circle().fill(p.primary).frame(width: 24, height: 24)
                        Circle().fill(p.secondary).frame(width: 24, height: 24)
                        Circle().fill(p.tertiary).frame(width: 24, height: 24)
                    }
                }
                .frame(height: 64)
                .overlay(RoundedRectangle(cornerRadius: Radius.md).stroke(selected ? look.colors.primary : look.colors.outlineVariant, lineWidth: selected ? 3 : 1))
                Text(themeName(theme)).font(look.type.labelLarge).foregroundStyle(selected ? look.colors.primary : look.colors.onSurface)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Security (SecurityScreen.kt): lock method, change PIN, Face ID, lock timeout, hide in app switcher, wipe after ten.
struct SecurityView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var changingPin = false

    var body: some View {
        let s = model.settings
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(L("security_lock_method")).font(look.type.titleMedium)
                Text(L(s.lockMethod == .PIN ? "security_lock_pin" : "security_lock_device")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                if s.lockMethod == .PIN {
                    Button { changingPin = true } label: {
                        SettingsRowLabel(icon: "number", title: L("security_change_pin"), subtitle: L("security_change_pin_help"), chevron: true)
                    }
                    .buttonStyle(.plain)
                    SettingsSwitchRow(icon: BiometricKeys.kind == .touchID ? "touchid" : "faceid", title: L("security_biometric"),
                                      subtitle: L(BiometricKeys.isAvailable ? "security_biometric_help" : "security_biometric_unavailable"),
                                      isOn: Binding(get: { s.biometricEnabled }, set: { model.setBiometric($0) }))
                        .disabled(!BiometricKeys.isAvailable)
                    if DeviceAuth.isAvailable {
                        Button { Task { _ = await model.chooseDeviceLock() } } label: {
                            SettingsRowLabel(icon: "iphone", title: L("security_use_device_lock"), subtitle: L("security_use_device_lock_help"), chevron: true)
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    Button { changingPin = true } label: {
                        SettingsRowLabel(icon: "number", title: L("security_set_pin"), subtitle: L("security_set_pin_help"), chevron: true)
                    }
                    .buttonStyle(.plain)
                }
                Text(L("security_timeout")).font(look.type.titleMedium)
                Text(L("security_timeout_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Picker(L("security_timeout"), selection: Binding(get: { s.lockTimeout }, set: { v in model.updateSettings { $0.lockTimeout = v } })) {
                    Text(L("timeout_immediately")).tag(LockTimeout.IMMEDIATELY)
                    Text(L("timeout_30s")).tag(LockTimeout.THIRTY_SECONDS)
                    Text(L("timeout_1m")).tag(LockTimeout.ONE_MINUTE)
                    Text(L("timeout_5m")).tag(LockTimeout.FIVE_MINUTES)
                }
                .pickerStyle(.segmented)
                SettingsSwitchRow(icon: "eye.slash", title: L("security_secure_screen"), subtitle: L("security_secure_screen_help"),
                                  isOn: Binding(get: { s.secureScreen }, set: { v in model.updateSettings { $0.secureScreen = v } }))
                if s.lockMethod == .PIN {
                    SettingsSwitchRow(icon: "trash", title: L("security_wipe"), subtitle: L("security_wipe_help"),
                                      isOn: Binding(get: { s.wipeAfterTenFailures }, set: { v in model.updateSettings { $0.wipeAfterTenFailures = v } }))
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_security"))
        .sheet(isPresented: $changingPin) {
            ChangePinView().environment(\.look, look)
        }
    }
}

/// New PIN twice; the vault keys are re-sealed (the keys themselves never change).
private struct ChangePinView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var pin = ""
    @State private var first = ""
    @State private var mismatch = false
    @State private var saving = false

    var body: some View {
        VStack(spacing: Spacing.lg) {
            Spacer()
            Text(L(first.isEmpty ? "setup_choose_title" : "setup_confirm_title")).font(look.type.headlineMedium)
            Text(L(mismatch ? "setup_mismatch" : "setup_pin_help")).font(look.type.bodyMedium)
                .foregroundStyle(mismatch ? look.colors.error : look.colors.onSurfaceVariant).multilineTextAlignment(.center)
            PinDots(count: pin.count, error: false)
            if saving { ProgressView() } else {
                PinPad(enabled: true, onDigit: { d in
                    guard pin.count < pinLength else { return }
                    pin.append(d)
                    guard pin.count == pinLength else { return }
                    if first.isEmpty {
                        first = pin
                        pin = ""
                        mismatch = false
                    } else if pin == first {
                        saving = true
                        let chosen = pin
                        Task {
                            try? await model.setPin(chosen)
                            if model.settings.biometricEnabled { model.setBiometric(true) }
                            dismiss()
                        }
                    } else {
                        first = ""
                        pin = ""
                        mismatch = true
                    }
                }, onBackspace: { if !pin.isEmpty { pin.removeLast() } })
            }
            Spacer()
        }
        .padding(Spacing.xl)
        .background(look.colors.surface.ignoresSafeArea())
    }
}

/// Relive reminders (NotificationsScreen.kt). Notifications are scheduled locally with the reminder digest.
struct NotificationsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look

    var body: some View {
        let s = model.settings
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                SettingsSwitchRow(icon: "calendar", title: L("notif_on_this_day"), subtitle: L("notif_on_this_day_help"),
                                  isOn: Binding(get: { s.onThisDayEnabled }, set: { v in model.updateSettings { $0.onThisDayEnabled = v }; Notifier.reschedule(model) }))
                DatePicker(L("notif_time"), selection: Binding(get: {
                    Calendar.current.date(bySettingHour: s.onThisDayMinuteOfDay / 60, minute: s.onThisDayMinuteOfDay % 60, second: 0, of: Date()) ?? Date()
                }, set: { d in
                    let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                    model.updateSettings { $0.onThisDayMinuteOfDay = (c.hour ?? 9) * 60 + (c.minute ?? 0) }
                    Notifier.reschedule(model)
                }), displayedComponents: .hourAndMinute)
                    .font(look.type.bodyLarge)
                    .padding(.horizontal, Spacing.md)
                SettingsSwitchRow(icon: "gift", title: L("notif_birthdays"), subtitle: L("notif_birthdays_help"),
                                  isOn: Binding(get: { s.birthdayReminders }, set: { v in model.updateSettings { $0.birthdayReminders = v }; Notifier.reschedule(model) }))
                SettingsSwitchRow(icon: "text.bubble", title: L("notif_prompts"), subtitle: L("notif_prompts_help"),
                                  isOn: Binding(get: { s.storyPrompts }, set: { v in model.updateSettings { $0.storyPrompts = v }; Notifier.reschedule(model) }))
                SettingsSwitchRow(icon: "square.grid.2x2", title: L("notif_widget"), subtitle: L("notif_widget_help"),
                                  isOn: Binding(get: { s.widgetShowsMemories }, set: { v in model.updateSettings { $0.widgetShowsMemories = v }; WidgetBridge.update(model) }))
                SettingsSwitchRow(icon: "iphone.radiowaves.left.and.right", title: L("notif_shake"), subtitle: L("notif_shake_help"),
                                  isOn: Binding(get: { s.shakeForMemory }, set: { v in model.updateSettings { $0.shakeForMemory = v } }))
                Text(L("notif_privacy")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_notifications"))
        .task { await Notifier.requestPermission() }
    }
}

/// Storage (StorageScreen.kt): usage, optimised copies, check my memories, erase everything.
struct StorageView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var confirmErase = false
    @State private var checking = false
    @State private var progress = (0, 0)
    @State private var result: (problems: Int, memories: Int)?

    var body: some View {
        let _ = model.revision
        let db = model.store?.snapshot ?? VaultDatabase()
        let photos = db.media.filter { $0.kind == .PHOTO }
        let videos = db.media.filter { $0.kind == .VIDEO }
        let total = db.media.reduce(Int64(0)) { $0 + $1.sizeBytes }
        let free = (try? URL(fileURLWithPath: NSHomeDirectory()).resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]))?
            .volumeAvailableCapacityForImportantUsage ?? 0
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(L("storage_used", formatBytes(total))).font(look.type.headlineSmall)
                Text(L("storage_free", formatBytes(free))).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Text(LP("storage_photos", photos.count)).font(look.type.bodyLarge)
                Text(LP("storage_videos", videos.count)).font(look.type.bodyLarge)
                Text(L("storage_database")).font(look.type.bodyLarge)
                SettingsSwitchRow(icon: nil, title: L("storage_optimize"), subtitle: L("storage_optimize_help"),
                                  isOn: Binding(get: { model.settings.storeOptimizedCopies }, set: { v in model.updateSettings { $0.storeOptimizedCopies = v } }))
                Text(L("storage_check")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                Text(L("storage_check_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                if checking {
                    Text(L("storage_check_progress", progress.0, progress.1)).font(look.type.bodyMedium)
                } else if let result {
                    Text(result.problems == 0 ? L("storage_check_ok") : L("storage_check_problems", result.problems, result.memories))
                        .font(look.type.bodyMedium).foregroundStyle(result.problems == 0 ? look.colors.success : look.colors.error)
                    Button(L("storage_check_again")) { runCheck(db) }.buttonStyle(TextButtonStyle())
                } else {
                    Button(L("storage_check_run")) { runCheck(db) }.buttonStyle(FilledButtonStyle(tonal: true))
                }
                Spacer().frame(height: Spacing.lg)
                Text(L("storage_danger")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                Text(L("storage_erase_help")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Button(L("storage_erase"), role: .destructive) { confirmErase = true }
                    .buttonStyle(TextButtonStyle())
                    .foregroundStyle(look.colors.error)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_storage"))
        .alert(L("storage_erase"), isPresented: $confirmErase) {
            Button(L("storage_erase_yes"), role: .destructive) { model.eraseEverything() }
            Button(L("action_cancel"), role: .cancel) {}
        } message: { Text(L("storage_erase_confirm")) }
    }

    /// "Check my memories" (VaultChecker): every file decrypts and matches the SHA-256 recorded at import.
    private func runCheck(_ db: VaultDatabase) {
        guard let vault = model.vault else { return }
        checking = true
        result = nil
        let media = db.media
        progress = (0, media.count)
        Task.detached(priority: .utility) {
            var bad = Set<Int64>()
            var problems = 0
            for (i, m) in media.enumerated() {
                let ok = vault.exists(m.encryptedPath) && ((try? vault.sha256OfPlaintext(at: vault.url(m.encryptedPath))) == m.sha256)
                if !ok {
                    problems += 1
                    if let id = m.memoryId { bad.insert(id) }
                }
                let done = i + 1
                await MainActor.run { progress = (done, media.count) }
            }
            let summary = (problems, bad.count)
            await MainActor.run {
                result = summary
                checking = false
            }
        }
    }
}

/// The .yaadein document for share/import sheets.
struct YaadeinBackupFile: FileDocument {
    static var readableContentTypes: [UTType] { [.data] }
    let url: URL
    init(url: URL) { self.url = url }
    init(configuration: ReadConfiguration) throws { throw CocoaError(.fileReadUnsupportedScheme) }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { try FileWrapper(url: url, options: .immediate) }
}

/// Backup and restore (BackupScreen.kt): the same password-protected `.yaadein` file as Android, so a backup made
/// on either phone restores on the other.
struct BackupView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var askPassword: PasswordPurpose?
    @State private var working: String?
    @State private var exported: YaadeinBackupFile?
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var importURL: URL?
    @State private var pending: (BackupCodec.Archive, URL)?
    @State private var message: (String, String)?

    enum PasswordPurpose: Identifiable {
        case export, restore
        var id: Int { self == .export ? 0 : 1 }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(L("backup_intro")).font(look.type.bodyLarge)
                Button(L("backup_export")) { askPassword = .export }.buttonStyle(FilledButtonStyle(fullWidth: true))
                Button(L("backup_import")) { showImporter = true }.buttonStyle(FilledButtonStyle(fullWidth: true, tonal: true))
                if let working {
                    HStack(spacing: Spacing.md) { ProgressView(); Text(working).font(look.type.bodyMedium) }
                }
                Text(L("backup_note")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
                Divider().padding(.vertical, Spacing.sm)
                AutoBackupSection()
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_backup"))
        .sheet(item: $askPassword) { purpose in
            PasswordSheet(title: L(purpose == .export ? "backup_set_password" : "backup_enter_password"),
                          message: L("backup_password_help"), confirm: purpose == .export) { pw in
                askPassword = nil
                if purpose == .export { export(pw) } else if let url = importURL { restore(url, pw) }
            }
            .environment(\.look, look)
        }
        .fileExporter(isPresented: $showExporter, document: exported, contentType: .data,
                      defaultFilename: "Yaadein-\(LocalDate.today().description).\(BackupCodec.fileExtension)") { result in
            if let url = exported?.url { try? FileManager.default.removeItem(at: url) }
            exported = nil
            if case .success = result {
                model.updateSettings { $0.lastBackupAtMillis = Int64(Date().timeIntervalSince1970 * 1000) }
                message = (L("backup_exported_title"), L("backup_exported_text"))
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.data, .item]) { result in
            if case let .success(url) = result {
                importURL = url
                askPassword = .restore
            }
        }
        .alert(L("backup_restore_confirm_title"), isPresented: Binding(get: { pending != nil }, set: { if !$0 { cleanupPending() } })) {
            Button(L("backup_restore_now"), role: .destructive) { applyRestore() }
            Button(L("action_cancel"), role: .cancel) { cleanupPending() }
        } message: {
            Text(L("backup_restore_confirm_text", pending?.0.manifest.persons ?? 0, pending?.0.manifest.memories ?? 0, pending?.0.manifest.media ?? 0))
        }
        .alert(message?.0 ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button(L("action_done")) { message = nil }
        } message: { Text(message?.1 ?? "") }
    }

    private func export(_ password: String) {
        guard let store = model.store, let vault = model.vault else { return }
        working = L("backup_exporting")
        let aead = model.aead
        let db = store.snapshot
        Task.detached(priority: .userInitiated) {
            let out = FileManager.default.temporaryDirectory.appendingPathComponent("backup-\(UUID().uuidString).yaadein")
            do {
                let salt = ChunkedAEAD.randomBytes(16)
                let key = Argon2id.hash(password: password, salt: salt)
                let sink = try FileSink(url: out)
                try BackupCodec.write(database: db, to: sink, key: key, salt: salt, aead: aead,
                                      now: Int64(Date().timeIntervalSince1970 * 1000),
                                      mediaFile: { path, write in try vault.stream(path, to: write) },
                                      exists: { vault.exists($0) })
                try sink.close()
                await MainActor.run {
                    working = nil
                    exported = YaadeinBackupFile(url: out)
                    showExporter = true
                }
            } catch {
                try? FileManager.default.removeItem(at: out)
                await MainActor.run {
                    working = nil
                    message = (L("backup_error_title"), L("error_generic"))
                }
            }
        }
    }

    private func restore(_ url: URL, _ password: String) {
        working = L("backup_restoring")
        let aead = model.aead
        Task.detached(priority: .userInitiated) {
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            let staging = FileManager.default.temporaryDirectory.appendingPathComponent("restore-\(UUID().uuidString)")
            do {
                try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
                let source = try FileSource(url: url)
                defer { try? source.close() }
                let header = try BackupCodec.readHeader(source)
                let key = Argon2id.hash(password: password, salt: header.salt)
                let (_, archive) = try BackupCodec.read(source, header: header, key: key, aead: aead, staging: staging)
                guard let archive else { throw BackupCodec.BackupError.notABackup }
                await MainActor.run {
                    working = nil
                    pending = (archive, staging)
                }
            } catch {
                try? FileManager.default.removeItem(at: staging)
                let wrong = (error as? BackupCodec.BackupError) == .wrongPassword
                await MainActor.run {
                    working = nil
                    message = (L("backup_error_title"), L(wrong ? "backup_wrong_password" : "error_generic"))
                }
            }
        }
    }

    private func applyRestore() {
        guard let p = pending, let store = model.store, let vault = model.vault else { return }
        let archive = p.0, staging = p.1
        pending = nil
        working = L("backup_restoring")
        Task.detached(priority: .userInitiated) {
            defer { try? FileManager.default.removeItem(at: staging) }
            do {
                if archive.manifest.pack {
                    let (db, added, newPeople, _) = try BackupCodec.merged(archive, into: store.snapshot) { url, folder, ext in
                        try vault.store(file: url, folder: folder, ext: ext).path
                    }
                    try store.replaceAll(db)
                    await MainActor.run {
                        working = nil
                        message = (L("pack_added_title"), L("pack_added_text", added, newPeople))
                    }
                } else {
                    let old = store.snapshot
                    let db = try BackupCodec.restoredDatabase(archive) { url, folder, ext in try vault.store(file: url, folder: folder, ext: ext).path }
                    try store.replaceAll(db)
                    old.media.forEach { vault.delete($0.encryptedPath); vault.delete($0.thumbPath) }
                    old.memories.compactMap(\.voiceNotePath).forEach { vault.delete($0) }
                    await MainActor.run {
                        working = nil
                        message = (L("backup_restored_title"), L("backup_restored_text"))
                    }
                }
            } catch {
                await MainActor.run {
                    working = nil
                    message = (L("backup_error_title"), L("error_generic"))
                }
            }
        }
    }

    private func cleanupPending() {
        if let staging = pending?.1 { try? FileManager.default.removeItem(at: staging) }
        pending = nil
    }
}

/// Password entry with show/hide and inline checks (PasswordDialog.kt).
struct PasswordSheet: View {
    let title: String
    let message: String
    let confirm: Bool
    let done: (String) -> Void
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var again = ""
    @State private var show = false

    var body: some View {
        let short = password.count < BackupCodec.minPasswordLength
        let mismatch = confirm && password != again
        NavigationStack {
            VStack(alignment: .leading, spacing: Spacing.md) {
                Text(message).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                field(L("pw_password"), $password)
                if confirm { field(L("pw_again"), $again) }
                Toggle(L("pw_show"), isOn: $show).font(look.type.bodyMedium)
                if !password.isEmpty && short { Text(L("pw_too_short", BackupCodec.minPasswordLength)).font(look.type.bodySmall).foregroundStyle(look.colors.error) }
                if confirm && !again.isEmpty && mismatch { Text(L("pw_mismatch")).font(look.type.bodySmall).foregroundStyle(look.colors.error) }
                Spacer()
            }
            .padding(Spacing.gutter)
            .screenTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L("action_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(L("action_done")) { done(password) }.disabled(short || mismatch) }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder private func field(_ label: String, _ text: Binding<String>) -> some View {
        SwiftUI.Group {
            if show { TextField(label, text: text) } else { SecureField(label, text: text) }
        }
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .font(look.type.bodyLarge)
        .padding(.horizontal, Spacing.lg)
        .frame(minHeight: look.sizes.touchTarget)
        .background(RoundedRectangle(cornerRadius: Radius.sm).stroke(look.colors.outline))
    }
}

/// Privacy (six sections, same text as Android's in-app privacy screen).
struct PrivacyView: View {
    @Environment(\.look) private var look
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                ForEach(1...6, id: \.self) { i in
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(L("privacy_s\(i)_title")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                        Text(L("privacy_s\(i)_body")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    }
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("privacy_title"))
    }
}

struct AboutView: View {
    @Environment(\.look) private var look
    var body: some View {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        ScrollView {
            VStack(spacing: Spacing.md) {
                Illustration(kind: .photoStack).frame(width: look.sizes.illustration * 0.8, height: look.sizes.illustration * 0.8)
                Text(L("app_name")).font(look.type.displaySmall)
                Text(L("about_tagline")).font(look.type.titleLarge).multilineTextAlignment(.center)
                Text(L("about_version", version)).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Text(L("app_credit")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
                NavigationLink(value: Route.settings(.appInfo)) { Text(L("app_info_open")) }.buttonStyle(FilledButtonStyle(fullWidth: true, tonal: true))
                Text(L("about_offline")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
                Text(L("about_fonts")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("settings_about"))
    }
}

/// "What is Yaadein?" (AppInfoScreen.kt).
struct AppInfoView: View {
    @Environment(\.look) private var look
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Text(L("app_info_headline")).font(look.type.headlineSmall)
                Text(L("app_info_intro")).font(look.type.bodyLarge)
                ForEach(["capture", "relive", "reels", "private", "backup", "share"], id: \.self) { k in
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(L("app_info_\(k)_title")).font(look.type.titleMedium).accessibilityAddTraits(.isHeader)
                        Text(L("app_info_\(k)_body")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                    }
                }
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("app_info_title"))
    }
}
