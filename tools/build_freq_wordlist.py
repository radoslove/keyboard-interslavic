"""Build dictionary/main_<lang>.combined for Norwegian (nb) and English (en).

Source: FrequencyWords 2018 `<iso>_50k.txt` (OpenSubtitles, hermitdave/FrequencyWords,
CC BY-SA 4.0): the 50,000 most frequent word forms, inflections included. `f` is
log-scaled to 1-255, the same scale as the other wordlists.

Norwegian: forms that Bokmålsordboka rejected are left out when the Interscandinavian
repo's lookup file is next to this one (`../Interscandinavian/data/no_forms_bob.csv`).

Usage:  python tools/build_freq_wordlist.py nb|en
"""
import csv
import math
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DICT = os.path.join(HERE, "..", "dictionary")

LANGS = {
    "nb": ("sources_nb/no_50k.txt", r"[a-zæøåéèêóòôäöü]+", "Norsk bokmål"),
    "en": ("sources_en/en_50k.txt", r"[a-z]+", "English"),
}
# Subtitle lists carry every letter as a "word"; keep the real one-letter words.
ONE_LETTER = {"nb": {"å", "i", "e", "o"}, "en": {"a", "i"}}


def rejected_nb():
    path = os.path.join(HERE, "..", "..", "Interscandinavian", "data", "no_forms_bob.csv")
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {r["form"] for r in csv.DictReader(f) if r["status"] == "unknown"}


def main(lang):
    src, letters, name = LANGS[lang]
    ok = re.compile(letters)
    bad = rejected_nb() if lang == "nb" else set()
    counts = []
    with open(os.path.join(DICT, src), encoding="utf-8") as f:
        for line in f:
            w, n = line.split()
            w = w.lower()
            if not ok.fullmatch(w) or w in bad:
                continue
            if len(w) == 1 and w not in ONE_LETTER[lang]:
                continue
            counts.append((w, int(n)))
    top = math.log(counts[0][1])
    freq = {}
    for w, n in counts:
        freq[w] = max(freq.get(w, 0), max(1, round(255 * math.log(n) / top)))
    out = os.path.join(DICT, f"main_{lang}.combined")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"dictionary=main:{lang},locale={lang},description={name} "
                f"(FrequencyWords CC BY-SA 4.0),date={int(time.time())},version=54\n")
        for w, n in sorted(freq.items(), key=lambda t: (-t[1], t[0])):
            f.write(f" word={w},f={n}\n")
    print(f"{out}: {len(freq):,} words ({len(bad):,} rejected by Bokmålsordboka)")


if __name__ == "__main__":
    main(sys.argv[1])
