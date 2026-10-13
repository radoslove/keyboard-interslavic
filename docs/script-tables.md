# Cyrillic and Glagolitic: script tables

The keyboard can write in Latin (default), Cyrillic or Glagolitic (Android, from test build 3.5.4):
in the settings (*Latinica, kirilica ili glagolica*) and, from 3.5.5, with the script key next to 🌐
(shows ABC / АБВ / ⰀⰁⰂ, a tap switches to the next script). Letters sit on the **same keys** as in Latin
(`docs/ms-latin-table.md`): `c` writes `ц`, hold `c` for `ч`. Inside, the keyboard stays Latin
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
| q w x | q w x (unchanged) | q w x (unchanged) |

Source: the community transliterator [medzuslovjansky/js-utils](https://github.com/medzuslovjansky/js-utils)
(MIT), `packages/translit`: Cyrillic = `latn2cyrl` core restricted to the standard alphabet;
Glagolitic = the original interslavic-dictionary.com converter ("v1", ported 1:1 there as `latn2glag2`),
because that is the Glagolitic readers of the dictionary have seen. Checked against the v1 examples in
`GLAGOLITIC-DIFF.md` (`azijatsky` → `ⰰⰸⰻⱝⱅⱄⰽⱐⰹ`, `abhazskom` → `ⰰⰱⱈⰰⰸⱄⰽⱁⱞ`, `-dnja` → `ⰴⱀⱝ`, `basnje-` → `ⱀⰹⰵ`).

## Resolved conflicts

| Question | Decision | Why |
|---|---|---|
| Reuse the removed `KBDMSKIR` Cyrillic layout (© 2012 R. Lombino jr.)? | No. Own layout, letters on their Latin keys | the upstream files were removed on 2026-10-07 for licensing; same positions also let swipe reuse the Latin decoder unchanged |
| `dž` in Cyrillic | `дж` (two letters), not `џ` | js-utils core has no `dž` rule; Latin `dž` is also two letters |
| Which Glagolitic converter | v1 (`m → ⱞ`, `y → ⱐⰹ`, `ja → ⱝ`), not the newer `latn2glag` (`ⰿ`, `ⱏⰹ`, `ⰹⰰ`) | v1 is what the dictionary shows; the two disagree on 55–77 % of forms |

## Open questions

- **Glagolitic is not settled in the community** (two converters, `GLAGOLITIC-DIFF.md`); this keyboard
  follows v1. Not reproduced: v1's stray `ⱏ` inside `zsk`, and Glagolitic punctuation.
- **Cyrillic key layouts (future).** Today the Cyrillic letters sit on their Latin keys. People who
  already type Cyrillic are used to national layouts (ЙЦУКЕН, the Serbian/Macedonian layout, the 2012
  Interslavic `KBDMSKIR`); offer them as an option later. Swipe then needs the key positions of that
  layout in the decoder, not the Latin ones.
- `q w x` have no letter in either script; they stay Latin for foreign words.
- Glagolitic needs a font on the phone. Most Android phones ship Noto Sans Glagolitic; if keys
  show boxes, the font must be bundled (Noto Sans Glagolitic, OFL).
- iOS app and Keyman package: not yet.
