# Speed budgets

Measured on a mid-range phone (Snapdragon 6-series / Dimensity 7000-class, 6 GB RAM) unless noted. The CI emulator
numbers come from `.github/workflows/device-tests.yml` (KVM-accelerated x86_64 Android 14).

| What | Budget | How it is measured |
|------|--------|--------------------|
| Cold start to first frame (lock screen or the welcome screen), baseline profile applied | ≤ 1.0 s phone, ≤ 2.0 s CI emulator | `StartupBenchmark.coldStartWithProfile`; CI fails above 2.0 s |
| PIN entered → timeline visible | ≤ 1.5 s | One Argon2id run (64 MiB, t = 3) opens the vault; fingerprint unlock skips Argon2 entirely |
| Timeline scroll | < 5 % janky frames | Macrobenchmark `FrameTimingMetric` (add a scroll test once CI seeds demo data) |
| Import 20 phone photos | ≤ 30 s | Encrypt + thumbnail + verify per photo, on the import queue |
| 30-second reel export | ≤ 90 s | Media3 Transformer, 1080×1920 H.264 at 10 Mbit/s (`RecapRenderer`); the screen shows a time estimate |
| Open a memory with photos | ≤ 300 ms to first thumbnails | Thumbnails are 512 px and decrypted in memory (no disk cache by design) |
| Quick Save: + tap → Save | ≤ 3 taps, ≤ 10 s typical | `capture_stats.tap_to_save_ms`, shown as median / p90 and "% within 10 s" in Settings → How I use Yaadein (`UsageSummary`); `QuickSaveFlowTest` keeps the Save call itself under 500 ms on the JVM |
| Save → every photo encrypted | background, never blocks Save | `capture_stats.save_to_encrypted_ms`, same screen; the card shows a progress ring meanwhile |
| Face embedding | < 1 s per face on a 2020 phone | logcat `Yaadein.Faces embed_ms=` (ML Kit detect + align + GhostFaceNet int8 on XNNPACK); the re-index worker runs behind the import lock so it never slows an import |
| Beta APK size | ≤ 64,790,000 bytes (Phase 0 baseline 58,499,460 + 6 MB) | CI "APK size" step fails above it; arm64-v8a + armeabi-v7a only since Phase 2 |

## Measured so far

CI runs on 7 Nov 2026 (KVM x86_64 emulator, Android 14, hand-written baseline profile):

| Run | Benchmark | Median | Fastest | Slowest |
|-----|-----------|--------|---------|---------|
| First | Cold start, profile applied | 1.71 s | 0.75 s | 1.82 s |
| First | Cold start, no compilation | 1.75 s | 1.59 s | 1.79 s |
| Second (R8 without obfuscation) | Cold start, profile applied | 1.00 s | | |
| Second (R8 without obfuscation) | Cold start, no compilation | 1.01 s | | |

Emulator timings vary a lot between runners, so compare runs on the same commit before reading a trend into them. The
small gap between "profile applied" and "no compilation" means the hand-written profile barely helps; adopting the
generated profile (below) is the next speed step. The CI guard (2.0 s) sits well above the measured medians, so it
catches regressions without failing on a slow runner; the phone budget still needs a measurement on a real mid-range phone.

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

## First Minute & Relive 2 round

| Budget | Target | How it is checked |
|--------|--------|-------------------|
| First run: screens before the user's own memories | ≤ 3 (welcome → Photo Picker → first story) | `AppSmokeTest` (welcome has four controls; picker opens from the first button), `OnboardingViewModelTest` |
| First run: welcome → memories playing | ≤ 60 s including the pick | Memory rows are written before any file is copied (`OnboardingViewModel.onPicked`), the story shows a page as soon as one photo of a memory is stored (`FirstStoryStateTest`); stopwatch on a phone |
| Cold start | unchanged budget (≤ 1.0 s phone, ≤ 2.0 s CI emulator) | The first frame is now one static screen instead of a pager; nothing new runs before it |
| APK size | unchanged budget (≤ 64,790,000 bytes) | CI "APK size" step; the round adds Compose code and strings only |
