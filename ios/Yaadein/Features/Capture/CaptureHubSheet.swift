import SwiftUI

/// The + sheet (CaptureHubSheet.kt): two big ways in, bulk upload, and the rest under "More ways".
struct CaptureHubSheet: View {
    let open: (Route) -> Void
    @Environment(\.look) private var look
    @State private var more = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(L("hub_title")).font(look.type.headlineSmall).accessibilityAddTraits(.isHeader)
                Text(L("hub_subtitle")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant)
                Spacer().frame(height: Spacing.sm)
                HStack(alignment: .top, spacing: Spacing.sm) {
                    bigOption("photo.on.rectangle.angled", L("hub_photos"), L("hub_photos_short"), primary: true) { open(.photoFirst) }
                    bigOption("bolt.fill", L("hub_quick"), L("hub_quick_short"), primary: false) { open(.quick) }
                }
                .fixedSize(horizontal: false, vertical: true)
                option("square.stack.3d.up", L("hub_bulk"), L("hub_bulk_sub")) { open(.bulkUpload) }
                option("mic", L("hub_voice"), L("hub_voice_sub")) { open(.voiceCapture(memoryId: nil, prompt: nil)) }
                Button {
                    withAnimation(Motion.standard) { more.toggle() }
                } label: {
                    HStack {
                        Text(L(more ? "hub_fewer" : "hub_more"))
                        Image(systemName: more ? "chevron.up" : "chevron.down")
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(TextButtonStyle())
                if more {
                    option("calendar", L("hub_event"), L("hub_event_sub")) { open(.eventCapture(memoryId: nil)) }
                    option("text.alignleft", L("hub_text"), L("hub_text_sub")) { open(.textOnly) }
                    option("clock.arrow.circlepath", L("hub_past"), L("hub_past_sub")) { open(.pastWizard) }
                    option("square.and.arrow.down", L("hub_import"), L("hub_import_sub")) { open(.importExport) }
                }
            }
            .padding(.horizontal, Spacing.gutter)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xxl)
        }
        .background(look.colors.surfaceContainerLow.ignoresSafeArea())
    }

    private func bigOption(_ icon: String, _ title: String, _ subtitle: String, primary: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Image(systemName: icon).font(.system(size: look.sizes.iconLg * 0.8))
                Text(title).font(look.type.titleMedium)
                Text(subtitle).font(look.type.bodyMedium).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .foregroundStyle(primary ? look.colors.onPrimary : look.colors.onPrimaryContainer)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(primary ? look.colors.primary : look.colors.primaryContainer))
        }
        .buttonStyle(.plain)
    }

    private func option(_ icon: String, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.lg) {
                Image(systemName: icon).font(.system(size: look.sizes.iconLg * 0.75)).foregroundStyle(look.colors.primary).frame(width: look.sizes.iconLg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(look.type.titleMedium).foregroundStyle(look.colors.onSurface)
                    Text(subtitle).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
            }
            .padding(Spacing.lg)
            .background(RoundedRectangle(cornerRadius: Radius.lg).fill(look.colors.surfaceContainer))
        }
        .buttonStyle(.plain)
    }
}
