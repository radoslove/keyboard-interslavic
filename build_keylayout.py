#!/usr/bin/env python3
"""
build_keylayout.py - generate the macOS layout (mac/KBDMSSTD.keylayout).

macOS keyboard layouts are open XML (.keylayout, the format Ukelele writes). No Mac
is needed to PRODUCE one - only to run it. This mirrors the Windows/Android layouts:

  * Base + Shift = plain US ANSI. No dead keys.
  * The Option (Alt) key carries the four letters a US/Polish keyboard cannot type,
    mnemonically: Option+C/S/Z/E -> c-caron / s-caron / z-caron / e-caron, plus the
    punctuation our MS texts use (Option+; ' - -> low/high quotes, en/em dash).
  * Standard orthography ONLY (HOUSE_STYLE.md): the extended letters
    (u e e o a r t d c d l n s z with their marks) are NOT here, same as the
    Android layout and the standard Windows layout.

Install on macOS:
  cp mac/KBDMSSTD.keylayout ~/Library/Keyboard\\ Layouts/
  then System Settings -> Keyboard -> Input Sources -> + -> Others ->
  "Medzuslovjansky (standard)". Switch with the input-source menu / Caps or ^Space.

USAGE
    python build_keylayout.py
"""
import io
import os

KEYBOARD_NAME = "Medžuslovjansky (standard)"   # ě-free display; caron z is fine
KEYBOARD_ID   = "-20126"                             # any unique negative id

# --- US ANSI base: keycode -> (unshifted, shifted) ---
_letters = {
    0: "a", 1: "s", 2: "d", 3: "f", 4: "h", 5: "g", 6: "z", 7: "x", 8: "c", 9: "v",
    11: "b", 12: "q", 13: "w", 14: "e", 15: "r", 16: "y", 17: "t",
    31: "o", 32: "u", 34: "i", 35: "p", 37: "l", 38: "j", 40: "k", 45: "n", 46: "m",
}
BASE = {}
for kc, ch in _letters.items():
    BASE[kc] = (ch, ch.upper())
BASE.update({
    18: ("1", "!"), 19: ("2", "@"), 20: ("3", "#"), 21: ("4", "$"),
    23: ("5", "%"), 22: ("6", "^"), 26: ("7", "&"), 28: ("8", "*"),
    25: ("9", "("), 29: ("0", ")"), 27: ("-", "_"), 24: ("=", "+"),
    33: ("[", "{"), 30: ("]", "}"), 42: ("\\", "|"),
    41: (";", ":"), 39: ("'", "\""), 43: (",", "<"), 47: (".", ">"),
    44: ("/", "?"), 50: ("`", "~"),
    # whitespace / control
    49: (" ", " "), 48: ("\t", "\t"), 36: ("\r", "\r"),
    51: ("\x08", "\x08"), 53: ("\x1b", "\x1b"), 76: ("\r", "\r"),
    # numeric keypad
    82: ("0", "0"), 83: ("1", "1"), 84: ("2", "2"), 85: ("3", "3"), 86: ("4", "4"),
    87: ("5", "5"), 88: ("6", "6"), 89: ("7", "7"), 91: ("8", "8"), 92: ("9", "9"),
    65: (".", "."), 67: ("*", "*"), 69: ("+", "+"), 75: ("/", "/"),
    78: ("-", "-"), 81: ("=", "="),
})

# --- Option layer (index 2) and Shift+Option (index 3): ONLY these keys ---
OPTION = {        # keycode: (option, shift+option)
    14: ("ě", "Ě"),  # E -> e-caron
    8:  ("č", "Č"),  # C -> c-caron
    1:  ("š", "Š"),  # S -> s-caron
    6:  ("ž", "Ž"),  # Z -> z-caron
    41: ("„", "“"),  # ;  -> low / high double quote
    39: ("’", "”"),  # '  -> right single / right double quote
    27: ("–", "—"),  # -  -> en dash / em dash
}


def ent(ch):
    """Every output as a hex char reference - sidesteps all XML escaping."""
    return "&#x%04X;" % ord(ch)


def keymap(index, picker):
    rows = ["    <keyMap index=\"%d\">" % index]
    for kc in sorted(BASE):
        ch = picker(kc)
        if ch is None:
            continue
        rows.append("      <key code=\"%d\" output=\"%s\"/>" % (kc, ent(ch)))
    rows.append("    </keyMap>")
    return "\n".join(rows)


def main():
    maps = []
    # 0 base, 1 shift, 4 caps  -> from BASE ; 2 option, 3 shift+option -> from OPTION
    maps.append(keymap(0, lambda kc: BASE[kc][0]))
    maps.append(keymap(1, lambda kc: BASE[kc][1]))
    maps.append(keymap(2, lambda kc: OPTION[kc][0] if kc in OPTION else None))
    maps.append(keymap(3, lambda kc: OPTION[kc][1] if kc in OPTION else None))
    # caps: letters uppercased, everything else base
    maps.append(keymap(4, lambda kc: BASE[kc][0].upper() if kc in _letters else BASE[kc][0]))

    xml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE keyboard SYSTEM "file://localhost/System/Library/DTDs/KeyboardLayout.dtd">
<keyboard group="126" id="{kid}" name="{name}" maxout="1">
  <layouts>
    <layout first="0" last="0" mapSet="ANSI" modifiers="commonModifiers"/>
  </layouts>
  <modifierMap id="commonModifiers" defaultIndex="0">
    <keyMapSelect mapIndex="0"><modifier keys=""/></keyMapSelect>
    <keyMapSelect mapIndex="1"><modifier keys="anyShift"/></keyMapSelect>
    <keyMapSelect mapIndex="2"><modifier keys="anyOption"/></keyMapSelect>
    <keyMapSelect mapIndex="3"><modifier keys="anyShift anyOption"/></keyMapSelect>
    <keyMapSelect mapIndex="4"><modifier keys="caps"/></keyMapSelect>
  </modifierMap>
  <keyMapSet id="ANSI">
{maps}
  </keyMapSet>
</keyboard>
""".format(kid=KEYBOARD_ID, name=KEYBOARD_NAME, maps="\n".join(maps))

    dst_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mac")
    os.makedirs(dst_dir, exist_ok=True)
    dst = os.path.join(dst_dir, "KBDMSSTD.keylayout")
    with io.open(dst, "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)
    print("wrote", dst)
    print("  keys in base map:", sum(1 for _ in BASE))
    print("  Option overrides:", len(OPTION), "(c s z e + punctuation)")


if __name__ == "__main__":
    main()
