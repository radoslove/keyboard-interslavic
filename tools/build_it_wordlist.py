#!/usr/bin/env python3
"""Build the Italian wordlist (`it`) from two open sources.

    python tools/build_it_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_it/README.md):

    dictionary/sources_it/it_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_it/it_IT.dic    hunspell it_IT (LibreOffice dictionaries),
    dictionary/sources_it/it_IT.aff    GPL-3 - used as a LOOKUP only
    dictionary/it_elisioni.txt         the elided forms kept (`dell'`, `quest'`)

Output: dictionary/main_it.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

The Slovenian recipe (tools/build_sl_wordlist.py): a form stays only if
hunspell accepts it AS LOWERCASE (names out), and its byte is the isv byte at
the same relative rank.

Elision: the subtitle tokeniser cuts `dell'anno` at the apostrophe, so the
list holds `dell'` and `anno` as two words, and hunspell knows neither half of
that cut. The elided forms go through the hand-checked list instead; the
keyboard then writes the next word straight after the apostrophe. The many
`piu'` / `perche'` in subtitles are a missing accent, not an elision, and stay
out: only words that exist go into the keyboard.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_it/it_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_it/it_IT"
ELISIONS = ROOT / "dictionary/it_elisioni.txt"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_it.combined"

MIN_COUNT = 2
# The Italian alphabet with its accented vowels, plus j k w x y of loanwords.
LETTERS = set("abcdefghijklmnopqrstuvwxyzàèéìíîòóùú")
# Real one-letter words: a (to), e (and), i (the), o (or), è (is).
ONE_LETTER = {"a", "e", "i", "o", "è"}


def main():
    for p in (FREQ, ISV, ELISIONS, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_it/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))
    elisions = {l.strip() for l in ELISIONS.open(encoding="utf-8")
                if l.strip() and not l.startswith("#")}

    words, seen = [], set()
    dropped_unknown = dropped_other = 0
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if word in seen:
            continue
        if word in elisions:
            words.append(word)
            seen.add(word)
            continue
        if not set(word) <= LETTERS or (len(word) == 1 and word not in ONE_LETTER):
            dropped_other += 1
            continue
        if not hun.lookup(word):
            dropped_unknown += 1
            continue
        words.append(word)
        seen.add(word)

    missing = sorted(elisions - seen)
    if missing:
        print("elisions not in the frequency list:", " ".join(missing))

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:it,locale=it,description=Italiano (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell it_IT),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Italian letters / stray single letters")


if __name__ == "__main__":
    main()
