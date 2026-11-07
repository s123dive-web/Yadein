import SwiftUI
import YaadeinDomain

/// Navigation map (Routes.kt on Android).
enum Route: Hashable {
    case memory(Int64)
    case mediaViewer(memoryId: Int64, index: Int)
    case person(Int64)
    case personEdit(Int64?)
    case groupEdit(Int64?)
    case together([Int64])
    case relationships
    case yearly(personId: Int64, year: Int)
    case eventCapture(memoryId: Int64?)
    case textOnly
    case quick
    case photoFirst
    case bulkUpload
    case pastWizard
    case settings(SettingsPage)
    case randomMemory
}

enum SettingsPage: String, Hashable, CaseIterable {
    case appearance, security, notifications, storage, backup, privacy, about, appInfo
}

extension View {
    /// Every screen a tab's NavigationStack can push.
    func yaadeinDestinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case let .memory(id): MemoryDetailView(memoryId: id)
            case let .mediaViewer(memoryId, index): MediaViewerView(memoryId: memoryId, startIndex: index)
            case let .person(id): PersonDetailView(personId: id)
            case let .personEdit(id): PersonEditView(personId: id, firstRun: false)
            case let .groupEdit(id): GroupEditView(groupId: id)
            case let .together(ids): TogetherView(initialIds: ids)
            case .relationships: RelationshipsMapView()
            case let .yearly(personId, year): YearlySummaryView(personId: personId, year: year)
            case let .eventCapture(memoryId): EventCaptureView(memoryId: memoryId)
            case .textOnly: TextOnlyView()
            case .quick: QuickCaptureView()
            case .photoFirst: PhotoFirstView()
            case .bulkUpload: BulkUploadView()
            case .pastWizard: PastWizardView()
            case let .settings(page): SettingsPageView(page: page)
            case .randomMemory: RandomMemoryView()
            }
        }
    }
}

/// Top bar title in the Fraunces headline style, as on Android's TopAppBar.
struct ScreenTitle: ViewModifier {
    let title: String
    var large = false
    @Environment(\.look) private var look

    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text(title).font(large ? look.type.headlineMedium : look.type.titleLarge).accessibilityAddTraits(.isHeader)
                }
            }
            .toolbarBackground(look.colors.surface, for: .navigationBar)
            .background(look.colors.surface.ignoresSafeArea())
    }
}

extension View {
    func screenTitle(_ title: String, large: Bool = false) -> some View { modifier(ScreenTitle(title: title, large: large)) }
}
