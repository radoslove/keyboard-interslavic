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

## What transfers to iOS, and what does not

*(added from `mc`, 2026-08-09)*

This track is also a rehearsal for the iOS app, so it is worth naming which lessons
survive the move and which do not.

| Transfers | Stays behind |
|---|---|
| The layout model from the one canonical table | The code — Kotlin vs Swift |
| Longpress and flick handling, and their traps | |
| Prediction-bar design and sizing decisions | |
| The swipe decoder algorithm | |
| The consent / collection UX | **Where collection lives** |

That last row is the one worth discovering early. An Android `InputMethodService`
reaches the network and storage like any app, so collection can live **in the keyboard**.
On iOS a keyboard extension without "Full Access" can reach neither the network nor a
shared container with its own app — and that isolation is the iOS app's main selling
point, so it stays. Collection there has to move into the container app.

Learning that here is cheaper than discovering it halfway through the Swift build.

---

# Appendix — the `mc` draft, kept for its framing

Written on `mc` on 2026-08-09, before it was known how far this track had already
progressed on `hp`. The milestones above supersede it — the code follows them, not this.
Kept because the framing is still the argument for why the track exists at all, and
because nothing here gets deleted, only versioned.

# Android keyboard app — the laboratory

**This is not a product.** Android already has a good Interslavic keyboard: Unexpected
Keyboard with our XML layout, plus HeliBoard with our `.dict` for swipe. Anything we write
from scratch will be worse than that for months.

It is a **laboratory for the iOS app**, and it earns its place because three things can be
learned here that iOS cannot teach — cheaply, and in minutes per iteration instead of days.

## Why Android is the right place to learn

**No gatekeeper.** No $99/year, no review, no signing ceremony. Build an APK, install it.

**No cable.** The APK gets served over HTTP from `mc` — the same trick that put the Keyman
package on the iPad today — and installed by tapping a link. Contrast iOS, where a device
cannot receive a self-built app at all without a one-time USB pairing. That difference is
the whole reason this track can move while the iOS one waits for an adapter.

**No "Full Access" equivalent.** An Android `InputMethodService` reaches the network and
storage like any app. So the word-collection loop can live **in the keyboard itself** —
precisely what iOS forbids. Building it here tells us what the interaction should feel
like before we have to fit it into an iOS app's narrower shape.

## What transfers to iOS, and what does not

| Transfers | Stays behind |
|---|---|
| Layout model from the one character table | The code — Kotlin vs Swift |
| Longpress + flick handling, and their traps | |
| Prediction-bar design and sizing decisions | |
| The swipe decoder algorithm | |
| Consent/collection UX | **Where collection lives**: keyboard on Android, app on iOS |

That last row is the single most valuable thing to discover early. On iOS a keyboard
extension without Full Access can reach neither the network nor a shared container with
its own app, so collection has to move into the container app. Learning that in a
prototype beats discovering it halfway through the Swift build.

## Toolchain (on `mc`, installed 2026-08-09)

```sh
brew install openjdk@21                 # NOT the default openjdk — see below
brew install --cask android-commandlinetools
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

⚠ **JDK 26 is too new.** Homebrew's default `openjdk` is 26; Android Gradle Plugin
supports 17–21. Use `openjdk@21` and set `JAVA_HOME` to it, or the first Gradle run fails
with an unhelpful toolchain error.

⚠ **`adb` over Tailscale did not connect** to `a55` — wireless debugging is off on the
phone, and Android 11+ additionally wants a pairing code. Not worth fixing to *test*: the
HTTP-install path needs no adb at all. Enable it later only for `logcat` during debugging.

## Milestones

**M1 — it types.** `InputMethodService`, the four letters on longpress *and* flick, shift,
backspace, space, return, numeric layer. Same character table as every other platform.
Done when it survives a day of real use next to Unexpected Keyboard.

**M2 — prediction bar.** Fed by the same wordlist. Android has no keyboard memory cap, so
this is where we find out how large a model is actually *useful* before iOS forces a
budget on us.

**M3 — collection loop.** The reason this laboratory exists. Opt-in, on-device filter for
names and digits, pseudonymous, withdrawable, aggregate. Feeds the queue that
`review_lexicon.py` already reads — the review half is built and now runs anywhere.

**M4 — swipe.** Gesture path scored against a trie. The only feature Keyman cannot give on
either platform. Weeks, not days; do not start before M2.

## What we deliberately will not do

Compete with Unexpected Keyboard or HeliBoard, ship to Play Store, or maintain this as a
supported product. If it turns out better than the existing pair, that is a surprise to
re-evaluate then — not a goal.

## Open questions

- Which layout does the prediction bar assume when the user is mid-word in a language the
  model does not cover? (Interslavic speakers routinely mix in their own language.)
- Does the collection loop need a server at all, or is "export a file and attach it to a
  GitHub issue" enough for the first hundred users?
