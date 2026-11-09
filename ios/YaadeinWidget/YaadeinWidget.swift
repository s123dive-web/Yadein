import SwiftUI
import WidgetKit

/// "Yaadein: on this day" (RemindersWidget on Android): the line the app prepared for today and a + button that opens
/// quick add. Tapping the widget opens the memory when titles are shown, otherwise the app.
struct WidgetEntry: TimelineEntry {
    let date: Date
    let day: AppGroup.WidgetDay?
    let addLabel: String
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> WidgetEntry { WidgetEntry(date: Date(), day: nil, addLabel: text("widget_add")) }

    func getSnapshot(in context: Context, completion: @escaping (WidgetEntry) -> Void) { completion(entries().first ?? placeholder(in: context)) }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WidgetEntry>) -> Void) {
        let list = entries()
        completion(Timeline(entries: list.isEmpty ? [placeholder(in: context)] : list, policy: .atEnd))
    }

    /// One entry per prepared day, each starting at local midnight.
    private func entries() -> [WidgetEntry] {
        guard let data = AppGroup.readWidget() else { return [] }
        let cal = Calendar.current
        let todayStart = cal.startOfDay(for: Date())
        let today = epochDay(todayStart)
        return data.days.filter { $0.epochDay >= today }.map { d in
            let start = cal.date(byAdding: .day, value: Int(d.epochDay - today), to: todayStart) ?? todayStart
            return WidgetEntry(date: max(start, Date().addingTimeInterval(-1)), day: d, addLabel: data.addLabel)
        }
    }

    private func epochDay(_ date: Date) -> Int64 {
        let local = date.timeIntervalSince1970 + Double(TimeZone.current.secondsFromGMT(for: date))
        return Int64((local / 86_400).rounded(.down))
    }
}

func text(_ key: String) -> String { Bundle.main.localizedString(forKey: key, value: nil, table: nil) }

/// Warm Album, the default theme (Palettes on Android): plum, amber, cream; dark surface for dark mode.
private enum WidgetColors {
    static let primaryLight = Color(red: 0x5B / 255, green: 0x2A / 255, blue: 0x86 / 255)
    static let primaryDark = Color(red: 0xD6 / 255, green: 0xBA / 255, blue: 0xFF / 255)
    static let amber = Color(red: 0xE8 / 255, green: 0xA3 / 255, blue: 0x3D / 255)
    static let surfaceLight = Color(red: 0xFB / 255, green: 0xF5 / 255, blue: 0xEC / 255)
    static let surfaceDark = Color(red: 0x1A / 255, green: 0x14 / 255, blue: 0x18 / 255)
}

struct WidgetView: View {
    let entry: WidgetEntry
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetFamily) private var family

    var body: some View {
        let primary = scheme == .dark ? WidgetColors.primaryDark : WidgetColors.primaryLight
        let title = entry.day?.title ?? text("widget_title_default")
        let line = entry.day?.text ?? text("widget_open_app")
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(title).font(.custom("Fraunces-SemiBold", size: 17)).foregroundStyle(primary).lineLimit(1)
                Spacer(minLength: 4)
                Link(destination: URL(string: "\(AppGroup.urlScheme)://quick")!) {
                    Image(systemName: "plus")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(WidgetColors.surfaceLight)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(primary))
                }
                .accessibilityLabel(entry.addLabel)
            }
            Text(line)
                .font(.custom("ManropeExtraLight-Medium", size: 14))
                .foregroundStyle(.primary)
                .lineLimit(family == .systemSmall ? 4 : 3)
            Spacer(minLength: 0)
            Capsule().fill(WidgetColors.amber).frame(width: 28, height: 4)
        }
        .widgetURL(URL(string: entry.day?.memoryId.map { "\(AppGroup.urlScheme)://memory/\($0)" } ?? "\(AppGroup.urlScheme)://open"))
        .containerBackground(for: .widget) { scheme == .dark ? WidgetColors.surfaceDark : WidgetColors.surfaceLight }
    }
}

@main
struct YaadeinWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "YaadeinReminders", provider: Provider()) { WidgetView(entry: $0) }
            .configurationDisplayName(text("widget_name"))
            .description(text("widget_description"))
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
