# Cyrillic and Glagolitic: script tables

The keyboard can write in Latin (default), Cyrillic or Glagolitic (Android, from test build 3.5.4):
in the settings (*Latinica, kirilica ili glagolica*) and, from 3.5.5, with the script key next to 🌐
(shows ABC / АБВ / ⰀⰁⰂ, a tap switches to the next script). Letters sit on the **same keys** as in Latin
(`docs/ms-latin-table.md`): `c` writes `ц`, hold `c` for `ч`. `q w x` have no letter in these
alphabets, so both of them use two of those positions for the `lj` and `nj` keys - `љ` and `њ` in
Cyrillic, the way the Serbian and Macedonian standards have them, `ⰾⱐ` and `ⱀⱐ` in Glagolitic -
and the `x` key is gone (2026-11-08, see below). Inside, the keyboard stays Latin
(dictionary, glide decoder, ranking); only labels and written text are converted
(`android-app/.../Script.kt`).

Standard orthography only, as on every other surface.

## Table

| Latin | Cyrillic | Glagolitic |
|---|---|---|
| a b c č d | а б ц ч д | ⰰ ⰱ ⱌ ⱍ ⰴ |
| e ě f g h | е є ф г х | ⰵ ⱑ ⱇ ⰳ ⱈ |
| i j k l lj | и ј к л љ | ⰻ ⰹ ⰽ ⰾ ⰾⱐ (before a vowel: ⰾⰹ / ⰾⱝ) |
| m n nj o p | м н њ о п | ⱞ ⱀ ⱀⱐ ⱁ ⱂ |
| r s š t u | р с ш т у | ⱃ ⱄ ⱎ ⱅ ⱆ |
| v y z ž dž | в ы з ж дж | ⰲ ⱐⰹ ⰸ ⰶ ⰴⰶ |
| ja ju jo | ја ју јо | ⱝ ⱓ ⱖ |
| q w x | `љ` on `q`, `њ` on `w`, no `x` key | `ⰾⱐ` on `q`, `ⱀⱐ` on `w`, no `x` key |

Source: the community transliterator [medzuslovjansky/js-utils](https://github.com/medzuslovjansky/js-utils)
(MIT), `packages/translit`: Cyrillic = `latn2cyrl` core restricted to the standard alphabet;
Glagolitic = the original interslavic-dictionary.com converter ("v1", ported 1:1 there as `latn2glag2`),
because that is the Glagolitic readers of the dictionary have seen. Checked against the v1 examples in
`GLAGOLITIC-DIFF.md` (`azijatsky` → `ⰰⰸⰻⱝⱅⱄⰽⱐⰹ`, `abhazskom` → `ⰰⰱⱈⰰⰸⱄⰽⱁⱞ`, `-dnja` → `ⰴⱀⱝ`, `basnje-` → `ⱀⰹⰵ`).

## `q w x`: no key in Cyrillic and Glagolitic (decided 2026-11-08)

Until 2026-11-08 the three keys stayed on the base row in every script and passed the Latin letter
through unchanged, so a foreign name came out in mixed script (`Нью-York`). That was the one place
where the rule applied to the OTHER foreign letters was not applied: `Layout.variants` hides
`ñ ø ł ą` in Cyrillic and Glagolitic ("they would be Latin letters in the middle of the other
script"), while `q w x` sat there doing exactly that.

**Decision (owner), in two steps.** First: no Latin letter stays in a non-Latin script. What
settled that is how a foreign word is actually written - `Linux`, `WhatsApp` and `Windows` stand
in a Cyrillic sentence **whole, in Latin**, not as three Latin letters inside a Cyrillic word, so
a key writing a single Latin `w` was never the way to such a word. The way to it is the script
key, which is also what every Slavic Cyrillic layout forces its users to do (measured below).
Second (owner, the same day, after reading what the Serbian and Macedonian layouts do): **the two
freed positions are not left empty - `q` carries the `lj` key and `w` the `nj` key**, in Cyrillic
`љ` and `њ`, which is exactly where those two standards put them and where a user coming from
them will look. Glagolitic followed an hour later, after the owner saw the two gaps on the phone
("to elegancko jakby było tak analogicznie jak cyrylicy"): it has no single SIGN for either pair,
so the keys read `ⰾⱐ` and `ⱀⱐ` - which is nothing new on this keyboard, since `y` has stood in the
top row as `ⱐⰹ` from the start. The key is a shortcut to the digraph, not a claim that it is a
letter. `x` gets nothing in either script, because `dž` is settled as two letters (`дж`, not
`џ`), so that key goes.

So, per script:

| | row 1 | row 2 | row 3 |
|---|---|---|---|
| Latin | `q w e r t y u i o p` | `a s d f g h j k l` | `z x c v b n m` |
| Cyrillic | `љ њ е р т ы у и о п` | `а с д ф г х ј к л` | `з ц в б н м` |
| Glagolitic | `ⰾⱐ ⱀⱐ ⰵ ⱃ ⱅ ⱐⰹ ⱆ ⰻ ⱁ ⱂ` | `ⰰ ⱄ ⰴ ⱇ ⰳ ⱈ ⰹ ⰽ ⰾ` | `ⰸ ⱌ ⰲ ⰱ ⱀ ⱞ` |

The digraph keys are the characters `q` and `w` INSIDE - no Interslavic form contains either
letter, so both are free symbols. What a key writes is `Layout.keyText`, and a glide over
one of them is read as the PAIR: `Dictionary.foldWord` folds `lj` onto `q` and `nj` onto `w`
(`foldFirst`/`foldLast` do the same for the two gates that run over every entry), switched on by
`Dictionary.digraphKeys` from the current script. The wordlist, the ranking and the collector stay
Latin as always.
In Cyrillic a word with `lj` therefore has **two** routes for the glide - over the `љ` key, or
over `л` and `ј`, since both are still on the board - and the decoder takes whichever fits the
trail better (`Dictionary.foldPlain`, both gates admitting either route, 3.6.15; the owner asked
for it the moment he saw the first build: "żeby łapało wyrazy i po swipowaniu przez љ albo њ i
przez osobno `l` i `j`"). A word without a pair still has exactly one route and costs nothing
extra. Tapping is unaffected and the hold on `l` still offers `lj`.
⚠ Three holds also become taps, which is what the variant was originally proposed for: `lj` and
`nj` (held on `l` and `n`) have their own keys now, while `dž` still needs the hold on `d`.
⚠ What a TAP writes has no context: the Glagolitic `ⰾⱐ` key commits the pair without looking at
the next letter, so `lj` + `u` comes out as `ⰾⱐⱆ` and not as the ligature `ⰾⱓ`. That is the
limitation per-tap Glagolitic already had - typing `l`, `j`, `u` one key at a time does the same -
and the glide does not have it, because it commits a whole word through the converter at once.

Two other ways out were weighed and rejected:
1. **Transcribe them the Slavic way** (`q → к`, `w → в`, `x → кс`). Rejected on evidence, not
   taste: transcription in the Slavic languages is of the PRONUNCIATION and it diverges (see
   below), so a flat table would be a NEW rule of ours. It would also break the lossless round
   trip (`в` reads back as `v`), and `x` would need one key to write two letters.
2. **Leave it as a deliberate difference.** Rejected: the keys cost accuracy nothing, but they do
   nothing either, and the inconsistency with `Layout.variants` would have stayed in the code with
   no reason to give.

Still open: the national Cyrillic layouts (ЙЦУКЕН, the Serbian one) as a whole, which remains
the item under Open questions, and the iOS port - there the wordlist is a prebuilt binary of key
indices (`build_swipe_dictionary.py`, `isv_swipe.bin`), so a second route per word is not the
five-line change it was on Android.

  **What the Slavic languages themselves do (measured 2026-11-08, layouts read off the Windows
  `KBD*.DLL` tables on kbdlayout.info):** no standard Cyrillic layout carries a single Latin
  letter. Every alphabetic position is a Cyrillic letter, because these alphabets have 30-33
  letters against 26 QWERTY positions — the overflow already spills onto `[ ] ; ' \` and the
  backtick. On the `q w x` positions sit `й ц ч` (Russian, Ukrainian, Belarusian) and `љ њ џ`
  (Serbian, Macedonian); Bulgarian БДС puts a comma on `q` and `у й` on `w x`. So a user writing
  `Wikipedia` on a Cyrillic layout **has to switch layouts** — there is nowhere to take the letter
  from. That is what this keyboard now does too.
  Transcription, where it happens, is of the PRONUNCIATION, not of the letter, and it diverges:
  `w` is `в` in Russian, Serbian, Macedonian and Belarusian (Вашингтон) but `у` in Bulgarian
  (Уикипедия) and in the Russian default for English `w` (Уайтфилд); `qu` is `ку` in Russian and
  `кв` in Ukrainian; `x` is usually `кс` but `гз` for [gz] and `з` for [z]. A flat `q → к, w → в,
  x → кс` table therefore matches no Slavic language and would be a NEW rule of ours, which is the
  one thing this project does not do — so transcription was out on evidence, not on taste.
  ⚠ The `љ њ` half of the future variant is not an invention either: that is exactly where the
  Serbian and Macedonian standards put those two letters. `џ` is not available to us, since `dž` is
  settled as two letters (see Resolved conflicts).

  **What removing the keys costs the glide decoder (measured 2026-11-08, `tools/swipe_eval.py`
  geometry swapped per variant, same 320-word sample, thresholds derived from each variant's own
  key width):**

  | geometry | top-1 | top-3 |
  |---|---|---|
  | as today, `q w x` present (10/9/7, rows fill the width) | 79.0 % | 91.8 % |
  | `q w x` gone, rows fill the width (8/9/6, keys 135 px) | 73.0 % | 89.0 % |
  | `q w x` gone, constant 108 px keys, every row centred (the iOS way) | 77.1 % | 91.5 % |
  | `q w x` gone, middle row left exactly as today, 108 px | 77.4 % | 90.3 % |
  | `q w x` gone, middle row left exactly as today, 120 px | 76.5 % | 91.5 % |
  | **`q w x` kept as slots, no letter moves** | **79.0 %** | **91.8 %** |
  | control: all keys present but every row centred at 108 px | 73.0 % | 87.8 % |

  Two readings. First, the last row is the one that matters for interpretation: it removes NO key
  and still loses six points, because the middle row's keys go from 120 px to 108 px and the
  columns of the alphabet stop lining up. So the six points in row two are the price of disturbing
  the grid, not the price of dropping three letters — and Norwegian's 11/11/7 is fine precisely
  because its grid is consistent. Second, the dead-slot row reproduces today's figures to the
  decimal, which is the positive control: no Interslavic form contains `q w x`, so a centre map
  without them must decode identically, and it does. Dropping the keys for real costs about two
  points of top-1 in the best layout tried; keeping the slots costs nothing.
  ⚠ `KeyboardView.keyWidth` had to be fixed FIRST, and was (3.6.13): it measured the key width as
  the distance between the centres of `q` and `w` and silently fell back to `dp(36)` when either
  was missing, which is exactly what would have happened on every glide in Cyrillic from now on -
  the keyboard would look and feel identical while the touch-offset compensation, the pivot cap and
  the glide minimum were scaled by a width no key has. It now takes the PITCH of the top letter row
  (the median gap between neighbouring centres), which is the same 108 px in both scripts. iOS never
  had the bug: `swipeKeyCentres` takes `max(b.bounds.width)` over the rendered buttons and the
  decoder skips centres equal to `.zero`.

  **Where the freed space goes (measured 2026-11-08, same 320-word sample, this time with the
  touch lift in play: paths synthesised with a FIXED physical offset of 37.8 px and compensated with
  today's 0.35 key widths).** This is the question the table above does not answer - the three keys
  are gone either way, and what is left is where to put the hole:

  | row 1 / row 3 geometry | top-1 | top-3 |
  |---|---|---|
  | Latin, as today (baseline) | 79.0 % | 91.8 % |
  | hole at the left edge, every letter left on its Latin pixel | 79.0 % | 91.8 % |
  | the neighbour absorbs the hole (`e` three keys wide, `z`/`c` half a key each) | 74.3 % | 88.7 % |
  | the edges absorb it (`e` and `p` half a key each) | 73.4 % | 87.1 % |
  | **padded symmetrically, Latin key width - WHAT SHIPS** | **77.4 %** | **90.3 %** |
  | the eight keys fill the width instead (plain Android, 135 px keys) | 74.3 % | 89.3 % |

  Row two is free but looks broken: the top row would start two key widths in and the bottom row
  would carry a gap between `z` and `c`. Rows three and four are the surprise and the reason the
  shipped layout does NOT widen keys into the gap: moving ONE frequent letter (`e`, by one key
  width) costs almost five points of top-1, MORE than shifting the whole row does, because a
  uniform shift keeps the SHAPE of every route inside the row while one displaced letter deforms
  every route through it. What ships is the Latin key width with the row padded symmetrically at
  both ends (the geometry the iOS code already used for a short row): 1.6 points of top-1 below
  Latin, and 3.1 points ABOVE letting the eight keys fill the width, which is what Android would
  have done on its own. Widening is not free for a second reason: every threshold in the decoder
  is expressed in key widths - the learned touch offset (0.35), the pivot cap (1.6), the glide
  minimum, the nearest-key reach - so 135 px keys make the decoder push the trail back by 47 px
  against a thumb that is off by 38. The phone's learned `Lift` value also stays valid across a
  script change only while the pitch is the same 108 px.

  **What the `љ`/`њ` keys do to the decoder (measured 2026-11-08, same sample and lift).** The
  three rows below share one geometry - `q`/`w` where Latin has them, row 3 without `x` and padded -
  and differ only in whether the decoder knows what the two keys mean:

  | Cyrillic layout | top-1 | top-3 |
  |---|---|---|
  | Latin, as today (baseline) | 79.0 % | 91.8 % |
  | `q`/`w` present but the decoder routes `lj` over `л`+`ј` | 72.7 % | 88.7 % |
  | the two keys GONE, both rows padded (what 3.6.13 shipped) | 77.4 % | 90.3 % |
  | **`q` = `љ`, `w` = `њ`, a glide over them read as the pair (3.6.14)** | **79.0 %** | **91.2 %** |
  | the same, but row 3 left on its Latin pixels with the hole where `x` was | 78.1 % | 91.5 % |

  Read the second row first: it is the same keyboard as the fourth, minus the fold, and it loses
  6.3 points. The loss is not the two keys sitting there (an identical run with them removed from
  the centre map scores the same 72.7 %) - it is row 3 moving half a key width while row 1 stays
  on its Latin pixels, which is the alignment effect the table above already showed.
  And the fold gives all of it back, so the Cyrillic keyboard decodes exactly as well as the Latin
  one. The reason is worth keeping in mind for any future key of this kind: 22 302 forms, 9.2 % of
  the wordlist, contain `lj` or `nj`, and until now every one of them had a route through `l` and
  `j` - two keys in the middle row, right where most glides pass. Giving them a waypoint in the
  top-left corner takes 9 % of the lexicon out of the most crowded part of the board, and every
  other word competes against fewer look-alikes.
  **Both routes, measured on the words that have two (2026-11-08).** 21 of the 320 sample words
  contain `lj` or `nj`; each was glided six times, once per route, against the 3.6.15 dictionary
  and against a 3.6.14 one that knows only the `љ` route:

  | the finger goes over | dictionary | top-1 | top-3 |
  |---|---|---|---|
  | `љ` / `њ` | both routes (3.6.15) | 82 % | 97 % |
  | `l` and `j` | both routes (3.6.15) | 74 % | 91 % |
  | `l` and `j` | only the `љ` route (3.6.14) | **0 %** | **3 %** |

  The last row is why the owner was right to ask for both: with one route, gliding the two letters
  for a digraph word returned the word **never** - 0 of 126 draws in top-1, 4 in top-3 - while
  looking perfectly healthy on every other word, because only 6.6 % of the wordlist can even show
  the defect. Admitting both routes costs about 2.5 points of top-1 on the whole sample (76.5 %
  against 79.0 % for one route), since every digraph word now enters the race twice; the `љ` route
  stays the better of the two, which is what it should be - it is shorter and lands in a corner no
  other word visits. Watched from now on by `SWIPE_SCRIPT=cyrl python tools/swipe_eval.py digraph`,
  which checks both routes AND carries the negative control (the one-route dictionary has to fail
  the two-letter gesture, or the gate is dead).
  ⚠ The trap that hid this for one build: `synth_path` folded the word it was given, and in
  Cyrillic that fold collapses `lj` onto `љ` - so an attempt to simulate "the finger went over `l`
  and `j`" silently drew the gesture over `љ` instead, and three measurement variants came out
  identical and looked like a result. `synth_path(..., keys=...)` now draws a given key sequence.

  The last row closes the alignment question the other way round: with the two digraph keys in
  place there is no reason to leave row 3 untouched. Keeping `з ц в б н м` on their Latin pixels
  means a one-key gap where `x` used to be and measures 0.9 points below simply padding the row,
  which is inside the noise floor below - so the layout that looks right costs nothing, and the
  gap buys nothing.

  **How much is a point worth here (measured 2026-11-08, and it corrects a claim made earlier the
  same day).** A key-width sweep of one geometry - short rows centred, 108 to 140 px, each run
  twice: once with today's compensation constant (0.35 key widths) and once with it re-derived so
  the compensation equals the 37.8 px physical offset:

  | key width | today's 0.35 | re-derived |
  |---|---|---|
  | 108 px | 77.1 % | 77.1 % |
  | 112 px | 76.8 % | 77.1 % |
  | 116 px | 73.7 % | 73.0 % |
  | 120 px | 77.7 % | 76.5 % |
  | 124 px | 76.8 % | 76.2 % |
  | 128 px | 73.7 % | 71.8 % |
  | 132 px | 76.5 % | 74.6 % |
  | 135 px | 76.5 % | 74.9 % |
  | 140 px | 74.3 % | 74.6 % |

  Two things follow, and one of them is a correction. First, re-deriving the compensation does NOT
  rescue wide keys - at 135 px it makes the result worse, not better - so the explanation given
  earlier, that widening keys hurts because it inflates a compensation expressed in key widths,
  is not what dominates. It is a real effect in the code (the learned `Lift` value, the pivot cap
  and the reach are all in key widths, so they do all move with the width), but what actually
  decides the score is WHICH words collide at a given geometry. Second, and more usefully: inside
  one family the score swings between 73.7 % and 77.7 % with no trend, so on this 320-word sample
  **differences under about two points do not carry a decision**. What the measurements above
  establish is the large structure - the 72.7 % level is really worse than the 79.0 % level, the
  digraph fold really does give back what the row-3 shift takes - not the fine ordering of the
  layouts within a point or two of each other. Anything that needs finer resolution than that
  needs more than one sample and more than one seed.

## Resolved conflicts

| Question | Decision | Why |
|---|---|---|
| Reuse the removed `KBDMSKIR` Cyrillic layout (© 2012 R. Lombino jr.)? | No. Own layout, letters on their Latin keys | the upstream files were removed on 2026-10-07 for licensing; same positions also let swipe reuse the Latin decoder unchanged |
| `dž` in Cyrillic | `дж` (two letters), not `џ` | js-utils core has no `dž` rule; Latin `dž` is also two letters |
| `q w x` in Cyrillic and Glagolitic | Both scripts put the `lj` and `nj` keys on `q` and `w` - `љ`/`њ` (the Serbian/Macedonian positions) and `ⰾⱐ`/`ⱀⱐ` - and neither has an `x` key. The script key (ABC) is the way to a Latin word | a foreign word inside a Cyrillic sentence is written WHOLE in Latin (`Linux`, `WhatsApp`), never as three Latin letters inside a Cyrillic word, so the keys bought nothing; no standard Slavic Cyrillic layout carries a Latin letter; the same rule `Layout.variants` already applies to `ñ ø ł ą`. Measured cost below |
| Which Glagolitic converter | v1 (`m → ⱞ`, `y → ⱐⰹ`, `ja → ⱝ`), not the newer `latn2glag` (`ⰿ`, `ⱏⰹ`, `ⰹⰰ`) | v1 is what the dictionary shows; the two disagree on 55–77 % of forms |

## Open questions

- **Glagolitic is not settled in the community** (two converters, `GLAGOLITIC-DIFF.md`); this keyboard
  follows v1. Not reproduced: v1's stray `ⱏ` inside `zsk`, and Glagolitic punctuation.
- **Cyrillic key layouts (future).** Today the Cyrillic letters sit on their Latin keys. People who
  already type Cyrillic are used to national layouts (ЙЦУКЕН, the Serbian/Macedonian layout, the 2012
  Interslavic `KBDMSKIR`); offer them as an option later. Swipe then needs the key positions of that
  layout in the decoder, not the Latin ones.
- **`nj` / `lj` across a morpheme boundary.** Today `nj` always becomes `њ`, so `pytanje` comes out as
  `пытање`; the Interslavic course (Matija, 2026) writes **`пытанје`** — `n` and `j` stay apart in the
  `-nje` ending. Needs to know where the boundary is (an ending list or dictionary data), not a guess.
  Glagolitic is affected the same way (`ⱀⱐ` vs `ⱀⰹ`).
- Glagolitic needs a font on the phone. Most Android phones ship Noto Sans Glagolitic; if keys
  show boxes, the font must be bundled (Noto Sans Glagolitic, OFL).
- ✅ **iOS: done, since 0.4.0** (`ios/Keyboard/Script.swift`, the same two tables and the same
  "everything inside stays Latin" rule; the script key sits next to 🌐 and the choice is also in
  the app picker *Latinica, kirilica ili glagolica*). `docs/PLATFORM_PARITY.md` carries it as done
  in both; that row read 0.3.0 until 2026-11-08, when `Script.swift` was measured as arriving in
  the 0.4.0 commit (25.10.2026, `MARKETING_VERSION: "0.4.0"`).
- **Keyman package: Latin only — and the obstacle is that this is the SAME language in another
  script.** In Keyman the unit of identity is the BCP-47 tag, and both of our packages declare
  `<Language ID="isv-Latn">` (`keyman/isv_latin/source/isv_latin.kps`,
  `keyman/radoslove.isv-latn.wordlist/source/*.model.kps`). Cyrillic and Glagolitic would be
  `isv-Cyrl` and `isv-Glag`, which means not one keyboard with a script key but **three keyboards**
  (each its own `.kmn`, touch layout and `.kvks`) and **three lexical models** — a model declares
  the script it PRODUCES (Keyman's lexical-model tutorial: the script field picks the ISO 15924 tag
  the model produces), and Keyman has no equivalent of `Script.out`: nothing converts a keyboard's
  output, so a keyboard writing Cyrillic must be fed Cyrillic suggestions. For the user the switch
  would therefore be a keyboard change in the Keyman menu, not a mode change on the keyboard.
  The cheap half is the `.kmn` itself (a key → letter table, which the table above already is) and
  the wordlist, which can be generated by running our own converter over the Latin one — inheriting
  the `-nje` problem above. Measured 2026-11-08 (`api.keyman.com/search/2.0?q=interslavic`): the
  Keyman catalogue holds exactly ONE Interslavic keyboard, ours, tagged `isv-latn` — so what we do
  not ship there, nobody ships.
