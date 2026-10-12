#!/usr/bin/env python3
"""Build the Polish wordlist for the second keyboard (`pl`) from two open sources.

    python3 tools/build_pl_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_pl/README.md):

    dictionary/sources_pl/pl_full.txt            FrequencyWords 2018, OpenSubtitles
                                                 (CC BY-SA 4.0), "word count" per line
    dictionary/sources_pl/PoliMorf-0.6.7.tab.gz  PoliMorf (BSD-2), form<TAB>lemma<TAB>tag...

Output: dictionary/main_pl.combined, the same `word=…,f=…` format as
main_isv.combined, so build_swipe_dictionary.py reads either.

A form is kept only if PoliMorf knows it - subtitles are full of typos, names
and English, and the morphological dictionary is the filter. Its spelling
(case) comes from PoliMorf: `polska` and `Polska` are both real, `warszawa` is
not. Acronyms (two capitals or more) are left out: on a swipe keyboard they
only ever beat the word that was meant (`SSSR` for `se` on isv).

Frequency: subtitle counts span seven orders of magnitude, the decoder was tuned
on the isv byte distribution (median 60, p90 109). So the byte is not scaled
from the count but taken from the SAME QUANTILE of the isv distribution: the
Polish word at rank r gets the frequency byte the isv word at that relative
rank has. One weight then means the same thing in both keyboards.
"""

import gzip
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_pl/pl_full.txt"
MORF = ROOT / "dictionary/sources_pl/PoliMorf-0.6.7.tab.gz"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_pl.combined"

MIN_COUNT = 2
LETTERS = set("aąbcćdeęfghijklłmnńoóprsśtuwyzźżqvx")


def main():
    for p in (FREQ, MORF, ISV):
        if not p.exists():
            sys.exit(f"missing input: {p}")

    # lowercase form -> the spellings PoliMorf has for it
    spellings = {}
    with gzip.open(MORF, "rt", encoding="utf-8") as f:
        for line in f:
            form = line.split("\t", 1)[0]
            if " " in form or not form:
                continue
            spellings.setdefault(form.lower(), set()).add(form)

    words = []
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if not set(word) <= LETTERS:
            continue
        forms = spellings.get(word)
        if not forms:
            continue
        form = word if word in forms else sorted(forms)[0]
        if sum(ch.isupper() for ch in form) >= 2:
            continue
        words.append(form)

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    with OUT.open("w", encoding="utf-8") as out:
        out.write("dictionary=main:pl,locale=pl,description=Polski (FrequencyWords "
                  "CC BY-SA 4.0 + PoliMorf BSD-2),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms -> {OUT}")


if __name__ == "__main__":
    main()
