"""Cross-filter of neighbouring standards: forms that leaked from language B
into the wordlist of language A.

Every keyboard wordlist is a frequency list (OpenSubtitles / Leipzig) checked
against the hunspell of its OWN language, so the leaks that remain are forms
hunspell A happens to accept but that people really wrote in B - Czech `hraje`
in the Slovak list, Croatian `kontrolirati` in the Serbian one. Asking
hunspell A again finds nothing; the signal is frequency: the form stands much
higher in the neighbour's list than in its own.

    python tools/cross_leak.py              # candidates per pair -> stdout summary
    python tools/cross_leak.py --out DIR    # one TSV per target language
    python tools/cross_leak.py --check      # negative control on known leaks

The lists are compared in ONE script per pair: the Cyrillic lists are kept
inside the keyboard in an internal Latin that differs per language (`ч` is
`h` in Russian and `č` in Ukrainian), so they are turned back into Cyrillic
first, and Serbian Latin is spelled in Cyrillic when it meets Macedonian.

The output is a review queue, not a verdict: a shared word that is simply
more frequent next door lands here too. Verdicts go to the overlay
(dictionary/verdicts.tsv), decided by a reviewer.
"""
import argparse
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))

from build_cyrillic_wordlist import TABLES as CYR_TABLES  # noqa: E402
from build_mk_wordlist import TRANSLIT as MK_TABLE  # noqa: E402

DICT = ROOT / "dictionary"

# Directed pairs: (target A, neighbour B) - "did B leak into A?"
PAIRS = [
    ("sk", "cs"), ("cs", "sk"),
    ("hr", "sr"), ("hr", "bs"), ("sr", "hr"), ("sr", "bs"),
    ("bs", "hr"), ("bs", "sr"), ("sl", "hr"), ("sl", "sr"),
    ("mk", "sr"), ("mk", "bg"), ("bg", "mk"), ("bg", "ru"),
    ("uk", "ru"), ("uk", "be"), ("be", "ru"), ("be", "uk"), ("ru", "uk"),
]
# Frequency units (0-255) by which B must beat A. Calibrated 261118 on the
# five known leaks from the slawista sample (--check).
MIN_GAP = 12
MIN_LEN = 4

# Known leaks (slawista sample 261118, seed 261118), in the script of the
# comparison. --check fails if any of them is NOT flagged.
KNOWN_LEAKS = [
    ("sk", "cs", "hraje"),
    ("sl", "hr", "igračke"),
    ("sr", "hr", "kontrolirati"),
    ("mk", "mk-spelling", "иљади"),
    ("uk", "ru", "красоту"),
]

SR_LAT2CYR = [
    ("lj", "љ"), ("nj", "њ"), ("dž", "џ"),
    ("a", "а"), ("b", "б"), ("c", "ц"), ("č", "ч"), ("ć", "ћ"), ("d", "д"),
    ("đ", "ђ"), ("e", "е"), ("f", "ф"), ("g", "г"), ("h", "х"), ("i", "и"),
    ("j", "ј"), ("k", "к"), ("l", "л"), ("m", "м"), ("n", "н"), ("o", "о"),
    ("p", "п"), ("r", "р"), ("s", "с"), ("š", "ш"), ("t", "т"), ("u", "у"),
    ("v", "в"), ("z", "з"), ("ž", "ж"),
]


def sr_to_cyr(word):
    out, i = [], 0
    while i < len(word):
        for lat, cyr in SR_LAT2CYR:
            if word.startswith(lat, i):
                out.append(cyr)
                i += len(lat)
                break
        else:
            return None   # a letter outside Serbian Latin
    return "".join(out)


def inverse(table):
    inv = {v: k for k, v in table.items()}
    assert len(inv) == len(table), "internal Latin is not bijective"
    return inv


TO_CYR = {lang: inverse(t) for lang, t in CYR_TABLES.items()}
TO_CYR["mk"] = inverse(MK_TABLE)
CYRILLIC = set(TO_CYR)


def load(lang):
    words = {}
    with open(DICT / f"main_{lang}.combined", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line.startswith("word="):
                continue
            w, freq = line[5:].split(",f=")[:2]
            words.setdefault(w, int(freq))
    return words


def in_script(lang, words, cyrillic):
    """Re-key a list into the script of the comparison; original form kept."""
    out = {}
    for w, f in words.items():
        if lang in TO_CYR:
            inv = TO_CYR[lang]
            if any(c not in inv for c in w):
                continue
            key = "".join(inv[c] for c in w)
        elif cyrillic and lang == "sr":
            key = sr_to_cyr(w)
            if key is None:
                continue
        else:
            key = w
        if key not in out or out[key][1] < f:
            out[key] = (w, f)
    return out


def candidates(a, b, cache):
    cyr = a in CYRILLIC or b in CYRILLIC
    la = in_script(a, cache[a], cyr)
    lb = in_script(b, cache[b], cyr)
    rows = []
    for key, (wa, fa) in la.items():
        if len(key) < MIN_LEN or key not in lb:
            continue
        fb = lb[key][1]
        if fb - fa >= MIN_GAP:
            rows.append((fb - fa, key, wa, fa, fb))
    rows.sort(key=lambda r: (-r[0], r[1]))
    return rows


# Spelling leaks inside ONE list: Macedonian writes `лј нј` where Serbian has
# `љ њ` (`илјади` / Serbian-style `иљади`). Both spellings pass hunspell mk, and
# the Serbian list has neither form (it says `хиљаде`), so the pair filter is
# blind to it. Flagged: the variant whose counterpart is MORE frequent in the
# same list. (Letters foreign to the alphabet - `ě` in Slovak, `ы` in
# Ukrainian - were measured 261118: 0 in all eight lists, the builders drop them.)
VARIANTS = {"mk": [("љ", "лј"), ("њ", "нј")]}


def variant_candidates(a, cache):
    la = in_script(a, cache[a], True)
    rows = []
    for key, (wa, fa) in la.items():
        for x, y in VARIANTS.get(a, []):
            if x in key:
                v = key.replace(x, y)
                if v in la and la[v][1] > fa:
                    rows.append((la[v][1] - fa, key, wa, fa, la[v][1]))
    rows.sort(key=lambda r: (-r[0], r[1]))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", help="directory for cross_<lang>.tsv")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    langs = sorted({x for p in PAIRS for x in p})
    cache = {lang: load(lang) for lang in langs}
    found = {p: candidates(*p, cache) for p in PAIRS}
    for a in VARIANTS:
        found[(a, a + "-spelling")] = variant_candidates(a, cache)
    # A form with any verdict has been reviewed: it leaves the queue. (Not in
    # --check, which must see the known leaks whatever the overlay says.)
    if not args.check:
        from apply_verdicts import VERDICTS, read_verdicts
        done = {(v["lang"], v["form"]) for v in read_verdicts(VERDICTS)}
        found = {(a, b): [r for r in rows if (a, r[2]) not in done]
                 for (a, b), rows in found.items()}

    if args.check:
        bad = 0
        for a, b, key in KNOWN_LEAKS:
            hit = any(r[1] == key for r in found[(a, b)])
            print(f"{'OK  ' if hit else 'MISS'} {a}<-{b} {key}")
            bad += not hit
        # Negative control the other way: a gate that flags everything also
        # "finds" the leaks. Shared function words must NOT be flagged.
        for a, b, key in [("sk", "cs", "alebo"), ("hr", "sr", "nešto")]:
            hit = any(r[1] == key for r in found[(a, b)])
            print(f"{'OK  ' if not hit else 'FLAG'} {a}<-{b} {key} (must not be flagged)")
            bad += hit
        print("check:", "PASS" if not bad else f"FAIL ({bad})")
        sys.exit(1 if bad else 0)

    total = 0
    for (a, b), rows in found.items():
        print(f"{a}<-{b}: {len(rows)}")
        total += len(rows)
    print("total:", total)
    if args.out:
        os.makedirs(args.out, exist_ok=True)
        per = {}
        for (a, b), rows in found.items():
            for gap, key, wa, fa, fb in rows:
                per.setdefault(a, []).append((a, wa, key, b, fa, fb, gap))
        for a, rows in per.items():
            rows.sort(key=lambda r: -r[6])
            with open(os.path.join(args.out, f"cross_{a}.tsv"), "w", encoding="utf-8") as f:
                f.write("lang\tform\tcompared_as\tneighbour\tf_own\tf_neighbour\tgap\n")
                for r in rows:
                    f.write("\t".join(map(str, r)) + "\n")


if __name__ == "__main__":
    main()
