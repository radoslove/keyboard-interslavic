#!/usr/bin/env python3
"""Add Polish FIRST NAMES and SURNAMES to the Polish wordlist, from the PESEL register.

    python3 tools/build_pl_names.py            # refreshes the .tsv files, merges into main_pl.combined

Why: the wordlist is built through PoliMorf, which keeps common nouns and drops
everything it does not know, so a keyboard that handles `zabezpieczeniem`
offered nothing for `Marek` (owner 2026-11-01: "czasami z wypelnianiem
formularzy jest chujnia jak musze wszystko wpisywac"). A name is exactly the
word a phone keyboard must not make you spell out.

Source: the Ministry of Digital Affairs publishes the register's own lists on
dane.gov.pl - every first name and surname of a LIVING person, with how many
people carry it. That is the authoritative list for Poland and it comes with
real counts, so the keyboard can rank Nowak above Nowakowianka.

    dictionary/sources_pl/pesel_imiona_meskie_2025.csv     dataset 1667, 2025-01-22
    dictionary/sources_pl/pesel_imiona_zenskie_2025.csv    dataset 1667, 2025-01-22
    dictionary/sources_pl/pesel_nazwiska_meskie_2026.csv   dataset 1681, 2026-01-20
    dictionary/sources_pl/pesel_nazwiska_zenskie_2026.csv  dataset 1681, 2026-01-20

The downloads are not committed; the filtered `names_pl.tsv` / `surnames_pl.tsv`
are, so the list can be rebuilt without them.

Thresholds: the raw files hold 69 179 first names and 598 476 surnames, and both
tails are single spellings that would only ever beat the word that was meant.

Frequency: a name must be reachable without outranking ordinary words, so the
byte is log-scaled - below `nie` (255) and `do` (237). Surnames sit a band lower
than first names, because they are rarer in running text.

A surname goes in even when the list already holds a lowercase twin, because
that twin is how PoliMorf happens to spell it and a surname is written with a
capital: `nowak` f=100 was in the wordlist, `Nowak` - the commonest surname in
Poland - was not. The price is that `zajac` the animal and `Zajac` the surname
now sit in the same candidate list; the animal wins on frequency, which is the
right default.
"""

import csv
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "dictionary/sources_pl"
WORDLIST = ROOT / "dictionary/main_pl.combined"
ASSET = ROOT / "android-app/app/src/main/assets/main_pl.combined"

GIVEN = [SRC / "pesel_imiona_meskie_2025.csv", SRC / "pesel_imiona_zenskie_2025.csv"]
FAMILY = [SRC / "pesel_nazwiska_meskie_2026.csv", SRC / "pesel_nazwiska_zenskie_2026.csv"]

MIN_GIVEN, GIVEN_BAND = 200, (60, 150)
MIN_FAMILY, FAMILY_BAND = 1000, (40, 110)
LETTERS = set("aąbcćdeęfghijklłmnńoóprsśtuwyzźżqvx-")


def title(name: str) -> str:
    """The register shouts: PIOTR -> Piotr, NOWAK-KOWALSKI -> Nowak-Kowalski."""
    return "-".join(p.capitalize() for p in name.lower().split("-"))


def read(paths, minimum):
    out = {}
    for path in paths:
        if not path.exists():
            sys.exit(f"missing input: {path}\nsee dictionary/sources_pl/README.md")
        with path.open(encoding="utf-8") as fh:
            reader = csv.reader(fh)
            next(reader, None)
            for row in reader:
                count = next((c for c in row[1:] if c.strip().isdigit()), None)
                if count is None:
                    continue
                name = title(row[0].strip())
                if len(name) < 2 or not set(name.lower()) <= LETTERS:
                    continue
                # One spelling can be both a man's and a woman's (Maria), and a
                # surname comes in a male and a female file: count the PEOPLE,
                # then filter - filtering per file loses a name carried by 600
                # men and 600 women.
                out[name] = out.get(name, 0) + int(count)
    return {n: c for n, c in out.items() if c >= minimum}


def freq(count, lo, hi, band):
    f_min, f_max = band
    if hi <= lo:
        return f_max
    t = (math.log(count) - math.log(lo)) / (math.log(hi) - math.log(lo))
    return max(f_min, min(f_max, round(f_min + t * (f_max - f_min))))


def main():
    text = WORDLIST.read_text(encoding="utf-8")
    have = {l[5:l.find(",f=")] for l in text.split("\n") if l.startswith("word=")}
    added, report = [], []
    for label, paths, minimum, band, tsv in (
        ("imiona", GIVEN, MIN_GIVEN, GIVEN_BAND, SRC / "names_pl.tsv"),
        ("nazwiska", FAMILY, MIN_FAMILY, FAMILY_BAND, SRC / "surnames_pl.tsv"),
    ):
        names = read(paths, minimum)
        lo, hi = min(names.values()), max(names.values())
        rows = sorted(names.items(), key=lambda kv: -kv[1])
        tsv.write_text("".join(f"{n}\t{c}\n" for n, c in rows), encoding="utf-8")
        new = [(n, freq(c, lo, hi, band)) for n, c in rows if n not in have]
        added += new
        report.append(f"{label}: {len(rows)} w rejestrze (>= {minimum} osob), "
                      f"{len(new)} nowych w slowniku -> {tsv.name}")

    for line in report:
        print(line)
    if not added:
        print("wordlist already has every name")
        return
    with WORDLIST.open("a", encoding="utf-8", newline="\n") as out:
        for n, f in added:
            out.write(f"word={n},f={f}\n")
    print(f"+{len(added)} -> {WORDLIST}")
    ASSET.write_bytes(WORDLIST.read_bytes())
    print(f"copied to {ASSET}")


if __name__ == "__main__":
    main()
