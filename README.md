# Yaadein — private memories of the people you love

Yaadein (यादें, "memories") is a premium, **fully offline** Android app. Capture memories of family
and friends as photos, videos or just a line of text, then relive them as a timeline, as per-person
stories, and as recap videos with music. No account, no internet permission, no analytics, no ads.

[![Build debug APK](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

Every push builds the **beta APK** (`Yaadein-v<version>-beta.apk`: R8-optimised, not debuggable) on GitHub Actions and
attaches it to a release tagged `build-<run number>` (see the Releases page). It installs over earlier builds and keeps
every memory. Add the release key secrets once (`docs/RELEASE_SIGNING.md`) and CI signs with your private key using
APK key rotation. Nothing is built on a laptop.

## Stable Android build (known good)

This is the Android baseline to go back to if a later change breaks the app. iOS work (in `ios/`) does not touch any
Android module, Gradle file or Android workflow.

| | |
|---|---|
| APK | [`Yaadein-v0.1.46-beta.apk`](https://github.com/s123dive-web/Yadein/releases/download/build-42/Yaadein-v0.1.46-beta.apk) (release [Build 42](https://github.com/s123dive-web/Yadein/releases/tag/build-42), published 2026-11-07 14:08 UTC) |
| APK SHA-256 | `eee15656499a07cff60881e0c0b99b117f233a75db18edb0d582ea17bdaafc13` |
| Version | `versionName` 0.1.46, `versionCode` 46 (beta build type: R8-optimised, not debuggable, public debug key) |
| Stable head | commit `189f6b273e3e08b158b971ed064856f138857efe` ("CI: print Hilt ViewModel generation diagnostics when unit tests fail"), originally on branch `claude/bulk-photo-upload-memory-filter-of4wnl` |
| Suggested tag | `android-stable-build-42` → `189f6b2` (create with `git tag android-stable-build-42 189f6b2 && git push origin android-stable-build-42`) |
| CI | [Build APK run 42](https://github.com/s123dive-web/Yadein/actions/runs/42379000221): unit tests, zero-network guard, lint and beta APK all green |

Note: the `build-N` release tags are created by the release action against `main`, so they do not point at the commit
that was built. Use the commit/tag above (or the run's `head_sha`) to find the source of an APK.

To rebuild or roll back to it: `git checkout 189f6b2 && ./gradlew :app:assembleBeta`.

## Features

- **Voice memories**: record a grandparent telling the story in their own voice; weekly story prompts
  ("Ask Aaji how she met Ajoba") open the recorder.
- **Never lose them**: weekly automatic encrypted backup to a file you choose (e.g. in Google Drive), a home banner
  when there has been no backup for a month, and a "Check my memories" integrity scan.
- **Share without a server**: a memory as a picture card or PDF, or an encrypted family pack another Yaadein adds.
- **Come back daily**: home-screen widget, birthday reminders with a one-tap reel, Relive "For you" feed with the newest
  reel playing, places.
- **Bring your history**: import a Google Photos Takeout or WhatsApp chat export with real dates.

- **Three ways in, one + button**: event-first, photo-first (auto-grouped by date), or just words with
  a rule-based *Polish* step that turns "goa trip 2024" into a proper card.
- **Timeline** with year and month headers, fuzzy dates ("Sometime in 2015", "Summer 2018"), filters
  and full-text search; two-pane on tablets and foldables.
- **People**: a face grid, each person's own story (count, first and last memory, upcoming birthday,
  groups), *Together* view, relationships map.
- **Relive**: "On this day", shake for a random memory, yearly summaries, recap videos.
- **Security**: the vault keys are sealed with the PIN (Argon2id + Android Keystore) or a biometric-bound Keystore key;
  optional phone screen lock mode; SQLCipher database; chunked AES-256-GCM media; tamper-proof lockout clock;
  optional screenshot blocking (`FLAG_SECURE`); encrypted backups.
- **All ages**: 48 dp targets, font scaling, Easy mode, compact and grid timelines; English, Hindi, Marathi, Gujarati,
  Tamil, Telugu, Bengali and Kannada.

## Architecture

```
┌──────────────────────────────── app ────────────────────────────────┐
│ MainActivity (FragmentActivity, splash, edge-to-edge, FLAG_SECURE)   │
│ YaadeinApp  → theme + lock overlay → AppNavHost → HomeScaffold       │
│ Coil ImageLoader with EncryptedImageFetcher · Hilt root · workers    │
└───────────┬───────────────┬───────────────┬──────────────────────────┘
            │               │               │
   feature:timeline  feature:people  feature:memory  feature:capture  feature:relive
   feature:recap     feature:settings feature:lock   feature:onboarding
            │  (Compose screens + Hilt ViewModels; depend only on core:ui + core:domain)
            ▼
┌────────── core:ui ──────────┐   ┌────────────── core:domain (pure Kotlin) ──────────────┐
│ Tokens.kt, 5 palettes +     │   │ models · repository interfaces · Dates (fuzzy dates)  │
│ dynamic colour, typography, │──►│ LockoutPolicy · Polish engine · photo auto-grouping   │
│ MemoryCardView, CoverArt,   │   │ (no Android dependencies; JUnit 5 tests)              │
│ avatars, empty states       │   └───────────────────────────────────────────────────────┘
└─────────────────────────────┘                             ▲
                                                            │
┌────────────────────────────── core:data ───────────────────┴───────────────────────────┐
│ Room + SQLCipher (AppDatabase, DAOs, Migrations)  ·  repositories  ·  DemoSeeder        │
│ crypto: KeystoreKeyWrapper → VaultKeys → MediaCrypto (AES-CTR) · PinManager (Argon2id)  │
│ EncryptedSettingsSerializer (DataStore) · MediaStore (app-private vault dirs)           │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

Conventions, the Room schema and the navigation map live in [CLAUDE.md](CLAUDE.md).

## Encryption design

| Asset | Protection |
|-------|------------|
| Wrapping key | AES-256-GCM key in the Android Keystore (`yaadein_master`), hardware-backed where available. Never leaves the Keystore. |
| Database + media keys | 2 × 32 random bytes. With a PIN: `salt ‖ Keystore(AES-GCM(Argon2id(PIN, salt), keys))`, so they cannot be recovered without the PIN, even by code running as the app; an optional second copy is sealed by a biometric-bound Keystore key (CryptoObject). Without a PIN (onboarding, phone-screen-lock mode): wrapped by the Keystore key only. Keys live in memory only after unlock; nothing reads the database before that. SQLCipher opens `yaadein.db` with the raw key (WAL mode). |
| Photos, videos, thumbnails, voice, reels | `YDN2`: 64 KiB chunks, each AES-256-GCM with nonce `prefix ‖ index ‖ last` (STREAM), so files stay seekable and any change, reordering or truncation is detected. Older `YDN1` (AES-256-CTR) files are still read. SHA-256 of the plaintext is recorded at import for *Check my memories*. |
| Reminder digest | Memory titles and first names for reminders and the widget, sealed with the Keystore key so notifications work while the vault is locked. |
| Settings | DataStore whose serializer encrypts the JSON with the Keystore key (AES-GCM). |
| PIN | Never stored: it derives the key that seals the vault keys (Argon2id m = 64 MiB, t = 3, p = 2). 5 wrong PINs → 30 s, then 1 m, 5 m, 15 m, 1 h, measured on the monotonic clock (changing the date does not help); optional wipe after 10. |
| Wipe | Deletes the Keystore key (everything wrapped with it becomes unrecoverable), the DB, the vault files and the PIN, then restarts the process. |
| Backup / family pack | `.yaadein` = chunked AES-256-GCM container, key from the user's password with Argon2id. Automatic backups keep that derived key sealed with the vault's media key. |

Decrypted bytes only ever live in memory: Coil's disk cache is disabled and decrypted previews
are never written to storage. Screenshots are allowed by default; Settings → Security → Block screenshots turns on
`FLAG_SECURE`, which also hides the Recents preview.

## Video pipeline (recap)

1. Pick scope (person / group / date range / one memory), a theme (Warm, Minimal, Festive, Classic
   Film) and a track (bundled royalty-free or your own file).
2. Memories are ordered chronologically; each photo becomes a Ken Burns clip, each video a trimmed
   clip, with title and date captions, intro and outro cards.
3. Rendering runs in a WorkManager job (survives app close, cancellable, progress notification) using
   Media3 Transformer. Encrypted sources are read through a custom decrypting `DataSource`.
4. The 1080p MP4 is saved to `Movies/Yaadein` via MediaStore and the share sheet opens.

## Status

All five build phases from the master prompt are implemented and documented in [CLAUDE.md](CLAUDE.md)
(phase table and STOP reports). Screenshots for every main screen live in [docs/SCREENSHOTS.md](docs/SCREENSHOTS.md);
Play Store copy and asset index in [docs/PLAY_LISTING.md](docs/PLAY_LISTING.md).

The improvement round (security, backups, voice, sharing, widget, languages, performance) is described in CLAUDE.md
under "Improvement round". Device tests, a startup benchmark with a budget and baseline-profile generation run in
`.github/workflows/device-tests.yml` (see docs/PERFORMANCE.md). Still open before Play: a TalkBack walkthrough on a
phone, native-speaker review of the Gujarati, Tamil, Telugu, Bengali and Kannada text, and adding the release key
secrets (docs/RELEASE_SIGNING.md).

## Building

CI does everything (`.github/workflows/build.yml`). To build locally you need JDK 17+ and an Android
SDK with platform 37 and build-tools 36.0.0:

```
ANDROID_HOME=/path/to/sdk ./gradlew :app:assembleBeta          # the APK users install
ANDROID_HOME=/path/to/sdk ./gradlew testDebugUnitTest :core:domain:test
ANDROID_HOME=/path/to/sdk ./gradlew :core:data:connectedDebugAndroidTest   # vault migration on a device
ANDROID_HOME=/path/to/sdk ./gradlew :app:updateDebugScreenshotTest   # regenerate screenshots
```

`./gradlew :app:verifyNoNetworkPermissionDebug` fails the build if any library adds a network or
storage permission to the merged manifest; `ManifestPermissionsTest` checks the same from a unit test.

## Google Play Data Safety answers

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | **No.** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — the app has no network access (no INTERNET permission). |
| Do you provide a way for users to request that their data is deleted? | Yes — uninstalling the app or using *Settings → Storage → Erase everything* deletes all data; nothing is stored off-device. |
| Data types | None collected, none shared. Photos, videos and text the user adds are stored only on the device, encrypted. |
| Security practices | Data encrypted at rest (SQLCipher + AES-256). No data sent to third parties. Independent security review: no. |
| Permissions | `USE_BIOMETRIC`/`USE_FINGERPRINT` (app lock), `POST_NOTIFICATIONS` (reminders), `CAMERA` (optional in-app capture), `RECORD_AUDIO` (voice memories, only while recording), `VIBRATE` (haptics). WorkManager adds `WAKE_LOCK`, `FOREGROUND_SERVICE` and `RECEIVE_BOOT_COMPLETED` for background video rendering and daily reminders. Photos are imported with the Android Photo Picker, which needs no storage permission. |

See [PRIVACY.md](PRIVACY.md) for the user-facing policy.

## Licences

Fraunces and Manrope are bundled under the SIL Open Font License 1.1. Material Symbols are Apache 2.0.
Bundled recap tracks are original, generated for this project and free to use within the app.
