# Yaadein — project brain

Yaadein is a premium, fully offline Android app for privately capturing memories of family and
friends (photos, videos, or just words) and reliving them as timelines, person-wise stories and
auto-generated recap videos with music. It is built as a sellable Play Store product.

This file is the single source of truth for architecture, decisions, conventions and phase status.
Update it at the end of every phase.

---

## Phase status

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| 0 | Plan | ✅ done | This document (modules, schema, tokens, nav map, workflow YAML) |
| 1 | Foundation and look | ✅ done | Skeleton, design system, 5 themes + dynamic, onboarding, PIN + biometric lock, SQLCipher DB, CI, demo seed, Timeline/People/Detail, 14 screenshots |
| 2 | Capture | ✅ done | Event-first, photo-first grouping, text-only + Polish, quick capture, backdating, past wizard, limits, people/groups |
| 3 | Relive | ✅ done | Filters, search, Together, relationships map, on-this-day, shake, yearly summaries |
| 4 | Recap video | ✅ done | Themes, music, Transformer pipeline, preview, share |
| 5 | Trust and polish | ✅ done | Backup/restore, face suggestions, Easy mode, a11y, perf, baseline profiles, privacy, release workflow, listing assets |

Phase STOP reports are appended at the bottom of this file under "Phase reports". The owner asked
for the build to proceed autonomously while they are offline, so each STOP report is recorded here
and work continues to the next phase; every phase is still a separate commit so any phase can be
reviewed or reverted in isolation.

---

## 1. Non-negotiables (from the master prompt)

- 100% on-device. **No INTERNET permission**, no analytics, no crash SDKs, no ads, no accounts.
  A unit test fails the build if `android.permission.INTERNET` appears in the merged manifest.
- Only these permissions: `USE_BIOMETRIC`, `POST_NOTIFICATIONS`, `CAMERA` (optional, in-app capture),
  `VIBRATE`. Photo Picker is used for import (no storage permissions ever).
- No hardcoded colors, sizes or strings: everything goes through `Tokens.kt` and string resources
  (English, Hindi, Marathi).
- Every write is atomic: Room transactions; media uses temp-file → encrypt → verify checksum → DB row
  in one transaction → rename/delete temp.
- Room migrations from version 1 onward with migration tests; never `fallbackToDestructiveMigration`.
- Designed empty states on every screen; no placeholder/lorem UI.
- Minimum 48 dp touch targets, font scaling, Easy mode, TalkBack content descriptions.
- APKs are built by GitHub Actions; the debug build needs no local.properties, keystore or secrets.

## 2. Tech stack (pinned)

| Concern | Choice |
|---------|--------|
| Language / UI | Kotlin 2.4.x, Jetpack Compose (BOM 2026.09.00), Material 3 (1.4.x) with dynamic color, Navigation Compose 2.10 (type-safe routes via kotlinx.serialization) |
| Build | AGP 9.5.1 (built-in Kotlin, no `kotlin-android` plugin), Gradle 9.8.2 wrapper, JDK 17 on CI, KSP 2.3.x |
| SDK | minSdk 26, compileSdk 37, targetSdk 37 (AGP 9.5 max API), 16 KB page-size compatible, edge-to-edge, predictive back |
| Persistence | Room 2.8.x + SQLCipher (`net.zetetic:sqlcipher-android` 4.20) via `SupportOpenHelperFactory`, WAL mode, versioned migrations |
| Settings | DataStore (Preferences) wrapped with AES-GCM via Keystore key (`EncryptedDataStore`); PIN hash + wrapped DB key in `EncryptedSharedPreferences` |
| DI / async | Hilt 2.60, Coroutines + Flow, WorkManager 2.12 (Hilt workers) |
| Security | BiometricPrompt (class 3) + 6-digit PIN (Argon2id via `argon2kt`), `FLAG_SECURE` default on, Keystore AES keys |
| Media | Coil 3 (custom encrypted fetcher), Media3 1.11 ExoPlayer (custom decrypting `DataSource`), Media3 Transformer for recap rendering, `ExifInterface` for dates |
| Faces (optional) | ML Kit Face Detection 16.1.7 (bundled model, on device); lightweight local embedding + clustering |
| Smart polish (optional) | ML Kit GenAI Prompt API (Gemini Nano via AICore) behind a capability check; rule-based Polish engine is the default and always works |
| Testing | JUnit 5 (android-junit5), Turbine, MockK, Room migration tests, Robolectric Compose UI tests, Compose Preview Screenshot Testing |

## 3. Module structure

```
yaadein/
├── app/                      com.yaadein.app — Application, MainActivity, NavHost, lock gate, Hilt root, workers registration
├── core/
│   ├── domain/               pure Kotlin/JVM: models, repository interfaces, use cases, Polish engine,
│   │                         date parsing/fuzzy dates, photo auto-grouping, relationship graph maths
│   ├── data/                 Room + SQLCipher, DAOs, entities, migrations, mappers, repositories impl,
│   │                         crypto (Keystore, media encryption), media import pipeline, DataStore settings,
│   │                         demo seed data, backup/restore container
│   ├── ui/                   Tokens.kt, themes, typography, components (MemoryCard, PersonAvatar, EmptyState,
│   │                         DateChips, FaceStack, Illustrations), encrypted Coil/Media3 bridges, window size helpers
│   └── testing/              fakes, test rules, sample fixtures shared by tests
└── feature/
    ├── onboarding/           three-screen onboarding → "Add your first person"
    ├── lock/                 lock screen (biometric + PIN), PIN setup, lockout logic UI
    ├── timeline/             home river of time, filters, search
    ├── people/               people grid, person story, person/group editors, Together, relationships map
    ├── memory/               memory detail, full-screen media viewer, edit
    ├── capture/              create hub, event-first, photo-first (auto-group), text-only + Polish, quick capture, past wizard
    ├── relive/               on-this-day, shake, yearly summaries, notifications
    ├── recap/                recap video setup, preview, render worker UI
    └── settings/             appearance/themes, security, storage, backup/restore, notifications, privacy policy, about, crash log export
```

Dependency rule: `feature:* → core:ui, core:domain` (and `core:data` only for Hilt bindings via `app`).
`core:data → core:domain`. `core:ui → core:domain`. `core:domain` depends on nothing Android.

Package root: `com.yaadein`. Each module uses `com.yaadein.<module>`.

## 4. Room schema (version 1)

All IDs are `Long` autogenerated. Dates on the timeline are stored as epoch-day integers so that
fuzzy dates sort sensibly; instants (createdAt, takenAt) are epoch millis UTC.

```
persons
  id INTEGER PK, name TEXT NOT NULL, nickname TEXT, relation TEXT NOT NULL (FAMILY|FRIEND|COLLEAGUE|OTHER),
  birthday_epoch_day INTEGER, profile_media_id INTEGER (FK media.id, SET NULL), notes TEXT,
  avatar_color_index INTEGER NOT NULL, created_at INTEGER NOT NULL
  index(name)

groups
  id INTEGER PK, name TEXT NOT NULL, emoji TEXT NOT NULL, created_at INTEGER NOT NULL

group_members
  group_id INTEGER FK groups.id CASCADE, person_id INTEGER FK persons.id CASCADE, PK(group_id, person_id), index(person_id)

memories
  id INTEGER PK, type TEXT NOT NULL (EVENT|QUICK|TEXT_ONLY),
  title TEXT NOT NULL, description TEXT NOT NULL,
  date_kind TEXT NOT NULL (EXACT|MONTH|YEAR|FUZZY),
  start_epoch_day INTEGER NOT NULL,      -- EXACT: the day; MONTH: 1st of month; YEAR: Jan 1; FUZZY: anchor day
  end_epoch_day INTEGER,                 -- multi-day events
  fuzzy_label TEXT,                      -- "Summer 2015"
  place TEXT NOT NULL DEFAULT '',
  mood TEXT,                             -- JOYFUL|CALM|NOSTALGIC|PROUD|FUNNY|SAD|CELEBRATION|null
  cover_media_id INTEGER (FK media.id, SET NULL),
  cover_style TEXT NOT NULL,             -- gradient/illustration key (e.g. "beach", "wedding", "diwali", "plain_1")
  voice_note_path TEXT,
  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
  index(start_epoch_day DESC), index(type)

media
  id INTEGER PK, memory_id INTEGER FK memories.id CASCADE (nullable while importing),
  kind TEXT NOT NULL (PHOTO|VIDEO), encrypted_path TEXT NOT NULL, thumb_path TEXT NOT NULL,
  taken_at INTEGER, duration_ms INTEGER, size_bytes INTEGER NOT NULL, caption TEXT NOT NULL DEFAULT '',
  width INTEGER NOT NULL, height INTEGER NOT NULL, sha256 TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
  index(memory_id)

media_persons   (media_id FK CASCADE, person_id FK CASCADE, PK(media_id, person_id), index(person_id))
memory_persons  (memory_id FK CASCADE, person_id FK CASCADE, PK(memory_id, person_id), index(person_id))

face_clusters
  id INTEGER PK, embedding BLOB NOT NULL, sample_media_id INTEGER FK media.id CASCADE,
  sample_left REAL, sample_top REAL, sample_right REAL, sample_bottom REAL,
  suggested_person_id INTEGER FK persons.id SET NULL, confirmed INTEGER NOT NULL DEFAULT 0,
  face_count INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL

memory_search (FTS4, tokenizer=unicode61)
  memory_id INTEGER (docid), body TEXT   -- denormalised: title + description + place + captions + tagged person names
  (maintained by MemoryRepository inside the same transaction as any memory/media/tag write)

import_jobs    -- resumable media import pipeline state (survives process death)
  id INTEGER PK, source_uri TEXT NOT NULL, memory_id INTEGER, stage TEXT NOT NULL (COPYING|ENCRYPTING|VERIFYING|DONE|FAILED),
  temp_path TEXT, target_path TEXT, thumb_path TEXT, sha256 TEXT, optimize INTEGER NOT NULL, created_at INTEGER NOT NULL
```

Migration policy: `AppDatabase.VERSION` starts at 1. Every schema change adds `MIGRATION_n_n1` in
`core/data/src/main/kotlin/com/yaadein/data/db/Migrations.kt` and a test in `MigrationTest` using
exported schemas under `core/data/schemas/`.

## 5. Design tokens (core/ui/theme/Tokens.kt)

- **Spacing** (4-pt grid): `xxs 2, xs 4, sm 8, md 12, lg 16, xl 24, xxl 32, xxxl 48` dp
- **Radius**: `sm 8, md 12, lg 20, xl 28, pill 999` dp — cards use `lg`, sheets `xl`, chips `pill`
- **Elevation**: `none 0, low 1, card 2, raised 4, modal 8` dp
- **Motion**: `fast 120 ms, normal 220 ms, slow 360 ms, emphasized 480 ms`; easing = Material 3 standard/emphasized
- **Touch target**: minimum 48 dp; Easy mode bumps text scale ×1.3 and min target 56 dp
- **Type scale** (headlines Fraunces serif, body Manrope sans, both bundled under `core/ui/res/font/`):
  `displayLarge 44/52`, `displayMedium 36/44`, `headlineLarge 30/38`, `headlineMedium 26/32`,
  `headlineSmall 22/28`, `titleLarge 20/28 (serif)`, `titleMedium 16/24 sans 600`, `titleSmall 14/20 sans 600`,
  `bodyLarge 16/24`, `bodyMedium 14/20`, `bodySmall 12/16`, `labelLarge 14/20 600`, `labelMedium 12/16 600`, `labelSmall 11/16 600`
- **Color roles** per theme (light + dark variants), exposed as a `ColorScheme` plus extended roles
  `YaadeinColors { memoryGradients, avatarPalette (8), moodColors, faceRing, timelineLine }`:

| Theme | Primary | Secondary / accent | Surface (light) | Surface (dark) |
|-------|---------|--------------------|-----------------|----------------|
| Warm Album (default) | Deep plum `#5B2A86` | Soft amber `#E8A33D` | Cream `#FBF5EC` | `#1A1418` |
| Midnight | Indigo `#7C8CFF` | Rose `#FF8FA3` | `#F2F3FA` | Near-black `#0E1020` |
| Monsoon | Teal `#0F766E` | Leaf `#5FA86B` | Mist `#EEF6F4` | `#0C1A18` |
| Festive | Marigold `#F2A900` | Vermilion `#C8102E` | Ivory `#FFF8E7` | `#1E1206` |
| Minimal Mono | Ink `#1F1F1F` | Graphite `#6B6B6B` | Paper `#FAFAFA` | `#101010` |
| Material You | dynamic (Android 12+) | dynamic | dynamic | dynamic |

Theme choice, dark mode (system/light/dark) and Easy mode are persisted in DataStore and applied
instantly at the `YaadeinTheme` root.

Icons: Compose `Icons.Rounded.*` (Material rounded set) only. One FAB (+) on home; bottom nav:
Timeline, People, Create (opens hub), Relive, Settings.

## 6. Navigation map (type-safe routes, Navigation Compose)

```
RootGraph
├── Onboarding (3 pages → "Add your first person" → PersonEdit(new) → Home)      [first run only]
├── LockSetup (choose PIN → confirm → enable biometrics?)                           [first run after onboarding]
├── Lock (biometric prompt auto-shows; PIN pad fallback; lockout timer)            [overlay whenever locked]
└── Home (scaffold with bottom nav + FAB; two-pane on expanded width)
    ├── Timeline            (filters sheet, search bar) ──► MemoryDetail(id) ──► MediaViewer(memoryId, index)
    ├── People              ──► PersonDetail(id) ──► Together(ids) / YearlySummary(personId, year)
    │                       ──► PersonEdit(id?) / GroupEdit(id?) / RelationshipsMap
    ├── Create (hub sheet)  ──► EventCapture(memoryId?) / PhotoFirst / TextOnly / QuickCapture / PastWizard
    ├── Relive              ──► OnThisDay / RandomMemory(shake) / YearlySummary / RecapSetup
    │                       RecapSetup ──► RecapPreview ──► (WorkManager render) ──► share sheet
    └── Settings            ──► Appearance / Security / Storage / Backup / Notifications / Faces / Privacy / About / CrashLog
```

Shared-element transitions: Timeline card → MemoryDetail cover → MediaViewer (Compose
`SharedTransitionLayout`). Lists fade in gently; no gratuitous animation.

## 7. Encryption design (summary; full text in README)

- **DB key**: 32 random bytes generated on first launch, wrapped with an Android Keystore AES-256-GCM
  key (`yaadein_master`), persisted in EncryptedSharedPreferences. SQLCipher opens the DB with the raw key.
- **Media key**: separate 32 random bytes wrapped the same way. Files are encrypted with
  AES-256-CTR (random 16-byte IV stored in the file header) so playback can seek; integrity is a
  SHA-256 of the plaintext stored in the DB and verified at import. Thumbnails use the same scheme.
- **PIN**: Argon2id (m=64 MiB, t=3, p=2), 16-byte salt, stored in EncryptedSharedPreferences; 5 wrong
  PINs → escalating delay (30 s, 1 m, 5 m, 15 m, 1 h); optional wipe after 10.
- **Backup**: `.yaadein` file = ZIP of (db dump + media + manifest) encrypted with AES-256-GCM, key derived
  from the user password with Argon2id. Restore verifies the manifest before touching the live DB.

## 8. Conventions

- Kotlin official style, 4-space indent, trailing commas on. One screen = `XxxRoute` (wires VM) + `XxxScreen` (stateless, previewable).
- ViewModels expose a single `StateFlow<UiState>` and take `UiEvent`s; no logic in composables.
- Strings: `core/ui/res/values{,-hi,-mr}/strings.xml` for shared copy; feature strings live in the feature module.
- Previews for every screen with phone + tablet sizes; screenshot tests under `app/src/screenshotTest`.
- Commit at the end of each phase: `Phase N: <summary>`.

## 9. Build pipeline (exact workflow YAML)

`.github/workflows/build.yml`:

```yaml
name: Build debug APK

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: build-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: android-actions/setup-android@v3
      - name: Gradle cache
        uses: gradle/actions/setup-gradle@v4
      - name: Unit tests
        run: ./gradlew test --no-daemon --stacktrace
      - name: Assemble debug APK
        run: ./gradlew :app:assembleDebug --no-daemon --stacktrace
      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: Yaadein-debug-apk
          path: app/build/outputs/apk/debug/Yaadein-*.apk
      - name: Attach APK to release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: build-${{ github.run_number }}
          name: Build ${{ github.run_number }}
          files: app/build/outputs/apk/debug/Yaadein-*.apk
```

Note: the workflow also triggers on `ccr-*` branches so the autonomous build branch gets CI runs before merge.

`.github/workflows/release.yml` (manual) decodes `KEYSTORE_BASE64` into `release.jks`, reads
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and runs `:app:bundleRelease`, uploading the AAB.

## 10. Local build (for Claude's own verification)

The cloud container has the Android SDK at `/home/user/android-sdk` (not committed). Builds run with
`ANDROID_HOME=/home/user/android-sdk ./gradlew ...`. CI never needs this.

---

## Phase reports

### Phase 0 — Plan (STOP report)

**Built**: this document — module structure (§3), Room schema v1 (§4), design tokens (§5),
navigation map (§6), encryption design (§7), conventions (§8), exact workflow YAML (§9).

**Tested**: n/a (planning only). Verified that AGP 9.5.1 requires Gradle ≥ 9.6 and JDK 17 and supports
API 37, and that Compose BOM 2026.09.00 / Room 2.8.5 / Hilt 2.60.1 / Media3 1.11.1 / SQLCipher 4.20.0 are
the current stable versions.

**Decisions**:
- Long autogenerated IDs (simpler Room relations and paging; backup restores whole DB).
- Epoch-day dates for timeline placement; fuzzy dates anchor to the first day of their period and carry a label.
- AES-CTR for media (seekable playback) + SHA-256 integrity; AES-GCM for the small blobs (keys, settings, backup).
- Rule-based Polish engine in pure Kotlin so it is unit-testable and deterministic; Smart polish is an optional adapter.
- Screenshots come from Compose Preview Screenshot Testing (JVM, layoutlib) because no emulator is available in CI or the cloud container.

**Next**: Phase 1 — Foundation and look.

### Phase 1 — Foundation and look (STOP report)

**Built**
- Gradle 9.8.2 / AGP 9.5.1 (built-in Kotlin 2.4.21) multi-module project: `app`, `core:{domain,data,ui,testing}`,
  `feature:{onboarding,lock,timeline,people,memory,capture,relive,recap,settings}`; builds with no local.properties or secrets.
- Design system: `Tokens.kt` (spacing, radius, elevation, motion, sizes, Easy-mode sizes), five hand-tuned palettes with
  light/dark variants plus Material You, Fraunces + Manrope bundled, procedural `CoverArt` for text-only cards, eight
  procedural kid-friendly illustrations, `MemoryCardView`, `PersonAvatar`/`FaceStack`, `EmptyState`.
- Security: Keystore-wrapped vault keys, SQLCipher database (WAL), AES-CTR media encryption with SHA-256 integrity,
  Argon2id PIN with escalating lockout and optional wipe, encrypted DataStore settings, `FLAG_SECURE` toggle, lock on
  launch and after the configured background timeout, class-3 BiometricPrompt with PIN fallback.
- Screens: 3-page onboarding → add first person → PIN setup (+ biometrics) → Home. Timeline (paged, year/month headers,
  fuzzy dates, filters row, search field, designed empty states, two-pane with people/filter sidebar on expanded widths),
  People grid + groups row, Person story (stats, birthday, groups, Together/recap entry points, per-year chips),
  Memory detail (hero, people, media grid, cover selection, delete), Relive (On this day, surprise me, birthdays, years),
  Settings (Easy mode, Appearance with theme swatches + dark mode, About with demo loader), capture hub sheet.
- Demo seed: 10-person Indian family, 3 groups, 32 memories 2006–2026 with on-device generated encrypted photos;
  `seedLarge(2000)` for performance checks (debug-only button).
- CI: `build.yml` (tests → zero-network guard → debug APK artifact + GitHub Release `build-N`), `release.yml` (manual, signed AAB).
- Docs: README (architecture, encryption, pipeline, Data Safety answers), PRIVACY.md.

**Tested** (all green locally: `./gradlew test :app:validateDebugScreenshotTest`, 94 checks)
- JUnit 5: `DatesTest`, `LockoutPolicyTest`, `PolishEngineTest`, `PhotoGrouperTest`, `MediaCryptoTest`, `PinManagerTest`,
  `VaultKeysTest`, `EncryptedSettingsSerializerTest`, `AppLockStateTest`, `TimelineSeparatorTest`.
- Robolectric: `MemoryRepositoryTest` (create/update/delete/search/filters/together/on-this-day/story), `MigrationTest`
  (schema v1 validates; every version has a migration), `LockScreenTest` (Compose UI), `ThemeSwitchTest`, `ManifestPermissionsTest`.
- Build guard: `verifyNoNetworkPermissionDebug` wired into `assembleDebug`.
- Screenshots: 14 references under `app/src/screenshotTestDebug/reference/...` — Timeline (phone light/dark, tablet two-pane,
  Midnight, Festive, Easy mode, empty), Memory detail (phone, tablet), People (phone, tablet), Person story, Lock, Onboarding.

**Self-check checklist (section 12)**
- [x] INTERNET permission absent from merged manifest (Gradle task + unit test)
- [x] All unit, migration and UI tests pass (94)
- [ ] Lint zero errors — lint not yet run in CI (added in Phase 5 polish); strings/colours are all resourced/tokenised by construction
- [x] App lock, PIN and biometric flows verified — unit + Compose UI tests (no emulator available in this environment)
- [ ] Photo Picker import — Phase 2
- [x] Text-only, backdated and fuzzy-date memories render on the timeline (screenshots + repository ordering test)
- [x] Timeline, People tested on phone and tablet window sizes (screenshots); Together/Recap — Phases 3/4
- [x] Theme switching and Easy mode verified (test + screenshots)
- [x] Gradle wrapper committed, gradlew executable, workflow file valid
- [x] CLAUDE.md and README updated

**Design approval gate**: screenshots are committed for review. Per the owner's instruction to continue while offline,
Phase 2 proceeds on the same design language; any look changes requested later apply across all phases.

**Known gaps carried forward**: capture routes (EventCapture, PhotoFirst, TextOnly, QuickCapture, PastWizard), GroupEdit,
Together, RelationshipsMap, YearlySummary, RecapSetup, MediaViewer and the remaining Settings pages are referenced by
navigation but implemented in Phases 2–5.

**Next**: Phase 2 — Capture.

### Phase 2 — Capture (STOP report)

**Built**
- **Import pipeline** (`MediaImporter`, `ImportCoordinator`): inspect (mime, size, EXIF/video date) → limits (20 MB photo /
  200 MB video, rejected before any copy with a clear message) → copy to temp → optimise (photos ≤ 2560 px re-encoded upright,
  videos ≤ 1080p via Media3 Transformer; "Keep original" per memory) → thumbnail → AES-CTR encrypt → SHA-256 verify →
  one Room transaction (media row, first photo becomes cover, text-only card upgrades itself to an event, search reindex) →
  temp deleted. `import_jobs` stages make it resumable: `recoverPending()` at startup finishes verified files, discards
  partial ones and prunes orphans. Imports run in an app-scoped coroutine so they survive navigation.
- **Event-first**: date first (chips: Today / Yesterday / Pick date / Month only / Year only / "Roughly…" labels with
  Summer/Monsoon/Winter/Around Diwali presets), title, place, people, description, mood, media (Photo Picker, multi-select),
  cover style for text-only; also used for editing and "Add photos" from the detail screen.
- **Photo-first**: multi-pick → `PhotoGrouper` groups by same day / consecutive days (face splitting hook ready) → draft cards
  with suggested titles and date ranges → rename, change date, tag people, merge with previous, split, remove → save all.
- **Text-only + Polish**: rule-based `PolishEngine` (category keywords incl. Hinglish, numeric/month/year/season date parsing,
  known Indian places, title templates such as "Goa Getaway, December 2024", warm two-line descriptions, cover + mood) with a live
  card preview, dictation via the system recogniser (offline preferred), optional "Smart polish" toggle wired to a
  `SmartPolishProvider` (on-device model adapter; `NoSmartPolish` default so the feature is hidden when unsupported).
- **Quick capture ("Today")**: one line, optional photo, people chips, Done-key saves.
- **Fill in the past wizard**: year by year ("Anything memorable in 2019?"), text or voice, save-and-stay / save-and-next, progress.
- **People & groups**: profile photo import (encrypted), group editor with emoji presets and members; `MediaViewer`
  (pager, pinch-zoom photos, ExoPlayer video through the decrypting `EncryptedDataSource`, captions, tag people, remove).
- **Storage settings**: usage breakdown, optimised-copies default, "Erase everything".

**Tested** (`./gradlew test :app:validateDebugScreenshotTest`: 100 checks green)
- `PolishEngineTest` (11 cases incl. Hinglish, seasons, numeric dates, month without year), `PhotoGrouperTest`,
  `MediaImporterTest` (Robolectric: encrypt/verify/row/cover upgrade, limits before copy, unreadable source cleanup, downscale,
  video date parsing, recovery/pruning), plus all Phase 1 suites; 8 new screenshots (hub, event, text+polish, quick, photo-first,
  past wizard, group editor, storage).

**Self-check**: INTERNET absent ✔ · tests ✔ · lint (Phase 5) · lock flows ✔ · Photo Picker limits enforced ✔ (20 MB/200 MB
checked in unit tests; emulator run not possible here) · text-only/backdated/fuzzy ✔ · phone/tablet ✔ · themes/Easy ✔ ·
wrapper/workflow ✔ (CI run 1 failed on `setup-android` default packages; fixed by pinning packages) · docs ✔.

**Next**: Phase 3 — Relive.

### Phase 3 — Relive (STOP report)

**Built**
- **Filters**: bottom sheet with people (optionally "all together"), groups, places (from the library), kind (photos / stories /
  quick notes), from/to dates; active-filter chips row; FTS search field already in place. Two-pane sidebar filters on tablets.
- **Together**: pick two or more people → only memories where all of them appear (`requireAllPeople` SQL).
- **Relationships map**: bubble/force graph (deterministic Fruchterman–Reingold layout, bubbles sized by memory count, line
  weight = shared memories, tap → person).
- **On this day**: `OnThisDayWorker` (Hilt worker; one-time job re-armed daily at the chosen minute, VISIBILITY_SECRET
  notification that deep-links to the memory once unlocked); Settings → Relive reminders asks POST_NOTIFICATIONS, time picker.
- **Shake for a random memory**: `ShakeLogic` (3 spikes ≥ 2.6 g within 600 ms, 2 s cooldown) + accelerometer listener active
  while unlocked; toggle in settings.
- **Yearly summaries**: "Your 2024 with Rahul" / "Your 2024" — counts, places, most-time-with people, first/last memory, all
  memories, entry point to the recap video; reachable from Person story chips, Relive year chips and Home.
- **Security settings** (user-adjustable lock timeout immediately / 30 s / 1 m / 5 m, biometrics, FLAG_SECURE toggle,
  wipe-after-10, change PIN).

**Tested**: `ShakeLogicTest`, `RelationshipsLayoutTest` (bounds, determinism, connected pairs closer), all earlier suites;
6 new screenshots (Relive, Together, Relationships map, Yearly summary, Notification + Security settings).

**Next**: Phase 4 — Recap video.

### Phase 4 — Recap video (STOP report)

**Built**
- **Planner** (`RecapPlanner`, pure Kotlin): scope → chronological storyboard; intro/outro cards; per-memory caption on the first
  item; Ken Burns start/end framings (deterministic pseudo-random); video clips trimmed to 2–6 s; large albums capped to 8 evenly
  spaced items; text-only memories become cards; Short/Medium/Long presets bound photo durations.
- **Renderer** (`RecapRenderer`, Media3 1.11 Transformer): `Composition` with a video sequence (PNG cards, encrypted JPEGs via
  `yaadein://media/{id}` through `VaultDataSourceFactory` → `DataSourceBitmapLoader`, clipped encrypted videos via the same
  factory) and a looping audio sequence (bundled raw track or the user's own audio with a persisted document grant).
  Effects: `Presentation` 1920×1080 scale-to-fit-with-crop, `MatrixTransformation` Ken Burns (smoothstep), `BitmapOverlay`
  caption strips drawn with the bundled fonts, per-theme grading (Warm: RGB warm + saturation; Minimal: desaturate + contrast;
  Festive: saturation + contrast; Classic Film: grayscale + sepia tint + contrast). Output H.264/AAC MP4.
- **Music**: four original 60 s loops synthesised for the project (Warm, Minimal, Festive, Classic) bundled as AAC in `res/raw`.
- **Background job**: `RecapRenderWorker` (Hilt worker, foreground service with progress notification + cancel action,
  `mediaProcessing` type on API 35+, `dataSync` on 29–34), progress polled from `Transformer.getProgress`, saved to
  `Movies/Yaadein` via MediaStore (API 29+) or app Movies dir + FileProvider (API 26–28), share sheet from the result card.
- **UI**: `RecapRoute` — inline preview (Compose plays the storyboard with the same framings; tap to pause), storyboard strip
  with durations, scope chips (everything / person / group / memory / year), title, theme swatches, music chips, length,
  include-videos; render/running/done/failed states observed through WorkManager.

**Tested**: `RecapPlannerTest` (ordering/captions, clips vs Ken Burns, length bounds, album cap + text cards, determinism,
video exclusion); build passes with the zero-network guard; 1 new screenshot. The Transformer pipeline itself cannot run on the JVM
(needs MediaCodec/GL) — verified by API review against the 1.11 jars; first on-device run is a Phase 5 checklist item.

**Next**: Phase 5 — Trust and polish.

### Phase 5 — Trust and polish (STOP report)

**Built**
- **Backup/restore** (`BackupManager`): one `.yaadein` file = `YDNB` header + salt + nonce + AES-256-GCM(zip of manifest, all
  tables as JSON, media decrypted from the vault). Key = Argon2id(password). Restore peeks the manifest first (count confirmation),
  stages everything in cache, re-encrypts media with this device's key and swaps the database in one transaction; a wrong
  password fails before the live vault is touched. Settings → Backup and restore uses the SAF create/open document pickers.
- **Face suggestions** (off by default): ML Kit bundled face detector → 32×32 normalised grey crop embedding → deterministic
  nearest-centroid clustering (`FaceClustering`, pure Kotlin). Scans on import when enabled and on demand; Settings → Face
  suggestions shows "Is this Rahul?" cards; confirming tags the sample photo. Nothing is tagged automatically.
- **Easy mode** (Phase 1 tokens) is surfaced on the Settings root and Appearance; home collapses to Timeline + big +.
- **Accessibility**: `AccessibilityTest` asserts every clickable on Timeline, People, Lock and Settings has a label; `ContrastTest`
  checks eight text/background role pairs ≥ 4.5:1 for all five palettes in light and dark; headings are marked with
  `semantics { heading() }`; icons that are decorative use `contentDescription = null`.
- **Performance**: `TimelineScaleTest` seeds 2 000 memories and checks the first paged page and filtered/FTS queries stay fast;
  "Add 2,000 test memories" is in About (debug). Hand-authored `baseline-prof.txt` (startup + timeline hot classes) ships with
  `profileinstaller`; to be replaced by a Macrobenchmark-generated profile on a device.
- **Privacy**: in-app Privacy screen (6 sections), `PRIVACY.md`, Data Safety answers in README.
- **Crash-free**: `CrashLog` global handler writes a bounded local log; Settings → Problem log shows/exports/clears it via FileProvider.
- **Release**: `release.yml` (manual, base64 keystore secret → signed AAB + APK); `docs/PLAY_LISTING.md` with copy, assets index,
  permission explanations; feature graphic rendered at 1024 × 500 from a preview.

**Tested**: `BackupManagerTest` (round trip incl. media bytes, cover, tags, FTS; wrong password rejected), `FaceClusteringTest`,
`ContrastTest`, `TimelineScaleTest`, `AccessibilityTest`, all earlier suites; 4 new screenshots (Backup, Faces, Privacy, feature graphic).
**Final self-check checklist (section 12) — run before the last APK build**
- [x] INTERNET permission absent from merged manifest (`verifyNoNetworkPermissionDebug` + `ManifestPermissionsTest`)
- [x] All unit, migration and UI tests pass — 161 checks (JUnit 5, Robolectric, Compose UI, screenshot validation)
- [x] Lint: 0 errors across all modules (`checkDependencies = true`, `abortOnError = true`, run in CI); 51 warnings, none for
      hardcoded strings or colours (categories: UseKtx, PluralsCandidate, ApplySharedPref, AutoboxingStateCreation, UnusedResources)
- [x] App lock, PIN and biometric flows — unit + Compose UI tests; emulator unavailable in this environment (device run pending)
- [x] Photo Picker import limits (20 MB / 200 MB) enforced before copy — `MediaImporterTest`; device run pending
- [x] Text-only, backdated and fuzzy-date memories render correctly on the timeline (tests + screenshots)
- [x] Timeline, People, Together and Recap flows on phone and tablet window sizes — screenshots (tablet: Timeline, People, Memory detail)
- [x] Theme switching and Easy mode verified (`ThemeSwitchTest`, screenshots)
- [x] Gradle wrapper committed, gradlew executable, workflow valid (CI runs 2–4 green)
- [x] CLAUDE.md and README updated with phase status

**Known limitations / next steps for the owner**
- Recap rendering, TalkBack pass, biometric prompt and the 2 000-item scroll need a physical device or emulator; everything that
  can run on the JVM does.
- "Smart polish" ships behind `SmartPolishProvider` with the no-op implementation; wiring the ML Kit GenAI Prompt API is a
  drop-in once a Gemini-Nano device is available for testing.
- Replace `app/src/main/baseline-prof.txt` with a Macrobenchmark-generated profile.
- Create the upload keystore and add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` secrets, then run `release.yml`.

### Post-release fixes (0.1.0 follow-ups)

- **Lock timeout**: default moved from "immediately" to one minute; `SettingsDto.settingsVersion` (now 2) migrates files
  written by version 1 so existing installs pick up the new default unless the user chose "immediately" later.
- **PIN setup**: the vault unlocks as soon as the PIN is confirmed, so the lock overlay no longer covers the biometric step.
- **Crash visibility**: `CrashLog` leaves a `crash.pending` marker; Home shows `CrashNoticeBanner` with one-tap
  "Share report" / "Dismiss", so the problem log is reachable even when Settings itself is what crashed.
- **App smoke test** (`app/src/test/.../smoke/AppSmokeTest.kt`): Hilt `@TestInstallIn` replaces `DataModule` with JVM
  fakes (`TestDataModule`), launches the real `MainActivity` under Robolectric (API 35) and walks the home tabs, every
  settings page, the capture hub, demo data, the lock overlay and the crash banner.
- **Stable debug signature**: CI runners used to generate a fresh debug key on every run, so a new build could not be
  installed over the previous one (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) without uninstalling and losing all data.
  `app/debug.keystore` (public, password `android`, alias `androiddebugkey`) is now committed and used by the debug build;
  release builds still sign only with the secret keystore from `release.yml`.
- **Startup**: `YaadeinApplication` injects `AppDatabase`, `MediaStore` and `MediaImporter` as `dagger.Lazy`, so Keystore,
  EncryptedSharedPreferences and SQLCipher setup run on the IO thread that recovers imports, not on the main thread at process start.
- **Device check** (headless emulators in the cloud container, software rendering, no KVM):
  - API 34: onboarding, first person, PIN setup, lock, Home and the Settings tab worked in English and Hindi with the real
    Keystore/SQLCipher stack.
  - API 36 with the CI `build-11` APK: first-run setup, then every Settings page (Appearance, Security, Relive reminders,
    Face suggestions, Storage, Backup and restore, Privacy, Problem log, About) opened and closed in one process with no crash
    or ANR. Lock timeout: back after 20 s → no PIN; back after 75 s → PIN screen.
  - The unaccelerated emulator needed `cmd package compile -m speed` before the app could finish starting within the system's
    process-start timeout; that is an emulator artefact, but it motivated the lazy startup change above.
  - The owner's reported "Settings exits" was not reproduced on either image; the home banner lets the device's own report be shared.

### Device-reported fixes (Surprise me, recap export, problem log share, media viewer)

- **Surprise me crashed** with `IllegalStateException: Required value was null` in `MemoryDao_Impl.random`. Room's generated
  code for a `@Transaction` relation query steps the statement once to collect keys, resets it, then steps it again to build
  rows; with `ORDER BY RANDOM()` the second pass returned a different memory whose relations had not been loaded.
  `MemoryDao.random()` now picks an id with `randomId()` and loads it with the deterministic `getWithRelations(id)`.
  Rule: never put `RANDOM()` (or anything non-deterministic) in a Room query that returns a type with `@Relation`s.
  Regression test: `MemoryRepositoryTest` "random memory loads with its people every time".
- **Recap export failed with "Asset loader error"**. Media3 `SequenceAssetLoader` rejects a sequence that starts with an
  item without audio (the intro card / photos) and later reaches a clip with audio unless `experimentalSetForceAudioTrack(true)`
  is set. `RecapRenderer` now mutes clips when a soundtrack is chosen (music is the only audio) and, with no soundtrack,
  keeps clip sound and forces a silent track under images. It also tone-maps HDR phone clips to SDR
  (`HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL`, output is 8-bit H.264), caps decoded photos at 2560 px on the long edge,
  appends the innermost cause to the failure message, and records the failure in the problem log via
  `CrashLog.recordNonFatal` (no crash banner).
- **Sharing the problem log crashed**: `files/crash.log` is not under a FileProvider root (`vault_file_paths.xml` exposes only
  `cache/exports/` and external `Movies/`). `CrashLog.shareIntent` copies the log to `cache/exports/` and also puts the text in
  `EXTRA_TEXT`; both the Settings page and the home banner use it. Test: `CrashLogTest`.
- **Media viewer had no next/previous**: the photo zoom handler (`detectTransformGestures`) consumed every drag, so the pager
  could not swipe. It now only consumes while pinching or when zoomed in, and the viewer shows previous/next buttons
  (also usable over videos, whose player view takes touches).

### Backup password "never works" (backup format 2)

- **Cause**: format 1 sealed the whole zip with one AES-GCM operation through `CipherOutputStream`/`CipherInputStream`.
  Android's Conscrypt provider cannot stream GCM: it buffers the entire input until `doFinal`. With real photos and videos
  the export ran out of memory and left a file holding only the 33-byte header, so every later restore reported
  "That password didn't open the file". JVM tests passed because they used tiny libraries.
- **Fix**: format 2 (`ChunkedAead.kt`) seals the zip in 64 KiB chunks, each its own AES-256-GCM operation (STREAM
  construction: nonce = 7-byte random prefix ‖ chunk counter ‖ last-chunk flag, header bytes as AAD). Memory stays bounded,
  a wrong password fails on the first chunk with `WrongPasswordException`, and truncation, reordering or tampering is detected.
  Format 1 files are still readable. Rule: never run a single GCM operation over unbounded data on Android.
- **Password dialog**: fields now use `KeyboardType.Password` with autocorrect and auto-capitalisation off (the keyboard could
  silently change the typed password), a show/hide toggle, and inline "too short" / "don't match" messages so a disabled
  button is explained.
- Tests: `ChunkedAeadTest` (chunk-boundary round trips, bounded output, wrong key, truncation, tampering), `BackupManagerTest`
  (wrong password on peek, format 1 still opens).

### Recap export: the real "Asset loader error" cause

- The owner's device finally showed the inner cause: `Could not retrieve required duration for EditedMediaItem 0`.
  Media3 1.11 `DefaultAssetLoaderFactory.createAssetLoader` routes an item to `ImageAssetLoader` only when
  `MediaItem.LocalConfiguration.imageDurationMs` is set. `RecapRenderer` set the duration with
  `EditedMediaItem.Builder.setDurationUs` only, so every title card and photo went to `ExoPlayerAssetLoader`, which has no
  duration for a still image, and `SequenceAssetLoader.onDurationUs` rejected item 0. Every recap failed, with or without clips.
  Fix: `MediaItem.Builder.setImageDurationMs(durationMs)` on image items.
- On-device test: `feature/recap/src/androidTest/.../RecapRenderDeviceTest.kt` runs the real Transformer pipeline with
  test assets (rotated H.264 clip with sound, HEVC clip, silent clip, 4000×3000 photo, portrait photo), with and without
  music. Run with `./gradlew :feature:recap:connectedDebugAndroidTest` on a device or emulator. In the cloud container the
  emulator runs without KVM: `emulator -avd t34 -no-window -gpu swiftshader_indirect -accel off` (system image
  `system-images;android-34;google_apis;x86_64`), then `adb shell am instrument -w com.yaadein.feature.recap.test/androidx.test.runner.AndroidJUnitRunner`.

### Reels instead of 1080p landscape video

- Recaps are now vertical reels: `RecapPlan` defaults to `REEL_WIDTH`×`REEL_HEIGHT` = 1080×1920 (9:16, for Instagram Reels,
  YouTube Shorts, WhatsApp status). User-facing copy says "reel" everywhere (EN/HI/MR); code names stay `recap*`.
- Each photo and clip gets its own `Presentation` crop to the reel frame *before* Ken Burns and the caption overlay, so the
  pan/zoom and caption work in output pixels (previously the caption was drawn on the source frame and the crop happened
  afterwards). Ken Burns pan is clamped to `(zoom-1)/zoom` in NDC so no black edge shows.
- `CardPainter` sizes text from the short side (`min(width, height)`), and captions sit above the bottom 18 % that reel apps
  cover with their own UI.
- The in-app preview is 9:16 at 62 % of the content width. `RecapRenderDeviceTest` asserts the output is 1080×1920.
- **Verified on an emulator** (API 34 x86_64, software rendering, no KVM; test APK pre-compiled with
  `cmd package compile -m speed -f com.yaadein.feature.recap.test`): all four `RecapRenderDeviceTest` cases pass — photos +
  clips with bundled music, photos + clips without music (clip sound kept), photos only with music, photos only without
  music. Each output is 1080×1920 H.264 with the planned duration (and AAC audio where expected). One reel takes 15–27 min
  on the unaccelerated emulator; the export watchdog (`setMaxDelayBetweenMuxerSamplesMs`, 60 s in the app) is disabled in the
  test for that reason.

### Settings → Update app (sideloaded debug builds only)

- `AppUpdater` (feature/settings): the user picks an APK with the document picker; it is copied to `cache/update/`, read with
  `getPackageArchiveInfo` (signing certificates included) and judged by the pure `UpdateDecision.decide`:
  other package → "not a Yaadein update"; different signing key → refused (Android would require an uninstall, which deletes
  data); same or lower `versionCode` → "Yaadein is already up to date" (says whether the file is older); newer → installed with a
  `PackageInstaller` session. Same package + same key = Android keeps all data (database, encrypted media, Keystore keys).
  If the file's signers can't be read, the system installer's own key check decides (a mismatch fails without touching data).
- First use sends the user to "Install unknown apps" for Yaadein; on return the install continues (`LifecycleResumeEffect`).
  `UpdateStatusReceiver` starts Android's confirmation screen (`STATUS_PENDING_USER_ACTION`) and reports failures.
- **Debug only**: `REQUEST_INSTALL_PACKAGES` and the receiver are declared in `app/src/debug/AndroidManifest.xml`; the Settings row
  shows only when `BuildConfig.DEBUG`. Play policy forbids self-updating apps, and the release permission list is unchanged.
  `ManifestPermissionsTest` asserts the permission is present exactly when `BuildConfig.DEBUG`.
- **Versioning**: `versionCode` = git commit count, `versionName` = `0.1.<count>` (APK `Yaadein-v0.1.<count>-debug.apk`).
  CI checks out with `fetch-depth: 0`. Every commit is therefore a newer build that installs over the previous one.

### Reel quality, maker credit, About page, Share → Yaadein

- **Reel quality** (`RecapRenderer`, `PhotoFramer`): each photo is pre-drawn into a 9:16 frame at 1.25× the reel size
  (1350×2400): EXIF orientation applied, filtered scaling, photos within 12 % of 9:16 fill the frame, others are shown whole over
  a blurred, dimmed copy of themselves (no cut-off heads). Effects order for photos: Ken Burns on the large frame → `Presentation`
  down to 1080×1920 (always downsampling, so the zoom stays sharp) → caption overlay in output pixels. Encoder: 16 Mbit/s H.264,
  192 kbit/s AAC via `DefaultEncoderFactory` with fallback. A codec reclaimed mid-export (`configure()` on a released codec, seen on
  the overloaded emulator, possible on a phone under memory pressure) is retried once.
- **Import fix**: `TransformerVideoOptimizer` used `Presentation.createForHeight(1080)`, which made portrait 4K clips 607×1080;
  now `createForShortSide(1080)` (portrait → 1080×1920) at 12 Mbit/s.
- **Maker credit**: `AppCredit` ("Crafted with AI by Suresh Dongardive", EN/HI/MR) is a one-line strip at the root of
  `YaadeinApp`, below every screen including the lock screen. The content above it calls
  `consumeWindowInsets(WindowInsets.navigationBars)` so Scaffold/NavigationBar/navigationBarsPadding don't pad twice. Also on About.
- **About this app**: Timeline top bar has an info button ("About Yaadein") → `AppInfoScreen` (`SettingsPage.APP_INFO`), a plain
  explanation of capture, relive, reels, privacy, backup and sharing; About links to it too.
- **Share → Yaadein**: `MainActivity` (singleTask) has SEND / SEND_MULTIPLE filters for `image/*` and `video/*` (label "Save to
  Yaadein"). `SharedMediaInbox` copies the files into `cache/shared/` right away (the sender's grant ends with our activity) and
  exposes them through the FileProvider (`shared` root) so the importer sees real mime/size/name. Once unlocked and set up,
  `YaadeinApp` navigates to `EventCapture(fromShare = true)`, whose ViewModel takes the inbox and attaches the files (date suggested
  from EXIF). Tests: `SharedMediaInboxTest`. Robolectric note: FileProvider caches roots per authority while every test gets a new
  cache dir, so tests that use it clear `FileProvider.sCache` in `@Before`.
- **Memory**: the emulator (4 GB guest) plus Gradle test JVMs can exceed the container's memory and the OOM killer takes the
  emulator. Don't run Gradle builds while the emulator is rendering; use `--max-workers=2` if both must run.
