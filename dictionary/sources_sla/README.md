# Sources of the Proto-Slavic wordlist (`main_sla.combined`)

The download is not committed. To rebuild:

    curl -sSLfo dictionary/sources_sla/psl.jsonl \
      https://kaikki.org/dictionary/Proto-Slavic/kaikki.org-dictionary-ProtoSlavic.jsonl
    python3 tools/build_sla_wordlist.py
    python3 build_swipe_dictionary.py sla

| Source | What it gives | License |
|---|---|---|
| [kaikki.org](https://kaikki.org/dictionary/Proto-Slavic/) wiktextract dump of the Proto-Slavic reconstructions on [English Wiktionary](https://en.wiktionary.org/wiki/Category:Proto-Slavic_lemmas) (51.7 MB, dump of 2026-10-30) | lemmas, full inflection tables, descendants | CC BY-SA 4.0 / GFDL |

There is no corpus of a reconstructed language, so there is no frequency.
A lemma is ranked by how many Slavic languages kept it (distinct descendant
language codes); an inflected form gets its lemma's weight minus 40.
Tone marks are stripped (`rǫkà` -> `rǫka`); the letters stay.

The list is in the keyboard's internal Latin: `ъ` is `q` and `ь` is `f`,
their keys on the layout; `Script` shows them as `ъ ь`.

Built 2026-11-11: **4 939 lemmas, 90 760 forms**; 99 entries were dropped for
letters outside the alphabet (syllabic `r̥`, stray Cyrillic).

The resulting wordlist is a derivative of Wiktionary and is therefore
CC BY-SA 4.0.
