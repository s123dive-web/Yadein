import SwiftUI
import YaadeinDomain

@main
struct YaadeinApp: App {
    @State private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .onOpenURL { model.handle(url: $0) }
        }
        .onChange(of: scenePhase) { _, phase in model.scenePhaseChanged(phase) }
    }
}

/// Root graph (AppNavHost + YaadeinApp on Android): onboarding → first person → PIN → home, with the lock screen
/// replacing everything while the vault is closed.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ThemeRoot(settings: model.settings) {
            ZStack {
                switch model.phase {
                case .onboarding: OnboardingView()
                case .firstPerson: NavigationStack { PersonEditView(personId: nil, firstRun: true) }
                case .lockSetup: LockSetupView()
                case .locked: LockView()
                case .home: HomeView()
                }
                // Hide content in the app switcher when "Block screenshots" is on (FLAG_SECURE on Android).
                if model.settings.secureScreen && scenePhase != .active { PrivacyCurtain() }
            }
            .animation(Motion.standard, value: model.phase)
        }
    }
}

private struct PrivacyCurtain: View {
    @Environment(\.look) private var look
    var body: some View {
        ZStack {
            look.colors.surface.ignoresSafeArea()
            Illustration(kind: .lockHeart).frame(width: 160, height: 160)
        }
    }
}
