# Sources of the Russian wordlist (`main_ru.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_ru/ru_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_50k.txt
    curl -sSLfo dictionary/sources_ru/ru.dic \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/ru/index.dic
    curl -sSLfo dictionary/sources_ru/ru.aff \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/ru/index.aff
    python -m pip install spylls
    python tools/build_cyrillic_wordlist.py ru

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `ru_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell-ru](https://github.com/wooorm/dictionaries/tree/main/dictionaries/ru) by Alexander I. Lebedev, read with [spylls](https://github.com/zverok/spylls) | which forms exist | BSD-style — lookup at build time, no text copied |

The list is written in the keyboard's INTERNAL LATIN, one character per
Cyrillic letter, for the phonetic layout (ЯШЕРТЫ): `я q`, `ш w`, `ы y`, `ч h`,
`х x`, `ц c`, `й j`, the rest on their look-alike keys; the held letters are
`ё ë`, `э ê` (on `e`), `щ ŵ` (on `w`), `ж ž` (on `z`), `ь ḃ`, `ъ ḅ` (on `b`) and
`ю ü` (on `u`). `Script` turns it back into Cyrillic.

Built 2026-11-11: **41 909 forms**; hunspell rejected 6 316 of the 50 000
(13 %, mostly names from the subtitles), and 1 775 more were not pure Cyrillic
or were stray single letters.

The resulting wordlist is a derivative of FrequencyWords and is therefore
CC BY-SA 4.0.
