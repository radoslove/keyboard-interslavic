#!/usr/bin/env python3
"""
check_docs.py - verify the docs still agree with the repo, and with each other.

There are now four places that describe the same facts: README.md (Interslavic,
with an English gloss), README.en.md (English documentation), the Keyman
package's welcome.htm, and the layout table in docs/. They are different genres
on purpose - a gloss is not documentation - but they quote the SAME facts, and
facts are what drift.

This is not hypothetical. In one session: a path was documented that only
existed inside a gitignored build/ folder; the iOS instructions said "hold the
key" in one file and "swipe up" in another after the gesture changed; and two
Interslavic forms were corrected in one file and left wrong in the other.

Every check below is something a machine can settle, so no one has to remember.

USAGE
    python3 check_docs.py          # exits non-zero if anything disagrees
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

README_MS = "README.md"
README_EN = "README.en.md"
WELCOME = "keyman/isv_latin/source/welcome.htm"
DOCS = [README_MS, README_EN]

# Top-level directories of this repo. A backticked token only counts as a
# documented path if it starts with one of these - that rules out bare
# extensions (`.dict`), shell fragments, external repos (`medzuslovjansky/...`)
# and deliberate ellipses (`windows/…/setup.exe`) without a pile of special
# cases that would themselves need maintaining.
REPO_DIRS = tuple(sorted(
    d for d in os.listdir(HERE)
    if os.path.isdir(os.path.join(HERE, d)) and not d.startswith(".")))

failures = []
checks = 0


def check(ok, label, detail=""):
    global checks
    checks += 1
    if not ok:
        failures.append("%s%s" % (label, (" - " + detail) if detail else ""))


def read(rel):
    with open(os.path.join(HERE, rel), encoding="utf-8") as f:
        return f.read()


def backticked_paths(text):
    """Tokens in backticks that genuinely point at something in this repo."""
    for tok in re.findall(r"`([^`\n]+)`", text):
        if " " in tok or "…" in tok or tok.startswith("http"):
            continue
        if tok.startswith(REPO_DIRS) or (
                os.path.sep not in tok and tok.endswith((".md", ".py"))):
            yield tok


def main():
    ms, en = read(README_MS), read(README_EN)
    welcome = read(WELCOME)

    # 1. Every documented path exists. A README promising a file nobody can
    #    download is worse than no README.
    for doc, text in ((README_MS, ms), (README_EN, en)):
        for p in backticked_paths(text):
            target = p.rstrip("/")
            check(os.path.exists(os.path.join(HERE, target)),
                  "%s: documented path does not exist" % doc, target)

    # 2. The two READMEs point at each other. Either link rotting strands
    #    half the readers.
    check(README_EN in ms, "README.md does not link to the English version")
    check(README_MS in en, "README.en.md does not link to the Interslavic one")

    # 3. Any six-digit count quoted in the docs must match something real.
    #    Deliberately does NOT assert which number belongs where: the compiled
    #    .dict and the source wordlist have genuinely different totals
    #    (253,273 vs 248,845), both correct in their own sentence. A check that
    #    confuses them cries wolf - this one did, on its first run. What it
    #    catches instead is a mistyped or stale figure.
    real = {
        sum(1 for l in read("dictionary/main_isv.combined").splitlines()
            if l.strip().startswith("word=")),
        sum(1 for l in read("keyman/radoslove.isv.wordlist/source/wordlist.tsv")
            .splitlines() if l and not l.startswith("#")),
        253273,   # compiled dictionary/main_isv.dict
    }
    for doc, text in ((README_MS, ms), (README_EN, en)):
        flat = re.sub(r"[\s, ]", "", text)
        for n in set(re.findall(r"\b(\d{6})\b", flat)):
            check(int(n) in real, "%s: quotes a count nothing produces" % doc,
                  "%s (real: %s)" % (n, sorted(real)))

    # 4. The letters are the whole point; all four must appear everywhere.
    for doc, text in ((README_MS, ms), (README_EN, en), (WELCOME, welcome)):
        for ch in "čšžě":
            check(ch in text, "%s: letter %s missing" % (doc, ch))

    # 5. Every doc teaches the SAME primary gesture on iOS. This one has
    #    already gone wrong once, in both directions.
    check("prěvlečeš" in ms, "README.md: iOS section does not teach the swipe")
    check("swipe up" in en.lower(), "README.en.md: does not teach the swipe")
    check("prěvlečeš" in welcome, "welcome.htm: does not teach the swipe")

    # 6. The built package matches the generator. Catches "edited the table,
    #    forgot to rebuild" - which ships docs describing a keyboard that is
    #    not the one in the .kmp.
    src_version = re.search(r'KEYBOARD_VERSION = "([^"]+)"',
                            read("build_keyman.py")).group(1)
    kmp = os.path.join(HERE, "keyman/isv_latin.kmp")
    if os.path.exists(kmp):
        import zipfile
        with zipfile.ZipFile(kmp) as z:
            meta = json.loads(z.read("kmp.json"))
        check(meta["keyboards"][0]["version"] == src_version,
              "keyman/isv_latin.kmp is stale",
              "package %s vs generator %s"
              % (meta["keyboards"][0]["version"], src_version))

    # 7. HOUSE_STYLE §1: the extended alphabet must not reappear anywhere the
    #    reader could mistake it for something the keyboard types.
    banned = set("ųėȯŕťďľđ")
    for ch in banned:
        check(ch not in welcome, "welcome.htm: extended letter %s present" % ch)

    print("%d checks" % checks)
    if failures:
        print("\nFAILED:")
        for f in failures:
            print("  - %s" % f)
        sys.exit(1)
    print("docs agree with the repo and with each other")


if __name__ == "__main__":
    main()
