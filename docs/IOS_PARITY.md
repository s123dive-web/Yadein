# Android ↔ iPhone parity checklist

✅ same on both · 🟡 on iPhone with a platform difference (noted) · ⬜ not on iPhone yet

## Foundation

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Strings in 8 languages | ✅ | ✅ | generated from strings.xml; per-app language via iOS Settings |
| 5 palettes, light/dark, Easy mode | ✅ | ✅ | Material You (dynamic colour) is Android-only |
| Fonts Fraunces + Manrope, type scale | ✅ | ✅ | same font files |
| Illustrations, cover art, avatars, memory card | ✅ | ✅ | same geometry and colours |
| Onboarding → first person → PIN → home | replaced (First Minute, Phase 1) | ✅ | Android now starts photos-first; the iPhone app keeps the slides → person → PIN flow |
| Relive story rings, full-screen story player with music, Then & Now, milestones, favourites (Relive 2, Phase 2) | ✅ | ⬜ deferred | Pure rules in `core/domain/relive` (`StoryRings`, `ThenAndNow`, `Milestones`, `StoryPicks`) are ready to port to YaadeinKit with their tests; a backup written by Android carries `favorite` (ignored by older readers) |
| Photos-first welcome (language picker, pick photos → first story, sample family with one-tap clear, lock offered after the first memory, people from faces) (First Minute, Phase 1) | ✅ | ⬜ deferred | iOS is frozen this round; the new strings are generated for it |
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
| Voice memories (new memory, or a note on a memory; story prompt opens the recorder) | ✅ | ✅ | AAC, encrypted, streamed back from the vault |
| Quick Save: + opens the Photo Picker, pre-filled date/title/people/place, Save returns at once, progress ring on the card, copy-first import, `capture_stats` (Fast & Rich, Phase 1) | ✅ | ⬜ deferred | iOS keeps the hub; `QuickSavePrefill` / `CaptureTimer` rules to port later with their Kotlin tests |
| Share → Yaadein from other apps | ✅ | 🟡 | share extension "Save to Yaadein" keeps the files in the App Group; the new memory opens with them the next time Yaadein is opened and unlocked (iOS does not let a share sheet open the app) |
| Google Photos Takeout / WhatsApp import | ✅ | ✅ | same ExportParsing rules; zip streamed on the phone |

## Relive and people

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Timeline cards / compact / grid, year & month headers | ✅ | ✅ | |
| Search (prefix, names, captions) and filters | ✅ | ✅ | same matching rules |
| Memory detail, media viewer, captions, tag people, cover | ✅ | ✅ | |
| People grid, groups, person story, Together | ✅ | ✅ | |
| Relationships map | ✅ | ✅ | |
| On this day, surprise me, shake, birthdays, places, years | ✅ | ✅ | |
| Relive: on this day widened to this week / this month (`Rewind`), "N years ago" labels, year and place photo tiles, one-time shake tip, birthday "Make a reel" within 7 days, surprise reveal + haptic, refresh on return | ✅ | ✅ | same `Rewind` / `Covers` rules and tests on both |
| Relive daily story (memories from years ago, person spotlight, memory of the day; auto-advance, tap/swipe, pause on hold) | ✅ | ✅ | same `DailyStory` rules; memory of the day pinned by a shared test |
| Yearly summary | ✅ | ✅ | |
| Weekly story prompt | ✅ | ✅ | same rotation |
| Reminders (on this day, birthdays, prompts) | ✅ | 🟡 | iOS schedules 30 days ahead when the app is opened |
| Home-screen widget | ✅ | ✅ | WidgetKit, small and medium; counts only unless "Show memories on the widget" is on; + opens quick add |
| Reels: planner (beat snap, Ken Burns, selection) | ✅ | ✅ | same RecapPlanner / AutoReelPicker |
| Reels: rendering, library (newest 10), player, share, 3 automatic reels | ✅ | ✅ | AVFoundation renderer, same storyboard, music and 1080×1920 output; automatic reels refresh when Your reels opens |
| Reels: choose exact photos, own music | ✅ | ✅ | own music from Files |
| Reels: Your songs (songs from the phone remembered for every reel, search, Hindi/Marathi suggestions) | ✅ | ⬜ | Android: `MusicLibrary` + `OwnMusicSheet`, system picker only (no audio permission). iOS still picks one file from Files per reel |
| Face suggestions | ✅ v2 | 🟡 diverged, deferred | Android (Fast & Rich, Phase 2): GhostFaceNet embeddings, person centroids, auto-tag ≥ 0.75 with undo, "Who is this?" groups, background re-index. iPhone still has the LBP descriptor; a Core ML port of the same model is deferred |

## Trust

| Feature | Android | iPhone | Notes |
|---------|---------|--------|-------|
| Backup / restore `.yaadein` | ✅ | ✅ | **cross-platform**: Android backups restore on iPhone and back |
| Family packs (send from a memory, add from Backup) | ✅ | ✅ | same file format both ways |
| Automatic weekly backup | ✅ | ✅ | into a folder you choose (iCloud Drive, Google Drive's Files folder…), after an unlock when a week has passed |
| Check my memories | ✅ | ✅ | |
| Erase everything | ✅ | ✅ | |
| Picture card sharing | ✅ | ✅ | |
| Festivals: bundled 2010–2035 table, "This Diwali, years ago" in Relive, festival titles on Quick Save, festival-day reminder, timeline festival filter (Fast & Rich, Phase 3) | ✅ | ⬜ deferred | `Festivals.kt` + `festivals.csv` are pure; port to YaadeinKit with `FestivalsTest` |
| Kinship terms ("Aaji", "Mama"…) on people, side-of-family colours on the relationships map (Fast & Rich, Phase 3) | ✅ | ⬜ deferred | `Relations.kt` is pure; `persons.kin_term` / `relation_text` restore from backups with null defaults |
| WhatsApp status reel preset (30 s hard cap, ≈3.5 Mbit/s, "Send to WhatsApp") (Fast & Rich, Phase 3) | ✅ | ⬜ deferred | iPhone shares through the system sheet |
| PDF book | ✅ | ✅ | |
| Problem log | ✅ | ⬜ | iOS has its own crash reports |
| Update app (sideload) | ✅ | n/a | iPhone apps update through TestFlight / App Store |
