# Sources of the Bosnian wordlist (`main_bs.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_bs/bs_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/bs/bs_50k.txt
    curl -sSLfo dictionary/sources_bs/bs_BA.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/bs_BA/bs_BA.dic
    curl -sSLfo dictionary/sources_bs/bs_BA.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/bs_BA/bs_BA.aff
    python -m pip install spylls
    python tools/build_bs_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `bs_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell bs_BA](https://github.com/LibreOffice/dictionaries/tree/master/bs_BA), the LibreOffice Bosnian dictionary, read with [spylls](https://github.com/zverok/spylls) | which forms exist | LGPL/MPL/GPL — lookup, no text copied |

Built 2026-11-10: **33 244 forms**; hunspell rejected **16 396** of the 50 000
(33 %), and 360 more were not Bosnian letters or were stray single letters.

⚠ That rejection rate is twice the Serbian one (15 %) and the reason is the
dictionary, not the subtitles: `bs_BA.dic` carries 30 542 stems, so it simply
knows fewer words than its Croatian and Serbian neighbours. The forms that
survive are real; the ones missing are mostly inflections the affix rules do not
reach. If the list turns out too thin in use, the fix is a better Bosnian
dictionary, not a looser filter — the filter is what keeps names and typos off a
glide keyboard.

A form is kept only if hunspell accepts it **as lowercase**: a word it knows
only capitalised is a name, and on a glide keyboard a name mostly beats the word
that was meant (same rule as the Czech list, `tools/build_cs_wordlist.py`).

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
