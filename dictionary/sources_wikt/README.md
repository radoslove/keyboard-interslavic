# Source of the try-box gloss (`assets/gloss_<lang>.tsv`)

The download is not committed. To rebuild:

    curl -sSLfo dictionary/sources_wikt/pl-extract.jsonl.gz \
      https://kaikki.org/dictionary/downloads/pl/pl-extract.jsonl.gz
    python tools/build_gloss.py

| Source | What it gives | License |
|---|---|---|
| [Polish Wiktionary](https://pl.wiktionary.org) as extracted by [wiktextract](https://github.com/tatuylonen/wiktextract) ([kaikki.org](https://kaikki.org/plwiktionary/)) | Polish definitions of words in other languages (`Haus` -> `dom`), translation tables of Polish entries, inflection tables | CC BY-SA 4.0 (and GFDL) |

Built 2026-11-15 from the extract of 2026-10-30: one line per entry (its forms,
a tab, the gloss); Polish kept to the 15 000 entries whose forms the Polish
keyboard list ranks highest, English to 20 000. The gloss files are a derivative
of Wiktionary and are distributed under **CC BY-SA 4.0**, not MIT; the app names
the source under the gloss.
