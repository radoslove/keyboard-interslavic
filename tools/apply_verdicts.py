"""Apply reviewer verdicts (dictionary/verdicts.tsv) to the keyboard wordlists.

    python tools/apply_verdicts.py            # rewrite dictionary/main_<lang>.combined
                                              # and the copy in android-app assets
    python tools/apply_verdicts.py --dry-run  # say what would change
    python tools/apply_verdicts.py --check    # exit 1 if a verdict is NOT in effect
    python tools/apply_verdicts.py --selftest # negative control on a temporary copy

Run it after every wordlist build: the builders regenerate the lists from the
frequency sources and would bring a removed form back. Idempotent - a second
run changes nothing. iOS reads its own binary (`ios/Keyboard/Resources/
<lang>_swipe.bin`, built from the same .combined by build_swipe_dictionary.py
on the build Mac), so a verdict reaches iOS only after that rebuild.
"""
import argparse
import shutil
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DICT = ROOT / "dictionary"
ASSETS = ROOT / "android-app/app/src/main/assets"
VERDICTS = DICT / "verdicts.tsv"
COLUMNS = ["lang", "form", "verdict", "target", "reason", "source", "who", "date"]
KINDS = {"keep", "demote", "remove", "fix"}


def read_verdicts(path):
    rows, header = [], None
    for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        cells = line.split("\t")
        if header is None:
            header = cells
            if header != COLUMNS:
                sys.exit(f"{path}:{n}: header must be {COLUMNS}")
            continue
        r = dict(zip(COLUMNS, cells + [""] * (len(COLUMNS) - len(cells))))
        if r["verdict"] not in KINDS:
            sys.exit(f"{path}:{n}: verdict {r['verdict']!r} not in {sorted(KINDS)}")
        if r["verdict"] == "demote" and not r["target"].isdigit():
            sys.exit(f"{path}:{n}: demote needs a frequency 0-255 in target")
        if r["verdict"] == "fix" and not r["target"]:
            sys.exit(f"{path}:{n}: fix needs the corrected form in target")
        if not r["source"] or not r["who"]:
            sys.exit(f"{path}:{n}: every verdict needs a source and who decided it")
        rows.append(r)
    return rows


def apply(text, verdicts):
    """Return (new_text, changes) for one .combined file."""
    by_form = {v["form"]: v for v in verdicts}
    out, changes, seen = [], [], set()
    for line in text.splitlines():
        if not line.startswith("word="):
            out.append(line)
            continue
        form, rest = line[5:].split(",f=", 1)
        freq, _, tail = rest.partition(",")
        v = by_form.get(form)
        if v is None or v["verdict"] == "keep":
            if form in seen:
                continue   # a fix may have produced it already
            seen.add(form)
            out.append(line)
            continue
        if v["verdict"] == "remove":
            changes.append(f"remove {form}")
            continue
        if v["verdict"] == "demote":
            if freq != v["target"]:
                changes.append(f"demote {form} {freq}->{v['target']}")
            freq = v["target"]
        elif v["verdict"] == "fix":
            changes.append(f"fix {form}->{v['target']}")
            form = v["target"]
            if form in seen:
                continue
        seen.add(form)
        out.append(f"word={form},f={freq}" + (f",{tail}" if tail else ""))
    return "\n".join(out) + "\n", changes


def in_effect(text, verdicts):
    forms = {}
    for line in text.splitlines():
        if line.startswith("word="):
            f, rest = line[5:].split(",f=", 1)
            forms.setdefault(f, rest.partition(",")[0])
    bad = []
    for v in verdicts:
        f, kind = v["form"], v["verdict"]
        if kind == "remove" and f in forms:
            bad.append(f"{f} still present")
        if kind == "fix" and (f in forms or v["target"] not in forms):
            bad.append(f"{f} not fixed to {v['target']}")
        if kind == "demote" and forms.get(f) not in (None, v["target"]):
            bad.append(f"{f} has f={forms[f]}, verdict says {v['target']}")
    return bad


def run(dict_dir, assets_dir, verdict_rows, write, check):
    langs = sorted({v["lang"] for v in verdict_rows})
    failed = 0
    for lang in langs:
        vs = [v for v in verdict_rows if v["lang"] == lang]
        src = dict_dir / f"main_{lang}.combined"
        text = src.read_text(encoding="utf-8")
        if check:
            bad = in_effect(text, vs)
            for b in bad:
                print(f"{lang}: {b}")
            failed += bool(bad)
            continue
        new, changes = apply(text, vs)
        print(f"{lang}: {len(changes)} change(s)" + "".join(f"\n  {c}" for c in changes))
        if write and changes:
            src.write_text(new, encoding="utf-8", newline="\n")
        if write and assets_dir and (assets_dir / src.name).exists():
            shutil.copyfile(src, assets_dir / src.name)
    return failed


def selftest():
    """A gate nobody has seen fail looks the same as a dead one."""
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        (d / "main_xx.combined").write_text(
            "dictionary=main:xx,locale=xx,version=1\n"
            "word=alfa,f=200\nword=beta,f=150\nword=gamma,f=100\nword=delta,f=50\n",
            encoding="utf-8")
        vs = [dict(lang="xx", form="beta", verdict="remove", target="", source="t", who="t"),
              dict(lang="xx", form="gamma", verdict="demote", target="10", source="t", who="t"),
              dict(lang="xx", form="delta", verdict="fix", target="delta2", source="t", who="t"),
              dict(lang="xx", form="alfa", verdict="keep", target="", source="t", who="t")]
        ok = True
        ok &= run(d, None, vs, write=False, check=True) == 1           # before: not in effect
        run(d, None, vs, write=True, check=False)
        ok &= run(d, None, vs, write=True, check=False) == 0           # idempotent
        ok &= run(d, None, vs, write=False, check=True) == 0           # after: in effect
        text = (d / "main_xx.combined").read_text(encoding="utf-8")
        ok &= text == ("dictionary=main:xx,locale=xx,version=1\n"
                       "word=alfa,f=200\nword=gamma,f=10\nword=delta2,f=50\n")
    print("selftest:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    a = ap.parse_args()
    if a.selftest:
        sys.exit(selftest())
    rows = read_verdicts(VERDICTS)
    if not rows:
        print("no verdicts yet")
    sys.exit(1 if run(DICT, ASSETS, rows, write=not (a.dry_run or a.check), check=a.check) else 0)


if __name__ == "__main__":
    main()
