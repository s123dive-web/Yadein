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
| 1 | Foundation and look | ⏳ next | Skeleton, design system, themes, onboarding, lock, encrypted DB, CI, seed data, Timeline/People/Detail, screenshots |
| 2 | Capture | ⬜ | Event-first, photo-first grouping, text-only + Polish, quick capture, backdating, past wizard, limits, people/groups |
| 3 | Relive | ⬜ | Filters, search, Together, relationships map, on-this-day, shake, yearly summaries |
| 4 | Recap video | ⬜ | Themes, music, Transformer pipeline, preview, share |
| 5 | Trust and polish | ⬜ | Backup/restore, face suggestions, Easy mode, a11y, perf, baseline profiles, privacy, release workflow, listing assets |

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
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
      - name: Attach APK to release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: build-${{ github.run_number }}
          name: Build ${{ github.run_number }}
          files: app/build/outputs/apk/debug/app-debug.apk
```

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
