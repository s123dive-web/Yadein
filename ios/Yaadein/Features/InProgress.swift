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

struct PhotoFirstView: View { var body: some View { Pending(title: L("photos_title")) } }
struct BulkUploadView: View { var body: some View { Pending(title: L("bulk_title")) } }
struct PastWizardView: View { var body: some View { Pending(title: L("past_title")) } }
