import Foundation
import WidgetKit
import YaadeinData
import YaadeinDomain

/// Feeds the home-screen widget (RemindersWidget.render on Android). The widget cannot open the encrypted vault, so the
/// app words the next seven days ahead whenever the vault is open and the library changes, and the widget only shows
/// those lines. Unless "Show memories on the home-screen widget" is on, the lines hold counts, never titles or names.
@MainActor
enum WidgetBridge {
    static func update(_ model: AppModel) {
        guard let store = model.store else { return }
        let showTitles = model.settings.widgetShowsMemories
        let digest = YaadeinDomain.Reminders.build(cards: store.cards(), people: store.persons(), nowMillis: Int64(Date().timeIntervalSince1970 * 1000))
        let today = LocalDate.today()
        let days: [AppGroup.WidgetDay] = (0..<7).map { offset in
            let day = today.plusDays(Int64(offset))
            let matches = YaadeinDomain.Reminders.onThisDay(digest, today: day)
            if let first = matches.first {
                return AppGroup.WidgetDay(epochDay: day.epochDay, title: L("widget_title_on_this_day"),
                                          text: showTitles ? LP("widget_years_ago", first.yearsAgo, first.memory.title) : LP("widget_memories_waiting", matches.count),
                                          memoryId: showTitles ? first.memory.id : nil)
            }
            if let b = YaadeinDomain.Reminders.birthdays(digest, today: day, daysAhead: 1).first {
                let text = showTitles ? L(b.inDays == 0 ? "widget_birthday_today" : "widget_birthday_tomorrow", b.person.name) : L("widget_birthday_hidden")
                return AppGroup.WidgetDay(epochDay: day.epochDay, title: L("widget_title_birthday"), text: text, memoryId: nil)
            }
            let pick = YaadeinDomain.Reminders.weeklyPrompt(digest, today: day)
            let prompt: String
            if let p = pick.person {
                prompt = LA(p.relation == .FRIEND ? "prompts_friend" : "prompts_family", pick.index, p.name)
            } else {
                prompt = LA("prompts_self", pick.index)
            }
            return AppGroup.WidgetDay(epochDay: day.epochDay, title: L("widget_title_prompt"), text: showTitles ? prompt : L("widget_prompt_hidden"), memoryId: nil)
        }
        let data = AppGroup.WidgetData(days: days, addLabel: L("widget_add"))
        do {
            try FileManager.default.createDirectory(at: AppGroup.container, withIntermediateDirectories: true)
            try JSONEncoder().encode(data).write(to: AppGroup.widgetFile, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            return
        }
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Erase everything: the widget goes back to "Open Yaadein once".
    static func clear() {
        try? FileManager.default.removeItem(at: AppGroup.widgetFile)
        AppGroup.clearShares()
        WidgetCenter.shared.reloadAllTimelines()
    }
}
