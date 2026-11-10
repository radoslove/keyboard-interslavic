# Sources of the Serbian (Latin) wordlist (`main_sr.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_sr/sr_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/sr/sr_50k.txt
    curl -sSLfo dictionary/sources_sr/sr-Latn.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sr/sr-Latn.dic
    curl -sSLfo dictionary/sources_sr/sr-Latn.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sr/sr-Latn.aff
    python -m pip install spylls
    python tools/build_sr_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `sr_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell sr-Latn](https://github.com/LibreOffice/dictionaries/tree/master/sr), the LibreOffice Serbian dictionary, Latin-script files, read with [spylls](https://github.com/zverok/spylls) | which forms exist | LGPL/MPL/GPL — lookup, no text copied |

Built 2026-11-10: **39 267 forms**; hunspell rejected **7 309** of the 50 000
(15 %), and 3 424 more were not Serbian Latin (mostly Cyrillic forms in the subtitles) letters or were stray single letters.

A form is kept only if hunspell accepts it **as lowercase**: a word it knows
only capitalised is a name, and on a glide keyboard a name mostly beats the
word that was meant (same rule as the Czech list, `tools/build_cs_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
