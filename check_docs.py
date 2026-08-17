#!/usr/bin/env python3
"""
check_docs.py - verify the docs still agree with the repo, and with each other.

There are four places that describe the same facts: README.md (Interslavic,
each paragraph followed by an English gloss), the Keyman package's welcome.htm,
the online help file source/help/isv_latin.php that the Keyman catalogue
requires, and the layout table in docs/. They are different genres on purpose -
a gloss is not documentation - but they quote the SAME facts, and facts are what
drift.

The .php help file was added on catalogue review (keymanapp/keyboards#4092) and
is a near-copy of welcome.htm. That makes it the single most likely file in this
repo to go stale: it says the same things in a second place, which is exactly
the shape of every drift incident listed below.

This is not hypothetical. In one session: a path was documented that only
existed inside a gitignored build/ folder; the iOS instructions said "hold the
key" in one file and "swipe up" in another after the gesture changed; and two
Interslavic forms were corrected in one file and left wrong in the other.

There used to be a separate README.en.md as well. It was folded into README.md
because the two drifted into different redactions of the same content - the
English one had grown a whole section and half a dictionary chapter the
Interslavic one never got. Check 2 below now guards against the split coming
back rather than against its links rotting.

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
WELCOME = "keyman/isv_latin/source/welcome.htm"
HELP_PHP = "keyman/isv_latin/source/help/isv_latin.php"
DOCS = [README_MS]

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
    ms = read(README_MS)
    welcome = read(WELCOME)
    help_php = read(HELP_PHP)

    # 1. Every documented path exists. A README promising a file nobody can
    #    download is worse than no README.
    for doc, text in ((README_MS, ms),):
        for p in backticked_paths(text):
            target = p.rstrip("/")
            check(os.path.exists(os.path.join(HERE, target)),
                  "%s: documented path does not exist" % doc, target)

    # 2. There is ONE README. The English text lives in README.md as a gloss
    #    under each paragraph, not in a second file - a split that already
    #    produced two diverging redactions once. Catches both a stale link to
    #    the old file and someone re-creating it.
    check("README.en" not in ms, "README.md links to the removed README.en.md")
    check(not os.path.exists(os.path.join(HERE, "README.en.md")),
          "README.en.md is back - the English text belongs in README.md as a gloss")

    # 3. Any five- or six-digit count quoted in the docs must match something
    #    real. Five as well as six, because the iOS wordlist total (39 777) is
    #    quoted now and is exactly the figure that goes stale when the
    #    frequency threshold in build_keyman_wordlist.py moves.
    #    Deliberately does NOT assert which number belongs where: the compiled
    #    .dict and the source wordlist have genuinely different totals
    #    (253,273 vs 248,845), both correct in their own sentence. A check that
    #    confuses them cries wolf - this one did, on its first run. What it
    #    catches instead is a mistyped or stale figure.
    real = {
        sum(1 for l in read("dictionary/main_isv.combined").splitlines()
            if l.strip().startswith("word=")),
        sum(1 for l in read("keyman/radoslove.isv-latn.wordlist/source/wordlist.tsv")
            .splitlines() if l and not l.startswith("#")),
        253273,   # compiled dictionary/main_isv.dict
    }
    for doc, text in ((README_MS, ms),):
        # Collapse ONLY the separator between digit groups ("253 273" ->
        # "253273"). Stripping every space instead - as this did until the
        # READMEs were merged - glued the figure to the next word
        # ("253273slov"), which killed the trailing \b and quietly matched
        # nothing at all. The check reported "50 checks" and validated no
        # number for its entire life.
        flat = re.sub(r"(?<=\d)[\s,](?=\d)", "", text)
        for n in set(re.findall(r"\b(\d{5,6})\b", flat)):
            check(int(n) in real, "%s: quotes a count nothing produces" % doc,
                  "%s (real: %s)" % (n, sorted(real)))

    # 4. The letters are the whole point; all four must appear everywhere.
    for doc, text in ((README_MS, ms), (WELCOME, welcome), (HELP_PHP, help_php)):
        for ch in "čšžě":
            check(ch in text, "%s: letter %s missing" % (doc, ch))

    # 5. Every doc teaches the SAME primary gesture on iOS. This one has
    #    already gone wrong once, in both directions. Both halves of README.md
    #    are checked: the Interslavic sentence AND its English gloss, which is
    #    where the two files used to disagree before they were merged.
    check("prěvlečeš" in ms, "README.md: iOS section does not teach the swipe")
    check("swipe up" in ms.lower(), "README.md: English gloss does not teach the swipe")
    check("prěvlečeš" in welcome, "welcome.htm: does not teach the swipe")
    check("prěvlečeš" in help_php, "help/isv_latin.php: does not teach the swipe")
    check("swipe up" in help_php.lower(),
          "help/isv_latin.php: English gloss does not teach the swipe")

    # 5b. The Full Access trap. This is the single most expensive fact in the
    #     repo - the keyboard renders BLANK on iOS 16+ without it, and the
    #     advice was wrong in the docs once already. It must survive in every
    #     file a user might actually read.
    for doc, text in ((WELCOME, welcome), (HELP_PHP, help_php)):
        check("Full Access" in text,
              "%s: the iOS 16+ Full Access warning is gone" % doc)

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
    #    The old README.en.md printed the whole set as an illustration while
    #    saying it was absent from the layouts. That is precisely the sentence
    #    a reader skims into believing the keyboard types them, so the merged
    #    README states the fact without spelling the letters out.
    banned = set("ųėȯŕťďľđ")
    for doc, text in ((README_MS, ms), (WELCOME, welcome), (HELP_PHP, help_php)):
        for ch in banned:
            check(ch not in text, "%s: extended letter %s present" % (doc, ch))

    # 8. The catalogue's own requirements for the online help file. A keyboard
    #    in `release` must ship source/help/<id>.php, and the page is assembled
    #    by help.keyman.com rather than served as-is: it needs the header.php
    #    include and must NOT carry its own <html>/<body> wrapper. The two
    #    on-screen-keyboard placeholders are what render the layout the reviewer
    #    asked for - without them the help page describes the keyboard but never
    #    shows it. None of this is checkable by kmc, which ignores help/.
    check("require_once('header.php')" in help_php,
          "help/isv_latin.php: missing the Keyman header.php include")
    for var in ("$pagename", "$pagetitle"):
        check(var in help_php, "help/isv_latin.php: missing %s" % var)
    for tag in ("<html", "<body", "<head", "<title"):
        check(tag not in help_php.lower(),
              "help/isv_latin.php: carries its own %s> wrapper" % tag)
    check("id='osk'" in help_php,
          "help/isv_latin.php: no desktop on-screen-keyboard placeholder")
    check("id='osk-phone'" in help_php,
          "help/isv_latin.php: no phone on-screen-keyboard placeholder")

    # 9. The on-screen keyboard must not go back to being an empty stub. It was
    #    one until the catalogue review: the .kvks had a header and no keys, so
    #    OSK users saw a blank keyboard and had no way to discover that the
    #    letters are on AltGr. All four shift states have to be present, and
    #    the AltGr ones have to actually carry the four letters.
    kvks = read("keyman/isv_latin/source/isv_latin.kvks")
    for state in ('shift=""', 'shift="S"', 'shift="RA"', 'shift="SRA"'):
        check(state in kvks, "isv_latin.kvks: missing layer %s" % state)
    for ch in "čšžě":
        check(ch in kvks, "isv_latin.kvks: letter %s not on the AltGr layer" % ch)
    check("<displayunderlying/>" in kvks,
          "isv_latin.kvks: auto-fill underlying layout flag is off")

    # 10. One touch form only. A second, byte-identical `tablet` form is what
    #     the catalogue review asked us to drop; regenerating it would silently
    #     reintroduce the duplicate the reviewer objected to.
    touch = json.loads(read("keyman/isv_latin/source/isv_latin.keyman-touch-layout"))
    check(list(touch.keys()) == ["phone"],
          "touch layout: expected the phone form only",
          "found %s" % sorted(touch.keys()))

    print("%d checks" % checks)
    if failures:
        print("\nFAILED:")
        for f in failures:
            print("  - %s" % f)
        sys.exit(1)
    print("docs agree with the repo and with each other")


if __name__ == "__main__":
    main()
