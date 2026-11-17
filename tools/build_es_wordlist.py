#!/usr/bin/env python3
"""Build the Spanish wordlist (`es`) from two open sources.

    python tools/build_es_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_es/README.md):

    dictionary/sources_es/es_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_es/es_ES.dic    hunspell es_ES (LibreOffice dictionaries,
    dictionary/sources_es/es_ES.aff    RLA-ES), GPL-3/LGPL-3/MPL-1.1 - LOOKUP only

Output: dictionary/main_es.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

The Slovenian recipe (tools/build_sl_wordlist.py): a form stays only if
hunspell accepts it AS LOWERCASE (names out), and its byte is the isv byte at
the same relative rank.

Spanish does not elide, so there is no hand list as in Italian. Words in
subtitles written without their accent (`esta` for `está`) are real words of
their own in most cases and stay; forms hunspell does not know go, unless
hunspell knows them CAPITALISED - then they are names and stay with the capital.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_es/es_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_es/es_ES"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_es.combined"

MIN_COUNT = 2
# The Spanish alphabet: ñ, the acute vowels and ü (pingüino).
LETTERS = set("abcdefghijklmnopqrstuvwxyzáéíóúüñ")
# Real one-letter words: a (to), e (and, before i-), o (or), u (or, before o-), y (and).
ONE_LETTER = {"a", "e", "o", "u", "y"}


def main():
    for p in (FREQ, ISV, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_es/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))

    words, seen = [], set()
    dropped_unknown = dropped_other = names = 0
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if word in seen:
            continue
        if not set(word) <= LETTERS or (len(word) == 1 and word not in ONE_LETTER):
            dropped_other += 1
            continue
        if not hun.lookup(word):
            # A proper name (España, María, Londres): the subtitle list is all
            # lowercase, hunspell knows the name only with its capital. Kept
            # capitalised, so nobody has to type names letter by letter.
            cap = word[:1].upper() + word[1:]
            if hun.lookup(cap) and cap not in seen:
                words.append(cap)
                seen.add(cap)
                names += 1
                continue
            dropped_unknown += 1
            continue
        words.append(word)
        seen.add(word)

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:es,locale=es,description=Español (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell es_ES),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms ({names} proper names, capitalised) -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Spanish letters / stray single letters")


if __name__ == "__main__":
    main()
