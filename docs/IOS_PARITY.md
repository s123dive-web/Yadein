# Android ↔ iPhone parity checklist

✅ same on both · 🟡 on iPhone with a platform difference (noted) · ⬜ not on iPhone yet

## Foundation

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Strings in 8 languages | ✅ | ✅ | generated from strings.xml; per-app language via iOS Settings |
| 5 palettes, light/dark, Easy mode | ✅ | ✅ | Material You (dynamic colour) is Android-only |
| Fonts Fraunces + Manrope, type scale | ✅ | ✅ | same font files |
| Illustrations, cover art, avatars, memory card | ✅ | ✅ | same geometry and colours |
| Onboarding → first person → PIN → home | ✅ | ✅ | |
| PIN lock, lockout 30 s…1 h, wipe after 10 | ✅ | ✅ | |
| Biometric unlock | ✅ fingerprint/face | ✅ Face ID / Touch ID | copy invalidated by new enrolment on both |
| Phone screen lock mode | ✅ | ✅ | |
| Lock timeout | ✅ | ✅ | |
| Block screenshots | ✅ FLAG_SECURE | 🟡 | iOS cannot block screenshots; the app is hidden in the app switcher |
| Encrypted database and media (YDN2) | ✅ | ✅ | same media format |
| No network access | ✅ | ✅ | |

## Capture

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Create hub (+) | ✅ | ✅ | |
| Event-first (date chips, title, place, people, mood, media, cover) | ✅ | ✅ | |
| Photo-first auto grouping, merge, split | ✅ | ✅ | same PhotoGrouper |
| Text-only + Polish | ✅ | ✅ | same PolishEngine (tested with the Kotlin cases) |
| Quick capture | ✅ | ✅ | |
| Bulk upload by year / month | ✅ | ✅ | same PeriodGrouper |
| Fill in the past | ✅ | ✅ | |
| Import limits (20 MB photo, 200 MB video), optimised copies | ✅ | ✅ | |
| Dictation in text capture | ✅ | 🟡 | iOS keyboard dictation (mic key) |
| Voice memories | ✅ | ⬜ | |
| Share → Yaadein from other apps | ✅ | ⬜ | needs a share extension |
| Google Photos Takeout / WhatsApp import | ✅ | ⬜ | parsing rules already ported (ExportParsing) |

## Relive and people

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Timeline cards / compact / grid, year & month headers | ✅ | ✅ | |
| Search (prefix, names, captions) and filters | ✅ | ✅ | same matching rules |
| Memory detail, media viewer, captions, tag people, cover | ✅ | ✅ | |
| People grid, groups, person story, Together | ✅ | ✅ | |
| Relationships map | ✅ | ✅ | |
| On this day, surprise me, shake, birthdays, places, years | ✅ | ✅ | |
| Yearly summary | ✅ | ✅ | |
| Weekly story prompt | ✅ | ✅ | same rotation |
| Reminders (on this day, birthdays, prompts) | ✅ | 🟡 | iOS schedules 30 days ahead when the app is opened |
| Home-screen widget | ✅ | ⬜ | WidgetKit |
| Reels: planner (beat snap, Ken Burns, selection) | ✅ | ✅ | same RecapPlanner / AutoReelPicker |
| Reels: rendering, library, player, automatic reels | ✅ | ⬜ | AVFoundation renderer to port |
| Face suggestions | ✅ | ⬜ | Vision framework |

## Trust

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Backup / restore `.yaadein` | ✅ | ✅ | **cross-platform**: Android backups restore on iPhone and back |
| Family packs | ✅ | 🟡 | importing a pack works; sending a pack from a memory is ⬜ |
| Automatic weekly backup | ✅ | ⬜ | |
| Check my memories | ✅ | ✅ | |
| Erase everything | ✅ | ✅ | |
| Picture card / PDF sharing | ✅ | ⬜ | |
| Problem log | ✅ | ⬜ | iOS has its own crash reports |
| Update app (sideload) | ✅ | n/a | iPhone apps update through TestFlight / App Store |
