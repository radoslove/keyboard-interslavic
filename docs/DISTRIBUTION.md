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
4. The maintainer publishes a `vX.Y` tag and an asset named
   **`interslove-X.Y.apk`** - `tools/name_release_apk.py` produces exactly that
   file from the build output. Up to and including v3.4 the asset was called
   `app-release.apk`, which told a person who downloaded it nothing: four
   different versions landed in one Downloads folder as `app-release (1).apk`
   and so on, and the file had to be installed to find out what it was. Upload
   `app-release.apk` as a SECOND copy of the same bytes for as long as anything
   still links to `releases/latest/download/app-release.apk` (the website does).
   Update `docs/INSTALL.md` and the corresponding fastlane changelog.
5. Update any pending catalogue recipes to reference that exact published version
   and source revision, then verify their CI results.

[Signing-key recovery](DRILL_keystore_restore.md) describes a separate private
recovery procedure. Device locations, backup inventories and signing credentials
must not be included in release notes.

## Two apps from one code (since 3.5)

`android-app/` has two product flavors. Releases of the published app are the **`interslove`**
flavor (`com.radoslove.interslavic`, display name Medžuslovjansky; a rename to **Interslove** is planned):

```sh
./gradlew --no-daemon clean assembleIntersloveRelease
# output: app/build/outputs/apk/interslove/release/app-interslove-release.apk
# publish it under the unchanged asset name app-release.apk
```

The `isc` flavor (`com.radoslove.interscandinavian`, Mellomskandinavisk) is built the same way
(`assembleIscRelease`) and is not part of the F-Droid submission. Before the first 3.5 release:
the F-Droid recipe needs `gradle: [interslove]`, and the store title and `AutoName` follow the new
display name.

## F-Droid recipe

The submission is [MR !45568](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568).
Use its current discussion and diff as the source of submission status.

- `Binaries` is NOT set (the field is only described in a comment), so F-Droid
  builds and signs from source and nothing outside this repo pins the asset
  filename - that is why renaming it is safe. If reproducible builds are ever
  turned on, the pattern becomes `.../releases/download/v%v/interslove-%v.apk`
  and from then on the filename IS pinned.
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
