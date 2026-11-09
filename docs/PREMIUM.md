# Yaadein Plus: one-time premium unlock (plan)

**Principle**: the core promise stays free forever: capture, timeline, people, relive, voice memories, encryption,
backups. Plus is a single purchase, not a subscription, and has no ads or tracking, which matches the privacy story.

## What Plus adds (Play build only)

| Feature | Free | Plus |
|---------|------|------|
| Reel themes | Warm, Minimal | + Festive, Classic Film and future themes |
| Reel music | 4 tracks | All 8 tracks and future packs |
| Reel length | Short, Medium | + Long (2 minutes) |
| PDF photobook | One memory | Whole person / year books |
| Family packs | Receive | Send |

Price: one-time, priced for India first (around ₹299), with regional pricing elsewhere.

## How it is built

- `core/domain/premium/Premium.kt` defines `PremiumFeature` and `PremiumGate`. Today `AllUnlocked` is used everywhere.
- The Play build will bind a `PlayBillingGate` backed by Google Play Billing (`BillingClient`, one non-consumable
  product `yaadein_plus`). Billing talks to the Play Store app over IPC; Yaadein itself still needs no internet permission.
  Verify this in the merged manifest with `verifyNoNetworkPermissionRelease` before shipping.
- The purchase state is cached in the encrypted settings so the unlock works offline.
- The GitHub beta build (`SIDELOAD_UPDATES = true`) always binds `AllUnlocked`.

## Before switching it on

1. Publish the free app and gather a few hundred users (see `PLAY_PUBLISHING.md`).
2. Add the in-app product in Play Console, then implement `PlayBillingGate` and a small "Yaadein Plus" screen in Settings.
3. Show the locked features with a gentle "Plus" badge, never a nag screen.
