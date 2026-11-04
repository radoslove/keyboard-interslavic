#!/usr/bin/env python3
"""
name_release_apk.py - put the version into the name of the file people download.

Gradle names every output after the module and the variant: `app-interslove-
release.apk`, `app-interslove-debug.apk`. The name is the same for every
version, so the moment the file leaves this machine it stops saying what it is.
Up to v3.4 that is exactly what the GitHub release asset was called, and four
different builds piled into one Downloads folder as `app-release (1).apk`,
`app-release (2).apk` ... with no way to tell them apart short of installing one.

This script takes the APK gradle just built and writes a correctly named copy
into build/upload/, ready to attach to a release or drop on Drive:

    interslove-3.5.apk        a release of the published app
    ms-test-3.5.39.apk        a test build (the name already used on Drive)
    isc-test-0.1.31.apk       the Interscandinavian test build

It never overwrites: a name that already exists is an error, because two
different builds under one name is the problem this exists to prevent.

The version is read from android-app/app/build.gradle.kts, so there is one
place to change and nothing here to keep in step by hand.

USAGE
    python3 tools/name_release_apk.py                  # interslove, release
    python3 tools/name_release_apk.py interslove debug
    python3 tools/name_release_apk.py --all            # every APK already built
"""
import hashlib
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(HERE, "android-app", "app", "build.gradle.kts")
OUTPUTS = os.path.join(HERE, "android-app", "app", "build", "outputs", "apk")
UPLOAD = os.path.join(HERE, "android-app", "app", "build", "upload")

# The release asset pattern, quoted in docs/DISTRIBUTION.md. check_docs.py
# asserts the two still say the same thing.
RELEASE_PATTERN = "interslove-<version>.apk"


def read_gradle():
    with open(GRADLE, encoding="utf-8") as f:
        return f.read()


def release_versions(text):
    """flavor -> versionName, from the productFlavors block."""
    out = {}
    for flavor in re.findall(r'create\("(\w+)"\)\s*\{(.*?)\n        \}', text, re.S):
        name, body = flavor
        m = re.search(r'versionName\s*=\s*"([^"]+)"', body)
        if m:
            out[name] = m.group(1)
    return out


def test_versions(text):
    """flavor -> (version, label slug), from the TESTS map."""
    block = re.search(r"val TESTS = mapOf\((.*?)\n\)", text, re.S)
    out = {}
    if not block:
        return out
    for flavor, base, build, label in re.findall(
        r'"(\w+)" to Triple\("([\d.]+)",\s*(\d+),\s*"([^"]+)"\)', block.group(1)
    ):
        out[flavor] = ("%s.%s" % (base, build), label.lower().replace(" ", "-"))
    return out


def target_name(flavor, build_type, text):
    if build_type == "release":
        version = release_versions(text).get(flavor)
        if not version:
            sys.exit("no release versionName for flavor %s in build.gradle.kts" % flavor)
        # Test builds count towards the NEXT release, so TEST_BASE runs ahead of
        # versionName for the whole development cycle and only meets it when a
        # release is cut. Cutting one without that bump would name the new file
        # after the version already published - same name, different bytes.
        base = test_versions(text).get(flavor, (None, None))[0]
        if base and base.rsplit(".", 1)[0] != version and not globals().get("FORCE"):
            sys.exit(
                "release version is %s but test builds already count towards %s.\n"
                "Bump versionName and versionCode in build.gradle.kts before cutting "
                "the release, or pass --force if %s really is the version being published."
                % (version, base.rsplit(".", 1)[0], version))
        return "%s-%s.apk" % (flavor, version)
    version, slug = test_versions(text).get(flavor, (None, None))
    if not version:
        sys.exit("no TESTS entry for flavor %s in build.gradle.kts" % flavor)
    return "%s-%s.apk" % (slug, version)


def built_apk(flavor, build_type):
    folder = os.path.join(OUTPUTS, flavor, build_type)
    if not os.path.isdir(folder):
        return None
    apks = [f for f in sorted(os.listdir(folder)) if f.endswith(".apk")]
    if len(apks) != 1:
        sys.exit("expected exactly one .apk in %s, found %s" % (folder, apks))
    return os.path.join(folder, apks[0])


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def copy_one(flavor, build_type, text):
    src = built_apk(flavor, build_type)
    if src is None:
        print("  %-10s %-7s  not built" % (flavor, build_type))
        return True
    name = target_name(flavor, build_type, text)
    os.makedirs(UPLOAD, exist_ok=True)
    dst = os.path.join(UPLOAD, name)
    if os.path.exists(dst):
        same = sha256(dst) == sha256(src)
        print("  %-10s %-7s  %s ALREADY EXISTS%s" % (
            flavor, build_type, name, " (identical bytes)" if same else " - DIFFERENT BYTES"))
        # Identical bytes are a repeat of the same build, not a collision.
        return same
    shutil.copy2(src, dst)
    size = os.path.getsize(dst)
    print("  %-10s %-7s  %s" % (flavor, build_type, name))
    print("  %-10s %-7s  %.1f MB  sha256 %s" % ("", "", size / 1048576.0, sha256(dst)))
    return True


def main():
    args = [a for a in sys.argv[1:]]
    if "--force" in args:
        args.remove("--force")
        globals()["FORCE"] = True
    text = read_gradle()
    print("ready to upload, in %s:" % os.path.relpath(UPLOAD, HERE).replace("\\", "/"))
    ok = True
    if "--all" in args:
        for flavor in sorted(set(list(release_versions(text)) + list(test_versions(text)))):
            for build_type in ("release", "debug"):
                ok &= copy_one(flavor, build_type, text)
    else:
        flavor = args[0] if args else "interslove"
        build_type = args[1] if len(args) > 1 else "release"
        ok &= copy_one(flavor, build_type, text)
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
