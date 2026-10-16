# Coffer

Coffer is an offline Android wallet for credit and debit card details. It's for personal use and has no network access.

## Branding

The logo is a gold "C" with a keyhole. The source files are `branding/coffer-icon.svg` (app icon, 512 px) and `branding/coffer-logo.svg` (horizontal logo with the name). The launcher icon is `res/drawable/ic_launcher_*.xml`, and Android 13+ themed icons have a one-colour version.

## Install

Build (or reuse) the release APK and install it with USB debugging on:

```
gradlew.bat assembleRelease
adb install app\build\outputs\apk\release\app-release.apk
```

Or copy `app-release.apk` to the phone and open it (allow "install unknown apps" when asked).
The APK is signed with this PC's local debug key. Install later updates from the same PC, or Android will refuse to update it.

## How the data is protected

```
vault.bin = AES-256-GCM[hardware key]( AES-256-GCM[password key]( cards ) )
password key = Argon2id(password, random salt, 64 MiB, 3 passes) -> 256 bits
```

- **No `INTERNET` permission.** The manifest strips it, so the OS blocks every network call.
- **AES-256-GCM encryption.** Authenticated encryption with a fresh random nonce on every save. The file header is authenticated too, so any tampering is detected.
- **Argon2id key derivation.** The memory-hard Argon2id function turns the master password into the AES-256 key, so each password guess is slow and expensive.
- **Hardware-backed outer layer.** The encrypted vault is sealed again with a non-exportable AES-256 key in the Android Keystore (StrongBox when the phone has it, otherwise the TEE). A copied vault file can't be brute-forced on another machine.
- **Fingerprint unlock (optional).** A copy of the vault key is sealed under a Keystore key that only opens after a strong biometric check. Enrolling a new fingerprint invalidates it.
- **Upgrade from 1.0.** Vaults created by version 1.0 are read once and rewritten in the current format on the next password unlock.
- `FLAG_SECURE` (no screenshots, recordings or recents thumbnail), overlay hiding, tapjacking filter.
- Backups and device transfer are disabled. Data lives in `noBackupFilesDir`.
- Auto-lock when you leave the app (configurable) and after 5 minutes idle. Lockout gets longer after 5 wrong passwords. Optional erase after 10.
- Copied values are marked sensitive and cleared from the clipboard after 30 s.
- Release builds are minified and have all logging stripped.

## Limits worth knowing

- **Forgotten password = lost data.** No recovery exists, by design.
- **No backup/export yet.** If the phone is lost, reset or the app is uninstalled, the cards are gone. Keep the physical cards or bank records.
- On a **rooted or compromised phone** (malware with root or accessibility control), no app can fully protect what's on screen while it's unlocked.
- Kotlin/JVM strings can't be reliably wiped from memory. Key bytes are zeroed on lock, but decrypted text may linger in RAM until garbage collection.

## Tests

`gradlew.bat testDebugUnitTest` runs the crypto checks: AES-256 round trip, wrong password, tamper detection, reading 1.0-format vaults, and card network and Luhn checks.
