# Platform parity: iOS ↔ Android

**Rule: the first step of every round on one platform is this table.** Whatever the other platform
gained since the last round is ported here before anything new is started, so the two apps never
drift into different keyboards. Update the table at the end of the round.

Last checked: 2026-10-25 (iOS 0.4.0 · Android MS 3.5.30-test, Mellomskandinavisk 0.1.23-test).

## iOS has, Android does not yet → port to Android

| Feature | iOS commit | Android notes |
|---|---|---|
| Learned lift (touch offset learned per user) | 143bdf2 (0.1.4) | Android decoder has no per-user offset |
| Acronyms, shift behaviour | ab921f2 (0.1.6) | compare shift state machines |
| Shift re-armed at sentence start (after `. ! ?` + space, new line) | ab921f2 | Android shift is one-shot only |
| `＋` also for the capitalised FIRST word of a sentence (shift put the capital), saved lowercase; mid-sentence capitals stay out | 0.4.0 (`saveForm`, `sentenceStart`) | Android `canSave` refuses any capital (`!word.first().isUpperCase()`), so the first word of a sentence can never be saved |

## Android has, iOS does not yet → port to iOS

| Feature | Android commit | iOS notes |
|---|---|---|
| **Interscandinavian** as a language (`isc`: rows of 11, `å ø æ`) | 3.5.14 | fourth extension, wordlist `main_isc.combined` |
| **Norwegian bokmål and English keyboards** (`nb`: `å ø æ` on own keys, rows of 11; `en`: plain) | 3.5.8 | two more extensions, like `pl`; wordlists `dictionary/main_nb.combined`, `main_en.combined` → `<lang>_swipe.bin` |
| **App screen in four languages** (MS + EN gloss, English, Polish, Norwegian; follows the phone) | 3.5.8 (`Ui.kt`) | `App/App.swift`: same texts, same keys |
| Languages as system entries (one input-method subtype each, stable `subtypeId`), 🌐 rotates them | 3.5.9–3.5.10 | iOS already: one extension per language |
| Mark counter ("Znaky": which symbols are typed, exported with the ranking only) | 9a02ea5 (3.5.20, `Symbols.kt`) | ⚠ same wall: without Full Access nothing counted in the keyboard reaches the app or leaves the phone |
| Theme choice: automatic (follows the phone) / light / dark | f48b96d (3.5.27, `Theme.kt`) | 0.4.0 code done (`Shared.theme`, `applyTheme`); ⚠ works once the App Group `group.com.radoslove.interslavic` is registered on the developer portal |
| "My words": the user's added words per language, each removable | 7f0a768 (3.5.29, `UserWords.remove`, `Dictionary.forget`) | 0.4.0 code done (app list, swipe to remove; `Shared.userWords`); ⚠ same App Group condition |

## Planned for both (not built yet)

| Feature | Notes |
|---|---|
| Emoji key in place of `,` on the bottom row; `,` becomes the FIRST item of the `.` hold popup | owner 2026-10-25. iOS cannot open the system emoji keyboard from a custom one, so iOS needs its own emoji panel; Android may switch to it |

## Done in both

| Feature | iOS | Android |
|---|---|---|
| Cyrillic and Glagolitic: script key ABC / АБВ / ⰀⰁⰂ next to 🌐, same tables, everything inside stays Latin | 0.3.0 (`Script.swift`, choice kept in the keyboard) | 14a658f (3.5.6) |
| Bottom row `123 🌐 [ABC] , space . ⏎` with the Android weights; no `?` on the letters; `123` row 3 `. , ' ! ?`, `¿` `¡` held on `?` `!` | 0.3.0 | 3.5.16–3.5.18 |
| Digraphs on hold: `d` → `dž`, `l` → `lj`, `n` → `nj` (first in the popup) | 0.3.0 | 3.5.7 |
| `＋ word` chip in the FIRST strip slot | 0.3.0 | 7f0a768 (3.5.29) |
| Hand typing is never a glide: a touch within 250 ms of a typed letter is a tap; a second finger = typing; the glide follows only its own finger | 0.3.0 (`lastLetterTapAt`, `SwipeGestureRecognizer.finger`) | 3.5.30 |
| Instant whole-word wipe after a glide, hold trims the ending | 0.1.4–0.1.6 (`tapBackspace`, `restoreTrimmed`) | 42c9c07 (3.5.3) |
| Polish keyboard (`pl`: ą ć ę ł ń ó ś ż ź on hold, own wordlist) | 7801f4b (0.2.1), own extension | 3.5.7: language setting + 🌐 cycles, lazy per-language dictionary |
| Hold popup with several letters, own first, foreign after, slide to pick | 7801f4b | 3.5.7 (Cyrillic/Glagolitic: own letters only) |
| `,` `.` on the letter layer with hold popups | 559a71f (0.1.10) | 3.5.7 (`?` moved to `123` in 3.5.16) |
| `123` like the system keyboard + `#+=` page, holds on numeric keys | ab921f2, 559a71f | 3.5.7 |
| No glide starting on the bottom row | 7801f4b | 3.5.7 |
| No glide starting on the suggestion strip (above the letters) | `swipeCanStart` | 7f0a768 (3.5.29, `lettersTop`) |
| `＋ word` chip offered again right after the space that finished an unknown word | `refreshCompletions` | 7f0a768 (3.5.29) |

## Deliberate differences

- Doubled-letter penalty (Android `DOUBLE_W`, 3.5.10): not on iOS. The `žže`/`že` tie was broken
  on Android by the per-word usage boost; iOS has no usage boost, so the tie goes to frequency
  and `že` already leads. Port it together with a usage boost, if one ever comes.
- Settings: since 0.4.0 the iOS keyboard ASKS for Full Access (owner 2026-10-25), only for the
  App Group it shares with the app (own words, theme, script, learned lift). It has no network
  code. With Full Access off it keeps all of that in its own defaults and works in full; the
  app then shows nothing.

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
