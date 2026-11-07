# Speed budgets

Measured on a mid-range phone (Snapdragon 6-series / Dimensity 7000-class, 6 GB RAM) unless noted. The CI emulator
numbers come from `.github/workflows/device-tests.yml` (KVM-accelerated x86_64 Android 14).

| What | Budget | How it is measured |
|------|--------|--------------------|
| Cold start to first frame (lock screen or onboarding), baseline profile applied | ≤ 1.0 s phone, ≤ 1.5 s CI emulator | `StartupBenchmark.coldStartWithProfile`; CI fails above 1.5 s |
| PIN entered → timeline visible | ≤ 1.5 s | One Argon2id run (64 MiB, t = 3) opens the vault; fingerprint unlock skips Argon2 entirely |
| Timeline scroll | < 5 % janky frames | Macrobenchmark `FrameTimingMetric` (add a scroll test once CI seeds demo data) |
| Import 20 phone photos | ≤ 30 s | Encrypt + thumbnail + verify per photo, on the import queue |
| 30-second reel export | ≤ 90 s | Media3 Transformer, 1080×1920 H.264 at 10 Mbit/s (`RecapRenderer`); the screen shows a time estimate |
| Open a memory with photos | ≤ 300 ms to first thumbnails | Thumbnails are 512 px and decrypted in memory (no disk cache by design) |

## What already helps

- **Release-style builds**: the GitHub APK is the R8-optimised, non-debuggable `beta` build. Debuggable builds run much
  slower (no ahead-of-time optimisation, debugger hooks), and every earlier GitHub APK was one.
- **Baseline profile**: `app/src/main/baseline-prof.txt` ships with `profileinstaller`. CI records a measured one with
  `BaselineProfileGenerator`; to adopt it, download the `device-results` artifact, take
  `benchmark/.../BaselineProfileGenerator_generate-baseline-prof.txt` and replace `app/src/main/baseline-prof.txt`.
- **Lazy startup**: the database, importer and Keystore work start on a background thread; nothing that reads memories
  is composed until the vault is open.
- **Reels**: 10 Mbit/s instead of 16 Mbit/s encodes faster and makes files about 40 % smaller; Instagram and WhatsApp
  re-compress anyway.

## PIN hashing on slow phones

Argon2id with 64 MiB and three passes takes roughly 0.3–0.8 s on mid-range phones and can exceed 1 s on entry-level
ones. The cost is deliberate: the vault keys are sealed with it, so it is what makes guessing the PIN slow. Fingerprint
unlock avoids it. If entry-level phones need it later, the parameters can be stored per vault and calibrated at PIN
setup (target 500 ms, never below 32 MiB / 2 passes) without changing the file format of existing vaults.

## Running the measurements yourself

```
./gradlew :benchmark:connectedBenchmarkAndroidTest      # needs a device or emulator
./gradlew :core:data:connectedDebugAndroidTest          # vault migration on real crypto
./gradlew :feature:recap:connectedDebugAndroidTest      # reel rendering on real MediaCodec
```
