# Sources of the Slovak wordlist (`main_sk.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_sk/sk_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/sk/sk_50k.txt
    curl -sSLfo dictionary/sources_sk/sk_SK.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sk_SK/sk_SK.dic
    curl -sSLfo dictionary/sources_sk/sk_SK.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sk_SK/sk_SK.aff
    python -m pip install spylls
    python tools/build_sk_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `sk_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell sk_SK](https://github.com/LibreOffice/dictionaries/tree/master/sk_SK) (sk-spell project, version 2.4.8), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL/LGPL/MPL tri-licence — lookup, no text copied |

Built 2026-11-10: **40 474 forms**; hunspell rejected **9 145** of the 50 000
(18 %), and 381 more were not Slovak letters or were stray single letters.

A form is kept only if hunspell accepts it **as lowercase**: a word it knows
only capitalised is a name, and on a glide keyboard a name mostly beats the
word that was meant (same rule as the Czech list, `tools/build_cs_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
