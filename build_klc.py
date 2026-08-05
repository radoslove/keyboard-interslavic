#!/usr/bin/env python3
"""
build_klc.py — generate our own Interslavic Windows layout (KBDMSSTD.klc).

WHY NOT JUST USE THE UPSTREAM ONE
`Projects/INTERSLOVE/kbdmslat/KBDMSLAT.klc` ((c) 2013 Adam Gola, medzuslovjansky
keyboards) is installed and works, but has defects we care about:

  1. EIGHT DEAD KEYS, and two of them sit on the BASE layer: OEM_3 makes plain
     backtick and tilde dead. Typing ` or ~ in a shell or in code then needs a
     second keystroke. That alone disqualifies it as an everyday layout.
  2. 80 characters on AltGr, of which ~26 are actual Interslavic letters. The rest
     is typographic filler (™ ® © ‰ ′ ″ § ¶ ° × ÷ ± − ∙).
  3. WRONG LETTER: it maps `ĺ` (l-acute) where Interslavic uses `ľ` (l-caron).
  4. MISSING: no `ť`, no `ď` — the extended block is incomplete.
  5. LOCALENAME sl-SI — it hijacks Slovenian, which is why "Slovenian" appears in
     the Windows switcher even though the user does not use Slovenian.

WHAT WE KEEP FROM IT
The scan-code / virtual-key / capslock skeleton, verbatim. That part is correct and
hand-retyping it would only invite transcription errors. Only the character columns
are rewritten.

DESIGN
  * Base + Shift stay plain ASCII, US-style. NO dead keys anywhere.
  * AltGr carries the letters, mnemonically (č under C, š under S, ž under Z, ě under E).
  * Standard-orthography letters ONLY (HOUSE_STYLE.md §1): č š ž ě on AltGr, plus the
    punctuation our texts use. The extended block (ų ė ę ȯ å ŕ ť ď ć đ ľ ń ś ź) is NOT
    included — those letters are withdrawn from MS everywhere, keyboard included
    (owner order 2026-08-05). ě is NOT extended and stays.
  * Retargeted to pl-PL, so the layout appears as an input method under Polish —
    no phantom Slovenian entry in the language list.

USAGE
    python build_klc.py
    # then open windows/src/KBDMSSTD.klc in MSKLC and Project > Build DLL and Setup

⚠ .klc files are UTF-16LE + CRLF. See .gitattributes — git must not touch them.
"""
import io
import os
import re

SRC = r"C:\Projects\vault_002\Projects\INTERSLOVE\kbdmslat\KBDMSLAT.klc"

KBD_NAME = "KBDMSSTD"                      # max 8 chars, must match the filename
DISPLAY = "Medzuslovjansky (standard)"     # MSKLC is happier with ASCII here
LOCALENAME = "pl-PL"
LOCALEID = "00000415"
LANGNAME = "Polish (Poland)"

# AltGr layer: VK -> (lowercase, uppercase). Anything not listed gets nothing.
ALTGR = {
    # --- standard alphabet ONLY: the four letters a Polish keyboard cannot type ---
    # The extended block (ų ė ę ȯ å ŕ ť ď ć đ ľ ń ś ź) was removed 2026-08-05 on owner
    # order: HOUSE_STYLE.md §1 withdraws extended letters from MS everywhere, so they
    # are not on the keyboard either. (ě is NOT extended — it stays.) Matches the
    # standard-only Android layout.
    "E": ("ě", "Ě"),
    "C": ("č", "Č"),
    "S": ("š", "Š"),
    "Z": ("ž", "Ž"),
    # --- punctuation actually used in our MS texts ---
    "OEM_1": ("„", "“"),        # ; :
    "OEM_7": ("’", "”"),        # ' "
    "OEM_MINUS": ("–", "—"),    # - _
}


def cp(ch):
    """Codepoint as MSKLC expects it: 4-digit lowercase hex, or -1 for nothing."""
    if ch is None:
        return "-1"
    return "%04x" % ord(ch)


def main():
    lines = io.open(SRC, encoding="utf-16").read().splitlines()

    out = []
    in_layout = False
    layout_done = False
    rewritten = 0

    for line in lines:
        # ---- header fields ----
        if line.startswith("KBD\t"):
            out.append(f'KBD\t{KBD_NAME}\t"{DISPLAY}"')
            continue
        if line.startswith("COPYRIGHT\t"):
            out.append('COPYRIGHT\t"(c) 2026 Radoslove"')
            continue
        if line.startswith("COMPANY\t"):
            out.append('COMPANY\t"vault_002"')
            continue
        if line.startswith("LOCALENAME\t"):
            out.append(f'LOCALENAME\t"{LOCALENAME}"')
            continue
        if line.startswith("LOCALEID\t"):
            out.append(f'LOCALEID\t"{LOCALEID}"')
            continue

        # ---- layout body ----
        if line.startswith("LAYOUT"):
            in_layout = True
            out.append(line)
            continue
        if in_layout and not layout_done and re.match(
                r"^(KEYNAME|LIGATURE|DEADKEY|DESCRIPTIONS|ENDKBD)", line):
            in_layout = False
            layout_done = True

        if in_layout and line.strip() and not line.lstrip().startswith("//"):
            # ⚠ DO NOT index raw tab-split cells. The source file uses two different
            #   raw shapes (11 and 10 cells) because some rows carry an extra padding
            #   tab after the VK name. Indexing by position silently shifts every
            #   character one column left on the short rows — which puts the letters
            #   on Ctrl and wipes the Shift column. Caught in verification 2026-08-05.
            #   Filtering empties yields exactly 8 logical fields on all 50 rows:
            #     SC, VK, Cap, base, shift, ctrl, altgr, altgr+shift
            head = line.split("//")[0]
            f = [c for c in head.split("\t") if c.strip() != ""]
            if len(f) == 8:
                sc, vk, cap = f[0], f[1], f[2]
                # strip dead-key markers — this layout has no dead keys
                base = f[3].replace("@", "")
                shift = f[4].replace("@", "")
                ctrl = f[5]
                pair = ALTGR.get(vk.strip())
                if pair:
                    a6, a7 = cp(pair[0]), cp(pair[1])
                    rewritten += 1
                    note = f"// AltGr: {pair[0]} {pair[1]}"
                else:
                    a6, a7 = "-1", "-1"
                    note = "//"
                # one canonical shape for every row
                out.append(
                    f"{sc}\t{vk}\t{cap}\t{base}\t{shift}\t{ctrl}\t{a6}\t{a7}\t{note}")
                continue

        # ---- kill the DEADKEY sections entirely ----
        if line.startswith("DEADKEY"):
            in_layout = False
            layout_done = True
        # (deadkey bodies are dropped below by the skip flag)

        # ---- tail metadata ----
        if re.match(r"^0409\t", line):
            if "Slovenian" in line or LANGNAME in line:
                out.append(f"0409\t{LANGNAME}")
            else:
                out.append(f"0409\t{DISPLAY}")
            continue

        out.append(line)

    # drop any DEADKEY block that survived (its rows are `hex\thex\t//name`)
    cleaned = []
    skipping = False
    for line in out:
        if line.startswith("DEADKEY"):
            skipping = True
            continue
        if skipping:
            if re.match(r"^[0-9a-fA-F]{4}\t[0-9a-fA-F]{4}", line):
                continue
            if not line.strip():
                continue
            skipping = False
        cleaned.append(line)

    dst_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "windows", "src")
    os.makedirs(dst_dir, exist_ok=True)
    dst = os.path.join(dst_dir, KBD_NAME + ".klc")

    # UTF-16LE + CRLF — MSKLC refuses anything else
    with io.open(dst, "w", encoding="utf-16", newline="\r\n") as f:
        f.write("\n".join(cleaned) + "\n")

    print(f"wrote {dst}")
    print(f"  AltGr keys mapped: {rewritten} / {len(ALTGR)} requested")
    print(f"  locale: {LOCALENAME} ({LOCALEID})  — appears under Polish, not Slovenian")
    missing = sorted(set(ALTGR) - {c.split("\t")[1].strip()
                                   for c in cleaned if c.count("\t") >= 7})
    if missing:
        print("  !! requested VK not present in the skeleton:", missing)


if __name__ == "__main__":
    main()
