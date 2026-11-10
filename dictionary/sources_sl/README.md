# Sources of the Slovenian wordlist (`main_sl.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_sl/sl_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/sl/sl_50k.txt
    curl -sSLfo dictionary/sources_sl/sl_SI.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sl_SI/sl_SI.dic
    curl -sSLfo dictionary/sources_sl/sl_SI.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/sl_SI/sl_SI.aff
    python -m pip install spylls
    python tools/build_sl_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `sl_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell sl_SI](https://github.com/LibreOffice/dictionaries/tree/master/sl_SI), the LibreOffice Slovenian dictionary (Amebis, T. Erjavec, A. Košir, P. Peterlin), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL/LGPL — lookup, no text copied |

Built 2026-11-10: **40 725 forms**; hunspell rejected **8 992** of the 50 000
(18 %), and 283 more were not Slovenian letters or were stray single letters.

A form is kept only if hunspell accepts it **as lowercase**: a word it knows
only capitalised is a name, and on a glide keyboard a name mostly beats the
word that was meant (same rule as the Czech list, `tools/build_cs_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
