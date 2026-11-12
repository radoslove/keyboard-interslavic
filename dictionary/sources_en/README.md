# Sources of the English and Norwegian wordlists (`main_en.combined`, `main_nb.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_en/en_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt
    curl -sSLfo dictionary/sources_nb/no_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/no/no_50k.txt
    # English only: drop subtitle names and noise (eeyore, tthe, hh) first
    pip install spylls; npm pack dictionary-en@4.0.0 dictionary-en-gb@3.0.0   # untar both
    python tools/build_en_filter.py <dictionary-en/package> <dictionary-en-gb/package>
    python tools/build_freq_wordlist.py en
    python tools/build_freq_wordlist.py nb

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `en_50k`, `no_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [dictionary-en](https://github.com/wooorm/dictionaries) / dictionary-en-gb (hunspell, SCOWL) | which English forms to leave out (`en_rejected.txt`) | MIT AND BSD; only the rejected forms are written |
| [Bokmålsordboka](https://ord.uib.no) lookups (Norwegian only, via the Interscandinavian repo) | which forms to leave out | lookup, no text copied |

The resulting wordlists are a derivative of FrequencyWords and are therefore
distributed under **CC BY-SA 4.0**, not MIT.
