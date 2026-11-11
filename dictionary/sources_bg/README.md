# Sources of the Bulgarian wordlist (`main_bg.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_bg/bg_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/bg/bg_50k.txt
    curl -sSLfo dictionary/sources_bg/bg.dic \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/bg/index.dic
    curl -sSLfo dictionary/sources_bg/bg.aff \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/bg/index.aff
    python -m pip install spylls
    python tools/build_cyrillic_wordlist.py bg
    python3 build_swipe_dictionary.py bg

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `bg_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell-bg](https://github.com/wooorm/dictionaries/tree/main/dictionaries/bg) from [bgOffice](http://bgoffice.sourceforge.net), read with [spylls](https://github.com/zverok/spylls) | which forms exist | GPL-2.0 OR LGPL-2.1 OR MPL-1.1 - lookup at build time, no text copied |

The list is in the keyboard's INTERNAL LATIN, one character per letter, for
the traditional phonetic layout ЯВЕРТЪ (xkeyboard-config `bg(phonetic)`):
`я q`, `в w`, `ъ y`, `ь x`, `ж v`, `х h`, `ц c`, the rest on their look-alike
keys; held: `ш š` and `щ ŝ` (on `s`), `ч č` (`c`), `ю ü` (`u`), `ѝ ì` (`i`).

Built 2026-11-11: **40 453 forms**; hunspell rejected 7 933 of the 50 000,
1 614 more were not pure Cyrillic or stray single letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
CC BY-SA 4.0.
