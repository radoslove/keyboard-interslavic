#!/usr/bin/env python3
"""Build the Romanian wordlist (`ro`) from two open sources.

    python tools/build_ro_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_ro/README.md):

    dictionary/sources_ro/ro_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_ro/ro.dic       hunspell ro (rospell, LibreOffice),
    dictionary/sources_ro/ro.aff       GPL 2 / LGPL 2.1 / MPL 1.1 - LOOKUP only

Output: dictionary/main_ro.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

Romanian writes `ș ț` with a comma below; subtitles mostly carry the old
cedilla `ş ţ` (6 088 forms against 1 015). Both are folded to the comma form
on input, so the two spellings of one word count as one.

The Spanish recipe (tools/build_es_wordlist.py): a form stays if hunspell
accepts it as lowercase; if hunspell knows it only CAPITALISED it is a proper
name (`București`, `Ion`) and stays with its capital.

Hyphens are everywhere in spoken Romanian (`s-a`, `n-am`, `într-o`,
`spune-mi`), and hunspell splits at `-`, accepting any row of words. So a
hyphenated form stays only if it is an entry of the .dic itself (5 804 are,
among them `s-a`, `într-o`), or two parts of which one is a short clitic
(`mi`, `l`, `s`...) and the other a word hunspell knows. The keyboard draws
no hyphen in the glide.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_ro/ro_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_ro/ro"
ISV = ROOT / "dictionary/main_isv.combined"
EN = ROOT / "dictionary/main_en.combined"
EN_TOP = 3000
OUT = ROOT / "dictionary/main_ro.combined"

MIN_COUNT = 2
LETTERS = set("abcdefghijklmnopqrstuvwxyzăâîșț")
# Real one-letter words: a (to / has), o (a, her), e (is), i (him), ș (and,
# colloquial `ș-a`) is not one on its own.
ONE_LETTER = {"a", "o", "e", "i"}
CEDILLA = str.maketrans("şţŞŢ", "șțȘȚ")
# The clitic pronouns and the reduced forms that attach with a hyphen.
CLITICS = {"m", "mi", "ți", "i", "l", "o", "ne", "v", "vi", "le", "li", "s",
           "n", "se", "te", "și", "a", "am", "ai", "au", "aș", "ar", "ați",
           "un", "o", "i", "nu", "ce", "că", "mă", "îl", "îi"}


def accepted(hun, word, hyphened):
    if "-" not in word:
        return bool(hun.lookup(word))
    if word in hyphened:
        return True
    parts = word.split("-")
    if len(parts) != 2 or not all(parts):
        return False
    a, b = parts
    return ((a in CLITICS and bool(hun.lookup(b))) or
            (b in CLITICS and bool(hun.lookup(a))))


def main():
    for p in (FREQ, ISV, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_ro/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))
    stems = [l.split("/")[0].strip() for l in
             Path(str(HUNSPELL) + ".dic").open(encoding="utf-8")]
    hyphened = {w for w in stems if "-" in w}
    # NOT done: taking a name from the .dic capitalised entries. hunspell ro
    # reads `românia` as a form of `românie` and `maria` as one of `a maria`,
    # but `peste` and `casa` the same way (`Peste`, `Casa` are entries too),
    # so the rule turned prepositions into names. Such names stay lowercase,
    # as `paris` in French.
    # Subtitles carry English lines: `The`, `New` are capitalised .dic
    # entries (New York) but not Romanian names to offer.
    english = set()
    for i, l in enumerate(EN.open(encoding="utf-8")):
        m = re.search(r"word=([^,]+),", l)
        if m:
            english.add(m.group(1).lower())
        if i > EN_TOP:
            break

    counts, seen = {}, set()        # form -> summed count (cedilla + comma)
    order = []
    dropped_unknown = dropped_other = names = joined = 0
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].translate(CEDILLA).lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if word in counts:
            counts[word] += count
            continue
        if word in seen:
            continue
        seen.add(word)
        core = word.replace("-", "")
        if (not core or not set(core) <= LETTERS or word.endswith("-")
                or word.startswith("-")
                or (len(word) == 1 and word not in ONE_LETTER)):
            dropped_other += 1
            continue
        cap = word[:1].upper() + word[1:]
        if not accepted(hun, word, hyphened):
            # Two letters capitalised are abbreviations, not names.
            if ("-" not in word and len(word) > 2 and hun.lookup(cap)
                    and cap not in counts and word not in english):
                counts[cap] = count
                order.append(cap)
                names += 1
                continue
            dropped_unknown += 1
            continue
        if "-" in word:
            joined += 1
        counts[word] = count
        order.append(word)

    words = sorted(order, key=lambda w: -counts[w])   # stable: ties keep list order

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:ro,locale=ro,description=Română (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell ro),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms ({names} proper names, capitalised; {joined} with a hyphen) -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Romanian letters / stray single letters")


if __name__ == "__main__":
    main()
