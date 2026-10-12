# Sources of the Polish wordlist (`main_pl.combined`, `pl_swipe.bin`)

The downloads are not committed (57 MB). To rebuild:

    curl -sSLfo dictionary/sources_pl/pl_full.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/pl/pl_full.txt
    curl -sSLfo dictionary/sources_pl/PoliMorf-0.6.7.tab.gz \
      "https://zil.ipipan.waw.pl/PoliMorf?action=AttachFile&do=get&target=PoliMorf-0.6.7.tab.gz"
    python3 tools/build_pl_wordlist.py
    python3 build_swipe_dictionary.py pl

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `pl_full` (OpenSubtitles) | word frequencies | CC BY-SA 4.0 |
| [PoliMorf](https://zil.ipipan.waw.pl/PoliMorf) 0.6.7 (SGJP + Morfologik) | which forms exist, their spelling | BSD 2-Clause |

The resulting wordlist and binary are a derivative of FrequencyWords and are
therefore distributed under **CC BY-SA 4.0**, not MIT.
