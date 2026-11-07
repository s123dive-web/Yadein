# Publishing Yaadein on Google Play

Play gives automatic updates and a trusted install source, and "no data collected" is a strong listing hook.
Listing copy and assets are in `PLAY_LISTING.md`; this is the step-by-step path.

## 1. Before the first upload

- [ ] Add the release key secrets (`RELEASE_SIGNING.md`). The same key signs the Play upload.
- [ ] Create a Google Play developer account (one-time fee) and a new app named **Yaadein – Private Memories**.
- [ ] Turn on **Play App Signing** and upload the key from step 1 as the *upload key*.
- [ ] Host the privacy policy: `PRIVACY.md` rendered as a GitHub Pages page works; paste its URL in the console.

## 2. Build

Run the **Release AAB (signed)** workflow by hand (Actions tab). It produces `Yaadein-release.aab`.
The Play build differs from the GitHub beta APK in three ways:

| | GitHub beta APK | Play AAB |
|---|---|---|
| Application id | `com.yaadein.app.debug` | `com.yaadein.app` |
| Settings → Update app | Yes | No (Play policy) |
| Install-packages permission | Yes | No |

Because the ids differ, a phone can hold both. To move memories from the beta to the Play app, use
Settings → Backup and restore in the beta, then restore the file in the Play app.

## 3. Internal testing first

1. Play Console → Testing → **Internal testing** → create a release → upload the AAB.
2. Add up to 100 testers by email, share the opt-in link, install from Play.
3. Check the pre-launch report (Play runs the app on real devices) for crashes and accessibility warnings.

## 4. Store listing and policy forms

- **Data safety**: answers are in the README table (no data collected or shared, encrypted at rest).
- **Permissions declaration**: copy from `PLAY_LISTING.md`. `RECORD_AUDIO` is used only for voice memories the user
  records; `CAMERA` only for in-app capture.
- **Content rating**: Everyone.
- **Target audience**: 13+ is simplest (no children's-policy review); the app is still suitable for all ages.

## 5. Production

Promote the tested internal release to **Closed testing** (new personal accounts must run a closed test with at least
12 testers for 14 days before production access), then to **Production** with a staged rollout (20% → 100%).

## Monetisation (planned)

`PREMIUM.md` describes the one-time premium unlock. It uses Play Billing only in the Play build; the GitHub build
keeps everything unlocked.
