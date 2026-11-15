"""Build the offline word-by-word gloss of the app's try box (assets/gloss_<lang>.tsv).

Source: the POLISH Wiktionary as extracted by wiktextract (kaikki.org,
`dictionary/sources_wikt/pl-extract.jsonl.gz`, CC BY-SA 4.0 / GFDL). Its entries
for foreign words are defined in Polish (`Haus` -> `dom`), and its Polish entries
carry translation tables. So:

  * a keyboard language other than Polish -> `gloss_<lang>.tsv`: forms \t Polish gloss
  * Polish -> `gloss_pl.tsv`: forms \t `de=Haus|cs=dům|...` (first translation of
    the first sense, for every keyboard language that has one)

Inflected forms come from the entries' inflection tables where Wiktionary has
them (Polish, Czech, Russian ...); German has none there, so the app falls back
on cutting a few endings. Polish entries are limited to the ones whose forms the Polish keyboard list
knows, the most frequent first, to keep the asset small. One line per entry:
all its forms, a tab, the gloss.

Usage:  python tools/build_gloss.py
"""
import gzip
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
SRC = os.path.join(ROOT, "dictionary", "sources_wikt", "pl-extract.jsonl.gz")
OUT = os.path.join(ROOT, "android-app", "app", "src", "main", "assets")

LANGS = ["cs", "sk", "sl", "hr", "sr", "bs", "mk", "ru", "uk", "bg", "be", "nb", "en", "de"]
PL_MAX_ENTRIES = 15000
# English, kept to the entries its keyboard list ranks highest (size).
EN_MAX_ENTRIES = 20000


GRAMMAR = re.compile(r"\b(lp|lm|forma|czasownika|przymiotnika|rzeczownika|zaimka|czas przeszły|"
                     r"stopień (wyższy|najwyższy)|imiesłów|tryb|dopełniacz|celownik|biernik|"
                     r"narzędnik|miejscownik|wołacz|liczba mnoga)\b")


def clean(g):
    """`(w złożeniach) dotyczący domu, domowy` -> `dotyczący domu`."""
    g = re.sub(r"\([^)]*\)", "", g)
    g = re.sub(r"\[[^\]]*\]", "", g)
    g = re.split(r"[;,:]", g)[0]
    g = re.sub(r"\s+", " ", g).strip(" .-–")
    return g if 0 < len(g) <= 40 else ""


def pl_list(lang="pl"):
    path = os.path.join(ROOT, "dictionary", f"main_{lang}.combined")
    rank = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = re.match(r"\s*word=([^,]+),f=(\d+)", line)
            if m and m.group(1).lower() not in rank:
                rank[m.group(1).lower()] = len(rank)
    return rank


def main():
    gloss = {l: [] for l in LANGS}   # (keys, gloss) per entry
    trans = []                        # (keys, {lang: word}) per Polish entry
    with gzip.open(SRC, "rt", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            lc = d.get("lang_code")
            if d.get("pos") == "unknown":
                continue
            word = d.get("word") or ""
            keys = [word] + [x.get("form", "") for x in d.get("forms", [])]
            keys = [k for k in keys if k and " " not in k]
            if not keys:
                continue
            if lc in gloss:
                g = ""
                for s in d.get("senses", []):
                    for gl in s.get("glosses", []):
                        g = clean(gl)
                        if g:
                            break
                    if g:
                        break
                if not g:
                    continue
                gloss[lc].append((keys if lc == "de" else [k.lower() for k in keys], g))
            elif lc == "pl":
                tr = {}
                for t in d.get("translations", []):
                    tl, tw = t.get("lang_code"), t.get("word", "")
                    if tl in gloss and tl not in tr and tw and len(tw) <= 30:
                        tr[tl] = tw.split(",")[0].strip()
                trans.append(([k.lower() for k in keys], tr))
    # A gloss that only DESCRIBES a form (`3. lp czasownika sein`, `czas
    # przeszły prosty czasownika finden`) is replaced by the gloss of the word
    # it names (`sein` -> `być`); one that names nothing usable is dropped, so
    # the word stays as typed instead of grammar talk (2026-11-15: `ist` came
    # out as `3. lp czasownika sein`).
    for l, rows in gloss.items():
        lemma = {}
        for keys, g in rows:
            lemma.setdefault(keys[0] if l == "de" else keys[0].lower(), g)
        fixed = []
        for keys, g in rows:
            if GRAMMAR.search(g):
                m = re.search(r"(\S+)$", g)
                named = m.group(1) if m else ""
                g = lemma.get(named) or lemma.get(named.lower()) or ""
                if not g or GRAMMAR.search(g):
                    continue
            fixed.append((keys, g))
        gloss[l] = fixed
    # Polish -> X where the Polish entry has no translation into X: the X
    # entries whose whole Polish gloss is that Polish word (`hvor` -> `gdzie`).
    rev = {l: {} for l in LANGS}
    for l, rows in gloss.items():
        for keys, g in rows:
            if " " not in g:
                rev[l].setdefault(g.lower(), keys[0])
    filled = []
    for keys, tr in trans:
        tr = dict(tr)
        for l in LANGS:
            if l not in tr and keys and keys[0] in rev[l]:
                tr[l] = rev[l][keys[0]]
        if tr:
            filled.append((keys, tr))
    trans = filled
    # One line per ENTRY: its forms, a tab, the gloss. A form listed under two
    # entries belongs to the first (the app reads the file top down).
    os.makedirs(OUT, exist_ok=True)
    en_rank = pl_list("en")
    ranked = [(min((en_rank[k] for k in keys if k in en_rank), default=None), keys, g)
              for keys, g in gloss["en"]]
    gloss["en"] = [(k, g) for r, k, g in sorted((t for t in ranked if t[0] is not None),
                                                key=lambda t: t[0])[:EN_MAX_ENTRIES]]
    for l, rows in gloss.items():
        with open(os.path.join(OUT, f"gloss_{l}.tsv"), "w", encoding="utf-8", newline="\n") as f:
            for keys, g in rows:
                f.write(" ".join(dict.fromkeys(keys)) + "\t" + g + "\n")
        print(f"gloss_{l}.tsv: {len(rows):,} entries")
    rank = pl_list()
    kept = []
    for keys, tr in trans:
        best = min((rank[k] for k in keys if k in rank), default=None)
        if best is not None:
            kept.append((best, keys, tr))
    kept.sort(key=lambda t: t[0])
    kept = kept[:PL_MAX_ENTRIES]
    with open(os.path.join(OUT, "gloss_pl.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for _, keys, tr in kept:
            f.write(" ".join(dict.fromkeys(keys)) + "\t" +
                    "|".join(f"{l}={w}" for l, w in sorted(tr.items())) + "\n")
    print(f"gloss_pl.tsv: {len(kept):,} entries (of {len(trans):,} with a translation)")

if __name__ == "__main__":
    main()
