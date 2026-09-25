# Distribution and release workflow

User-facing installation instructions are in [INSTALL.md](INSTALL.md).
This document describes release mechanics and public distribution requirements.
Account ownership, verification progress, credentials and tester recruitment
belong in private operational records.

## Distribution channels

| Component | Distribution |
|---|---|
| Keyman keyboard | [Keyman catalogue](https://keyman.com/keyboards/isv_latin) |
| Keyman lexical model | Catalogue submission under `keymanapp/lexical-models` |
| Native Android app | [GitHub Releases](https://github.com/radoslove/keyboard-interslavic/releases); [F-Droid submission](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568) |
| Native desktop layouts | Platform instructions in this repository |

Store availability must be confirmed before advertising an installation route.
Release assets on GitHub also provide the reference binaries used by F-Droid.
Other possible distribution channels include Google Play and IzzyOnDroid;
listing them here does not imply an accepted or active listing.

## Android release checklist

1. Build the intended source revision with Java 21, Android platform 34 and
   build-tools 34.0.0. Verify behavior on a real device.
2. Review [dictionary attribution](../android-app/DICTIONARY_DATA.md), the privacy
   policy, permissions and outstanding [test findings](../android-app/FEEDBACK.md).
   Do not describe unresolved findings as fixed based only on a successful build.
3. Sign with the established release key. Verify the certificate fingerprint and
   record the APK's SHA-256. The expected certificate SHA-256 is
   `5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a`.
4. The maintainer publishes a `vX.Y` tag and an asset named **`app-release.apk`**.
   Update `docs/INSTALL.md` and the corresponding fastlane changelog.
5. Update any pending catalogue recipes to reference that exact published version
   and source revision, then verify their CI results.

[Signing-key recovery](DRILL_keystore_restore.md) describes a separate private
recovery procedure. Device locations, backup inventories and signing credentials
must not be included in release notes.

## F-Droid recipe

The submission is [MR !45568](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568).
Use its current discussion and diff as the source of submission status.

- `Binaries` uses the release pattern `.../releases/download/v%v/app-release.apk`.
  Preserve the asset filename and version-tag convention.
- Pin the full source commit and the public certificate in
  `AllowedAPKSigningKeys`.
- Keep `versionName`, `versionCode`, `commit`, `CurrentVersion` and
  `CurrentVersionCode` aligned with the published release.
- Keep both `windows` and `ios/Keyboard/Resources/isv_swipe.bin` in `scandelete`;
  these binary resources are not inputs to the Android build.
- Preserve the canonical `fdroid rewritemeta` format, including the trailing space
  on an otherwise empty `Binaries:` line when applicable.
- Verify lint, metadata/schema checks, source scanning and binary comparison.
  A successful check on one platform does not establish identical output on
  every other platform.

## Other distribution work

Store graphics and listing text are documented in [the brand kit](../brand/BRAND.md)
and `fastlane/metadata/android/`. Check the store's current requirements at
submission time rather than copying account-specific setup notes into this repo.

Windows native layouts need a matching MSKLC build and device verification after
source changes. Keep source KLC files in UTF-16 with CRLF; do not edit generated
DLLs directly. Catalogue submissions and release publication are maintainer actions.
