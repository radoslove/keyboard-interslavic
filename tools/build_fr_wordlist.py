#!/usr/bin/env python3
"""Build the French wordlist (`fr`) from two open sources.

    python tools/build_fr_wordlist.py

Inputs (downloaded, not committed - see dictionary/sources_fr/README.md):

    dictionary/sources_fr/fr_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_fr/fr.dic       hunspell fr (Grammalecte / LibreOffice,
    dictionary/sources_fr/fr.aff       version 7.7), MPL 2.0 - LOOKUP only
    dictionary/fr_elisions.txt         the elided forms kept (`l'`, `qu'`)

Output: dictionary/main_fr.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

The Spanish recipe (tools/build_es_wordlist.py): a form stays if hunspell
accepts it as lowercase; if hunspell knows it only CAPITALISED it is a proper
name (`Paris`, `Marie`) and stays with its capital.

Elision as in Italian: the subtitle tokeniser cuts `l'homme` at the apostrophe,
so `l'` arrives as a word of its own and hunspell rejects it; the real elided
forms are a hand list. Whole words with an apostrophe (`aujourd'hui`) and
hyphenated ones (`peut-être`, `est-ce`, `là-bas`) stay when hunspell accepts the
whole form; the keyboard draws neither the apostrophe nor the hyphen.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_fr/fr_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_fr/fr"
ELISIONS = ROOT / "dictionary/fr_elisions.txt"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_fr.combined"

MIN_COUNT = 2
# The French alphabet with its marks, plus the ligatures.
LETTERS = set("abcdefghijklmnopqrstuvwxyzàâæçéèêëîïôœùûüÿ")
# Real one-letter words: a (has), à (to), y (there).
ONE_LETTER = {"a", "à", "y"}


PRONOUNS = {"je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
            "moi", "toi", "lui", "leur", "le", "la", "les", "y", "en", "ce", "ci", "là"}


def accepted(hun, word, hyphened):
    """hunspell on a plain form. A hyphenated one: hunspell BREAKs at `-` and
    accepts any row of words (`est-ce-que`), so it must be an entry of the
    .dic itself (`peut-être`, `grand-mère`), or a verb with its pronoun after
    it (`avez-vous`, `dis-moi`, `a-t-il`)."""
    if "-" not in word:
        return bool(hun.lookup(word))
    if word in hyphened:
        return True
    parts = word.split("-")
    if len(parts) == 2:
        return parts[1] in PRONOUNS and bool(hun.lookup(parts[0]))
    # `a-t-il`, `sera-t-elle`: the euphonic t, which hunspell does not list as
    # a whole. Only this one shape - accepting any hyphenated row of known
    # words let `est-ce-que` and `y-a-t-il` in, which French does not write.
    parts = word.split("-")
    return (len(parts) == 3 and parts[1] == "t"
            and parts[2] in ("il", "elle", "on", "ils", "elles")
            and bool(hun.lookup(parts[0])))


def main():
    for p in (FREQ, ISV, ELISIONS, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_fr/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))
    hyphened = {l.split("/")[0].strip() for l in
                Path(str(HUNSPELL) + ".dic").open(encoding="utf-8") if "-" in l.split("/")[0]}
    elisions = {l.strip() for l in ELISIONS.open(encoding="utf-8")
                if l.strip() and not l.startswith("#")}

    words, seen = [], set()
    dropped_unknown = dropped_other = names = joined = 0
    for line in FREQ.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) != 2:
            continue
        word, count = parts[0].lower().replace("’", "'"), int(parts[1])
        if count < MIN_COUNT:
            break                      # the file is sorted by count
        if word in seen:
            continue
        if word in elisions:
            words.append(word)
            seen.add(word)
            continue
        core = word.replace("'", "").replace("-", "")
        if (not core or not set(core) <= LETTERS or word.endswith(("'", "-"))
                or word.startswith(("'", "-"))
                or (len(word) == 1 and word not in ONE_LETTER)):
            dropped_other += 1
            continue
        if not accepted(hun, word, hyphened):
            cap = word[:1].upper() + word[1:]
            if "'" not in word and "-" not in word and hun.lookup(cap) and cap not in seen:
                words.append(cap)
                seen.add(cap)
                names += 1
                continue
            dropped_unknown += 1
            continue
        if "'" in word or "-" in word:
            joined += 1
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
        out.write("dictionary=main:fr,locale=fr,description=Français (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell fr),version=1\n")
        for rank, form in enumerate(words):
            f = isv[min(k - 1, rank * k // n)]
            out.write(f"word={form},f={max(1, min(255, f))}\n")

    print(f"{n} forms ({names} proper names, capitalised; {joined} with ' or -) -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not French letters / stray single letters / cut halves")


if __name__ == "__main__":
    main()
