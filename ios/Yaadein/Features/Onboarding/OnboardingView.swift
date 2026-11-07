import SwiftUI

/// Three pages, then "Add your first person" (OnboardingScreen.kt).
struct OnboardingView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var page = 0

    private let pages: [(IllustrationKind, String, String)] = [
        (.photoStack, "onboarding_1_title", "onboarding_1_body"),
        (.lockHeart, "onboarding_2_title", "onboarding_2_body"),
        (.peopleCircle, "onboarding_3_title", "onboarding_3_body"),
    ]

    var body: some View {
        let last = page == pages.count - 1
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if !last { Button(L("action_skip")) { model.finishOnboarding() }.buttonStyle(TextButtonStyle()) }
            }
            .frame(height: look.sizes.touchTarget)
            .padding(Spacing.md)
            TabView(selection: $page) {
                ForEach(pages.indices, id: \.self) { i in
                    VStack(spacing: 0) {
                        Illustration(kind: pages[i].0).frame(width: look.sizes.illustration * 1.3, height: look.sizes.illustration * 1.3)
                        Spacer().frame(height: Spacing.xxl)
                        Text(L(pages[i].1))
                            .font(look.type.headlineLarge)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                            .minimumScaleFactor(0.8)
                            .accessibilityAddTraits(.isHeader)
                        Spacer().frame(height: Spacing.md)
                        Text(L(pages[i].2))
                            .font(look.type.bodyLarge)
                            .foregroundStyle(look.colors.onSurfaceVariant)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: 480)
                    .padding(.horizontal, Spacing.xxl)
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            HStack(spacing: 0) {
                ForEach(pages.indices, id: \.self) { i in
                    Capsule()
                        .fill(i == page ? look.colors.primary : look.colors.surfaceContainerHighest)
                        .frame(width: i == page ? 24 : 8, height: 8)
                        .padding(Spacing.xs)
                }
            }
            .animation(Motion.standard, value: page)
            Spacer().frame(height: Spacing.lg)
            Button(L(last ? "onboarding_add_first_person" : "action_next")) {
                if last { model.finishOnboarding() } else { withAnimation(Motion.standard) { page += 1 } }
            }
            .buttonStyle(FilledButtonStyle(fullWidth: true))
            .frame(maxWidth: 480)
            .padding(.horizontal, Spacing.xl)
            Spacer().frame(height: Spacing.xl)
        }
        .background(look.colors.surface.ignoresSafeArea())
    }
}
