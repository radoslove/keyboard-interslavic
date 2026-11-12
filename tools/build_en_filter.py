"""Write dictionary/sources_en/en_rejected.txt: the subtitle forms that are not English words.

FrequencyWords `en_50k` comes from OpenSubtitles, so besides real words it carries
character names (`eeyore`, `ettore`), subtitle noise (`tthe`, `itt`, `chffffff`) and
letter runs (`hh`, `gg`). In a swipe decoder those are not harmless: they win
gestures meant for real words.

A form is KEPT when any of these holds:
  * it is a word of en_US or en_GB hunspell (dictionary-en / dictionary-en-gb, SCOWL),
  * it is the stem of a "n't" contraction (`didn`, `wasn`) - the keyboard has no
    apostrophe words yet, so the stem is what the user glides before typing 't,
  * it is a capitalised word of either dictionary (days, months, countries, common
    names) AND frequent in the subtitles (f >= PROPER_MIN),
  * it is very frequent anyway (f >= COLLOQUIAL_MIN: `ok`, `mmm`, `ohh`).
Everything else goes into the rejected list, which build_freq_wordlist.py skips.
Only the rejected forms are written (no dictionary content is copied).

Needs `pip install spylls` and the two npm packages unpacked somewhere:

    npm pack dictionary-en@4.0.0 dictionary-en-gb@3.0.0   # then untar each
    python tools/build_en_filter.py <dir with en index.aff/.dic> <dir with en-gb ones>
    python tools/build_freq_wordlist.py en
"""
import os
import re
import sys

from spylls.hunspell import Dictionary

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "dictionary", "sources_en", "en_50k.txt")
OUT = os.path.join(HERE, "..", "dictionary", "sources_en", "en_rejected.txt")
PROPER_MIN = 120
COLLOQUIAL_MIN = 150
# Foreign words and noise that slip through a capitalised dictionary entry.
DENY = {"herr", "chffffff"}


def main(us_dir, gb_dir):
    us = Dictionary.from_files(os.path.join(us_dir, "index"))
    gb = Dictionary.from_files(os.path.join(gb_dir, "index"))
    known = lambda w: us.lookup(w) or gb.lookup(w)
    rows = []
    with open(SRC, encoding="utf-8") as f:
        for line in f:
            w, n = line.split()
            w = w.lower()
            if re.fullmatch(r"[a-z]+", w):
                rows.append((w, int(n)))
    import math
    top = math.log(rows[0][1])
    rejected = []
    for w, n in rows:
        f = max(1, round(255 * math.log(n) / top))   # same scale as the wordlist
        if w in DENY:
            rejected.append(w)
        elif known(w) or us.lookup(w + "'t"):
            continue
        elif f >= PROPER_MIN and (known(w.capitalize()) or known(w.upper())):
            continue
        elif f >= COLLOQUIAL_MIN:
            continue
        else:
            rejected.append(w)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sorted(set(rejected))) + "\n")
    print(f"{OUT}: {len(set(rejected)):,} forms rejected of {len(rows):,}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
