#!/usr/bin/env python3
"""Add the Polish PAST-PERSONAL and CONDITIONAL forms the morphology filter drops.

    python3 tools/build_pl_aglutynanty.py             # merge into main_pl.combined + the asset
    python3 tools/build_pl_aglutynanty.py --na-sucho   # report + negative control, writes nothing

Why: build_pl_wordlist.py keeps a subtitle form only if PoliMorf lists it. But
the Polish personal endings -em/-es/-smy/-scie and the conditional "by" are
AGGLUTINANTS - PoliMorf stores them as separate lexemes (aglt, qub), so no
composite form is ever in it, and the filter dropped every single one. Measured
2026-11-04 on the shipped list: 32 951 past-tense forms, of which 325 personal
and FOUR conditional. "zrobil" was in, "zrobilem" was not; "chcial" was in,
"chcialbym" was not; "zeby" was in, "zebym" was not.

The owner found it before the measurement did: 15 of the 28 words in his own
user_words_pl.txt on the phone are exactly this class (chcialem, moglbym,
bylby, przydalby, zaczalem, bym...). He was repairing by hand, one word at a
time, what the build was losing by the thousand.

HOW, and why not by rule. The first attempt grew the endings off the wordlist
itself, guarding against junk by asking whether the paradigm's other forms were
present. The negative control killed it: stol -> stoly, bialy -> biali and
szkoly -> szkoli (from szkolic) are accidental siblings, so it produced stolby,
kosciolbym, anioles, bialysmy. A rule cannot tell a noun from a participle;
PoliMorf can, so this reads PoliMorf and takes only rows tagged praet.

PoliMorf also settles the alternation that no rule of mine would have got right.
It carries BOTH variants of the ~200 masculine participles that have them:

    mogl   moc   praet:sg:m1.m2.m3:imperf:agl    <- the stem a clitic attaches to
    mogl   moc   praet:sg:m1.m2.m3:imperf:nagl   <- the standalone form

so the personal endings are grown off the agl form (moglem, nioslem, wiozlem)
and the conditional off the nagl one (moglby). Getting that backwards would have
put moglem's wrong twin into a keyboard dictionary.

Frequency: inherited from the participle's own byte in the wordlist, minus a
step by how common the ending is in running text (1sg/2sg cost little, the 2pl
-liscie a lot). A form is only grown when its participle is already in the list,
so nothing enters for a verb nobody writes, and the derived form stays reachable
without outranking the word it came from.
"""

import gzip
import sys
from pathlib import Path

# A Windows console is cp1252 and would die on the first non-ASCII letter.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
MORF = ROOT / "dictionary/sources_pl/PoliMorf-0.6.7.tab.gz"
WORDLIST = ROOT / "dictionary/main_pl.combined"
ASSET = ROOT / "android-app/app/src/main/assets/main_pl.combined"

# tag slot -> (personal endings, conditional endings), each (suffix, cost in bytes)
TABLE = {
    "sg:m1.m2.m3": ([("em", 5), ("eś", 8)],
                    [("by", 10), ("bym", 10), ("byś", 18)]),
    "sg:f":        ([("m", 5), ("ś", 8)],
                    [("by", 12), ("bym", 12), ("byś", 20)]),
    "sg:n1.n2":    ([],
                    [("by", 12)]),
    "pl:m1.p1":    ([("śmy", 8), ("ście", 40)],
                    [("by", 14), ("byśmy", 20), ("byście", 40)]),
    "pl:m2.m3.f.n1.n2.p2.p3": ([("śmy", 20), ("ście", 40)],
                               [("by", 16), ("byśmy", 25), ("byście", 40)]),
}
FLOOR = 15

# Closed class, so written out rather than derived: the same agglutinant on a
# conjunction and on its own. The owner had to add "bym" by hand.
PARTICLES = {
    "bym": 150, "byś": 120, "byśmy": 110, "byście": 70,
    "żebym": 150, "żebyś": 130, "żebyśmy": 100,
    "żebyście": 70,
    "abym": 120, "abyś": 105, "abyśmy": 90, "abyście": 60,
    "gdybym": 150, "gdybyś": 130, "gdybyśmy": 100, "gdybyście": 65,
    "jakbym": 120, "jakbyś": 105, "choćbym": 90, "choćbyś": 80,
}

# Non-verbs that merely END like a participle. The rule-based attempt grew forms
# off every one of these, so they stay as the negative control: a run that
# produces anything here is wrong, however good its totals look.
KONTROLA = ["stół", "kościół", "wesół",
            "popiół", "anioł", "cały", "biały", "mały",
            "szkoły", "wesoły", "chwały", "skały", "stoły"]


def load_wordlist():
    have = {}
    for line in WORDLIST.read_text(encoding="utf-8").split("\n"):
        if line.startswith("word=") and ",f=" in line:
            have[line[5:line.find(",f=")]] = int(line[line.find(",f=") + 3:])
    return have


def read_participles():
    """(lemma, slot) -> {"agl": stem a clitic attaches to, "plain": standalone}"""
    if not MORF.exists():
        sys.exit("brak %s\nzobacz dictionary/sources_pl/README.md" % MORF)
    out = {}
    with gzip.open(MORF, "rt", encoding="utf-8") as fh:
        for line in fh:
            p = line.rstrip("\n").split("\t")
            if len(p) < 3 or not p[2].startswith("praet:"):
                continue
            tag = p[2]
            slot = next((s for s in TABLE if ":" + s + ":" in ":" + tag + ":"), None)
            if slot is None:
                continue
            # Both variants belong to ONE lexeme slot, so key on lemma+slot and
            # let them meet: the agl form (mogl) is not a word on its own and
            # would otherwise look like a participle in its own right.
            rec = out.setdefault((p[1], slot), {"agl": None, "plain": None})
            if tag.endswith(":agl"):
                rec["agl"] = p[0]
            elif tag.endswith(":nagl"):
                rec["plain"] = p[0]
            else:
                rec["agl"] = rec["agl"] or p[0]
                rec["plain"] = rec["plain"] or p[0]
    return out


def main():
    dry = "--na-sucho" in sys.argv
    have = load_wordlist()
    print("w slowniku: %d form" % len(have))
    parts = read_participles()
    print("imieslowy praet w PoliMorfie: %d (lemat x slot)" % len(parts))

    new = {}
    used = 0
    for (lemma, slot), rec in parts.items():
        plain = rec["plain"] or rec["agl"]
        stem_clitic = rec["agl"] or plain
        if plain is None or plain not in have:
            continue                      # no frequency for it => nobody writes it
        f = have[plain]
        used += 1
        personal, conditional = TABLE[slot]
        for base, endings in ((stem_clitic, personal), (plain, conditional)):
            for suffix, cost in endings:
                form = base + suffix
                if form in have:
                    continue
                val = max(FLOOR, f - cost)
                if new.get(form, 0) < val:
                    new[form] = val

    for w, f in PARTICLES.items():
        if w not in have:
            new[w] = f

    print("imieslowow z czestoscia w slowniku: %d" % used)
    print("nowych form: %d" % len(new))

    print("")
    print("SONDA:")
    for probe in ("zrobiłem", "chciałbym", "mógłbym", "mogłem",
                  "poszłam", "zacząłem", "wziąłem",
                  "szedłem", "niosłem", "byliśmy", "żebym"):
        if probe in new:
            state = "NOWE f=%d" % new[probe]
        elif probe in have:
            state = "juz bylo f=%d" % have[probe]
        else:
            state = "BRAK"
        print("  %-12s %s" % (probe, state))

    print("")
    print("KONTROLA NEGATYWNA (nie-czasowniki konczace sie jak imieslow):")
    bad = []
    for n in KONTROLA:
        made = sorted(k for k in new if k.startswith(n) and k != n)
        if made:
            bad.append(n)
        print("  %-12s %s" % (n, ", ".join(made) if made else "nic"))
    if bad:
        print("  -> ZLE: %d slow wyprodukowalo formy" % len(bad))
        sys.exit("kontrola negatywna nie przeszla - nic nie zapisane")
    print("  -> czysto, %d/%d" % (len(KONTROLA), len(KONTROLA)))

    if dry:
        print("")
        print("--na-sucho: nic nie zapisane")
        return
    with WORDLIST.open("a", encoding="utf-8", newline="\n") as out:
        for w, f in sorted(new.items(), key=lambda kv: (-kv[1], kv[0])):
            out.write("word=%s,f=%d\n" % (w, f))
    print("")
    print("+%d -> %s" % (len(new), WORDLIST))
    ASSET.write_bytes(WORDLIST.read_bytes())
    print("skopiowane do %s" % ASSET)


if __name__ == "__main__":
    main()
