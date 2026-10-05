#!/usr/bin/env python3
"""
build_mac_pkg.py - wrap mac/KBDMSSTD.keylayout in a macOS installer package (.pkg).

The package copies the layout into "Keyboard Layouts". Installer offers two targets:
  * all users  -> /Library/Keyboard Layouts   (asks for an admin password)
  * only me    -> ~/Library/Keyboard Layouts  (no admin needed)

After installing: log out and back in, then System Settings -> Keyboard ->
Input Sources -> Edit -> + -> Others -> "Medzuslovjansky (standard)".

Signing: the package is unsigned unless --sign names a "Developer ID Installer"
identity (an "Apple Development" certificate cannot sign installers). macOS blocks
an unsigned package on first open; the user allows it once in
System Settings -> Privacy & Security -> Open Anyway.

Needs macOS (pkgbuild, productbuild). Output goes to dist/mac/ (git-ignored);
attach it to the GitHub release.

USAGE
    python3 build_mac_pkg.py [--version 3.4] [--sign "Developer ID Installer: ..."]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
LAYOUT = os.path.join(HERE, "mac", "KBDMSSTD.keylayout")
OUT_DIR = os.path.join(HERE, "dist", "mac")
IDENTIFIER = "com.radoslove.interslavic.keylayout"
TITLE = "Medžuslovjansky (standard) keyboard layout"

DISTRIBUTION = """<?xml version="1.0" encoding="utf-8"?>
<installer-gui-script minSpecVersion="2">
  <title>{title}</title>
  <domains enable_anywhere="false" enable_currentUserHome="true" enable_localSystem="true"/>
  <options customize="never" require-scripts="false" hostArchitectures="x86_64,arm64"/>
  <choices-outline>
    <line choice="default">
      <line choice="{ident}"/>
    </line>
  </choices-outline>
  <choice id="default"/>
  <choice id="{ident}" visible="false">
    <pkg-ref id="{ident}"/>
  </choice>
  <pkg-ref id="{ident}" version="{version}" onConclusion="none">component.pkg</pkg-ref>
</installer-gui-script>
"""


def git_version():
    """Latest tag without the leading v, e.g. v3.4 -> 3.4."""
    tag = subprocess.check_output(
        ["git", "describe", "--tags", "--abbrev=0"], cwd=HERE, text=True).strip()
    return tag.lstrip("v")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    ap.add_argument("--version", help="package version (default: latest git tag)")
    ap.add_argument("--sign", help='"Developer ID Installer: ..." identity')
    args = ap.parse_args()

    if sys.platform != "darwin":
        sys.exit("build_mac_pkg.py needs macOS (pkgbuild/productbuild)")
    if not os.path.isfile(LAYOUT):
        sys.exit(f"missing {LAYOUT} - run build_keylayout.py first")

    version = args.version or git_version()
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"Medzuslovjansky-standard-{version}.pkg")

    with tempfile.TemporaryDirectory() as tmp:
        root = os.path.join(tmp, "root")
        os.makedirs(root)
        shutil.copy2(LAYOUT, root)

        # Component package: the payload and where it lands.
        subprocess.run([
            "pkgbuild", "--root", root,
            "--install-location", "/Library/Keyboard Layouts",
            "--identifier", IDENTIFIER, "--version", version,
            os.path.join(tmp, "component.pkg"),
        ], check=True)

        # Product archive: adds the "all users / only me" choice.
        dist_xml = os.path.join(tmp, "distribution.xml")
        with open(dist_xml, "w", encoding="utf-8") as f:
            f.write(DISTRIBUTION.format(title=TITLE, ident=IDENTIFIER, version=version))
        cmd = ["productbuild", "--distribution", dist_xml, "--package-path", tmp]
        if args.sign:
            cmd += ["--sign", args.sign]
        subprocess.run(cmd + [out], check=True)

    print(f"OK {out}" + ("" if args.sign else "  (unsigned)"))


if __name__ == "__main__":
    main()
