# Sources of the Ukrainian wordlist (`main_uk.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_uk/uk_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/uk/uk_50k.txt
    curl -sSLfo dictionary/sources_uk/uk.dic \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/uk/index.dic
    curl -sSLfo dictionary/sources_uk/uk.aff \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/uk/index.aff
    python -m pip install spylls
    python tools/build_cyrillic_wordlist.py uk
    python3 build_swipe_dictionary.py uk

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `uk_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell-uk](https://github.com/wooorm/dictionaries/tree/main/dictionaries/uk) from [brown-uk/dict_uk](https://github.com/brown-uk/dict_uk), read with [spylls](https://github.com/zverok/spylls) | which forms exist; the stems before an apostrophe | GPL-3.0 - lookup at build time, no text copied |

The list is in the keyboard's INTERNAL LATIN, one character per letter, for
the homophonic layout (xkeyboard-config `ua(homophonic)`): `я q`, `ш w`,
`и y`, `і i`, `ґ g`, `г h`, `х x`, `ц c`, `в v`, the rest on their look-alike
keys; held: `ч č` (on `c`), `ж ž` (`z`), `ю ü` (`u`), `щ ŵ` (`w`), `є ê`
(`e`), `ї ï` (`i`), `ь ḃ` (`b`). The apostrophe stays `'` in the word and has
no key: the glide skips it.

The subtitle list has no apostrophe at all - its tokenizer split `п'ять` into
`п` and `ять`. Those forms are recovered by gluing a hunspell stem to a list
word starting with я ю є ї, kept if hunspell accepts the whole.

Built 2026-11-11: **29 209 forms**, 2 143 of them with an apostrophe (2 221
recovered); hunspell rejected 17 247 of the 50 000 (mostly Russian lines in
the Ukrainian subtitles), 5 765 more were not pure Cyrillic or stray letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
CC BY-SA 4.0.
