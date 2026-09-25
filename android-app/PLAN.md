# Android keyboard — development guide

The native Android IME provides Interslavic typing, suggestions and glide input.
Its character set follows [the canonical table](../docs/ms-latin-table.md).
The project uses Java 21, Android platform 34 and build-tools 34.0.0.

## Components

| Source | Responsibility |
|---|---|
| `ImeService.kt` | Android input-method lifecycle and editor integration |
| `KeyboardView.kt` | Touch handling, drawing, suggestions and editing state |
| `Layout.kt` | Letter and symbol layouts |
| `Dictionary.kt` | Completion and gesture decoding |
| `Usage.kt`, `Popularity.kt` | Local usage and word-ranking support |
| `CrashReporter.kt`, `GestureLog.kt` | Optional debug diagnostics |

Check behavior on a real device; a successful compilation or simulated gesture
is not evidence that a touch interaction works correctly on hardware.

## Build

From `android-app/`:

```bash
./gradlew --no-daemon assembleDebug assembleRelease
```

Debug builds have the `.debug` application ID suffix and can coexist with a
release installation. Select the intended keyboard before testing. Release
signing is optional local configuration; without it the release APK is unsigned.
See [the recovery procedure](../docs/DRILL_keystore_restore.md) for separate signing.

## Optional debug diagnostics

Fresh clones have **no diagnostics destination**. To enable debug reporting,
create the ignored `android-app/diagnostics.local.properties` file:

```properties
crashReportUrl=https://diagnostics.example.invalid/api/crash
gestureReportUrls=https://diagnostics.example.invalid/api/gesture
```

Replace the example URLs with a service you control. Multiple gesture endpoints
can be comma-separated. Debug gesture reports contain paths, candidates and
committed/corrected words; use synthetic test input. Crash reports contain stack
traces. Keep destinations and collected data out of this repository.

Only the debug build reads these destinations into `BuildConfig`. Release values
are empty, and the release manifest has no Internet permission. An absent or
empty local configuration disables the corresponding debug reporting path.

## Verification areas

- Tap and long-press input for `č š ž ě`, uppercase variants and digraphs.
- Symbol layout output, including underscore and punctuation.
- Suggestion selection, cursor movement and app/field switching.
- Glide completion, correction of word endings, backspace and undo.
- Password fields, RTL system settings and backup behavior.
- Physical-device comparison for any change to gesture scoring.

[FEEDBACK.md](FEEDBACK.md) records technical reproduction cases and open issues.
[DICTIONARY_DATA.md](DICTIONARY_DATA.md) separates code licensing from lexical data;
specific data sources and redistribution terms still need to be documented.

## Future work

An editing panel, cursor controls and punctuation discoverability are possible
improvements. Evaluate short-glide ranking using both the offline evaluator and
real device traces. Avoid changing the canonical character set while adjusting
where a character is reached.

The layout data, ranking approach and test cases can inform other platforms.
Android `InputConnection` integration and lifecycle code remain platform-specific.
