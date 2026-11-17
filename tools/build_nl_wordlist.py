#!/usr/bin/env python3
"""Build the Dutch wordlist (`nl`) from two open sources.

    python tools/build_nl_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_nl/README.md):

    dictionary/sources_nl/nl_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_nl/nl.dic       hunspell nl (OpenTaal), BSD-3 and/or
    dictionary/sources_nl/nl.aff       CC BY 3.0 - LOOKUP only

Output: dictionary/main_nl.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

The Spanish recipe (tools/build_es_wordlist.py): a form stays if hunspell
accepts it as lowercase; if hunspell knows it only CAPITALISED it is a proper
name (`Amsterdam`, `Jan`) and stays with its capital. Dutch capitalises the
digraph `ij` as a whole: `IJsland`, never `Ijsland`.

The subtitle tokeniser cut every word at the apostrophe - the list has none,
so `auto's`, `foto's`, `zo'n` arrive as `auto` + `s`. They come back the
Ukrainian way: after each accepted form ending in a vowel the form + `'s` is
tried against hunspell and kept at a twentieth of the base count (the plural
and the genitive are rarer than the word itself); `zo'n` is the one form of
the other shape, added at the count of `zo` / 20. The keyboard draws no
apostrophe in the glide. Hyphenated forms (`e-mail`) are fine as they are:
the Dutch hunspell has BREAK 0, so it accepts a hyphenated form only whole.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_nl/nl_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_nl/nl"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_nl.combined"

MIN_COUNT = 2
# Dutch letters with the marks Dutch writes (één, ideeën, café, ruïne,
# coördinatie) and the ç of loanwords (façade).
LETTERS = set("abcdefghijklmnopqrstuvwxyzáàäâéèëêíïîóöôúüûç")
# Real one-letter words: u (formal you).
ONE_LETTER = {"u"}
APOS_AFTER = set("aiouyé")
APOS_SHARE = 20
EXTRA = {"zo'n": "zo"}


def capitalised(word):
    if word.startswith("ij"):
        return "IJ" + word[2:]
    return word[:1].upper() + word[1:]


def main():
    for p in (FREQ, ISV, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_nl/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))

    rows, seen = [], set()          # (form, count)
    dropped_unknown = dropped_other = names = apos = 0
    counts = {}
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower(), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if word in seen:
            continue
        core = word.replace("-", "")
        if (not core or not set(core) <= LETTERS or word.endswith("-")
                or word.startswith("-")
                or (len(word) == 1 and word not in ONE_LETTER)):
            dropped_other += 1
            continue
        if not hun.lookup(word):
            cap = capitalised(word)
            # Two letters capitalised are abbreviations and element symbols
            # (`Dr`, `He`), not names.
            if ("-" not in word and len(word) > 2 and hun.lookup(cap)
                    and cap not in seen):
                rows.append((cap, count))
                seen.add(cap)
                names += 1
                continue
            dropped_unknown += 1
            continue
        rows.append((word, count))
        seen.add(word)
        counts[word] = count

    # The apostrophe forms the tokeniser cut off.
    for word, count in list(counts.items()):
        if word[-1] in APOS_AFTER and "-" not in word:
            form = word + "'s"
            if form not in seen and hun.lookup(form):
                rows.append((form, max(MIN_COUNT, count // APOS_SHARE)))
                seen.add(form)
                apos += 1
    for form, base in EXTRA.items():
        if form not in seen and base in counts and hun.lookup(form):
            rows.append((form, max(MIN_COUNT, counts[base] // APOS_SHARE)))
            seen.add(form)
            apos += 1

    rows.sort(key=lambda r: (-r[1], r[0]))
    words = [r[0] for r in rows]

    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(words), len(isv)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:nl,locale=nl,description=Nederlands (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell nl OpenTaal),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms ({names} proper names, capitalised; {apos} with an apostrophe "
          f"recovered) -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Dutch letters / stray single letters")


if __name__ == "__main__":
    main()
