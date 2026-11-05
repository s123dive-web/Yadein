# Yaadein — private memories of the people you love

Yaadein (यादें, "memories") is a premium, **fully offline** Android app. Capture memories of family
and friends as photos, videos or just a line of text, then relive them as a timeline, as per-person
stories, and as recap videos with music. No account, no internet permission, no analytics, no ads.

[![Build debug APK](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

Every push to `main` builds a debug APK on GitHub Actions and attaches it to a release tagged
`build-<run number>` (see the Releases page). Nothing is built on a laptop; the build needs no
`local.properties`, keystore or secrets.

## Features

- **Three ways in, one + button**: event-first, photo-first (auto-grouped by date), or just words with
  a rule-based *Polish* step that turns "goa trip 2024" into a proper card.
- **Timeline** with year and month headers, fuzzy dates ("Sometime in 2015", "Summer 2018"), filters
  and full-text search; two-pane on tablets and foldables.
- **People**: a face grid, each person's own story (count, first and last memory, upcoming birthday,
  groups), *Together* view, relationships map.
- **Relive**: "On this day", shake for a random memory, yearly summaries, recap videos.
- **Security**: app lock (biometric class 3 + 6-digit PIN with Argon2id), SQLCipher database,
  AES-encrypted media, `FLAG_SECURE`, encrypted password-protected backups.
- **All ages**: 48 dp targets, font scaling, Easy mode, English / Hindi / Marathi.

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
| Database key | 32 random bytes, wrapped by the Keystore key, stored in `EncryptedSharedPreferences`. SQLCipher opens `yaadein.db` with the raw key (WAL mode). |
| Media key | A second 32 random bytes, wrapped the same way. |
| Photos, videos, thumbnails | `AES-256-CTR` with a random 16-byte IV per file (`YDN1` header + IV + ciphertext). CTR keeps files seekable so video playback can scrub without decrypting the whole file. Integrity: SHA-256 of the plaintext stored in the DB and verified at import. |
| Settings | DataStore whose serializer encrypts the JSON with the Keystore key (AES-GCM). |
| PIN | Argon2id (m = 64 MiB, t = 3, p = 2), random 16-byte salt; hash in `EncryptedSharedPreferences`. 5 wrong PINs → 30 s, then 1 m, 5 m, 15 m, 1 h. Optional wipe after 10. |
| Wipe | Deletes the Keystore key (everything wrapped with it becomes unrecoverable), the DB, the vault files and the PIN, then restarts the process. |
| Backup | `.yaadein` = AES-256-GCM container, key derived from the user's password with Argon2id. |

Decrypted bytes only ever live in memory: Coil's disk cache is disabled and decrypted previews
are never written to storage. `FLAG_SECURE` (on by default) blocks screenshots and the Recents preview.

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

What still needs a real device before publishing: a first on-device run of the recap renderer (Media3 Transformer
needs MediaCodec), a TalkBack walkthrough, and a Macrobenchmark-generated baseline profile to replace the
hand-authored one.

## Building

CI does everything (`.github/workflows/build.yml`). To build locally you need JDK 17+ and an Android
SDK with platform 37 and build-tools 36.0.0:

```
ANDROID_HOME=/path/to/sdk ./gradlew :app:assembleDebug
ANDROID_HOME=/path/to/sdk ./gradlew test
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
| Permissions | `USE_BIOMETRIC`/`USE_FINGERPRINT` (app lock), `POST_NOTIFICATIONS` ("On this day" reminders), `CAMERA` (optional in-app capture), `VIBRATE` (haptics). WorkManager adds `WAKE_LOCK`, `FOREGROUND_SERVICE` and `RECEIVE_BOOT_COMPLETED` for background video rendering and daily reminders. Photos are imported with the Android Photo Picker, which needs no storage permission. |

See [PRIVACY.md](PRIVACY.md) for the user-facing policy.

## Licences

Fraunces and Manrope are bundled under the SIL Open Font License 1.1. Material Symbols are Apache 2.0.
Bundled recap tracks are original, generated for this project and free to use within the app.
