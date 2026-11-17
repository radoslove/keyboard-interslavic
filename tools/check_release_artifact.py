#!/usr/bin/env python3
"""Gate for a release artifact (.apk or .aab) before it goes to a store.

    python tools/check_release_artifact.py <file.apk|file.aab> [...]
    python tools/check_release_artifact.py --self-test

The released keyboard has NO network permission: a keyboard that cannot reach
the network cannot leak what is typed, and the store's data-safety answers
rest on that. Test builds add INTERNET (src/debug/AndroidManifest.xml) and the
diagnostic sink addresses from android-app/diagnostics.local.properties, so
this gate reads the BUILT file, not the source:

  1. no `android.permission.INTERNET` in any AndroidManifest.xml inside it
     (binary XML in an .apk stores strings as UTF-16, the protobuf manifest of
     an .aab as UTF-8 - both are searched);
  2. none of the values from diagnostics.local.properties (if that file exists
     on this machine) appear anywhere in the artifact's entries;
  3. no entry whose name mentions `diagnostics`.

Exit 0 = clean, 1 = a finding (each printed, without the private values).
--self-test runs the negative control: the gate must FAIL on the newest test
build it can find, or it is not proven to work.
"""

import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIAG = ROOT / "android-app" / "diagnostics.local.properties"
PERM = "android.permission.INTERNET"


def private_values():
    if not DIAG.exists():
        return []
    vals = []
    for line in DIAG.read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            for v in line.split("=", 1)[1].split(","):
                v = v.strip()
                if len(v) >= 8:
                    vals.append(v)
    return vals


def check(path):
    findings = []
    secrets = [(v.encode("utf-8"), v.encode("utf-16-le")) for v in private_values()]
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            name = info.filename
            if "diagnostics" in name.lower():
                findings.append(f"entry named like diagnostics: {name}")
            data = z.read(info)
            if name.endswith("AndroidManifest.xml"):
                if PERM.encode("utf-8") in data or PERM.encode("utf-16-le") in data:
                    findings.append(f"{name}: requests {PERM}")
            for i, (u8, u16) in enumerate(secrets):
                if u8 in data or u16 in data:
                    findings.append(f"{name}: contains diagnostics value #{i + 1}")
    return findings


def self_test():
    out = ROOT / "android-app" / "app" / "build" / "outputs"
    tests = sorted(out.glob("**/*debug*.a[pa][kb]"), key=lambda p: p.stat().st_mtime)
    if not tests:
        print("self-test: no test build found under android-app/app/build/outputs")
        return 2
    t = tests[-1]
    f = check(t)
    print(f"self-test on {t.name}: {len(f)} finding(s) - " +
          ("gate FIRES, as it must" if f else "gate SILENT on a test build: BROKEN"))
    return 0 if f else 1


def main(argv):
    if argv == ["--self-test"]:
        return self_test()
    if not argv:
        print(__doc__)
        return 2
    bad = 0
    for p in argv:
        f = check(p)
        print(f"{'FAIL' if f else 'PASS'}: {p}")
        for x in f:
            print("   ", x)
        bad += bool(f)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
