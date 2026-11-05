# Google Play listing — Yaadein

## Assets
- App icon: adaptive (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`), foreground/monochrome vectors in `res/drawable`.
- Feature graphic (1024 × 500): `app/src/screenshotTestDebug/reference/com/yaadein/app/screenshots/TrustPreviewsKt/FeatureGraphic_*.png`
- Phone screenshots (1080 × 2339): the `TimelinePhone`, `MemoryDetailPhone`, `PeoplePhone`, `PersonStoryPhone`, `TextOnly`, `RecapSetup`,
  `RelationshipsMap`, `LockPhone` and `OnboardingPhone` references under `app/src/screenshotTestDebug/reference/...`.
- Tablet screenshots (1920 × 1200): `TimelineTablet`, `MemoryDetailTablet`, `PeopleTablet`.

## Title (30)
Yaadein – Private Memories

## Short description (80)
Keep family memories private: photos, words, timelines, recaps. 100% offline.

## Full description
Yaadein (“memories”) is a private, offline album for the people you love.

• Three ways in, one + button — add an event, start from photos (grouped by date automatically), or just type a line like “goa trip 2024”. Yaadein turns it into a beautiful card.
• A timeline you can wander through — year by year, month by month, with fuzzy dates like “Summer 2015”.
• Stories about people — every person gets their own timeline, first and last memory, upcoming birthday, and a map of who appears together.
• Relive — “On this day” reminders, shake for a random memory, yearly summaries.
• Recap videos — pick a person, group or year, choose a theme and music, and get a 1080p video to share on birthdays.
• Private by design — no internet permission, no account, no ads, no analytics. Everything is encrypted on your phone and locked with your PIN or fingerprint. Move to a new phone with a password-protected backup file.
• For all ages — big touch targets, Easy mode, English, Hindi and Marathi.

## Data safety
See the “Google Play Data Safety answers” table in README.md: no data collected, no data shared, encrypted at rest, deletion by uninstall or in-app erase.

## Content rating
Everyone. No user-generated content leaves the device.

## Permissions explained (Play Console “Permissions declaration”)
USE_BIOMETRIC / USE_FINGERPRINT — app lock · POST_NOTIFICATIONS — daily reminder and render progress · CAMERA — optional in-app capture ·
VIBRATE — haptics · FOREGROUND_SERVICE(+DATA_SYNC/MEDIA_PROCESSING), WAKE_LOCK, RECEIVE_BOOT_COMPLETED — background video rendering and reminders (WorkManager).
