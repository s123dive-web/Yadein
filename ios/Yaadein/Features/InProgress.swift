import SwiftUI
import YaadeinDomain

// Screens whose full iOS port lands in the following commits. Each shows a designed empty state, never a blank page.

private struct Pending: View {
    let title: String
    var body: some View {
        ScrollView { EmptyState(illustration: .boxClosed, title: title, message: L("loading")) }
            .screenTitle(title)
    }
}

struct PeopleView: View { var body: some View { Pending(title: L("people_title")) } }
struct ReliveView: View { let onPlace: (String) -> Void; var body: some View { Pending(title: L("relive_title")) } }
struct SettingsView: View { var body: some View { Pending(title: L("settings_title")) } }
struct MemoryDetailView: View { let memoryId: Int64; var body: some View { Pending(title: "") } }
struct MediaViewerView: View { let memoryId: Int64; let startIndex: Int; var body: some View { Pending(title: "") } }
struct PersonDetailView: View { let personId: Int64; var body: some View { Pending(title: "") } }
struct GroupEditView: View { let groupId: Int64?; var body: some View { Pending(title: L("group_new_title")) } }
struct TogetherView: View { let initialIds: [Int64]; var body: some View { Pending(title: L("together_title")) } }
struct RelationshipsMapView: View { var body: some View { Pending(title: L("relationships_title")) } }
struct YearlySummaryView: View { let personId: Int64; let year: Int; var body: some View { Pending(title: String(year)) } }
struct EventCaptureView: View { let memoryId: Int64?; var body: some View { Pending(title: L("event_new_title")) } }
struct TextOnlyView: View { var body: some View { Pending(title: L("text_title")) } }
struct QuickCaptureView: View { var body: some View { Pending(title: L("quick_title")) } }
struct PhotoFirstView: View { var body: some View { Pending(title: L("photos_title")) } }
struct BulkUploadView: View { var body: some View { Pending(title: L("bulk_title")) } }
struct PastWizardView: View { var body: some View { Pending(title: L("past_title")) } }
struct SettingsPageView: View { let page: SettingsPage; var body: some View { Pending(title: L("settings_title")) } }
struct RandomMemoryView: View { var body: some View { Pending(title: L("relive_random_title")) } }
