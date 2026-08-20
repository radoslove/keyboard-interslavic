#!/usr/bin/env python3
"""Compile the wordlist into the binary the swipe decoder reads at runtime.

    python3 build_swipe_dictionary.py

Source is `dictionary/main_isv.combined` - the same 248 845 forms shipped to
Android. Nothing is pruned. The 5.2 MB cut we made for Keyman in August was
forced by the trie format, not by the data: as a flat blob the whole lexicon is
3.2 MB, which a keyboard extension can hold without noticing. That matters,
because pruning by frequency also drops rarer forms of common paradigms, and a
swipe decoder that cannot produce `pisala` is a swipe decoder that is wrong
about Interslavic.

The file is READ IN PLACE - the decoder never parses it up front. Entries are
grouped into 676 buckets keyed by (first letter, last letter), so a gesture only
touches the handful of buckets whose endpoints its path could plausibly have,
and startup cost is zero.

Layout (little endian):

    magic     8    "ISVSWIPE"
    version   2    1
    count     4    number of entries
    offsets   4*677  byte offset of each bucket into the entry blob,
                     plus a terminator, relative to the blob start
    blob      ...  entries, grouped by bucket, each bucket sorted by
                   descending frequency:
                       freq     1   1..255, as in the source
                       keyLen   1   letters in the folded key sequence
                       wordLen  1   bytes of UTF-8 in the display form
                       keys     n   0..25, index into 'a'..'z'
                       word     m   UTF-8, WITH its diacritics

The key sequence is folded to the base letters because the accented letters are
not keys - `č` lives on longpress of `c`, so a finger spelling `červeny` crosses
exactly the same keys as `cerveny`. Folding here rather than at runtime is what
lets the decoder work in plain key indices and still emit correct orthography.
"""

import re
import struct
import sys
from pathlib import Path

SRC = Path("dictionary/main_isv.combined")
OUT = Path("ios/Keyboard/Resources/isv_swipe.bin")

# The four standard-orthography letters, mapped to the key they are held on.
# HOUSE_STYLE §1: the extended alphabet does not exist here, so this is the
# complete set - if that ever changes, this table and Layout.accents change
# together.
FOLD = {"č": "c", "š": "s", "ž": "z", "ě": "e"}
ALPHA = "abcdefghijklmnopqrstuvwxyz"
INDEX = {c: i for i, c in enumerate(ALPHA)}


def fold(word):
    """Display form -> key letters, or None if it cannot be typed on the layout."""
    out = []
    for ch in word.lower():
        ch = FOLD.get(ch, ch)
        if ch not in INDEX:
            return None
        out.append(INDEX[ch])
    return out


def main():
    if not SRC.exists():
        sys.exit(f"missing source: {SRC}")

    entries = []
    skipped = 0
    for line in SRC.open(encoding="utf-8"):
        m = re.match(r"\s*word=(\S+?),f=(\d+)", line)
        if not m:
            continue
        word, freq = m.group(1), int(m.group(2))
        keys = fold(word)
        if keys is None or not keys:
            skipped += 1
            continue
        entries.append((keys, word, min(max(freq, 1), 255)))

    buckets = {}
    for keys, word, freq in entries:
        buckets.setdefault((keys[0], keys[-1]), []).append((freq, keys, word))

    blob = bytearray()
    offsets = []
    for first in range(26):
        for last in range(26):
            offsets.append(len(blob))
            # Descending frequency so the likeliest reading of an ambiguous
            # path is also the one found first.
            for freq, keys, word in sorted(buckets.get((first, last), []),
                                           key=lambda e: -e[0]):
                utf8 = word.encode("utf-8")
                if len(keys) > 255 or len(utf8) > 255:
                    continue
                blob += struct.pack("<BBB", freq, len(keys), len(utf8))
                blob += bytes(keys)
                blob += utf8
    offsets.append(len(blob))

    header = b"ISVSWIPE" + struct.pack("<HI", 1, len(entries))
    header += struct.pack(f"<{len(offsets)}I", *offsets)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(header + blob)

    used = sum(1 for b in buckets.values() if b)
    print(f"{len(entries)} forms -> {OUT} ({OUT.stat().st_size / 1024 / 1024:.2f} MB)")
    print(f"{used} of 676 buckets used, largest {max(len(b) for b in buckets.values())}")
    if skipped:
        print(f"{skipped} forms skipped (not typeable on the letter layout)")


if __name__ == "__main__":
    main()
