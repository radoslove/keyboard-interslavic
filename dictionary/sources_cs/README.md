# Sources of the Czech wordlist (`main_cs.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_cs/cs_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/cs/cs_50k.txt
    curl -sSLfo dictionary/sources_cs/cs_CZ.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/cs_CZ/cs_CZ.dic
    curl -sSLfo dictionary/sources_cs/cs_CZ.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/cs_CZ/cs_CZ.aff
    python -m pip install spylls
    python tools/build_cs_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `cs_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell cs_CZ](https://github.com/LibreOffice/dictionaries/tree/master/cs_CZ) (261 167 stems), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL/LGPL/MPL tri-licence — lookup, no text copied |

Built 2026-11-01: **40 959 forms**; hunspell rejected **8 600** of the 50 000
(17 %: typos such as `samozřejmně`, names, English), and 441 more were not
Czech letters or were stray single letters.

A form is kept only if hunspell accepts it **as lowercase** — a word it knows
only capitalised is a name (`Praha`, `Petr`), and on a glide keyboard a name
mostly beats the word that was meant. That is the same decision as the Polish
list's "acronyms out" (`tools/build_pl_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
