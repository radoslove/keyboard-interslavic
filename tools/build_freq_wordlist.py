"""Build dictionary/main_<lang>.combined for Norwegian (nb) and English (en).

Source: FrequencyWords 2018 `<iso>_50k.txt` (OpenSubtitles, hermitdave/FrequencyWords,
CC BY-SA 4.0): the 50,000 most frequent word forms, inflections included. `f` is
log-scaled to 1-255, the same scale as the other wordlists.

Norwegian: forms that Bokmålsordboka rejected are left out when the Interscandinavian
repo's lookup file is next to this one (`../Interscandinavian/data/no_forms_bob.csv`).

German (de, 2026-11-14): the subtitle list is all lowercase, so every form goes
through hunspell de_DE (LibreOffice `de_DE_frami`, read with spylls, lookup only):
accepted lowercase -> kept lowercase; accepted ONLY with a capital -> a noun,
written `Haus`; accepted neither way -> left out (names and English from the
subtitles: `chandler`, `gonna`). Measured on the 50 000 forms: 18 150 capital,
21 208 lowercase, 10 642 out.

German, second source (2026-11-15): the subtitle list misses the words of
offices, shops and the news (`Mehrwertsteuer`, `Kindergeld`, `Kassenzettel`,
`Ladekabel`), and, being lowercase, it cannot tell `leben` from `Leben`: hunspell
accepts a capital on every word (sentence start), so `das Leben`, `guten Morgen`
and the polite `Sie` were missing. The Leipzig corpora keep the case, so:
  1. a lowercase form already in the list gets its capitalised twin when the
     corpora write it capitalised at least [NOUN_SHARE] of the time (`Leben`);
  2. forms the list lacks are added when hunspell accepts them (same rule as
     above) and they occur at least [LEIPZIG_MIN] times, ranked BELOW every
     subtitle word (f 1..[NEW_TOP]), at most [LEIPZIG_MAX_NEW] of them.

Usage:  python tools/build_freq_wordlist.py nb|en|de
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
    "de": ("sources_de/de_50k.txt", r"[a-zäöüß]+", "Deutsch"),
}
# Leipzig Corpora Collection (CC BY 4.0), `*-words.txt`: id, word, count.
LEIPZIG_DE = [
    "sources_de/deu_news_2023_1M/deu_news_2023_1M-words.txt",
    "sources_de/deu-de_web_2021_300K/deu-de_web_2021_300K-words.txt",
]
LEIPZIG_MIN = 5          # occurrences in both corpora together, after scaling
LEIPZIG_MAX_NEW = 60000
NOUN_SHARE = 0.3
MID_MIN = 5              # capitalised mid-sentence uses needed for a twin
# Interjections the news writes as nouns (`ein klares Nein`, `ein großes Hallo`)
# but people write lowercase in a sentence: never given a capitalised twin.
LOWER_ONLY = {"nein", "ja", "hallo", "oh", "ach", "na", "okay", "tschüss", "danke",
              "bitte", "ah", "hey", "aha", "nee", "jein", "huch", "pfui", "tja"}
NEW_TOP = 60             # the subtitle list's lowest f is 68


def leipzig_de():
    """lowercase -> [lowercase count, capitalised count], every corpus scaled
    to the size of the first one."""
    ok = re.compile(r"[a-zäöüß]+")
    out = {}
    base = None
    for rel in LEIPZIG_DE:
        rows = []
        with open(os.path.join(DICT, rel), encoding="utf-8") as f:
            for line in f:
                p = line.rstrip("\n").split("\t")
                if len(p) < 3:
                    continue
                rows.append((p[1], int(p[2])))
        total = sum(n for _, n in rows)
        base = base or total
        k = base / total
        for w, n in rows:
            low = w.lower()
            if len(low) < 2 or not ok.fullmatch(low):
                continue
            if w == low:
                slot = 0
            elif w == low[:1].upper() + low[1:]:
                slot = 1
            else:
                continue            # `USA`, `McDonald`, headlines in capitals
            c = out.setdefault(low, [0.0, 0.0])
            c[slot] += n * k
    return out


def leipzig_mid_de():
    """lowercase -> [lowercase, capitalised] counts INSIDE sentences only.
    The word counts mix in the capital every word gets at the start of a
    sentence, which made `Nein`, `Ich`, `Hallo` look like nouns (2026-11-15);
    here the first word of a sentence and every word after `. ! ? :` or an
    opening quote is skipped."""
    tok = re.compile(r"[.!?:\"„“»«(]|[A-Za-zÄÖÜäöüß]+")
    out = {}
    for rel in LEIPZIG_DE:
        rel = rel.replace("-words.txt", "-sentences.txt")
        with open(os.path.join(DICT, rel), encoding="utf-8") as f:
            for line in f:
                text = line.split("	", 1)[-1]
                start = True
                for m in tok.finditer(text):
                    w = m.group()
                    if not w[0].isalpha():
                        start = True
                        continue
                    if start:
                        start = False
                        continue
                    low = w.lower()
                    if w == low:
                        slot = 0
                    elif w == low[:1].upper() + low[1:]:
                        slot = 1
                    else:
                        continue
                    c = out.get(low)
                    if c is None:
                        c = out[low] = [0, 0]
                    c[slot] += 1
    return out


def extend_de(freq, case):
    """Add the Leipzig forms to the subtitle-built `freq` (form -> f)."""
    counts = leipzig_de()
    mid = leipzig_mid_de()
    have = {w.lower() for w in freq}
    cand = sorted(((c[0] + c[1], low) for low, c in counts.items()
                   if low not in have and c[0] + c[1] >= LEIPZIG_MIN), reverse=True)
    added = 0
    top = math.log(cand[0][0] / LEIPZIG_MIN + 1) if cand else 1.0
    for n, low in cand:
        if added >= LEIPZIG_MAX_NEW:
            break
        form = case(low)
        if form is None:
            continue
        freq[form] = max(1, round(NEW_TOP * math.log(n / LEIPZIG_MIN + 1) / top))
        added += 1
    twins = 0
    for form, f in list(freq.items()):
        if form != form.lower() or form in LOWER_ONLY:
            continue
        c = mid.get(form)
        if c and c[1] < MID_MIN:
            c = None
        cap = form[:1].upper() + form[1:]
        if not c or cap in freq or c[1] < NOUN_SHARE * (c[0] + c[1]):
            continue
        ratio = max(c[1], 1.0) / max(c[0], 1.0)
        freq[cap] = max(1, min(255, f + round(20 * math.log2(ratio))))
        twins += 1
    extra = 0
    with open(os.path.join(DICT, "de_extra.txt"), encoding="utf-8") as f:
        for line in f:
            w = line.strip()
            if w and not w.startswith("#") and w not in freq:
                freq[w] = NEW_TOP // 2
                extra += 1
    print(f"de_extra.txt: {extra} words by hand")
    print(f"Leipzig: {twins:,} capitalised twins, {added:,} new forms "
          f"(of {len(cand):,} candidates)")
    return freq


# Subtitle lists carry every letter as a "word"; keep the real one-letter words.
ONE_LETTER = {"nb": {"å", "i", "e", "o"}, "en": {"a", "i"}, "de": set()}


def rejected_nb():
    path = os.path.join(HERE, "..", "..", "Interscandinavian", "data", "no_forms_bob.csv")
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {r["form"] for r in csv.DictReader(f) if r["status"] == "unknown"}


def rejected_en():
    """Subtitle forms that are not English words (names, noise); see build_en_filter.py."""
    path = os.path.join(DICT, "sources_en", "en_rejected.txt")
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip()}


def german_case():
    """lowercase form -> the form German writes, or None (see the module doc)."""
    from spylls.hunspell import Dictionary
    d = Dictionary.from_files(os.path.join(DICT, "sources_de", "de_DE_frami"))

    def look(w):
        try:
            return d.lookup(w)
        except Exception:      # spylls trips over a few odd forms
            return False

    def case(w):
        if look(w):
            return w
        cap = w[:1].upper() + w[1:]
        return cap if look(cap) else None
    return case


def main(lang):
    src, letters, name = LANGS[lang]
    ok = re.compile(letters)
    bad = {"nb": rejected_nb, "en": rejected_en, "de": set}[lang]()
    case = german_case() if lang == "de" else None
    counts = []
    with open(os.path.join(DICT, src), encoding="utf-8") as f:
        for line in f:
            w, n = line.split()
            w = w.lower()
            if not ok.fullmatch(w) or w in bad:
                continue
            if len(w) == 1 and w not in ONE_LETTER[lang]:
                continue
            if case is not None:
                w = case(w)
                if w is None:
                    continue
            counts.append((w, int(n)))
    top = math.log(counts[0][1])
    freq = {}
    for w, n in counts:
        freq[w] = max(freq.get(w, 0), max(1, round(255 * math.log(n) / top)))
    if lang == "de":
        freq = extend_de(freq, case)
    out = os.path.join(DICT, f"main_{lang}.combined")
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(f"dictionary=main:{lang},locale={lang},description={name} "
                f"(FrequencyWords CC BY-SA 4.0{' + Leipzig CC BY 4.0' if lang == 'de' else ''}),date={int(time.time())},version=54\n")
        for w, n in sorted(freq.items(), key=lambda t: (-t[1], t[0])):
            f.write(f" word={w},f={n}\n")
    print(f"{out}: {len(freq):,} words ({len(bad):,} forms on the reject list)")


if __name__ == "__main__":
    main(sys.argv[1])
