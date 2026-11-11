#!/usr/bin/env python3
"""Run every automatic gate of the repo in one go, the same way on every machine.

    python3 tools/check_all.py                  # everything that needs no SDK
    python3 tools/check_all.py --lang sla,ru    # floors of these languages only
    python3 tools/check_all.py --build ios      # + an Xcode build (macOS)
    python3 tools/check_all.py --build android  # + a Gradle debug build

Exit code 0 only if every gate passed. Each gate prints one line.

Gates:

* docs        check_docs.py - the docs agree with the repo and each other
* public      check_public_tree.py - nothing private in publishable files
* unit        the tools/test_*.py suites
* isv words   swipe_eval.py test - the isv gate words come back in the top 3
* digraph     swipe_eval.py digraph, isv and Serbian Cyrillic - a word with
              `lj`/`nj` glides over the digraph key and over the two letters
* floors      five_seeds.py per language: the mean top-1 of seeds 1-5 must not
              fall more than FLOOR_MARGIN points below the value in FLOORS.
              One run swings 7-9 points between seeds; the mean of five moves
              about 2-3, hence the margin. Languages run in parallel.

Negative control, run every time: the floor check must REJECT a number below
its floor. For the record: Proto-Slavic measured 65.8 % while the harness did
not fold `ǫ` onto `o` and 71.5 % after the fix (2026-11-11); its floor here is
67.5, so that bug would have been caught.

After a deliberate change that moves a language (a new wordlist, a decoder
change), re-measure with five_seeds.py and update FLOORS in the same commit.
"""

import argparse
import concurrent.futures as cf
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
PY = sys.executable

# Mean top-1 of five_seeds.py, measured 2026-11-11 (iOS 0.6.4, Android 3.8.15).
FLOORS = {
    "isv": 73.7, "pl": 69.1, "cs": 82.6, "sk": 82.2, "sl": 81.9, "hr": 81.3,
    "sr": 78.6, "bs": 85.4, "mk": 77.9, "ru": 89.1, "uk": 88.0, "bg": 83.8, "sla": 71.5,
    "nb": 71.0, "en": 80.1,
}
FLOOR_MARGIN = 4.0


def run(cmd, env=None, timeout=1800):
    e = dict(os.environ)
    e.update(env or {})
    p = subprocess.run(cmd, cwd=ROOT, env=e, capture_output=True, text=True,
                       timeout=timeout)
    return p.returncode, p.stdout + p.stderr


def tail(text, n=6):
    return "\n".join("      " + l for l in text.strip().splitlines()[-n:])


def floor_ok(measured, floor):
    return measured >= floor - FLOOR_MARGIN


def seeds(lang):
    code, out = run([PY, str(TOOLS / "five_seeds.py")], {"SWIPE_LANG": lang})
    m = re.search(r"mean of 5: top-1 ([\d.]+)%\s+top-3 ([\d.]+)%", out)
    return lang, code, out, (float(m.group(1)), float(m.group(2))) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", help="comma-separated languages for the floors (default: all)")
    ap.add_argument("--build", choices=["ios", "android"], action="append", default=[])
    ap.add_argument("--no-floors", action="store_true", help="skip the slow five-seed floors")
    a = ap.parse_args()

    results = []   # (name, ok, detail)

    def gate(name, cmd, env=None):
        code, out = run(cmd, env)
        results.append((name, code == 0, "" if code == 0 else tail(out)))
        print(f"{'ok  ' if code == 0 else 'FAIL'} {name}", flush=True)

    # Negative control first: a gate nobody has seen fail looks like a dead one.
    neg = not floor_ok(65.8, 71.5) and floor_ok(71.5, 71.5)
    results.append(("negative control (floor rejects 65.8 vs 71.5)", neg, ""))
    print(f"{'ok  ' if neg else 'FAIL'} negative control (floor rejects 65.8 vs 71.5)", flush=True)

    gate("docs", [PY, "check_docs.py"])
    gate("public", [PY, str(TOOLS / "check_public_tree.py")])
    for t in sorted(TOOLS.glob("test_*.py")):
        gate(f"unit {t.name}", [PY, str(t)])
    gate("isv gate words", [PY, str(TOOLS / "swipe_eval.py"), "test"])
    gate("digraph isv cyrl", [PY, str(TOOLS / "swipe_eval.py"), "digraph"],
         {"SWIPE_SCRIPT": "cyrl"})
    gate("digraph sr cyrl", [PY, str(TOOLS / "swipe_eval.py"), "digraph"],
         {"SWIPE_SCRIPT": "cyrl", "SWIPE_LANG": "sr"})

    if not a.no_floors:
        langs = a.lang.split(",") if a.lang else list(FLOORS)
        unknown = [l for l in langs if l not in FLOORS]
        if unknown:
            sys.exit(f"unknown language(s): {unknown}")
        with cf.ThreadPoolExecutor(max_workers=os.cpu_count() or 4) as pool:
            for lang, code, out, got in pool.map(seeds, langs):
                floor = FLOORS[lang]
                if code != 0 or got is None:
                    ok, line = False, f"floor {lang}: harness failed"
                    results.append((line, False, tail(out)))
                else:
                    t1, t3 = got
                    if floor is None:
                        ok, line = True, f"floor {lang}: top-1 {t1:.1f} % (no floor yet - set FLOORS)"
                    else:
                        ok = floor_ok(t1, floor)
                        line = f"floor {lang}: top-1 {t1:.1f} % (floor {floor} - {FLOOR_MARGIN:g}), top-3 {t3:.1f} %"
                    results.append((line, ok, ""))
                print(f"{'ok  ' if ok else 'FAIL'} {line}", flush=True)

    if "ios" in a.build:
        gate("build ios", ["sh", "-c",
             "cd ios && xcodegen generate >/dev/null && env PATH=/usr/bin:/bin:/usr/sbin:/sbin "
             "xcodebuild -project InterslavicKeyboard.xcodeproj -scheme InterslavicKeyboard "
             "-configuration Debug -destination 'generic/platform=iOS' -derivedDataPath build/dd_check "
             "CODE_SIGNING_ALLOWED=NO build -quiet"])
    if "android" in a.build:
        gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
        gate("build android", ["sh", "-c", f"cd android-app && {gradlew} -q assembleDebug"]
             if os.name != "nt" else ["cmd", "/c", f"cd android-app && {gradlew} -q assembleDebug"])

    failed = [r for r in results if not r[1]]
    print()
    for name, _, detail in failed:
        print(f"FAIL {name}\n{detail}" if detail else f"FAIL {name}")
    print(f"{len(results) - len(failed)}/{len(results)} gates passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
