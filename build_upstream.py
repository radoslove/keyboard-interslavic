#!/usr/bin/env python3
"""
build_upstream.py - stage the two Keyman catalogue submissions.

Getting into the Keyman catalogue is what turns this from "a file in a GitHub
repo" into something a person can find. Today a stranger has to locate an
obscure repo, download a .kmp, know what Keyman is, use Install From File, and
then discover the Full Access trap. In the catalogue it is one tap inside the
Keyman app they already have.

Neither catalogue had any Interslavic entry when this was written.

Two separate upstream repos, two separate PRs:

  keymanapp/keyboards       release/i/isv_latin/
  keymanapp/lexical-models  release/radoslove/radoslove.isv-latn.wordlist/

Both take SOURCE only - upstream runs its own build - so `build/` is excluded
deliberately. Run the normal builds first and make sure they are warning-clean;
that is an explicit submission requirement.

USAGE
    python3 build_keyman.py && python3 build_keyman_wordlist.py
    kmc build keyman/isv_latin/isv_latin.kpj
    kmc build keyman/radoslove.isv-latn.wordlist/radoslove.isv-latn.wordlist.kpj
    python3 build_upstream.py
"""
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
DEST = os.path.join(HERE, "upstream_prep")

JOBS = [
    {
        "src": os.path.join(HERE, "keyman", "isv_latin"),
        "dst": os.path.join(DEST, "keymanapp-keyboards", "release", "i",
                            "isv_latin"),
        "repo": "keymanapp/keyboards",
    },
    {
        "src": os.path.join(HERE, "keyman", "radoslove.isv-latn.wordlist"),
        "dst": os.path.join(DEST, "keymanapp-lexical-models", "release",
                            "radoslove", "radoslove.isv-latn.wordlist"),
        "repo": "keymanapp/lexical-models",
    },
]

# build/ is upstream's job; .gitignore and .user files are ours, not theirs.
SKIP_DIRS = {"build", ".git"}
SKIP_FILES = {".gitignore"}
SKIP_SUFFIX = (".kpj.user",)


def stage(src, dst):
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    n = 0
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            if name in SKIP_FILES or name.endswith(SKIP_SUFFIX):
                continue
            rel = os.path.relpath(os.path.join(root, name), src)
            out = os.path.join(dst, rel)
            os.makedirs(os.path.dirname(out), exist_ok=True)
            shutil.copy2(os.path.join(root, name), out)
            n += 1
    return n


def main():
    for job in JOBS:
        n = stage(job["src"], job["dst"])
        print("%-28s %2d files -> %s"
              % (job["repo"], n, os.path.relpath(job["dst"], HERE)))


if __name__ == "__main__":
    main()
