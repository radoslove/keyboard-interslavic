# Platform parity: iOS ↔ Android

**Rule: the first step of every round on one platform is this table.** Whatever the other platform
gained since the last round is ported here before anything new is started, so the two apps never
drift into different keyboards. Update the table at the end of the round.

Last checked: 2026-10-14 (iOS 0.2.1 · Android MS 3.5.18-test, Mellomskandinavisk 0.1.11-test).

## iOS has, Android does not yet → port to Android

| Feature | iOS commit | Android notes |
|---|---|---|
| Learned lift (touch offset learned per user) | 143bdf2 (0.1.4) | Android decoder has no per-user offset |
| Acronyms, shift behaviour | ab921f2 (0.1.6) | compare shift state machines |
| Shift re-armed at sentence start (after `. ! ?` + space, new line) | ab921f2 | Android shift is one-shot only |

## Android has, iOS does not yet → port to iOS

| Feature | Android commit | iOS notes |
|---|---|---|
| **Cyrillic and Glagolitic** (script key ABC / АБВ / ⰀⰁⰂ, setting) | 14a658f (3.5.6) | `Script.kt` → Swift; same tables (`docs/script-tables.md`) |
| **Bottom row `123 🌐 , space . ⏎`**: comma left of space, period right (owner 2026-10-14); no `?` on the letters; `123` row 3 is `. , ' ! ?` so `?` `!` sit next to backspace, `¿` `¡` held on them | 3.5.16-3.5.18 | replaces iOS `. , space ?` (7801f4b) |
| Digraphs on hold: `d` → `dž`, `l` → `lj`, `n` → `nj` (first in the popup) | 3.5.7 | add to `Layout.accents` for `isv` |
| **Interscandinavian** as a language (`isc`: rows of 11, `å ø æ`) | 3.5.14 | fourth extension, wordlist `main_isc.combined` |
| **Norwegian bokmål and English keyboards** (`nb`: `å ø æ` on own keys, rows of 11; `en`: plain) | 3.5.8 | two more extensions, like `pl`; wordlists `dictionary/main_nb.combined`, `main_en.combined` → `<lang>_swipe.bin` |
| **App screen in four languages** (MS + EN gloss, English, Polish, Norwegian; follows the phone) | 3.5.8 (`Ui.kt`) | `App/App.swift`: same texts, same keys |
| Languages as system entries (one input-method subtype each, stable `subtypeId`), 🌐 rotates them | 3.5.9–3.5.10 | iOS already: one extension per language |
| Doubled-letter penalty in the glide decoder (`žže` drew the same line as `že` and won on one kept use) | 3.5.10 (`Dictionary.DOUBLE_W`) | check `SwipeDecoder.swift`: same tie, same fix if `žže` leads there |
| Instant whole-word wipe after a glide, hold trims the ending | 42c9c07 (3.5.3) | check iOS backspace (0.1.4 has its own) |

## Done in both

| Feature | iOS | Android |
|---|---|---|
| Polish keyboard (`pl`: ą ć ę ł ń ó ś ż ź on hold, own wordlist) | 7801f4b (0.2.1), own extension | 3.5.7: language setting + 🌐 cycles, lazy per-language dictionary |
| Hold popup with several letters, own first, foreign after, slide to pick | 7801f4b | 3.5.7 (Cyrillic/Glagolitic: own letters only) |
| `,` `.` on the letter layer with hold popups | 559a71f (0.1.10) | 3.5.7 (`?` moved to `123` in 3.5.16) |
| `123` like the system keyboard + `#+=` page, holds on numeric keys | ab921f2, 559a71f | 3.5.7 |
| No glide starting on the bottom row | 7801f4b | 3.5.7 |

## Deliberate differences

- Languages: iOS has one keyboard extension per language (the system list shows `isv` and
  `pl — isv`); Android has one keyboard, the languages are ticked in the app and 🌐 cycles them
  (held: the system keyboard picker). Same languages, same layouts, the platform's own way to
  switch.
- The script key (ABC / АБВ / ⰀⰁⰂ) shows for Interslavic only; Polish is Latin.

## Two apps, one code

Since 3.5.14 / 0.1.7 `android-app/` builds two apps (product flavors): **Medžuslovjansky** (later Interslove)
(`interslove`, `com.radoslove.interslavic`, opens in Interslavic) and **Mellomskandinavisk**
(`isc`, `com.radoslove.interscandinavian`, opens in Interscandinavian). Both offer all five
languages; per flavor only `src/<flavor>/` differs (`App.kt`, `Ui.kt`, `method.xml`, names).
The iOS app is one app with one extension per language; Interscandinavian is not an iOS
extension yet (its iOS side is the Keyman package).

## Both, shared

- Dictionary format: iOS reads a compact binary (`Resources/<lang>_swipe.bin`), Android parses the
  text `.combined` into objects. One format for both (the binary) would also keep memory flat when
  more languages arrive. Android 3.5.7 already loads each language only on first use.
- The layout tables are copied by hand into `Layout.swift` and `Layout.kt`. A shared table file
  that generates both would end the porting of data (code still needs porting).
