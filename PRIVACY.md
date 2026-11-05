# Yaadein privacy policy

_Last updated: November 2026_

Yaadein is a memory-keeping app that runs entirely on your phone.

## What we collect

**Nothing.** Yaadein does not collect, transmit, sell or share any personal data. The app does not
request the INTERNET permission, so it is technically unable to send data anywhere.

## What stays on your device

- The people, memories, photos, videos, captions and notes you add.
- Your settings (theme, lock timeout, reminder time).
- Your PIN, stored only as a salted Argon2id hash.

All of this is stored in the app's private storage and encrypted at rest (SQLCipher for the
database, AES-256 for media). It is excluded from Android cloud backup and device-to-device transfer.

## Optional on-device features

- **Face suggestions** (off by default) run ML Kit face detection locally. No images or face data
  leave the device, and nothing is tagged until you confirm it.
- **Smart polish** (if your device supports on-device Gemini Nano) rewrites your text locally. It is
  optional and never required.
- **Notifications**: "On this day" reminders are scheduled on the device and contain only your own
  memory titles.

## Sharing

The only way data leaves the device is when **you** export it: sharing a recap video, or creating a
password-protected `.yaadein` backup file through the Android share sheet. You choose where it goes.

## Deleting your data

Uninstall the app, or use *Settings → Storage → Erase everything*. Because nothing is stored off
your device, there is nothing else to delete.

## Children

Yaadein is designed for all ages and contains no ads, no accounts and no tracking.

## Contact

Questions about this policy can be sent to the developer address listed on the Play Store page.
