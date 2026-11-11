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

Ukrainian writes an APOSTROPHE inside words (`п'ять`, `м'ясо`, `сім'я`).
It stays in the word - the keyboard writes it - but has no key: the glide
skips it, so `п'ять` is drawn as `пять` (build_swipe_dictionary.py, the
harness and both apps fold it to nothing). FrequencyWords splits every such
word at the apostrophe (the 2018 uk list holds `п` and `ять`, never `п'ять`),
so those forms are recovered: a stem before the apostrophe taken from the
hunspell .dic, glued to a list word that starts with я ю є ї, kept if
hunspell accepts the whole, counted as the RARER of its two halves (a
tail that is itself a word, `є` or `я`, gives a twentieth of that).

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
# Ukrainian, two published phonetic arrangements, measured against each other
# (2026-11-11) - xkeyboard-config `ua(phonetic)` (ЯВЕРТИ) and `ua(homophonic)`
# (after the national transliteration). Keys the phone does not have
# (`[ ] ; ' \ - =`) become holds on the nearest Latin match.
UK_A = {   # ЯВЕРТИ: в w, и y, г g, х h, ь x, ж v. Held: ш щ on s, ч c, ґ g, ю u, ї i, є e.
    "а": "a", "б": "b", "в": "w", "г": "g", "ґ": "ĝ", "д": "d", "е": "e", "є": "ê",
    "ж": "v", "з": "z", "и": "y", "і": "i", "ї": "ï", "й": "j", "к": "k", "л": "l",
    "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "c", "ч": "č", "ш": "š", "щ": "ŝ", "ь": "x", "ю": "ü",
    "я": "q", "'": "'",
}
UK_B = {   # homophonic: ш w, и y, ґ g, г h, х x, в v. Held: ч c, ж z, ю u, щ w, є e, ї i, ь b.
    "а": "a", "б": "b", "в": "v", "г": "h", "ґ": "g", "д": "d", "е": "e", "є": "ê",
    "ж": "ž", "з": "z", "и": "y", "і": "i", "ї": "ï", "й": "j", "к": "k", "л": "l",
    "м": "m", "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "x", "ц": "c", "ч": "č", "ш": "w", "щ": "ŵ", "ь": "ḃ", "ю": "ü",
    "я": "q", "'": "'",
}
# Five seeds on the same list: A 87.4 % / 97.4 %, B 87.4 % / 98.0 % (top-1 /
# top-3) - a tie; the owner chose B (2026-11-11). A stays here as measured.
TABLES["uk"] = UK_B
ONE_LETTER = {
    "ru": {"а", "в", "и", "к", "о", "с", "у", "я"},
    "uk": {"а", "в", "з", "і", "й", "к", "о", "с", "у", "я"},
}
NAMES = {"ru": "Russkij", "uk": "Ukrajinsky"}
APOSTROPHES = str.maketrans({"ʼ": "'", "’": "'", "`": "'"})
AFTER_APOSTROPHE = set("яюєї")


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

    counts = {}
    for line in freq.open(encoding="utf-8"):
        parts = line.split()
        if len(parts) == 2:
            counts.setdefault(parts[0].lower().translate(APOSTROPHES), int(parts[1]))
    rows = list(counts.items())
    recovered = 0
    if "'" in table:
        # stem before the apostrophe + the first letter after it, from the .dic
        stems = set()
        for l in Path(str(hunspell) + ".dic").open(encoding="utf-8"):
            w = l.split("/")[0].strip().translate(APOSTROPHES)
            if "'" in w and w.islower():
                head, _, tail = w.partition("'")
                if tail:
                    stems.add((head, tail[0]))
        tails = [(w, c) for w, c in rows if w[:1] in AFTER_APOSTROPHE and c >= MIN_COUNT]
        for head, first in sorted(stems):   # sorted: the same list on every build
            hc = counts.get(head)
            if not hc:
                continue
            for tail, tc in tails:
                if tail[0] != first:
                    continue
                cand = head + "'" + tail
                if cand not in counts and hun.lookup(cand):
                    # A tail that is a word of its own (`є` = is, `я` = I) was
                    # counted mostly as that word, not as the end of `п'є`.
                    counts[cand] = max(MIN_COUNT, min(hc, tc) // (20 if hun.lookup(tail) else 1))
                    recovered += 1
        rows = sorted(counts.items(), key=lambda r: -r[1])

    words, seen, unknown, other = [], set(), 0, 0
    for word, count in rows:
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
    if "'" in table:
        print(f"recovered {recovered} forms split at the apostrophe; "
              f"{sum(1 for w in words if chr(39) in w)} forms with an apostrophe in the list")


if __name__ == "__main__":
    main()
