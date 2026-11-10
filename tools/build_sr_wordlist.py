#!/usr/bin/env python3
"""Build the Serbian (Latin) wordlist (`sr`) from two open sources.

    python tools/build_sr_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_sr/README.md):

    dictionary/sources_sr/sr_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_sr/sr-Latn.dic    hunspell sr-Latn (LibreOffice dictionaries),
    dictionary/sources_sr/sr-Latn.aff    GPL/LGPL/MPL tri-licence - used as a LOOKUP

Output: dictionary/main_sr.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

Why a second source at all: subtitles are full of typos, names and English, and
Czech showed it (`samozrejmne` for `samozrejme` near the top). Serbian
follows the Czech and Slovak recipe (tools/build_cs_wordlist.py) and gets the
LibreOffice hunspell dictionary sr-Latn, read through `spylls` (pure-Python hunspell, MIT),
which applies the real affix rules instead of us guessing them. Nothing is
copied out of it - it only answers "is this a Serbian word", the same arrangement
as the Bokmalsordboka lookups behind the Norwegian list.

A form is kept only if hunspell accepts it AS LOWERCASE. That is deliberate:
a word hunspell only knows capitalised is a name (`Beograd`, `Marko`), and on a
glide keyboard a name mostly beats the word that was meant. Acronyms go out for
the same reason (`SSSR` beat `se` on isv).

Frequency: subtitle counts span seven orders of magnitude, and the decoder was
tuned on the isv byte distribution. So the byte is not scaled from the count but
taken from the SAME QUANTILE of the isv distribution - the Serbian word at rank r
gets the byte the isv word at that relative rank has, so one weight means the
same thing in every keyboard.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_sr/sr_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_sr/sr-Latn"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_sr.combined"

MIN_COUNT = 2
# The Serbian Latin alphabet (dž lj nj are written as two letters), plus
# q/w/x/y of loanwords.
LETTERS = set("abcčćdđefghijklmnopqrsštuvwxyzž")
# Subtitle lists carry every letter as a "word"; keep the real one-letter ones
# (the prepositions, and `a` = and).
ONE_LETTER = {"a", "i", "k", "o", "s", "u", "v", "z"}


def main():
    for p in (FREQ, ISV, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_sr/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))

    words, dropped_unknown, dropped_other = [], 0, 0
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if not set(word) <= LETTERS or (len(word) == 1 and word not in ONE_LETTER):
            dropped_other += 1
            continue
        if not hun.lookup(word):
            dropped_unknown += 1
            continue
        words.append(word)

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:sr,locale=sr,description=Srpski latinica (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell sr-Latn),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Serbian letters / stray single letters")


if __name__ == "__main__":
    main()
