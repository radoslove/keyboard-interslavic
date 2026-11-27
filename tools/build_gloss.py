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

# Every keyboard language the Polish Wiktionary defines in Polish (es fr it nl ro
# added 261118). Not isv: its glosses need the dictionary team's consent.
LANGS = ["be", "bg", "bs", "cs", "de", "en", "es", "fr", "hr", "it", "mk", "nb", "nl", "ro", "ru", "sk", "sl", "sr", "uk"]
PL_MAX_ENTRIES = 15000
# A form listed under more entries than this is an auxiliary, not a form of each.
SHARED_MAX = 8
# Verbs whose forms ARE the auxiliaries (`jest`, `był`, `będzie`): never thinned.
AUX = {"bym", "byś", "by", "byśmy", "byście", "będę", "będziesz", "będzie", "będziemy",
       "będziecie", "będą", "był", "była", "było", "byli", "były", "niech", "niechaj"}
FORM_OF = re.compile(r"(?:od:?|zaimka osobowego)\s+(\w+)\s*$")
KEEP_ALL = {"być", "byt", "biti", "byť", "быть", "бути", "быць", "съм", "sein", "werden", "be"}
# English, kept to the entries its keyboard list ranks highest (size).
EN_MAX_ENTRIES = 20000
# A gloss saying the headword is an abbreviation's expansion or a name.
NAMEISH = re.compile(r"→|^(imię|od imion|od imienia|nazwisko)")


# Plain errors in the source, fixed here until the per-language review keeps
# its own verdicts (261118, `slawista` sample): (Polish word, language) -> word.
FIXES = {
    ("krzak", "uk"): "кущ",   # Wiktionary had the Polish word in Cyrillic letters, "Кшак"
    ("teraz", "nb"): "nå",    # Wiktionary had "nu" (Danish, old Riksmål), not bokmål
}


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
    form_of = {}                      # Polish form -> the lemmas it is a form of
    names = {l: set() for l in LANGS}        # (head, gloss) kept out of the X -> Polish table
    capital = {l: set() for l in LANGS}      # (head, gloss) of capitalised headwords
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
                # A name or abbreviation, lowercased, posed as a word: "IS"
                # (information system) and "Is" (Isaiah) answered English
                # "is" (261127). Kept for filling Polish -> X, left out of the
                # X -> Polish table: an abbreviation in capitals, or a
                # capitalised headword glossed as an expansion ("Book of
                # Isaiah → Iz") or a name ("imię męskie"). `Russia` -> `Rosja`
                # stays: the keyboard lists are lowercase, so frequency cannot
                # tell a name from a word.
                if lc != "de" and len(word) > 1 and word[:1].isupper() and (
                        word.isupper() or NAMEISH.search(g)):
                    names[lc].add((word.lower(), g))
                if lc != "de" and word[:1].isupper():
                    capital[lc].add((word.lower(), g))
            elif lc == "pl":
                tr = {}
                for t in d.get("translations", []):
                    tl, tw = t.get("lang_code"), t.get("word", "")
                    if tl in gloss and tl not in tr and tw and len(tw) <= 30:
                        tr[tl] = tw.split(",")[0].strip()
                for (pw, tl), fixed in FIXES.items():
                    if word.lower() == pw and tl in tr:
                        tr[tl] = fixed
                trans.append(([k.lower() for k in keys], tr))
                # A Polish FORM page (`mam`: "1. os. lp od: mieć", `mną`: "... zaimka
                # osobowego ja") names the lemmas the form belongs to.
                for s_ in d.get("senses", []):
                    for gl in s_.get("glosses", []):
                        fm = FORM_OF.search(gl)
                        if fm:
                            form_of.setdefault(word.lower(), set()).add(fm.group(1).lower())
    # Inflection tables list analytic forms word by word (`będę polemizował`,
    # `polemizowałbym`), so auxiliaries - `będę`, `był`, `by`, `bym` - turned up
    # as forms of every verb and `był` came out as `polemizować` (2026-11-15).
    # A form shared by many entries is such a helper: it stays only with the
    # entry whose headword it is.
    def drop_shared(rows, low):
        count = {}
        for keys, _ in rows:
            for k in set(keys[1:]):
                count[k] = count.get(k, 0) + 1
        heads = {keys[0] for keys, _ in rows}
        out = []
        for keys, v in rows:
            head = keys[0]
            if head in KEEP_ALL:          # the auxiliaries' own verb keeps them
                out.append((keys, v))
                continue
            kept = [head] + [k for k in keys[1:] if count.get(k, 0) <= SHARED_MAX and k not in AUX and
                             # a headword of its own that begins differently
                             # (`by`, `bym` under `polemizować`) is not this
                             # word's form; `jest` keeps its own entry. The
                             # FIRST letter decides, not the first two: those
                             # threw `mam` and `ma` out of `mieć` ("ja mam" was
                             # not translated).
                             not (len(k) <= 3 and k in heads and k[0] != head[0])]
            out.append((kept, v))
        return out
    for l in gloss:
        gloss[l] = drop_shared(gloss[l], l != "de")
    trans = drop_shared(trans, True)
    # A form whose own page says whose form it is goes to that entry only:
    # `mną` sat under `miąć` ("ze mną" came out as `knittern`) and was missing
    # under `ja` (2026-11-16).
    fixed = []
    for keys, tr in trans:
        head = keys[0]
        kept = [head] + [k for k in keys[1:] if k not in form_of or head in form_of[k]]
        kept += [k for k, ls in form_of.items() if head in ls and k not in kept and len(ls) == 1]
        fixed.append((kept, tr))
    trans = fixed
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
            rows = [(keys, g) for keys, g in rows if (keys[0], g) not in names[l]]
            # The word before the name: the first line wins, so `May` (maj)
            # went before `may` (móc). Capitalised headwords go last.
            rows = sorted(rows, key=lambda r: (r[0][0], r[1]) in capital[l])
            for keys, g in rows:
                f.write(" ".join(dict.fromkeys(keys)) + "\t" + g + "\n")
        print(f"gloss_{l}.tsv: {len(rows):,} entries")
    rank = pl_list()
    # An entry that is only a FORM page of a bigger entry (`mam` -> cs `mám`,
    # `jest` -> cs `jest`, beside `mieć` and `być`) went first and hid the
    # verb's translations (2026-11-16); it gives way to that entry.
    parent = {}
    for keys, tr in trans:
        for k in keys[1:]:
            if k != keys[0]:
                parent[k] = max(parent.get(k, 0), len(tr))
    trans = [(keys, tr) for keys, tr in trans
             if not (keys[0] in parent and len(tr) <= 3 and parent[keys[0]] > 2 * len(tr))]
    # A word with an entry of its own is looked up there, not under another
    # entry that lists it as a form: `pole` under the rare `poła` (`Schoß`),
    # `wino` under `wina`, `droga` under `drogi` (2026-11-16).
    # Only when that entry is at least half as rich: `były` stays with `być`
    # beside the thin adjective, `ryby` with `ryba` beside the zodiac sign.
    own = {}
    for keys, tr in trans:
        own[keys[0]] = max(own.get(keys[0], 0), len(tr))
    trans = [([keys[0]] + [k for k in keys[1:] if 2 * own.get(k, 0) < len(tr)], tr)
             for keys, tr in trans]
    kept = []
    for keys, tr in trans:
        best = min((rank[k] for k in keys if k in rank), default=None)
        if best is not None:
            kept.append((best, keys, tr))
    kept.sort(key=lambda t: (t[0], -len(t[2])))   # a tie goes to the richer entry
    kept = kept[:PL_MAX_ENTRIES]
    with open(os.path.join(OUT, "gloss_pl.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for _, keys, tr in kept:
            f.write(" ".join(dict.fromkeys(keys)) + "\t" +
                    "|".join(f"{l}={w}" for l, w in sorted(tr.items())) + "\n")
    print(f"gloss_pl.tsv: {len(kept):,} entries (of {len(trans):,} with a translation)")

if __name__ == "__main__":
    main()
