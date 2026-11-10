# Platform parity: iOS and Android

One keyboard, written twice — Kotlin (`android-app/`) and Swift (`ios/`). The layout, the
wordlists, the glide decoder and the suggestion strip are meant to behave the same on both, so
this file lists only what currently **differs**: a contributor can then tell a missing feature
from a deliberate choice. Anything not named here works the same way on both platforms.

## Only on Android so far

| Feature | Where |
|---|---|
| Interscandinavian as a language | flavor `isc` |
| Linear frequency weighting for the Polish and Czech wordlists | `Dictionary.LINEAR_FREQ` |
| Both glide anchors read the slowest sample near their end | `KeyboardView.anchorPoint` — ported to iOS and switched off again there: iOS sends no `touchesMoved` while the finger rests, so the end of the glide is never sampled (`SwipeDecoder.slowAnchors = false`) |
| Mark counter, "my words" list and theme choice shown in the APP | iOS keeps all three inside the keyboard; the app can only show them with Full Access |
| App screen in four languages | `Ui.kt` |

## Only on iOS so far

| Feature | Where |
|---|---|
| Keyboard settings drawn inside the keyboard (theme, script, layout, own words) | `holdLayer`, `settingsPanel` |

## In code on both, released on iOS test builds first

| Feature | Android | iOS |
|---|---|---|
| Slovak, Slovenian, Croatian and Serbian (Latin) keyboards | `Lang`, `Layout.skOwn` / `slOwn` / `hrSrOwn`, subtypes in `method.xml` | one extension each (`KeyboardSK`, `KeyboardSL`, `KeyboardHR`, `KeyboardSR`) |
| QWERTY or QWERTZ per language, defaulting to the language's own keyboards (QWERTZ for Czech, Slovak, Slovenian, Croatian, Serbian; Norwegian keeps its own row) | `KeyRows`, settings section in the app | `Layout.qwertz`, row in the in-keyboard settings |

## Deliberate differences

- **Languages.** iOS ships one keyboard extension per language, so the system lists them
  separately; Android ships one keyboard with an input-method subtype per language, ticked in the
  system settings and cycled with 🌐. Same languages, same layouts, each platform's own way to
  switch.
- **Full Access (iOS).** The keyboard asks for it only to reach the App Group it shares with the
  app (own words, theme, script, layout, learned touch offset). It contains no network code. With
  Full Access off it keeps all of that in its own defaults and works in full; the app then shows
  nothing of it.
- **Doubled-letter penalty.** Android only (`DOUBLE_W`). It exists there to break a tie that the
  per-word usage boost creates; iOS has no usage boost, so frequency already decides.
- **Script key** (ABC / АБВ / ⰀⰁⰂ) shows for Interslavic only, on both platforms.
