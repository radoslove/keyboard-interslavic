# Sources of the Croatian wordlist (`main_hr.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_hr/hr_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/hr/hr_50k.txt
    curl -sSLfo dictionary/sources_hr/hr_HR.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/hr_HR/hr_HR.dic
    curl -sSLfo dictionary/sources_hr/hr_HR.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/hr_HR/hr_HR.aff
    python -m pip install spylls
    python tools/build_hr_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `hr_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell hr_HR](https://github.com/LibreOffice/dictionaries/tree/master/hr_HR), the LibreOffice Croatian dictionary, read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL/LGPL/MPL tri-licence — lookup, no text copied |

Built 2026-11-10: **39 689 forms**; hunspell rejected **9 917** of the 50 000
(20 %), and 394 more were not Croatian letters or were stray single letters.

A form is kept only if hunspell accepts it **as lowercase**: a word it knows
only capitalised is a name, and on a glide keyboard a name mostly beats the
word that was meant (same rule as the Czech list, `tools/build_cs_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
