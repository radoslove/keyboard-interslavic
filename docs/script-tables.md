# Cyrillic and Glagolitic: script tables

The keyboard can write in Latin (default), Cyrillic or Glagolitic: in the settings
(*Latinica, kirilica ili glagolica*) and with the script key next to 🌐, which shows
ABC / АБВ / ⰀⰁⰂ and switches to the next script on a tap. Letters sit on the **same keys** as in
Latin (`docs/ms-latin-table.md`): `c` writes `ц`, hold `c` for `ч`. Inside, the keyboard stays
Latin — wordlist, glide decoder, ranking; only the labels and the written text are converted
(`android-app/.../Script.kt`, `ios/Keyboard/Script.swift`).

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
Glagolitic = the original interslavic-dictionary.com converter ("v1", ported 1:1 there as
`latn2glag2`), because that is the Glagolitic readers of the dictionary have seen. Checked against
the v1 examples in `GLAGOLITIC-DIFF.md` (`azijatsky` → `ⰰⰸⰻⱝⱅⱄⰽⱐⰹ`, `abhazskom` → `ⰰⰱⱈⰰⰸⱄⰽⱁⱞ`,
`-dnja` → `ⰴⱀⱝ`, `basnje-` → `ⱀⰹⰵ`).

## Rows per script

| | row 1 | row 2 | row 3 |
|---|---|---|---|
| Latin | `q w e r t y u i o p` | `a s d f g h j k l` | `z x c v b n m` |
| Cyrillic | `љ њ е р т ы у и о п` | `а с д ф г х ј к л` | `з ц в б н м` |
| Glagolitic | `ⰾⱐ ⱀⱐ ⰵ ⱃ ⱅ ⱐⰹ ⱆ ⰻ ⱁ ⱂ` | `ⰰ ⱄ ⰴ ⱇ ⰳ ⱈ ⰹ ⰽ ⰾ` | `ⰸ ⱌ ⰲ ⰱ ⱀ ⱞ` |

`q w x` have no letter in these alphabets. Two of the three positions carry the `lj` and `nj`
keys — `љ` and `њ`, where the Serbian and Macedonian standards put them, `ⰾⱐ` and `ⱀⱐ` in
Glagolitic — and the `x` key is gone, because `dž` is settled as two letters (`дж`, not `џ`).
A Latin letter is never left standing in a non-Latin script: a foreign word inside a Cyrillic
sentence is written whole, in Latin (`Linux`, `WhatsApp`), which is what the script key is for,
and no standard Slavic Cyrillic layout carries a Latin letter either. The row keeps the LATIN key
width, padded at both ends, so every threshold the decoder expresses in key widths — the learned
touch offset, the pivot cap, the glide minimum — stays calibrated across a script change.

The digraph keys are the characters `q` and `w` inside: no Interslavic form contains either
letter, so both are free symbols. What a key writes is `Layout.keyText`, and a glide over one of
them is read as the PAIR (`Dictionary.foldWord`, switched on by `Dictionary.digraphKeys`). In
Cyrillic a word with `lj` therefore has **two** routes — over the `љ` key, or over `л` and `ј`,
since both are still on the board — and the decoder takes whichever fits the trail better. Tapping
is unaffected and the hold on `l` still offers `lj`; `dž` still needs the hold on `d`.

⚠ What a TAP writes has no context: the Glagolitic `ⰾⱐ` key commits the pair without looking at
the next letter, so `lj` + `u` comes out as `ⰾⱐⱆ`, not as the ligature `ⰾⱓ`. That is the
limitation per-tap Glagolitic already had — typing `l`, `j`, `u` one key at a time does the same.
A glide does not have it, because it commits a whole word through the converter at once.

## Resolved conflicts

| Question | Decision | Why |
|---|---|---|
| Reuse the removed `KBDMSKIR` Cyrillic layout (© 2012 R. Lombino jr.)? | No. Own layout, letters on their Latin keys | the upstream files were removed on 2026-10-07 for licensing; the same positions also let the glide decoder reuse the Latin geometry unchanged |
| `dž` in Cyrillic | `дж` (two letters), not `џ` | js-utils core has no `dž` rule; Latin `dž` is also two letters |
| `q w x` in Cyrillic and Glagolitic | `lj` and `nj` keys on `q` and `w`; no `x` key; the script key (ABC) is the way to a Latin word | a foreign word in a Cyrillic sentence is written whole in Latin, so a key for a single Latin letter bought nothing; the same rule `Layout.variants` already applies to `ñ ø ł ą` |
| Transcribe `q w x` the Slavic way (`к`, `в`, `кс`)? | No | transcription in the Slavic languages is of the PRONUNCIATION and it diverges (`w` is `в` in Russian but `у` in Bulgarian; `qu` is `ку` in Russian, `кв` in Ukrainian), so a flat table would be a new rule of ours and would break the lossless round trip |
| Which Glagolitic converter | v1 (`m → ⱞ`, `y → ⱐⰹ`, `ja → ⱝ`), not the newer `latn2glag` (`ⰿ`, `ⱏⰹ`, `ⰹⰰ`) | v1 is what the dictionary shows; the two disagree on 55–77 % of forms |

## Open questions

- **Glagolitic is not settled in the community** (two converters, `GLAGOLITIC-DIFF.md`); this
  keyboard follows v1. Not reproduced: v1's stray `ⱏ` inside `zsk`, and Glagolitic punctuation.
- **National Cyrillic key layouts.** Today the Cyrillic letters sit on their Latin keys. People who
  already type Cyrillic are used to ЙЦУКЕН or the Serbian/Macedonian layout; those could be offered
  as an option later. The glide decoder would then need that layout's key positions, not the Latin
  ones.
- **`nj` / `lj` across a morpheme boundary.** Today `nj` always becomes `њ`, so `pytanje` comes out
  as `пытање`, while the Interslavic course (Matija, 2026) writes **`пытанје`** — `n` and `j` stay
  apart in the `-nje` ending. Doing this right needs to know where the boundary is (an ending list
  or dictionary data), not a guess. Glagolitic is affected the same way (`ⱀⱐ` vs `ⱀⰹ`).
- **A font on the phone for Glagolitic.** Most Android phones ship Noto Sans Glagolitic; if the keys
  show boxes, the font has to be bundled (Noto Sans Glagolitic, OFL).
- **Keyman package: Latin only.** In Keyman the unit of identity is the BCP-47 tag, and both of our
  packages declare `isv-Latn`. Cyrillic and Glagolitic would be `isv-Cyrl` and `isv-Glag`, which
  means not one keyboard with a script key but three keyboards and three lexical models — a model
  declares the script it PRODUCES, and Keyman has no equivalent of `Script.out`, so a keyboard
  writing Cyrillic must be fed Cyrillic suggestions. For the user the switch would be a keyboard
  change in the Keyman menu, not a mode change on the keyboard. The cheap half is the `.kmn` itself
  (a key → letter table, which the table above already is) and the wordlist, which can be generated
  by running our own converter over the Latin one — inheriting the `-nje` problem above.
