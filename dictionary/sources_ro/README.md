# Sources of the Romanian wordlist (`main_ro.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_ro/ro_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ro/ro_50k.txt
    curl -sSLfo dictionary/sources_ro/ro.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ro/ro_RO.dic
    curl -sSLfo dictionary/sources_ro/ro.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ro/ro_RO.aff
    python -m pip install spylls
    python tools/build_ro_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `ro_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell ro](https://github.com/LibreOffice/dictionaries/tree/master/ro) (rospell), read with [spylls](https://github.com/zverok/spylls) | which forms exist, which are proper names, which hyphenated forms are words | GPL 2 / LGPL 2.1 / MPL 1.1 — lookup, no text copied |
| `dictionary/main_en.combined` (first 3 000 forms) | English words to keep out of the names (`The`, `New`) | this repository |

Built 2026-11-17: **36 708 forms**, of them **1 678 proper names** kept with
their capital (`Europa`, `Londra`, `Franța`) and **1 559 with a hyphen**
(`s-a`, `n-am`, `într-o`, `spune-mi`). The subtitles write `ș ț` mostly with
the old cedilla (`ş ţ`, 6 088 forms against 1 015); both spellings are folded
to the comma below and counted together. hunspell splits at `-` and accepts
any row of words, so a hyphenated form stays only if it is an entry of
`ro.dic` itself or a word joined to a short clitic (`mi`, `l`, `s`...).
Some names stay lowercase: hunspell reads `românia` and `maria` as ordinary
inflected forms, the same way as `peste` and `casa`, so they cannot be told
apart by the dictionary. Hunspell rejected 11 862 forms (mostly spellings
without diacritics, `si`, `esti`, `daca`); 424 were not Romanian letters or
stray single letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
