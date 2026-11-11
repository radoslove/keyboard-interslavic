#!/usr/bin/env python3
"""Build the Proto-Slavic wordlist (`sla`) from the Wiktionary reconstructions.

    python tools/build_sla_wordlist.py

Input (downloaded, not committed - see dictionary/sources_sla/README.md):

    dictionary/sources_sla/psl.jsonl   kaikki.org wiktextract dump of the
                                       Proto-Slavic entries of English
                                       Wiktionary (CC BY-SA 4.0 / GFDL)

Output: dictionary/main_sla.combined, the same `word=...,f=...` format as the
other wordlists.

Proto-Slavic is reconstructed, so there is no corpus and no frequency. Two
stand-ins:

* Rank: a lemma that survives in MORE Slavic languages is taken to be more
  basic (`voda`, `rǫka`, `dьnь` before `kъnorzъ`). The count is of DISTINCT
  Slavic descendant language codes, not of descendant lines - one dialect atlas can
  list hundreds of local forms of a single word (`bьčela`).
* Bytes: as for Czech, the byte is taken from the same quantile of the isv
  distribution, so one weight means the same in every keyboard. An inflected
  form gets its lemma's byte minus FORM_STEP, so the citation form wins a tie.

Spelling: Wiktionary marks accent with tone diacritics on the vowel (`rǫkà`,
`rǫ̑cě`). Those are stripped; the letters themselves (`ě ę ǫ ъ ь y`, the caron
palatals `ľ ň ř ť ď`, `ś`) are kept. Syllabic `r̥ l̥` and anything not in the
alphabet below is dropped.

Internal Latin: `ъ` and `ь` are written `q` and `f`, their keys (Proto-Slavic
has neither letter), so every form glides over the 26 Latin keys; `Script` shows them as
`ъ ь` again. All other letters fold to their base key like Czech ones.
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "dictionary/sources_sla/psl.jsonl"
ISV = ROOT / "dictionary/main_isv.combined"
OUT = ROOT / "dictionary/main_sla.combined"

FORM_STEP = 40
# Only Slavic descendants count: a word borrowed into twenty neighbours
# (`ukrajina`) is not more basic for that.
SLAVIC = {"be", "bg", "bs", "cs", "csb", "cu", "dsb", "hr", "hsb", "mk", "orv",
          "pl", "pox", "ru", "rue", "sh", "sk", "sl", "sr", "szl", "uk"}
# Tone and length marks on vowels; caron (U+030C), ogonek (U+0328) and the
# acute of `ś` are letters, not accents, and stay.
TONE = {"̀", "́", "̂", "̃", "̄", "̆",
        "̏", "̑"}
VOWELS = set("aeiouyъьęǫě")
LETTERS = set("abcčdďeęěghijklľmnňoǫprřsśštťuvxyzžъь")
INTERNAL = str.maketrans({"ъ": "q", "ь": "f"})
SKIP_TAGS = {"table-tags", "inflection-template", "class", "accent-paradigm",
             "romanization"}


def plain(form):
    """Strip tone marks, keep letters; None if the result is not Proto-Slavic."""
    out = []
    for ch in unicodedata.normalize("NFD", form.strip().lower()):
        if ch in TONE:
            base = unicodedata.normalize("NFC", out[-1]) if out else ""
            if ch == "́" and base == "s":
                out.append(ch)
            continue
        if ch == "̥":
            return None
        out.append(ch)
    word = unicodedata.normalize("NFC", "".join(out))
    if not word or not set(word) <= LETTERS:
        return None
    if len(word) == 1 and word not in {"a", "i", "o", "u", "v", "k", "s", "z"}:
        return None
    return word


def spread(entry):
    codes = set()

    def walk(items):
        for d in items or []:
            if isinstance(d, dict):
                c = d.get("lang_code") or ""
                if c in SLAVIC or c.startswith(("zle-", "zlw-", "zls-")):
                    codes.add(c)
                walk(d.get("descendants"))
    walk(entry.get("descendants"))
    return len(codes)


def main():
    for p in (SRC, ISV):
        if not p.exists():
            sys.exit(f"missing input: {p}\n(see dictionary/sources_sla/README.md)")

    lemmas = {}            # lemma -> (spread, set of forms)
    dropped = 0
    for line in SRC.open(encoding="utf-8"):
        e = json.loads(line)
        if e.get("pos") in {"suffix", "prefix", "name", "infix", "affix"}:
            continue
        lemma = plain(e.get("word", ""))
        if lemma is None:
            dropped += 1
            continue
        forms = set()
        for f in e.get("forms") or []:
            if SKIP_TAGS & set(f.get("tags") or []):
                continue
            for part in re.split(r"[ ,/]+", f.get("form", "")):
                w = plain(part)
                if w and w != lemma:
                    forms.add(w)
        s, old = lemmas.get(lemma, (0, set()))
        lemmas[lemma] = (max(s, spread(e)), old | forms)

    order = sorted(lemmas, key=lambda w: (-lemmas[w][0], w))
    isv = sorted((int(m.group(1)) for m in
                  (re.search(r",f=(\d+)", l) for l in ISV.open(encoding="utf-8")) if m),
                 reverse=True)
    n, k = len(order), len(isv)
    weight = {}
    for rank, lemma in enumerate(order):
        f = isv[min(k - 1, rank * k // n)]
        weight[lemma] = max(weight.get(lemma, 0), f)
        for form in lemmas[lemma][1]:
            weight[form] = max(weight.get(form, 0), f - FORM_STEP)

    with OUT.open("w", encoding="utf-8", newline="\n") as out:
        out.write("dictionary=main:sla,locale=sla,description=Praslovjansky "
                  "(Wiktionary reconstructions via kaikki.org, CC BY-SA 4.0; "
                  "internal Latin),version=1\n")
        for w in sorted(weight, key=lambda w: (-weight[w], w)):
            out.write(f"word={w.translate(INTERNAL)},f={max(1, min(255, weight[w]))}\n")

    print(f"{n} lemmas, {len(weight)} forms -> {OUT}")
    print(f"dropped {dropped} entries with letters outside the alphabet")


if __name__ == "__main__":
    main()
