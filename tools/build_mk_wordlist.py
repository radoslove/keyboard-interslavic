#!/usr/bin/env python3
"""Build the Macedonian wordlist (`mk`) from two open sources.

    python tools/build_mk_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_mk/README.md):

    dictionary/sources_mk/mk_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_mk/mk_MK.dic    hunspell mk (Dimitrij Mijoski, GPL-3)
    dictionary/sources_mk/mk_MK.aff    - used as a LOOKUP, nothing copied out

Output: dictionary/main_mk.combined, in the keyboard's INTERNAL LATIN.

Macedonian is the first language here with no Latin orthography of its own, so
it is also the first whose wordlist is transliterated at build time. Everything
inside the keyboard stays Latin - the wordlist, the glide decoder, the ranking,
the learned touch offset - and only the key labels and the committed text are
turned into Cyrillic (`Script`), exactly as for Interslavic and Serbian. That
is the cheap half of the owner's decision of 2026-11-10: convert by rule into
an internal Latin instead of teaching the dictionary about scripts.

The rule has to be REVERSIBLE, and the obvious transliteration is not:
`љ њ џ` are usually written `lj nj dž`, and Macedonian also spells those
sequences as two letters - `илјада` (thousand), `волја`, `инјекција`,
`надживее`. Measured on the frequency list: 49 forms in 50 000 (0.098 %) would
convert back wrongly, and one of them is the word for "thousand". So each of
those three letters gets a SINGLE Latin character of its own (`ǉ ǌ ǆ`,
Unicode's own digraph letters) and the pair stays the pair. The keyboard folds
`ǉ ǌ ǆ` onto the `q w x` keys, where `љ њ џ` sit in every Cyrillic layout of
this family, so the grid is unchanged and nothing has to learn a digraph rule.

The same reasoning gives `ѓ ќ ѕ` their own letters (`ǵ ḱ ẑ`, as in the
scientific transliteration), each folding onto the key it is held on.

⚠ The frequency list carries forms written with MIXED alphabets - a Cyrillic
word with a Latin `e` or `o` inside, which looks identical on screen. 551 such
pairs collide in the list. They are dropped here: a form must be pure Cyrillic.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_mk/mk_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_mk/mk_MK"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_mk.combined"

MIN_COUNT = 2

# Cyrillic -> the keyboard's internal Latin. One character per letter, so the
# mapping is bijective and `Script` can turn it back without guessing.
TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "ѓ": "ǵ", "е": "e",
    "ж": "ž", "з": "z", "ѕ": "ẑ", "и": "i", "ј": "j", "к": "k", "л": "l",
    "љ": "ǉ", "м": "m", "н": "n", "њ": "ǌ", "о": "o", "п": "p", "р": "r",
    "с": "s", "т": "t", "ќ": "ḱ", "у": "u", "ф": "f", "х": "h", "ц": "c",
    "ч": "č", "џ": "ǆ", "ш": "š",
}
CYRILLIC = set(TRANSLIT)
# The one-letter words that are real (prepositions and the conjunction `и`).
ONE_LETTER = {"а", "в", "и", "о", "с", "у"}


def translit(word):
    return "".join(TRANSLIT[c] for c in word)


def main():
    for p in (FREQ, ISV, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_mk/README.md)")
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
        # Pure Cyrillic only: a mixed-alphabet form is a typo that looks right.
        if not set(word) <= CYRILLIC or (len(word) == 1 and word not in ONE_LETTER):
            dropped_other += 1
            continue
        if not hun.lookup(word):
            dropped_unknown += 1
            continue
        words.append(translit(word))

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:mk,locale=mk,description=Makedonski (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell mk; internal Latin),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not pure Cyrillic / stray single letters")


if __name__ == "__main__":
    main()
