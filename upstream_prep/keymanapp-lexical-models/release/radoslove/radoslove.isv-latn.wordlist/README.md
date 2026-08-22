Medžuslovjansky slovnik — Interslavic lexical model
===================================================

Description
-----------
Word prediction and autocorrect for **Interslavic** (medžuslovjansky, `isv`),
standard orthography.

39,777 **inflected forms**, not lemmas. The distinction matters: you type
`slovami`, not `slovo`, so a lemma-only model predicts almost nothing useful in
a language this heavily inflected.

Source and trimming
-------------------
Derived from the same wordlist as the Interslavic HeliBoard swipe dictionary —
248,845 forms generated from the medzuslove dictionary database.

The full list compiles to a 33 MB trie, which is well past what an iOS keyboard
*extension* can hold, so this model is cut to forms with frequency ≥ 100
(~5 MB). Vocabulary specific to Interslavic itself is kept regardless of
frequency: the underlying corpus scores `medžuslovjansky` and `slovjansky` at
f=26 and `tipkovnica` at 99, so a plain cut-off would have dropped precisely the
words its users type most.

Orthography
-----------
Standard only — `č š ž ě`. Forms using the extended alphabet are excluded, so
the suggestion bar never proposes a spelling the standard does not use. This
matches the companion keyboard `isv_latin`.

Links
-----
- Keyboard and full sources: https://github.com/radoslove/keyboard-interslavic
- Companion keyboard: `isv_latin` (Medžuslovjansky (latinica))

Copyright
---------
Model source and packaging: MIT (see [LICENSE.md](LICENSE.md)). The bundled
wordlist is word **forms + corpus frequencies only** (no definitions or
translations), machine-generated from a lemma list compiled from various
Interslavic lexical resources — **no ownership of the underlying
lexical data is claimed.** See LICENSE.md for the full scope note.
