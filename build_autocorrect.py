#!/usr/bin/env python3
"""
build_autocorrect.py — generate the AutoHotkey v2 house-style autocorrect from the
verified extended->standard word map.

WHY A GENERATOR AND NOT A HAND-WRITTEN .ahk
The map is word-level on purpose (HOUSE_STYLE.md §1): a character-level fold would
turn `brať` (to take) into `brat` (brother) and `predviď` into the non-existent
`predvid`. Hand-maintaining ~100 pairs invites exactly the typo that reintroduces
those bugs, so the pairs live here once and the .ahk is emitted.

SOURCE OF THE PAIRS
Extracted from the 2026-08-05 orthography sweep that converted the whole vault
(146 substitutions / 26 files). These are the mistakes actually made in practice,
not a theoretical list.

USAGE
    python build_autocorrect.py          # writes ms_autocorrect.ahk next to this file

WHEN THE MAP GROWS
Add the pair below, re-run, reload the script from the tray. Verify any new MS form
against the medzuslove DB first (`/isv-verify`) — this file must never become a place
where unattested forms get invented.
"""
import io
import os

# extended orthography -> standard orthography.
# Capitalisation is handled by AutoHotkey itself (hotstrings conform to typed case),
# so only lowercase entries belong here.
MAP = {
    # --- nasals: ų / ę ---
    "sę": "se", "imę": "ime", "sųt": "sut", "bijųt": "bijut", "idųt": "idut",
    "rųka": "ruka", "rųky": "ruky", "rųkah": "rukah", "rųkami": "rukami",
    "bųben": "buben", "bųbėn": "buben", "gųsli": "gusli",
    "krųg": "krug", "krųgu": "krugu", "krųgom": "krugom",
    "vodų": "vodu", "črnų": "črnu", "pěsnjų": "pěsnju", "svojų": "svoju",
    "tvojų": "tvoju", "mojų": "moju", "nogų": "nogu", "rěkų": "rěku",
    "goręt": "goret", "znamę": "zname", "zabųdi": "zabudi", "tųga": "tuga",
    "pųt": "put", "pųť": "put", "měsęc": "měsec", "pamęt": "pamet",
    "pamęť": "pamet", "vzęti": "vzeti", "plęsati": "plesati",
    "sųsěd": "susěd", "mųž": "muž", "męč": "meč", "zvųči": "zvuči",
    "zvųčati": "zvučati", "stęg": "steg", "desęť": "deset", "jedinų": "jedinu",
    "radostjų": "radostju", "nočjų": "nočju", "bitvų": "bitvu",
    "nastupnų": "nastupnu", "klaviaturų": "klaviaturu", "hviljejų": "hviljeju",
    "vitęź": "vitez", "vęče": "veče", "najčęstějših": "najčestějših",
    # --- ė ---
    "dėnj": "denj", "tanėc": "tanec", "dnėś": "dnes", "dneś": "dnes",
    "hlåpėc": "hlapec", "grėměti": "greměti", "krėst": "krest",
    "krėstovy": "krestovy",
    # --- ȯ ---
    "sȯlnce": "solnce", "sȯn": "son", "tȯj": "toj", "tȯjže": "tojže",
    "dȯlg": "dolg", "dȯlgy": "dolgy", "dȯlgo": "dolgo", "gȯrdy": "gordy",
    "mȯlčati": "molčati", "ljubȯv": "ljubov", "kȯgda": "kogda",
    "kȯgdy": "kogdy", "prědȯk": "prědok", "brȯnja": "bronja",
    # --- å ---
    "gråd": "grad", "råzsvět": "razsvět", "glås": "glas", "glåsno": "glasno",
    "mlådy": "mlady", "råzum": "razum", "råzumnik": "razumnik",
    "izvråćeny": "izvračeny", "bråniti": "braniti",
    # --- ŕ ---
    "sŕdce": "srdce", "sŕdca": "srdca", "sŕdcu": "srdcu",
    "tŕpělivosť": "trpelivost",
    # --- ť / ď : NOT a bare t/d fold, the -osť class takes -ost ---
    "gosť": "gost", "radosť": "radost", "žalosť": "žalost",
    "gordosť": "gordost", "slabosť": "slabost", "gostinnosť": "gostinnost",
    "ščedrosť": "ščedrost", "trpělivosť": "trpelivost", "malosť": "malost",
    "budućnosť": "budučnost", "lisťje": "listje",
    # ⚠ verbs keep the infinitive -i, they do NOT collapse to a bare stem:
    #   brať -> brati (NOT "brat" = brother), piť -> piti, braniť -> braniti
    "brať": "brati", "piť": "piti", "braniť": "braniti",
    "predviď": "prědvidi", "prěveď": "prěvedi",
    # --- ć / đ / ľ / ń / ś / ź ---
    "noć": "noč", "noćevati": "nočevati", "prěnoćevati": "prěnočevati",
    "polnoć": "polnoč", "ćuđi": "čudži", "ćuđinėc": "čudžinec",
    "čuže": "čudže", "vraćati": "vračati", "nasyćeny": "nasyčeny",
    "slovjańsky": "slovjanjsky", "siľny": "siljny", "odpuščeńje": "odpuščenje",
    "probuđeny": "probudženy",
    # --- wrong cluster, not extended orthography at all ---
    "šťestlivy": "ščestlivy", "šťestliva": "ščestliva", "šťestje": "ščestje",
}

# Typing aids: reach the STANDARD letters without AltGr.
# `;` prefix because it never occurs mid-word — a plain `cx`/`sx` trigger would fire
# inside ordinary English/Polish typing.
SHORTCUTS = {
    ";c": "č", ";s": "š", ";z": "ž", ";e": "ě",
    ";C": "Č", ";S": "Š", ";Z": "Ž", ";E": "Ě",
    ";dz": "dž", ";Dz": "Dž",
}

HEADER = r'''#Requires AutoHotkey v2.0
#SingleInstance Force
; ============================================================================
;  ms_autocorrect.ahk — Interslavic house-style autocorrect for Windows
;
;  GENERATED FILE — do not edit by hand.
;  Source: Projects\INTERSLOVE\keyboards\build_autocorrect.py
;  Rules:  Projects\INTERSLOVE\HOUSE_STYLE.md
;
;  WHAT IT DOES
;    1. Rewrites extended-orthography words to standard as you finish typing them
;       (`sųt` -> `sut`, `dėnj` -> `denj`). Word-level, fires on the word-ending
;       character — never mid-word, so it cannot mangle a longer word.
;    2. `;`-prefixed shortcuts for the four standard letters a Polish keyboard
;       lacks, for when AltGr is inconvenient.
;
;  WHAT IT DOES NOT DO
;    No suggestions, no swipe, no dictionary lookup — Windows has no user-extensible
;    prediction engine. This is the closest desktop analogue to the Android setup.
;
;  PRIMARY INPUT PATH IS STILL THE KEYBOARD LAYOUT
;    "Medžuslovjansky (latinica)" is installed: AltGr+C=c-caron, AltGr+S, AltGr+Z,
;    AltGr+E=e-caron (plus the whole extended block). This script is a safety net on
;    top of it, not a replacement.
;
;  Ctrl+Alt+M  toggles the autocorrect off/on (the shortcuts keep working).
; ============================================================================

TraySetIcon("shell32.dll", 45)
A_IconTip := "Interslavic house-style autocorrect"

global MSCorrect := true

^!m:: {
    global MSCorrect
    MSCorrect := !MSCorrect
    Suspend(!MSCorrect)
    TrayTip("Interslavic autocorrect", MSCorrect ? "ON" : "OFF", 1)
}
'''


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "ms_autocorrect.ahk")

    lines = [HEADER, ""]

    lines.append("; --- typing aids: standard letters without AltGr ---")
    # `*` = fire immediately, no ending character needed.
    # `C` = case-sensitive, so `;s` and `;S` stay distinct.
    for k, v in SHORTCUTS.items():
        lines.append(f":*C:{k}::{v}")
    lines.append("")

    lines.append("; --- house-style corrections: extended -> standard (word-level) ---")
    lines.append(f"; {len(MAP)} pairs, from the 2026-08-05 vault sweep")
    # No options: the hotstring requires a word-ending character (that IS the
    # word-boundary guarantee) and conforms to the typed capitalisation, so a
    # separate capitalised entry per pair is unnecessary.
    for k in sorted(MAP, key=lambda s: (-len(s), s)):
        lines.append(f"::{k}::{MAP[k]}")
    lines.append("")

    text = "\n".join(lines)
    # AHK v2 wants UTF-8; the BOM makes the non-ASCII unambiguous on Windows.
    with io.open(out, "w", encoding="utf-8-sig", newline="\r\n") as f:
        f.write(text)

    print(f"wrote {out}")
    print(f"  {len(SHORTCUTS)} typing shortcuts")
    print(f"  {len(MAP)} autocorrect pairs")

    # sanity: no pair may map onto another pair's trigger (would ping-pong)
    collisions = sorted(set(MAP.values()) & set(MAP.keys()))
    if collisions:
        print("  !! WARNING, replacement is also a trigger:", collisions)
    else:
        print("  no trigger/replacement collisions")


if __name__ == "__main__":
    main()
