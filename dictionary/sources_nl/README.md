# Sources of the Dutch wordlist (`main_nl.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_nl/nl_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/nl/nl_50k.txt
    curl -sSLfo dictionary/sources_nl/nl.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/nl_NL/nl_NL.dic
    curl -sSLfo dictionary/sources_nl/nl.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/nl_NL/nl_NL.aff
    python -m pip install spylls
    python tools/build_nl_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `nl_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell nl](https://github.com/LibreOffice/dictionaries/tree/master/nl_NL) (OpenTaal), read with [spylls](https://github.com/zverok/spylls) | which forms exist, which are proper names, which apostrophe forms are words | BSD-3-Clause and/or CC BY 3.0 — lookup, no text copied |

Built 2026-11-17: **41 346 forms**, of them **4 141 proper names** kept with
their capital (`Amsterdam`, `België`, `IJsland` - the digraph `ij` is
capitalised whole) and **576 forms with an apostrophe** (`auto's`, `foto's`,
`zo'n`). The subtitle tokeniser cut every word at the apostrophe, so those are
rebuilt: a form ending in a vowel + `'s`, kept if hunspell accepts it, at a
twentieth of the base count. Two-letter capitalised forms (`Dr`, `He`) are
abbreviations and symbols and are left out. Hyphenated forms (`e-mail`) need
no rule of their own: the Dutch hunspell has `BREAK 0` and accepts them only
whole. Hunspell rejected 8 732 more; 498 were not Dutch letters or stray
single letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
