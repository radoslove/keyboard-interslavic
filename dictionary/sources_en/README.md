# Sources of the English and Norwegian wordlists (`main_en.combined`, `main_nb.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_en/en_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt
    curl -sSLfo dictionary/sources_nb/no_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/no/no_50k.txt
    python tools/build_freq_wordlist.py en
    python tools/build_freq_wordlist.py nb

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `en_50k`, `no_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [Bokmålsordboka](https://ord.uib.no) lookups (Norwegian only, via the Interscandinavian repo) | which forms to leave out | lookup, no text copied |

The resulting wordlists are a derivative of FrequencyWords and are therefore
distributed under **CC BY-SA 4.0**, not MIT.
