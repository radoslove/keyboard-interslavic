# Sources of the Spanish wordlist (`main_es.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_es/es_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/es/es_50k.txt
    curl -sSLfo dictionary/sources_es/es_ES.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.dic
    curl -sSLfo dictionary/sources_es/es_ES.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/es/es_ES.aff
    python -m pip install spylls
    python tools/build_es_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `es_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell es_ES](https://github.com/LibreOffice/dictionaries/tree/master/es), the RLA-ES Spanish dictionary, read with [spylls](https://github.com/zverok/spylls) | which forms exist, and which are proper names | GPL-3 / LGPL-3 / MPL-1.1 — lookup, no text copied |

Built 2026-11-17: **38 133 forms**, of them **453 proper names** kept with
their capital (`María`, `España`, `Londres`): the subtitle list is lowercase,
and a form hunspell knows only capitalised is a name. Hunspell rejected
11 392 more (English from subtitles, names it does not know); 475 were not
Spanish letters or stray single letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
