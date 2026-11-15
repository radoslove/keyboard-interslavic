# Sources of the Italian wordlist (`main_it.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_it/it_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/it/it_50k.txt
    curl -sSLfo dictionary/sources_it/it_IT.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/it_IT/it_IT.dic
    curl -sSLfo dictionary/sources_it/it_IT.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/it_IT/it_IT.aff
    python -m pip install spylls
    python tools/build_it_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `it_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell it_IT](https://github.com/LibreOffice/dictionaries/tree/master/it_IT), the LibreOffice Italian dictionary (G. Turconi, D. Prina, A. Pescetti, LibreItalia), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL-3 — lookup, no text copied |
| `dictionary/it_elisioni.txt` (this repo) | the elided forms kept (`dell'`, `quest'`), hand-checked | MIT |

Built 2026-11-15: **37 244 forms**; hunspell rejected **11 828** of the 50 000
(24 %: English and names from subtitles, and accents typed as an apostrophe -
`piu'`, `perche'`), 928 more were not Italian letters or stray single letters.

The tokeniser cut `dell'anno` at the apostrophe, so elided forms arrive as
words of their own and hunspell knows none of them; they come from the hand
list instead, and the keyboard writes the next word straight after them.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
