import Combine
import SwiftUI
import YaadeinDomain

enum HomeTab: Hashable { case timeline, people, create, relive, settings }

/// Home (HomeScaffold.kt): bottom tabs Timeline, People, Create (+), Relive, Settings. Easy mode keeps Timeline and
/// Settings with a big + button. Create opens the capture hub sheet instead of a tab.
struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var tab: HomeTab = .timeline
    @State private var lastTab: HomeTab = .timeline
    @State private var hubOpen = false
    @State private var paths: [HomeTab: NavigationPath] = [:]
    @State private var placeFilter: String?

    private func path(_ t: HomeTab) -> Binding<NavigationPath> {
        Binding(get: { paths[t] ?? NavigationPath() }, set: { paths[t] = $0 })
    }

    var body: some View {
        let easy = model.settings.easyMode
        ZStack(alignment: .bottomTrailing) {
            TabView(selection: $tab) {
                NavigationStack(path: path(.timeline)) {
                    TimelineView(onCreate: { hubOpen = true }, placeFilter: $placeFilter).yaadeinDestinations()
                }
                .tabItem { Label(L("nav_timeline"), systemImage: "calendar.day.timeline.left") }
                .tag(HomeTab.timeline)
                if !easy {
                    NavigationStack(path: path(.people)) { PeopleView().yaadeinDestinations() }
                        .tabItem { Label(L("nav_people"), systemImage: "person.2") }
                        .tag(HomeTab.people)
                    Color.clear
                        .tabItem { Label(L("nav_create"), systemImage: "plus.circle.fill") }
                        .tag(HomeTab.create)
                    NavigationStack(path: path(.relive)) {
                        ReliveView(onPlace: { place in
                            placeFilter = place
                            tab = .timeline
                        }).yaadeinDestinations()
                    }
                    .tabItem { Label(L("nav_relive"), systemImage: "sparkles") }
                    .tag(HomeTab.relive)
                }
                NavigationStack(path: path(.settings)) { SettingsView().yaadeinDestinations() }
                    .tabItem { Label(L("nav_settings"), systemImage: "gearshape") }
                    .tag(HomeTab.settings)
            }
            .onChange(of: tab) { old, new in
                if new == .create {
                    hubOpen = true
                    tab = old
                } else {
                    lastTab = new
                }
            }
            if easy {
                Button { hubOpen = true } label: {
                    Image(systemName: "plus").font(.system(size: look.sizes.iconLg, weight: .semibold))
                        .foregroundStyle(look.colors.onPrimary)
                        .frame(width: 96, height: 96)
                        .background(RoundedRectangle(cornerRadius: Radius.xl).fill(look.colors.primary))
                        .shadow(radius: 4, y: 2)
                }
                .accessibilityLabel(L("nav_create"))
                .padding(.trailing, Spacing.lg)
                .padding(.bottom, 64 + Spacing.lg)
            }
        }
        .sheet(isPresented: $hubOpen) {
            CaptureHubSheet { route in
                hubOpen = false
                tab = lastTab == .create ? .timeline : lastTab
                var p = paths[tab] ?? NavigationPath()
                p.append(route)
                paths[tab] = p
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .environment(\.look, look)
        }
        .onReceive(NotificationCenter.default.publisher(for: .yaadeinShake)) { _ in
            guard model.settings.shakeForMemory, (model.store?.memoryCount ?? 0) > 0 else { return }
            var p = paths[tab] ?? NavigationPath()
            p.append(Route.randomMemory)
            paths[tab] = p
        }
        .onChange(of: model.pendingMemoryId) { _, id in
            guard let id else { return }
            model.pendingMemoryId = nil
            tab = .timeline
            var p = NavigationPath()
            p.append(Route.memory(id))
            paths[.timeline] = p
        }
    }
}
