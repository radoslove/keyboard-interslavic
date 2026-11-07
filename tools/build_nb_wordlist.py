#!/usr/bin/env python3
"""Build the Norwegian bokmal wordlist (`nb`) from two open sources.

    python tools/build_nb_wordlist.py

The Czech builder (tools/build_cs_wordlist.py) with Norwegian inputs. Until
2026-11-07 `main_nb.combined` came from tools/build_freq_wordlist.py, filtered
only by an 11 821-form Bokmalsordboka lookup kept in another repo - so the
other ~38 000 subtitle forms went in unchecked, and glides on the phone
offered `gotham`, `dewey`, `loomis`, `derry` for Norwegian words.

Inputs (downloaded, not committed - see dictionary/sources_nb/README.md):

    dictionary/sources_nb/no_50k.txt   FrequencyWords 2018, OpenSubtitles
                                       (CC BY-SA 4.0), "word count" per line
    dictionary/sources_nb/nb_NO.dic    hunspell nb_NO (LibreOffice dictionaries,
    dictionary/sources_nb/nb_NO.aff    Norsk ordbank), GPL - used as a LOOKUP

Output: dictionary/main_nb.combined, the same `word=...,f=...` format as the
other wordlists, so build_swipe_dictionary.py reads it too.

Why a second source at all: subtitles are full of typos, names and English, and
the top of this very list already carries `samozrejmne` for `samozrejme`. Polish
got PoliMorf for exactly this (tools/build_pl_wordlist.py); Czech and Norwegian
get the official hunspell dictionary, read through `spylls` (pure-Python hunspell, MIT),
which applies the real affix rules instead of us guessing them. Nothing is
copied out of it - it only answers "is this a Norwegian word", the same arrangement
as the Bokmalsordboka lookups behind the Norwegian list.

A form is kept only if hunspell accepts it AS LOWERCASE. That is deliberate:
a word hunspell only knows capitalised is a name (`Praha`, `Petr`), and on a
glide keyboard a name mostly beats the word that was meant. Acronyms go out for
the same reason (`SSSR` beat `se` on isv).

Frequency: subtitle counts span seven orders of magnitude, and the decoder was
tuned on the isv byte distribution. So the byte is not scaled from the count but
taken from the SAME QUANTILE of the isv distribution - the Norwegian word at rank r
gets the byte the isv word at that relative rank has, so one weight means the
same thing in every keyboard.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FREQ = ROOT / "dictionary/sources_nb/no_50k.txt"
HUNSPELL = ROOT / "dictionary/sources_nb/nb_NO"
OUT = ROOT / "dictionary/main_nb.combined"

MIN_COUNT = 2
# a-z, the three own letters, and the accents the layout holds (é è ê ó ò ô à)
# plus ä ö, held on æ ø.
LETTERS = set("abcdefghijklmnopqrstuvwxyzæøåéèêóòôàäö")
# Subtitle lists carry every letter as a "word"; keep the real one-letter ones
# (the prepositions, and `a` = and).
ONE_LETTER = {"å", "i", "e", "o"}


def main():
    for p in (FREQ, Path(str(HUNSPELL) + ".dic"), Path(str(HUNSPELL) + ".aff")):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_nb/README.md)")
    try:
        from spylls.hunspell import Dictionary
    except ImportError:
        sys.exit("needs spylls (pure-Python hunspell): python -m pip install spylls")

    hun = Dictionary.from_files(str(HUNSPELL))

    words, dropped_unknown, dropped_other = [], 0, 0
    counts = {}
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
        # The genitive -s (`verdens`, `dagens`, `guds`) is not in the nb_NO
        # affix rules; without this it was the one class of real, frequent
        # forms the filter dropped. Only on a stem hunspell knows in lowercase.
        genitive = word.endswith("s") and len(word) > 3 and hun.lookup(word[:-1])
        if not hun.lookup(word) and not genitive:
            dropped_unknown += 1
            continue
        if word not in counts:
            words.append(word)
        counts[word] = max(counts.get(word, 0), count)

    # Frequency: the SAME log scale as before (tools/build_freq_wordlist.py,
    # which Android's nb has always shipped), not the isv quantile the Czech
    # builder uses. Measured 2026-11-07 on real glides: the quantile pressed the
    # top of the list together (`hvordan` 203 -> 167) and `gleden` overtook it.
    # The filter decides WHICH forms; this keeps HOW MUCH they weigh unchanged.
    import math
    top = math.log(counts[words[0]])
    n = len(words)
    if n == 0:
        sys.exit("no forms survived the filter - check the hunspell files")
    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:nb,locale=nb,description=Norsk bokmal (FrequencyWords "
                  "CC BY-SA 4.0 + hunspell nb_NO),version=55\n")
        for form in words:
            f = max(1, round(255 * math.log(counts[form]) / top))
            out.write(f" word={form},f={min(255, f)}\n")

    print(f"{n} forms -> {OUT}")
    print(f"dropped: {dropped_unknown} hunspell does not know, "
          f"{dropped_other} not Norwegian letters / stray single letters")


if __name__ == "__main__":
    main()
