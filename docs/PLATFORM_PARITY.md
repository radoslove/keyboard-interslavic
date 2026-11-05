# Platform parity: iOS ↔ Android

**Rule: the first step of every round on one platform is this table.** Whatever the other platform
gained since the last round is ported here before anything new is started, so the two apps never
drift into different keyboards. Update the table at the end of the round.

Last checked: 2026-11-05 (iOS 0.4.6 on `iph` · Android MS 3.6.2-test on `a55`).

## iOS has, Android does not yet → port to Android

Nothing outstanding: the five rows that stood here were ported in **MS 3.5.31 /
ISC 0.1.24** (2026-11-01) and moved to *Done in both*.

## Android has, iOS does not yet → port to iOS

| Feature | Android commit | iOS notes |
|---|---|---|
| **Both glide anchors read the slowest sample within 120 ms of their end**, not the first and last sample. A thumb decelerates onto the letter it means and carries past it, so the extreme samples are the approach and the overshoot - and both anchors are hard gates, so one noisy point decided whether the right word could compete at all. Worth +1 to +3 points of top-1 across the offset sweep, never below baseline. Widening the anchor set instead measured as nothing: the gate was aimed wrong, not too narrow | 3.6.1 (`KeyboardView.anchorPoint`, `speedCurve`) | ⚠ **Ported in 0.4.4 and measured HARMFUL on iOS, switched off in 0.4.5** (`SwipeDecoder.slowAnchors = false`, code kept). 21 real `iph` glides replayed both ways: `pisati` top-1 3/13 → 0/13, `slovo` 8/8 → 3/8, `se`/`to` unchanged. iOS sends no `touchesMoved` while the finger rests, so the deceleration onto the last letter is never sampled and the slowest point near the end is the previous TURN (`slovo` → `suprug`, `doč`). Re-measure only after the end of the glide is sampled (e.g. coalesced touches) - the log carries `ms` since 0.4.4 |
| **A way into the keyboard's own settings FROM the keyboard**: hold 🌐 for a two-item strip: 🌐 the system keyboard picker first, over the key, and ⚙ settings one slide to the right. Before this the settings were reachable only from the app icon, so changing anything meant leaving what you were typing - and a tester does not necessarily know the keyboard has an app at all (owner, 2026-11-04). Icons, not words: the keyboard surface carries no translated strings | 3.5.42 (`KeyboardView.globeKey`, `ImeService.openSettings`) | iOS has no globe of its own (the system key switches extensions) and an extension cannot just start an activity - it needs its own route into the container app; decide the gesture first |
| Frequency pulls LINEARLY in the byte for Polish and Czech too, not log-of-a-rank. The byte is already a quantile, so the second log left the whole signal worth ~5 px of path deviation - 5 % of a key - and `semtex` outranked the median of the dictionary | 3.5.39 (`Dictionary.LINEAR_FREQ`) | `SwipeDictionary.swift` scores its own way; check which transform its binary carries before copying the constant |
| Polish PAST-PERSONAL and CONDITIONAL forms in the wordlist (136 577 added): the endings are agglutinants, so PoliMorf has no composite form and the filter dropped every one - `zrobil` was in, `zrobilem` was not | 3.5.39 (`tools/build_pl_aglutynanty.py`) | rebuild `pl_swipe.bin` from the new `main_pl.combined` |
| Each app names the keyboard languages in ITS OWN language: the MS app's picker and settings read `medžuskandinavsky poljsky norvežsky anglijsky češsky`, and the sixth is the service's own name (owner 2026-11-01, every adjective verified in medzuslove_canon) | 3.5.34–3.5.35 | one extension per language, so the names live in each `Info.plist` display name plus the list in `App/App.swift` |
| **Czech** as a language (`cs`: QWERTY rows, á č ď ě é í ň ó ř š ť ú ů ý ž on hold) | 3.5.31 | needs its own extension + `cs_swipe.bin` from `main_cs.combined` (40 959 forms); `foldBase` must fold the Czech letters or every glide over them dies |
| **Interscandinavian** as a language (`isc`: rows of 11, `å ø æ`) | 3.5.14 | fourth extension, wordlist `main_isc.combined` |
| **Norwegian bokmål keyboard** (`nb`: `å ø æ` on own keys, rows of 11) | 3.5.8 | ⚠ needs design first: the iOS swipe binary knows 26 keys a–z, so `ø`/`æ` words drop out and `å` folds to `a`; widen to own keys (29), together with `isc` |
| **App screen in four languages** (MS + EN gloss, English, Polish, Norwegian; follows the phone) | 3.5.8 (`Ui.kt`) | `App/App.swift`: same texts, same keys |
| Languages as system entries (one input-method subtype each, stable `subtypeId`), 🌐 rotates them | 3.5.9–3.5.10 | iOS already: one extension per language |
| Mark counter ("Znaky": which symbols are typed, exported with the ranking only) | 9a02ea5 (3.5.20, `Symbols.kt`) | ⚠ same wall: without Full Access nothing counted in the keyboard reaches the app or leaves the phone |
| Theme choice: automatic (follows the phone) / light / dark | f48b96d (3.5.27, `Theme.kt`) | 0.4.0 code done (`Shared.theme`, `applyTheme`); ⚠ works once the App Group `group.com.radoslove.interslavic` is registered on the developer portal |
| "My words": the user's added words per language, each removable | 7f0a768 (3.5.29, `UserWords.remove`, `Dictionary.forget`) | 0.4.0 code done (app list, swipe to remove; `Shared.userWords`); ⚠ same App Group condition |

## Done in both

| Feature | iOS | Android |
|---|---|---|
| Hold on `.` of the letter layer: `:` BEFORE `;` (owner 2026-11-01: the colon is the one he types, so it sits nearer the thumb); same swap in the `,` hold | 0.4.3 code (`periodAccents`/`commaAccents` tails) ⚠ not built yet — needs an Xcode build on `mc` | 3.5.32 |
| The learned touch offset may go NEGATIVE, floor -0.5: a thumb landing below the key is as real as one above; a 0 floor switched compensation off on the phone that needed it | 0.4.4 (`SwipeInput.learn`; iOS learned the offset since 0.1.4, only the floor was 0) | 3.6.1 (`Lift.MIN`) |
| A letter tapped after a glide extends that word only if the WORDLIST says it still can; time is just a ceiling. Deciding it on the clock alone glued the next word onto the last one whenever typing was quick (`wyjsc` + `s` -> `wyjscs`, owner 2026-11-04) | 0.4.6 (`SwipeInput.canExtend`, `SwipeDictionary.hasPrefix`; folded-key prefix, user words count) | 3.5.39 (`Dictionary.hasPrefix`) |
| Cyrillic and Glagolitic: script key ABC / АБВ / ⰀⰁⰂ next to 🌐, same tables, everything inside stays Latin | 0.3.0 (`Script.swift`, choice kept in the keyboard) | 14a658f (3.5.6) |
| Bottom row `123 🌐 [ABC] , space . ⏎` with the Android weights; no `?` on the letters; `123` row 3 `. , ' ! ?`, `¿` `¡` held on `?` `!` | 0.3.0 | 3.5.16–3.5.18 |
| Digraphs on hold: `d` → `dž`, `l` → `lj`, `n` → `nj` (first in the popup) | 0.3.0 | 3.5.7 |
| `＋ word` chip in the FIRST strip slot | 0.3.0 | 7f0a768 (3.5.29) |
| Hand typing is never a glide: a touch within 250 ms of a typed letter is a tap; a second finger = typing; the glide follows only its own finger | 0.3.0 (`lastLetterTapAt`, `SwipeGestureRecognizer.finger`) | 3.5.30 |
| Instant whole-word wipe after a glide, hold trims the ending | 0.1.4–0.1.6 (`tapBackspace`, `restoreTrimmed`) | 42c9c07 (3.5.3) |
| English keyboard (`en`: plain a–z, foreign letters on hold, Android's `main_en.combined`) | 0.4.1 (`KeyboardEN` extension, `en_swipe.bin`, 46 693 forms) | 3.5.8 |
| Learned touch lift: the trail is pushed back down before decoding, and every KEPT glide nudges the value (default 0.35 key widths) | 143bdf2 (0.1.4), 0.4.3 (`adoptLocalLift`) | 3.5.31 (`Lift.kt`, `learnLift`); one value per phone, in `isv_collector` prefs |
| Shift re-armed at the start of a sentence (field start, new line, `. ! ?` + space); a shift set by hand is left alone | ab921f2 (0.1.6) | 3.5.31 (`rearmShift`, driven by `onSelectionChanged` + `onInputStarted`) |
| Shift on selected text cycles small → Capital → CAPS → small (this is how an acronym is typed) | 0.4.2 (`cycleCase`) | 3.5.31 (`cycleCase`, selection read via `getSelectedText`) |
| 😀 key in place of `,`, own panel (lately used first, then a fixed set of 168, rows of eight, ABC back + ⌫); `,` FIRST in the `.` hold, then `?`, the old `.` hold, `; : -` | 0.4.2 (`Emoji.swift`, `Layout.periodLetterAccents`) | 3.5.31 (`Emoji.kt` GENERATED from the Swift list so the order cannot drift, `Layout.periodLetterHold`) |
| `＋` also for the capitalised FIRST word of a sentence, saved lowercase; a capital mid-sentence (a name) stays out | 0.4.0 (`saveForm`, `sentenceStart`) | 3.5.31 (`saveForm`, `sentenceStart`) |
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

⚠ **The `isc` flavor is PARKED from 2026-11-04** (owner's call): it stays in
`build.gradle.kts` and nothing is cut, it simply is not built or handed to a phone any
more. Do not read the two `Ui.kt` as being in step - they already differ by about thirty
lines, and that gap grows with every settings text added to `interslove` alone.
Un-parking costs one `assemble`; cutting the flavor out would cost half an hour twice and
save nothing per round, which is why it is not cut.

This parks the separate APP, not the language: Mellomskandinavisk stays one of the six
languages of the published app, and its dictionary ships in `src/main/assets`, so both
flavors carry it anyway.

## Both, shared

- Dictionary format: iOS reads a compact binary (`Resources/<lang>_swipe.bin`), Android parses the
  text `.combined` into objects. One format for both (the binary) would also keep memory flat when
  more languages arrive. Android 3.5.7 already loads each language only on first use.
- The layout tables are copied by hand into `Layout.swift` and `Layout.kt`. A shared table file
  that generates both would end the porting of data (code still needs porting).
