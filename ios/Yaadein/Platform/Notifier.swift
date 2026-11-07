import Foundation
import UserNotifications
import YaadeinData
import YaadeinDomain

/// Local reminders (OnThisDayWorker + Reminders on Android): "On this day", birthdays and the Sunday story prompt.
/// iOS cannot run code at a set time, so the next 30 days are scheduled ahead from the reminder digest whenever the
/// vault is open and the library changes. Notifications show titles and first names only; nothing leaves the phone.
@MainActor
enum Notifier {
    static func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    static func reschedule(_ model: AppModel) {
        guard let store = model.store else { return }
        WidgetBridge.update(model)
        let settings = model.settings
        let digest = YaadeinDomain.Reminders.build(cards: store.cards(), people: store.persons(), nowMillis: Int64(Date().timeIntervalSince1970 * 1000))
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let today = LocalDate.today()
        let hour = settings.onThisDayMinuteOfDay / 60, minute = settings.onThisDayMinuteOfDay % 60
        var count = 0
        for offset in 0..<30 where count < 60 {
            let day = today.plusDays(Int64(offset))
            var c = DateComponents()
            c.year = day.year
            c.month = day.month
            c.day = day.day
            c.hour = hour
            c.minute = minute
            let content = UNMutableNotificationContent()
            content.sound = .default
            if settings.onThisDayEnabled, let match = YaadeinDomain.Reminders.onThisDay(digest, today: day).first {
                content.title = L("notification_on_this_day_title", match.memory.title)
                content.body = LP("notification_years_ago", match.yearsAgo)
                content.userInfo = ["memoryId": match.memory.id]
            } else if settings.birthdayReminders, let b = YaadeinDomain.Reminders.birthdays(digest, today: day, daysAhead: 1).first {
                content.title = L(b.inDays == 0 ? "notification_birthday_today" : "notification_birthday_tomorrow", b.person.name)
                content.body = L("notification_birthday_text")
            } else if settings.storyPrompts, YaadeinDomain.Reminders.isPromptDay(day) {
                let pick = YaadeinDomain.Reminders.weeklyPrompt(digest, today: day)
                content.title = L("notification_prompt_title")
                if let p = pick.person {
                    content.body = LA(p.relation == .FRIEND ? "prompts_friend" : "prompts_family", pick.index, p.name)
                } else {
                    content.body = LA("prompts_self", pick.index)
                }
            } else {
                continue
            }
            count += 1
            let request = UNNotificationRequest(identifier: "yaadein-\(day.description)", content: content,
                                                trigger: UNCalendarNotificationTrigger(dateMatching: c, repeats: false))
            center.add(request)
        }
    }
}
