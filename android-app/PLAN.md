# Android app — native Interslavic keyboard (IME)

*For educational purposes.* Sibling track to `ios/`. Where the iOS extension
exists because iOS has no layout-file format, the Android **layout** already
ships as `android/isv_latin.xml` for Unexpected Keyboard / HeliBoard. This app
is the **standalone IME** — our own `InputMethodService`, no host keyboard
required, one install, and eventually the prediction bar + swipe that a layout
XML alone cannot carry.

## Source of truth — do not re-decide the character set here

Standard orthography only (HOUSE_STYLE §1). This app restates, never extends:

| Reach | Keys | From |
|---|---|---|
| Base QWERTY | `q…m` | `android/isv_latin.xml` |
| Four diacritics (longpress) | `c→č  s→š  z→ž  e→ě` | `docs/ms-latin-table.md`, `ios/Keyboard/Layout.swift` |
| Three digraphs (longpress) | `d→dž  l→lj  n→nj` | `android/isv_latin.xml` |
| Punctuation | `„ " – —` | `docs/ms-latin-table.md` |

**No extended letters** (`ų ė ę ȯ å ŕ ť ď ľ đ …` are BANNED). If the canonical
table ever changes, `Layout.kt` here is one more place that changes with it —
same warning as the iOS `Layout.swift`.

Naming mirrors iOS: `applicationId = com.radoslove.interslavic`, display name
**Medžuslovjansky**, IME subtype language `isv`.

## Milestones

Each milestone ends in an artifact verifiable by a command exit code or a
**real device** (`a55` on the tailnet) — never an emulator verdict alone.

### M0 — toolchain + skeleton that installs
Empty `InputMethodService` that appears in *Settings → Languages → Keyboards*
and can be enabled. Proves the build + sideload loop works.
**Verify:** `./gradlew assembleDebug` exit 0; APK served over HTTP, installed
on `a55` from the browser; the keyboard appears in the enable list.
**Files:**
- `settings.gradle.kts`, `build.gradle.kts` (root), `gradle.properties`
- `gradle/wrapper/*` + `gradlew` (`gradle wrapper --gradle-version 8.x`, needs gradle once)
- `app/build.gradle.kts` (AGP 8.x, `minSdk 24`, `compileSdk 34`, Kotlin)
- `app/src/main/AndroidManifest.xml` — declares the IME service
- `app/src/main/res/xml/method.xml` — IME metadata, subtype `isv`
- `app/src/main/kotlin/com/radoslove/interslavic/ImeService.kt` — stub
- `app/src/main/res/values/strings.xml`

### M1 — keyboard that types the standard ISV letters  ← **start here once toolchain is in**
The real deliverable: a working alphabetic keyboard.
- 3 letter rows + shift + backspace + space + return + globe(switch)
- longpress `c s z e` → `č š ž ě`; longpress `d l n` → `dž lj nj`
- shift produces the uppercase forms (`Č Š Ž Ě`)
**Verify on `a55`:** type a full ISV sentence containing all four diacritics
and one digraph; confirm the letters commit (screenshot). The uppercase-longpress
trap bit us twice on iOS — verify caps forms explicitly.
**Files added:** `Layout.kt` (the table above), `KeyboardView` (Compose or a
custom `View`), `res/layout/`, longpress popup handling.

### M2 — numeric/symbol layer + punctuation popups
Numeric layer (`1…0`, symbols) and the `„ " – —` popups on `.`/`-`, mirroring
`ios/Keyboard/Layout.swift` `numericRows` / `periodAccents` / `hyphenAccents`.
**Verify on `a55`:** each popup commits the intended glyph.

### M3 — prediction bar
Wire suggestions to the lexical model. Assets already exist:
`dictionary/main_isv.combined` (5.2 MB, 39.8k forms) and `main_isv.dict`
(1.14 MB, AOSP-compiled). **Watch the memory budget** — the same cap that forced
the Keyman model from 33 MB down to 5 MB applies to an IME process.
**Verify on `a55`:** typing a known stem surfaces correct completions.

### M4 — swipe / glide typing
Score a gesture path against a trie of the wordlist. The expensive one —
**weeks, not an afternoon** — and the single feature no layout XML or Keyman
package can deliver. Only start after M1–M3 are solid on a device.

## Who does what

**Me (dev-keyboard):** author every source file from the canonical table; keep
parity with iOS/Windows/macOS/Keyman; once a toolchain is present, run
`./gradlew assembleDebug`, report the exit code, stand up the HTTP server for the
APK, and drive device verification on `a55`.

**Owner:** (1) decide the **build machine** — `mc` already has the Android SDK
(cheapest path), or provision `hp`; (2) if `hp`, accept the JDK 21 + Android
cmdline-tools install; (3) enable the keyboard in Settings on `a55` and eyeball
each device check; (4) merge — feature branches only, merges are yours.

## Toolchain status (this session, on `hp`)

`hp` has **none of it**: no `java`/`javac`, no `gradle`, no Android SDK
(`ANDROID_HOME` empty), no `adb`. M0 cannot build here until a JDK + SDK land.
`mc` is documented as carrying the Android SDK — running this track there skips
the `hp` install entirely. Owner's call.

## Traps carried in from prior sessions

- **JDK 21**, not 26 — AGP 8.x does not accept JDK 26.
- **Install via HTTP + manual "Install From File"** on `a55`; don't fight `adb`
  (Android needs no cable — serve the APK, tap the link).
- **A real device is the only truth.** Key sizes, longpress popups and gesture
  thresholds have all behaved differently on hardware than in an emulator here.
  Say "builds" / "runs in emulator" — never "works" — until `a55` confirms it.

## Future options (deferred — not built yet)

- **Space-behaviour toggle in the setup screen.** Smart-space (space goes
  BEFORE the next word; punctuation glues to the word) is the default and stays
  the default. Add a user switch to choose classic trailing-space instead,
  placed on the same setup screen as the "Zbieraj nowe słowa" toggle. Owner
  wants this later; smart-space-as-default is fine for now. (Owner note via
  coordinator, 2026-08-10.)
- **Usage counts visible in the database.** The adaptive-ranking `usage.tsv`
  is on-device only; syncing counts into medzuslove (a `usage` table, reusing
  the M3 export/ingest path) so they are "visible in the base" is a small
  follow-up when wanted.
- **Context / n-gram ranking.** No bigram corpus exists yet; adaptive usage is
  the achievable stand-in. Real bigram context needs a harvested MS corpus.
