# Yaadein privacy policy

_Last updated: November 2026_

Yaadein is a memory-keeping app that runs entirely on your phone.

## What we collect

**Nothing.** Yaadein does not collect, transmit, sell or share any personal data. The app does not
request the INTERNET permission, so it is technically unable to send data anywhere.

## What stays on your device

- The people, memories, photos, videos, voice recordings, captions and notes you add.
- Your settings (theme, lock, reminders, backup choice).
- Your PIN never leaves the phone and is never stored as such: it is used (with Argon2id) to seal the keys that
  encrypt your memories.

All of this is stored in the app's private storage and encrypted at rest (SQLCipher for the database, AES-256-GCM for
media and voice recordings). When you use a Yaadein PIN, the encryption keys can only be unlocked with that PIN or your
fingerprint. Data is excluded from Android cloud backup and device-to-device transfer.

## Optional on-device features

- **Voice memories** use the microphone only while you press record. Recordings are encrypted on the phone.
- **Face suggestions** (off by default) find faces with ML Kit's bundled detector and compare them with a bundled
  on-device model (GhostFaceNet, MIT licence). Face measurements are stored only inside your encrypted vault, travel with
  your encrypted backups, are deleted together with a person, and can be wiped at any time with Settings → Face
  suggestions → Delete all face data. No image or face data leaves the device. A very sure match is tagged with a visible
  "suggested" badge that one tap undoes; everything else waits for you in "Who is this?".
- **Smart polish** uses Gemini Nano through Android's on-device AICore service, only on phones where that model is
  already installed. Your text goes to the on-phone model, not to the internet.
- **Reminders** ("On this day", birthdays, weekly story prompts) are scheduled on the device and hidden on the lock
  screen. To work while the app is locked they keep a small list of memory titles and first names, encrypted with the
  phone's hardware key store.
- **Home-screen widget** shows only how many memories are waiting, unless you choose to show titles.

## Sharing and backups

Data leaves the device only when **you** send it:

- sharing a reel, a picture card or a PDF of a memory through the Android share sheet;
- a password-protected **family pack** of chosen memories;
- a password-protected `.yaadein` **backup**, made by hand or weekly to a file you picked (for example in your Google
  Drive folder; then the Drive app, not Yaadein, uploads it).

Backups and family packs are encrypted with AES-256-GCM using your password; without it they cannot be opened.

## Deleting your data

Uninstall the app, or use *Settings → Storage → Erase everything*. Backup files you saved elsewhere are yours to delete.

## Children

Yaadein is designed for all ages and contains no ads, no accounts and no tracking.

## Contact

Questions about this policy can be sent to the developer address listed on the Play Store page.
