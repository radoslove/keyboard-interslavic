# Sources of the German wordlist (`main_de.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_de/de_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/de/de_50k.txt
    curl -sSLfo dictionary/sources_de/de_DE_frami.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/de/de_DE_frami.dic
    curl -sSLfo dictionary/sources_de/de_DE_frami.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/de/de_DE_frami.aff
    cd dictionary/sources_de
    for n in deu_news_2023_1M deu-de_web_2021_300K; do
      curl -sSLfo $n.tar.gz https://downloads.wortschatz-leipzig.de/corpora/$n.tar.gz
      tar -xzf $n.tar.gz $n/$n-words.txt $n/$n-sentences.txt
    done
    cd ../..
    python -m pip install spylls
    python tools/build_freq_wordlist.py de

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `de_50k` (OpenSubtitles) | the spoken core: word forms and frequencies | CC BY-SA 4.0 |
| [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download), `deu_news_2023_1M` and `deu-de_web_2021_300K` | the words of the news, offices and shops the subtitles lack; and, because it keeps the case, which forms are nouns | CC BY 4.0 |
| [hunspell de_DE_frami](https://github.com/LibreOffice/dictionaries/tree/master/de), read with [spylls](https://github.com/zverok/spylls) | which forms exist, and which are written with a capital | GNU GPL 2 or 3 — lookup, no text copied |
| `dictionary/de_extra.txt` | a handful of real words (each in Duden) neither source brings in: `Homeoffice`, `WLAN`, `rausnehmen` … | this repository |

Built 2026-11-15: **102 691 forms** = 39 258 from the subtitles, 3 428
capitalised twins (`Leben` next to `leben`), 60 000 new forms from Leipzig,
5 by hand.

1. The subtitle list is all lowercase. Every form goes through hunspell: accepted
   lowercase → kept lowercase; accepted only with a capital → a noun (`Haus`);
   accepted neither way → left out (names and English from the subtitles). Unlike
   the Czech and Slovak lists, a form known only capitalised is NOT dropped as a
   name, because in German that is every noun.
2. hunspell accepts a capital on every word (sentence start), so it cannot tell
   `leben` from `Leben`. A lowercase form gets a capitalised twin when the
   Leipzig sentences write it capitalised at least 30 % of the time INSIDE a
   sentence (the first word of a sentence is not counted). A short list of
   interjections (`nein`, `hallo`, `oh` …) never gets one, because the news
   writes them as nouns (`ein klares Nein`) and people do not.
3. Forms neither list has are added from Leipzig when hunspell accepts them and
   they occur at least 5 times, ranked below every subtitle word.

The resulting wordlist is a derivative of FrequencyWords and the Leipzig
corpora and is therefore distributed under **CC BY-SA 4.0**, not MIT.
