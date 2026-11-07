# Yaadein for iPhone

The iPhone app lives in `ios/`. It is a native SwiftUI app that follows the Android app screen by screen, uses the
same strings, colours, fonts and music, gives the same results for the same input (dates, Polish, grouping, reels),
and reads and writes the same `.yaadein` backup files. **Nothing under `ios/` is part of the Android build**:
no Android module, Gradle file or Android workflow was changed, and the Android CI keeps building the APK on every
push (see "Stable Android build" in the README).

## Layout

```
ios/
├── project.yml              XcodeGen spec (the .xcodeproj is generated, not committed)
├── YaadeinKit/              Swift package, builds and tests on Linux and macOS (no UIKit, no dependencies)
│   ├── YaadeinDomain        port of core:domain (models, Dates, PolishEngine, PhotoGrouper, PeriodGrouper,
│   │                        Reminders, RecapPlanner, AutoReelPicker, ExportParsing, LockoutPolicy, settings)
│   ├── YaadeinCrypto        Argon2id, BLAKE2b, AES-256-GCM (reference), chunked STREAM format (YDN2 / YDNB),
│   │                        SHA-256, CRC32, DEFLATE, zip reader/writer, random-access media reader
│   └── YaadeinData          VaultStore (repository rules of core:data), MediaVault, BackupCodec, VaultSession,
│                            LockoutTracker, SettingsStore
├── Yaadein/                 the app: SwiftUI screens, design system, platform code (Keychain, Face ID,
│                            CryptoKit, Photos picker, AVFoundation, notifications)
│   └── Resources/<lang>.lproj   GENERATED from Android strings.xml (do not edit)
├── YaadeinTests/            app tests (CryptoKit = portable AES-GCM, strings in all languages, fonts)
└── scripts/sync_from_android.py   generates strings and palettes from the Android sources
```

Fonts (`core/ui/src/main/res/font`) and reel music (`feature/recap/src/main/res/raw`) are referenced directly from
the Android folders by `project.yml`, so both apps always ship the same files.

## Keeping the two apps the same

| What | Source of truth | How iOS follows |
|------|-----------------|-----------------|
| Text in 8 languages | Android `strings.xml` | `python3 ios/scripts/sync_from_android.py`; CI fails if out of date |
| Colours (5 palettes, avatars, cover gradients) | `core/ui/.../theme/Color.kt` | same script → `Palettes.generated.swift` |
| Sizes, radii, type scale, motion | `Tokens.kt`, `Type.kt` | `DesignSystem/Tokens.swift`, `Typography.swift` (same numbers) |
| Illustrations and cover art | `Illustration.kt`, `CoverArt.kt` | same geometry in `Illustration.swift`, `Components.swift` |
| Business rules | `core/domain` | `YaadeinDomain`, with the Kotlin tests ported one for one |
| Backup / family pack file | `BackupManager.kt`, `ChunkedAead.kt` | `BackupCodec` — tested against files written by Java |
| PIN / backup key | argon2kt (t=3, m=64 MiB, p=2) | `Argon2id` — tested against the reference C implementation |

When a feature changes on Android: change `strings.xml` / `Color.kt` and run the sync script; port the rule change
to `YaadeinDomain` together with its Kotlin test; update the screen in `ios/Yaadein/Features`; tick
[IOS_PARITY.md](IOS_PARITY.md).

## Security on iPhone (same design as Android)

| Android | iPhone |
|---------|--------|
| Keystore key `yaadein_master` | 32-byte device key in the Keychain, *this device only, when unlocked* (not in iCloud or device backups) |
| Vault keys sealed `Keystore(AES-GCM(Argon2id(PIN, salt), keys))` | same: `DeviceKey(AES-GCM(Argon2id(PIN, salt), keys))` |
| Biometric copy (CryptoObject, invalidated by new enrolment) | Keychain item with `.biometryCurrentSet` (Face ID / Touch ID) |
| Phone screen lock mode | phone passcode / Face ID (`deviceOwnerAuthentication`) |
| SQLCipher database | the whole database sealed with AES-256-GCM, written atomically, held in memory only while unlocked |
| `YDN2` media files | identical `YDN2` format; videos stream through an AVAssetResourceLoader that decrypts only the requested ranges |
| Lockout on the monotonic clock | system uptime clock; a reboot restarts the delay |
| `FLAG_SECURE` | "Block screenshots" hides the app content in the app switcher |
| No INTERNET permission | no networking code at all; the app never opens a connection |

## Building

CI (`.github/workflows/ios.yml`) does everything: package tests on Linux, a check that strings/colours match Android,
and on macOS: generate the project, build, run the app tests on an iPhone simulator, launch the app and save a
screenshot (artifact `Yaadein-ios-simulator`).

On a Mac:

```
brew install xcodegen
cd ios && xcodegen generate && open Yaadein.xcodeproj
cd ios/YaadeinKit && swift test        # the shared logic, also works on Linux
```

## Installing on an iPhone

Unlike an APK, an iPhone app must be signed with an Apple Developer account before it can be installed:

1. Join the Apple Developer Program (99 USD a year) with your Apple ID.
2. In App Store Connect create the app with bundle id `com.yaadein.app`.
3. For testers: archive in Xcode (Product → Archive) and upload to TestFlight; invite family by email. To do this from
   CI instead, add the secrets `APPLE_TEAM_ID`, an App Store Connect API key (`ASC_KEY_ID`, `ASC_ISSUER_ID`,
   `ASC_KEY_P8_BASE64`) and a distribution certificate; a signed `testflight` job can then be added to `ios.yml`.
4. Without a paid account, Xcode can install a build on your own iPhone with a free Apple ID (re-install every 7 days).

## Not yet on iPhone

Tracked in [IOS_PARITY.md](IOS_PARITY.md): reel rendering (AVFoundation port of the Media3 pipeline), voice
memories, the home-screen widget, face suggestions, Google Photos / WhatsApp import, picture card and PDF sharing.
