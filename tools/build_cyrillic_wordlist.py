#!/usr/bin/env python3
"""Build a wordlist for a Cyrillic-only language typed on the PHONETIC layout.

    python tools/build_cyrillic_wordlist.py ru

Inputs (downloaded, not committed - see dictionary/sources_<lang>/README.md):

    dictionary/sources_<lang>/<lang>_50k.txt   FrequencyWords 2018, OpenSubtitles
                                               (CC BY-SA 4.0), "word count" per line
    dictionary/sources_<lang>/<lang>.dic/.aff  hunspell - used as a LOOKUP only

Output: dictionary/main_<lang>.combined, in the keyboard's INTERNAL LATIN.

The recipe of tools/build_mk_wordlist.py (2026-11-10), generalised: the list is
transliterated once, at build time, one Latin character per Cyrillic letter, so
the mapping is bijective and `Script` turns it back without guessing. Everything
inside the keyboard - decoder, ranking, learned touch offset - stays Latin.

The phonetic layout (owner 2026-11-11) puts each letter
on the Latin key that sounds or looks like it, the arrangement Russian users know
as ЯШЕРТЫ: я q, ш w, е e, р r, т t, ы y, у u, и i, о o, п p / а s д f г ч(h) й(j)
к л / з х(x) ц(c) в(v) б н м. The letters that have no key of their own are HELD
on a neighbour, and their internal letter is a Latin letter with a mark that
folds onto that key - which is what lets the glide treat them like every held
letter of the Latin languages.

⚠ Forms in MIXED alphabets (a Cyrillic word with a Latin `e` inside, identical
on screen) are dropped: a form must be pure Cyrillic.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ISV = ROOT / "dictionary/main_isv.combined"
MIN_COUNT = 2

TABLES = {
    # Russian, ЯШЕРТЫ. Held: э ё on е, щ on ш, ж on з, ь ъ on б, ю on у.
    "ru": {
        "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "ë",
        "ж": "ž", "з": "z", "и": "i", "й": "j", "к": "k", "л": "l", "м": "m",
        "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
        "ф": "f", "х": "x", "ц": "c", "ч": "h", "ш": "w", "щ": "ŵ", "ъ": "ḅ",
        "ы": "y", "ь": "ḃ", "э": "ê", "ю": "ü", "я": "q",
    },
}
ONE_LETTER = {
    "ru": {"а", "в", "и", "к", "о", "с", "у", "я"},
}
NAMES = {"ru": "Russkij"}


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else ""
    if lang not in TABLES:
        sys.exit(f"usage: build_cyrillic_wordlist.py {{{','.join(TABLES)}}}")
    table, one = TABLES[lang], ONE_LETTER[lang]
    assert len(set(table.values())) == len(table), "transliteration must be one to one"
    src = ROOT / f"dictionary/sources_{lang}"
    freq, hunspell = src / f"{lang}_50k.txt", src / lang
    out_path = ROOT / f"dictionary/main_{lang}.combined"
    for p in (freq, ISV, Path(str(hunspell) + ".dic"), Path(str(hunspell) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see {src}/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")
    hun = Dictionary.from_files(str(hunspell))
    letters = set(table)

    words, seen, unknown, other = [], set(), 0, 0
    for line in freq.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if not set(word) <= letters or (len(word) == 1 and word not in one):
            other += 1
            continue
        if word in seen:
            continue
        if not hun.lookup(word):
            unknown += 1
            continue
        seen.add(word)
        words.append("".join(table[c] for c in word))

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with out_path.open("w", encoding="utf-8", newline="\n") as out:
        out.write(f"dictionary=main:{lang},locale={lang},description={NAMES[lang]} "
                  f"(FrequencyWords CC BY-SA 4.0 + hunspell {lang}; internal Latin),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")
    print(f"{n} forms -> {out_path}")
    print(f"dropped: {unknown} hunspell does not know, {other} not pure Cyrillic / stray single letters")


if __name__ == "__main__":
    main()
