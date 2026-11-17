# Sources of the French wordlist (`main_fr.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_fr/fr_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/fr/fr_50k.txt
    curl -sSLfo dictionary/sources_fr/fr.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/fr_FR/dictionaries/fr.dic
    curl -sSLfo dictionary/sources_fr/fr.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/fr_FR/dictionaries/fr.aff
    python -m pip install spylls
    python tools/build_fr_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `fr_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell fr](https://github.com/LibreOffice/dictionaries/tree/master/fr_FR) version 7.7 (Grammalecte), read with [spylls](https://github.com/zverok/spylls) | which forms exist, which are proper names, which hyphenated forms are words | MPL 2.0 — lookup, no text copied |
| `dictionary/fr_elisions.txt` | the elided forms (`l'`, `qu'`, `jusqu'`) | this repository |

Built 2026-11-17: **39 519 forms**, of them **2 546 proper names** kept with
their capital (`Paris`, `Marie`) and **2 215 with an apostrophe or a hyphen**
(`aujourd'hui`, `peut-être`, `avez-vous`, `a-t-il`). hunspell splits at `-`
and accepts any row of words, so a hyphenated form stays only if it is an
entry of `fr.dic` itself or a verb followed by its pronoun; that keeps
`est-ce-que` and `y-a-t-il` out. Hunspell rejected 9 833 more; 648 were not
French letters, stray single letters or halves cut at an apostrophe.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
