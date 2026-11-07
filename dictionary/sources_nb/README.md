# Sources of the Norwegian bokmål wordlist (`main_nb.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_nb/no_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/no/no_50k.txt
    curl -sSLfo dictionary/sources_nb/nb_NO.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/no/nb_NO.dic
    curl -sSLfo dictionary/sources_nb/nb_NO.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/no/nb_NO.aff
    python -m pip install spylls
    python tools/build_nb_wordlist.py
    python build_swipe_dictionary.py nb

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `no_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell nb_NO](https://github.com/LibreOffice/dictionaries/tree/master/no) (Norsk ordbank), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL — lookup, no text copied |

Built 2026-11-07: **35 835 forms** (was 49 206); hunspell rejected **13 399**
(names such as `gotham`, `dewey`, `loomis`, English such as `the`, `new`,
subtitle OCR slips such as `lkke`, `lngen`), and 766 more were other letters or
stray single letters. A form is kept only if hunspell accepts it **as
lowercase**; the genitive `-s` (`verdens`, `dagens`) is accepted on a stem
hunspell knows, because the nb_NO affix rules lack it.

Frequency bytes keep the log scale of the previous list
(`tools/build_freq_wordlist.py`): replaying real glides showed the isv-quantile
scale of the Czech builder pushes `hvordan` below `gleden`.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
