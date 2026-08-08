#!/usr/bin/env python3
"""
build_icon.py - generate keyman/isv_latin/source/isv_latin.ico.

The Keyman catalogue expects a keyboard icon; every keyboard in
keymanapp/keyboards ships one. Written by hand because there is no image
library on this machine and an .ico is only a header wrapping a PNG.

The mark is a caron, not a letter. At 16 px a `č` is an illegible smudge, and
the caron is the thing that actually distinguishes this keyboard - the four
letters it exists to type are č š ž ě.

USAGE
    python3 build_icon.py
"""
import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "keyman", "isv_latin", "source", "isv_latin.ico")

SIZE = 32
BG = (0x44, 0x44, 0xCC, 0xFF)      # same blue as the welcome page
FG = (0xFF, 0xFF, 0xFF, 0xFF)
TRANSPARENT = (0, 0, 0, 0)


def caron_pixels():
    """A thick V: two strokes meeting at the bottom centre."""
    on = set()
    thickness = 3
    top, bottom = 9, 21
    for y in range(top, bottom + 1):
        # how far the stroke has travelled down, 0..1
        t = (y - top) / (bottom - top)
        left = 8 + t * (SIZE / 2 - 8)
        right = SIZE - 1 - left
        for d in range(thickness):
            on.add((int(left) + d, y))
            on.add((int(right) - d, y))
    return on


def png_bytes():
    on = caron_pixels()
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)                      # filter type 0 for each scanline
        for x in range(SIZE):
            r, g, b, a = FG if (x, y) in on else BG
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = tag + data
        return (struct.pack(">I", len(data)) + c
                + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)  # 8-bit RGBA
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def main():
    png = png_bytes()
    # ICO directory with a single PNG-compressed entry (Vista+ reads PNG here)
    header = struct.pack("<HHH", 0, 1, 1)
    entry = struct.pack("<BBBBHHII", SIZE, SIZE, 0, 0, 1, 32,
                        len(png), 6 + 16)
    with open(OUT, "wb") as f:
        f.write(header + entry + png)
    print("wrote %s (%d bytes)" % (OUT, 6 + 16 + len(png)))


if __name__ == "__main__":
    main()
