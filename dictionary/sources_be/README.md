# Sources of the Belarusian wordlist (`main_be.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_be/bel_wikipedia_2021_300K.tar.gz \
      https://downloads.wortschatz-leipzig.de/corpora/bel_wikipedia_2021_300K.tar.gz
    curl -sSLfo dictionary/sources_be/bel_news_2020_100K.tar.gz \
      https://downloads.wortschatz-leipzig.de/corpora/bel_news_2020_100K.tar.gz
    cd dictionary/sources_be
    tar -xzf bel_wikipedia_2021_300K.tar.gz bel_wikipedia_2021_300K/bel_wikipedia_2021_300K-words.txt
    tar -xzf bel_news_2020_100K.tar.gz bel_news_2020_100K/bel_news_2020_100K-words.txt
    cd ../..
    curl -sSLfo dictionary/sources_be/be.dic \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/be_BY/be-official.dic
    curl -sSLfo dictionary/sources_be/be.aff \
      https://raw.githubusercontent.com/LibreOffice/dictionaries/master/be_BY/be-official.aff
    python -m pip install spylls
    python tools/build_cyrillic_wordlist.py be
    python3 build_swipe_dictionary.py be

| Source | What it gives | License |
|---|---|---|
| [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download), `bel_wikipedia_2021_300K` and `bel_news_2020_100K` (`*-words.txt`) | word forms and frequencies; each corpus scaled to the same total, case and apostrophe variants merged, top 50 000 | CC BY 4.0 ("The text corpora offered for download are made available under the Creative Commons licence CC BY", [Terms of Usage](https://wortschatz.uni-leipzig.de/en/usage), read 2026-11-11) |
| [hunspell be_BY](https://github.com/LibreOffice/dictionaries/tree/master/be_BY) (official orthography 2008, from the Belarusian Grammar Database, bnkorpus.info), read with [spylls](https://github.com/zverok/spylls) | which forms exist | CC BY-SA 4.0 or LGPLv3 - lookup at build time, no text copied |

FrequencyWords (the source of `ru`, `uk`, `bg`) has no Belarusian list in
2016 or 2018, and wooorm/dictionaries has no `be` (both 404, 2026-11-11).

The list is in the keyboard's INTERNAL LATIN, one character per letter, for
the published phonetic layout xkeyboard-config `by(phonetic)` (ЯВЕРТЫ with
`і` on `i` and `ў` in place of `щ`; owner's choice 2026-11-11): `я q`, `в w`,
`ы y`, `і i`, `х h`, `ь x`, `ж v`, `ц c`, the rest on their look-alike keys;
held: `ш š` (on `s`), `ч č` (`c`), `ў ŭ` and `ю ü` (`u`), `э ê` and `ё ë` (`e`).
The apostrophe (`сям'я`, `аб'ём`) stays in the word and is skipped by the
glide, as in Ukrainian.

Measured 2026-11-11, five seeds, the same list in three arrangements:
A `by(phonetic)` top-1 85.0 % / top-3 96.1 %, B (our Russian ЯШЕРТЫ with `і`)
87.5 / 97.5, C (B with `ў` on `w`) 87.1 / 96.5. The owner chose A, the
published one.

Built 2026-11-11: **40 279 forms** (276 with an apostrophe); hunspell
rejected 5 793 of the 50 000, 3 928 more were not pure Cyrillic or stray
single letters.

The resulting wordlist is a derivative of the Leipzig corpora and is
therefore CC BY 4.0 (attribution: Leipzig Corpora Collection, Universität
Leipzig).
