# Release signing: finishing the move off the public debug key

Until now every Yaadein APK on the Releases page was signed with `app/debug.keystore`, which is committed to this
public repository. Anyone could sign their own APK with it and Android would accept it as an "update" that inherits all
of a user's memories. The CI build now produces an optimised, non-debuggable **beta** APK, and it is ready to switch to a
private key the moment you add one. Nobody has to uninstall: the switch uses Android's APK key rotation.

## One-time setup (about 5 minutes, on any computer with Java)

1. Create the private key. Keep the file and the passwords somewhere safe (a password manager). If they are lost,
   future updates cannot be signed.

   ```
   keytool -genkeypair -keystore yaadein-release.jks -alias yaadein -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Yaadein"
   ```

2. Turn it into one line of text:

   ```
   base64 -w0 yaadein-release.jks > yaadein-release.b64      # macOS: base64 -i yaadein-release.jks -o yaadein-release.b64
   ```

3. On GitHub open **Settings → Secrets and variables → Actions → New repository secret** and add four secrets:

   | Name | Value |
   |------|-------|
   | `KEYSTORE_BASE64` | contents of `yaadein-release.b64` |
   | `KEYSTORE_PASSWORD` | the keystore password you typed |
   | `KEY_ALIAS` | `yaadein` |
   | `KEY_PASSWORD` | the key password (same as the keystore password if you pressed Enter) |

4. Push any commit (or run the **Build APK** workflow by hand). The release notes then say "Signed with: the private
   release key".

## What happens on phones

- **Android 9 and newer**: the new APK carries a signed proof that the old key handed over to the new one. It installs
  over the existing app with all data, and Android from then on refuses any APK signed only with the old public key.
- **Android 8.x (API 26–27)**: key rotation does not exist there, so the APK still verifies with the old key on those
  phones. They update normally, but stay exposed to the public key until they move to a newer Android version.
- Settings → Update app understands rotation: it accepts a file whose signing history contains the installed key and
  refuses an older-key file after rotation.

## Google Play

`release.yml` (manual) builds the Play AAB with the same secrets. The Play build has a different application id
(`com.yaadein.app`, no `.debug` suffix), no Update screen and no install permission. See `docs/PLAY_PUBLISHING.md`.
