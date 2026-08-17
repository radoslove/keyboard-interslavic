#!/usr/bin/env python3
"""
build_keyman.py - generate the Keyman keyboard (keyman/isv_latin/source/).

This is the iOS/iPadOS track. iOS has no layout-file format at all: a third-party
keyboard there is a full app. Keyman is the way around that - it is a free App Store
app that loads .kmp keyboard packages, so we ship a package instead of an app.
The same .kmp also installs on Android, Windows, macOS and Linux.

Three artefacts come out of the one table below:

  * isv_latin.kmn                  - the rules. RALT (AltGr) mirrors the Windows
                                     layout key-for-key, so desktop behaviour is
                                     identical to windows/src/KBDMSSTD.klc.
  * isv_latin.keyman-touch-layout  - the phone layout. There is no AltGr on
                                     a touchscreen, so the same letters sit on
                                     LONGPRESS of their base letter (hold c -> c).
  * isv_latin.kvks                 - the on-screen keyboard. Desktop users who
                                     cannot see AltGr legends on their physical
                                     keycaps read this instead.

Standard orthography ONLY (HOUSE_STYLE.md §1), matching the Windows .klc and the
macOS .keylayout: c-caron, s-caron, z-caron, e-caron plus the punctuation our MS
texts actually use. The extended block is deliberately absent.

GOTCHA - touch subkeys cannot reach AltGr. The valid `layer` values in a touch
layout are only shift/ctrl/alt/ctrlshift/altshift/ctrlalt/ctrlaltshift; `rightalt`
is not one of them. So the longpress keys are touch-only T_* keys with their own
rules, NOT a re-use of the RALT rules. Both sets are emitted from LETTERS below,
which is what keeps desktop and touch in agreement.

CATALOGUE REVIEW - keymanapp/keyboards#4092, LornaSIL, 2026-08-10. Four changes
came out of that review and all four are encoded here rather than hand-patched
into the generated files:

  1. The .kvks was an empty stub, so the On-Screen Keyboard showed nothing. It
     is now generated in full: the underlying US layout on default/shift (the
     "Fill from layout" step) plus our RALT/SHIFT+RALT cells, with the
     `displayunderlying` and `usealtgr` flags set (the "Auto-fill underlying
     layout" checkbox). An OSK user now sees which key to press.
  2. &TARGETS is 'any' rather than the hand-listed platform soup. 'any' is the
     documented value for "compile for all platforms" and is what the compiler
     defaults to; enumerating them added nothing and could only drift.
  3. The touch layout emits the `phone` form ONLY. It used to emit an identical
     `tablet` form as well; two byte-identical copies is maintenance debt, and
     the phone form is used on tablets when no tablet form is present.
  4. Online help lives in source/help/isv_latin.php - required for `release`.
     It is hand-maintained prose (like welcome.htm and readme.htm) and guarded
     against drift by check_docs.py, not generated here.

USAGE
    python3 build_keyman.py
    kmc build keyman/isv_latin/isv_latin.kpj
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "keyman", "isv_latin", "source")

KEYBOARD_NAME = "Medžuslovjansky (latinica)"
COPYRIGHT = "© Radoslove"
KEYBOARD_VERSION = "1.6"

# --- the canonical table -------------------------------------------------
# base key -> (lowercase, uppercase, T_ key stem)
# The base key is BOTH the AltGr key on desktop and the longpress host on touch;
# for these four they happen to coincide, which is why the mnemonic works.
LETTERS = [
    ("K_C", "č", "Č", "CCARON"),
    ("K_S", "š", "Š", "SCARON"),
    ("K_Z", "ž", "Ž", "ZCARON"),
    ("K_E", "ě", "Ě", "ECARON"),
]

# desktop-only: AltGr punctuation, same cells as the Windows layout
PUNCT = [
    ("K_COLON", "„", "“"),   # ;  -> low / high double quote
    ("K_QUOTE", "’", "”"),   # '  -> right single / right double quote
    ("K_HYPHEN", "–", "—"),  # -  -> en dash / em dash
]

# touch-only punctuation keys (longpress), name -> character
TOUCH_PUNCT = [
    ("QUOTLOW", "„"),
    ("QUOTHIGH", "”"),
    ("ENDASH", "–"),
    ("EMDASH", "—"),
]

# The underlying physical layout, for the on-screen keyboard: (vkey, base, shift).
# This keyboard overrides NOTHING on the base or shift layers - every rule it has
# is on RALT - so the OSK's default/shift layers are simply US, unmodified. That
# is the point of the reviewer's "Fill from layout": without it the OSK is blank
# and a user has no way to see that č is AltGr+C.
BASE_LAYOUT = [
    ("K_BKQUOTE", "`", "~"),
    ("K_1", "1", "!"), ("K_2", "2", "@"), ("K_3", "3", "#"),
    ("K_4", "4", "$"), ("K_5", "5", "%"), ("K_6", "6", "^"),
    ("K_7", "7", "&"), ("K_8", "8", "*"), ("K_9", "9", "("),
    ("K_0", "0", ")"),
    ("K_HYPHEN", "-", "_"), ("K_EQUAL", "=", "+"),
    ("K_Q", "q", "Q"), ("K_W", "w", "W"), ("K_E", "e", "E"),
    ("K_R", "r", "R"), ("K_T", "t", "T"), ("K_Y", "y", "Y"),
    ("K_U", "u", "U"), ("K_I", "i", "I"), ("K_O", "o", "O"),
    ("K_P", "p", "P"),
    ("K_LBRKT", "[", "{"), ("K_RBRKT", "]", "}"), ("K_BKSLASH", "\\", "|"),
    ("K_A", "a", "A"), ("K_S", "s", "S"), ("K_D", "d", "D"),
    ("K_F", "f", "F"), ("K_G", "g", "G"), ("K_H", "h", "H"),
    ("K_J", "j", "J"), ("K_K", "k", "K"), ("K_L", "l", "L"),
    ("K_COLON", ";", ":"), ("K_QUOTE", "'", "\""),
    ("K_Z", "z", "Z"), ("K_X", "x", "X"), ("K_C", "c", "C"),
    ("K_V", "v", "V"), ("K_B", "b", "B"), ("K_N", "n", "N"),
    ("K_M", "m", "M"),
    ("K_COMMA", ",", "<"), ("K_PERIOD", ".", ">"), ("K_SLASH", "/", "?"),
    ("K_SPACE", "", ""),
]


# --- .kmn ----------------------------------------------------------------
def build_kmn():
    L = []
    add = L.append
    add("c isv_latin - Interslavic (Latin), standard orthography")
    add("c GENERATED by build_keyman.py - do not edit by hand")
    add("")
    add("store(&NAME) '%s'" % KEYBOARD_NAME)
    add("store(&COPYRIGHT) '%s'" % COPYRIGHT)
    # Stays at 10.0 even though flicks need Keyman 17+. Raising it would lock
    # out everyone on an older Keyman for a feature that is purely additive -
    # they still have longpress, which is what 10.0 supports. The compiler
    # hints about this on purpose; the hint is the correct outcome here.
    add("store(&VERSION) '10.0'")
    add("store(&KEYBOARDVERSION) '%s'" % KEYBOARD_VERSION)
    # 'any' == compile for all platforms, and is the compiler's own default.
    # The previous hand-written list enumerated every platform individually,
    # which says exactly the same thing in a form that can rot.
    add("store(&TARGETS) 'any'")
    add("store(&VISUALKEYBOARD) 'isv_latin.kvks'")
    add("store(&LAYOUTFILE) 'isv_latin.keyman-touch-layout'")
    add("store(&BITMAP) 'isv_latin.ico'")
    add("")
    add("begin Unicode > use(main)")
    add("")
    add("group(main) using keys")
    add("")
    add("c --- desktop: AltGr, mirroring windows/src/KBDMSSTD.klc ---")
    for key, lo, up, _stem in LETTERS:
        add("+ [RALT %s] > '%s'" % (key, lo))
        add("+ [SHIFT RALT %s] > '%s'" % (key, up))
    add("")
    for key, lo, up in PUNCT:
        add("+ [RALT %s] > '%s'" % (key, lo))
        add("+ [SHIFT RALT %s] > '%s'" % (key, up))
    add("")
    add("c --- touch: longpress keys. Subkeys cannot reach RALT, so these")
    add("c     touch-only T_ keys carry the same letters explicitly.")
    add("c")
    add("c     GOTCHA, paid for on a device twice: a subkey on the SHIFT layer")
    add("c     fires with the shift modifier held, so a bare '+ [T_X]' rule does")
    add("c     NOT match there and the key silently emits nothing. The fix is the")
    add("c     [SHIFT ...] rules below. Do NOT also pin the subkey to")
    add("c     layer 'default' in the touch layout - that breaks id resolution")
    add("c     and the subkey falls back to its parent letter (C instead of Č).")
    for _key, lo, up, stem in LETTERS:
        add("+ [T_%s] > '%s'" % (stem, lo))
        add("+ [SHIFT T_%s] > '%s'" % (stem, lo))
        add("+ [T_%s_UC] > '%s'" % (stem, up))
        add("+ [SHIFT T_%s_UC] > '%s'" % (stem, up))
    add("")
    for stem, ch in TOUCH_PUNCT:
        add("+ [T_%s] > '%s'" % (stem, ch))
        add("+ [SHIFT T_%s] > '%s'" % (stem, ch))
    add("")
    return "\n".join(L) + "\n"


# --- .kvks (on-screen keyboard) ------------------------------------------
# Shift-state codes in a .kvks layer are NOT the touch-layout names: they are
# "" (base), "S" (shift), "RA" (right alt) and "SRA" (shift + right alt).
# Verified against real catalogue keyboards (release/e/enga, e/east_syriac_qwerty)
# rather than guessed - the touch-layout vocabulary does not carry over.
def build_kvks():
    def esc(s):
        return (s.replace("&", "&amp;").replace("<", "&lt;")
                 .replace(">", "&gt;"))

    def layer(shift, pairs):
        out = ['    <layer shift="%s">' % shift]
        for vkey, text in pairs:
            out.append("      <key vkey=\"%s\">%s</key>" % (vkey, esc(text)))
        out.append("    </layer>")
        return out

    base = [(vk, lo) for vk, lo, _up in BASE_LAYOUT]
    shift = [(vk, up) for vk, _lo, up in BASE_LAYOUT]

    # RALT layers carry only what this keyboard actually maps there.
    ralt, sralt = [], []
    for vkey, lo, up, _stem in LETTERS:
        ralt.append((vkey, lo))
        sralt.append((vkey, up))
    for vkey, lo, up in PUNCT:
        ralt.append((vkey, lo))
        sralt.append((vkey, up))

    L = ['<?xml version="1.0" encoding="utf-8"?>',
         "<visualkeyboard>",
         "  <header>",
         "    <version>10.0</version>",
         "    <kbdname>isv_latin</kbdname>",
         "    <flags>",
         # "Auto-fill underlying layout" - show the physical keycaps under the
         # Keyman legends, so the OSK is readable on any national layout.
         "      <displayunderlying/>",
         # This keyboard's entire payload is on AltGr; without this the OSK
         # gives the user no way to reach the RA/SRA layers at all.
         "      <usealtgr/>",
         "    </flags>",
         "  </header>",
         '  <encoding name="unicode" fontname="Tahoma" fontsize="-12">']
    L += layer("", base)
    L += layer("S", shift)
    L += layer("RA", ralt)
    L += layer("SRA", sralt)
    L += ["  </encoding>", "</visualkeyboard>"]
    return "\n".join(L) + "\n"


# --- touch layout --------------------------------------------------------
ROW1 = "qwertyuiop"
ROW2 = "asdfghjkl"
ROW3 = "zxcvbnm"

# longpress hosts: base letter -> list of (text, T_ id)
def subkeys(upper):
    out = {}
    for _key, lo, up, stem in LETTERS:
        host = lo[0] if not upper else up[0]
        # host letter is the plain ascii base: c/s/z/e
        base = {"č": "c", "š": "s", "ž": "z", "ě": "e"}[lo]
        host = base.upper() if upper else base
        out[host] = [{"text": up if upper else lo,
                      "id": "T_%s%s" % (stem, "_UC" if upper else "")}]
    return out


def key(char, upper=False, sk_map=None):
    k = {"id": "K_" + char.upper(), "text": char}
    if sk_map and char in sk_map:
        k["sk"] = sk_map[char]
        # Longpress is unreliable here and it is NOT our bug: Keyman's own
        # EuroLatin keyboard misses just as often on the same iPad. Keyman
        # wants you to slide onto the popup before releasing, and the popup
        # lands in a different place for every key (it gets nudged inward near
        # the screen edges), so the "right" direction differs per key and no
        # habit ever forms.
        #
        # Flicks are entirely ours, so all EIGHT directions map to the letter.
        # Restricting them was a mistake twice over: `ne` alone worked only
        # "quite often", and up-and-right still lost every swipe that drifted
        # low or left. Now any swipe in any direction produces the letter and
        # precision stops mattering altogether - which makes this keyboard more
        # reliable than the stock one it is modelled on.
        DIRECTIONS = ("n", "ne", "e", "se", "s", "sw", "w", "nw")
        k["flick"] = {d: dict(sk_map[char][0]) for d in DIRECTIONS}
    return k


def period_key(upper):
    """The . key keeps its stock punctuation popup, plus the MS quotes."""
    sk = [
        {"text": ",", "id": "K_COMMA"},
        {"text": "!", "id": "K_1", "layer": "shift"},
        {"text": "?", "id": "K_SLASH", "layer": "shift"},
        {"text": "„", "id": "T_QUOTLOW"},
        {"text": "”", "id": "T_QUOTHIGH"},
        {"text": "'", "id": "K_QUOTE"},
        {"text": "\"", "id": "K_QUOTE", "layer": "shift"},
        {"text": ":", "id": "K_COLON", "layer": "shift"},
        {"text": ";", "id": "K_COLON"},
    ]
    return {"id": "K_PERIOD", "text": ".", "sk": sk}


def letter_layer(layer_id, upper):
    sk_map = subkeys(upper)
    cases = (lambda s: s.upper()) if upper else (lambda s: s)
    r1 = [key(cases(c), upper, sk_map) for c in ROW1]
    r2 = [key(cases(c), upper, sk_map) for c in ROW2]
    r2[0]["pad"] = "50"
    r2.append({"text": "", "width": "10", "sp": "10"})
    r3 = [{"id": "K_SHIFT", "text": "*Shift*", "sp": "1",
           "nextlayer": "default" if upper else "shift"}]
    r3 += [key(cases(c), upper, sk_map) for c in ROW3]
    r3.append(period_key(upper))
    r3.append({"id": "K_BKSP", "text": "*BkSp*", "width": "100", "sp": "1"})
    r4 = [
        {"id": "K_NUMLOCK", "text": "*123*", "width": "150", "sp": "1",
         "nextlayer": "numeric"},
        {"id": "K_LOPT", "text": "*Menu*", "width": "120", "sp": "1"},
        {"id": "K_SPACE", "text": "", "width": "610", "sp": "0"},
        {"id": "K_ENTER", "text": "*Enter*", "width": "150", "sp": "1"},
    ]
    return {"id": layer_id,
            "row": [{"id": 1, "key": r1}, {"id": 2, "key": r2},
                    {"id": 3, "key": r3}, {"id": 4, "key": r4}]}


def numeric_layer():
    digits = [{"id": "K_" + d, "text": d} for d in "1234567890"]
    sym = [("K_4", "$"), ("K_2", "@"), ("K_3", "#"), ("K_5", "%"),
           ("K_7", "&"), ("K_HYPHEN", "_"), ("K_EQUAL", "="),
           ("K_BKSLASH", "|"), ("K_BKSLASH", "\\")]
    r2 = [{"id": i, "text": t} for i, t in sym]
    r2.append({"text": "", "width": "10", "sp": "10"})
    r3 = [
        {"id": "K_LBRKT", "text": "["},
        {"id": "K_9", "text": "("},
        {"id": "K_0", "text": ")"},
        {"id": "K_RBRKT", "text": "]"},
        {"id": "K_EQUAL", "text": "+"},
        {"id": "K_HYPHEN", "text": "-",
         "sk": [{"text": "–", "id": "T_ENDASH"},
                {"text": "—", "id": "T_EMDASH"}]},
        {"id": "K_8", "text": "*"},
        {"id": "K_SLASH", "text": "/"},
        {"id": "K_BKSP", "text": "*BkSp*", "width": "100", "sp": "1"},
    ]
    r4 = [
        {"id": "K_LOWER", "text": "*abc*", "width": "150", "sp": "1",
         "nextlayer": "default"},
        {"id": "K_LOPT", "text": "*Menu*", "width": "120", "sp": "1"},
        {"id": "K_SPACE", "text": "", "width": "610", "sp": "0"},
        {"id": "K_ENTER", "text": "*Enter*", "width": "150", "sp": "1"},
    ]
    return {"id": "numeric",
            "row": [{"id": 1, "key": digits}, {"id": 2, "key": r2},
                    {"id": 3, "key": r3}, {"id": 4, "key": r4}]}


def build_touch_layout():
    layers = [letter_layer("default", False),
              letter_layer("shift", True),
              numeric_layer()]
    form = {"font": "Tahoma", "layer": layers}
    # PHONE ONLY, on catalogue review (#4092). There used to be a `tablet` form
    # too, byte-identical to this one - it was added on the theory that an iPad
    # needs its own form, but an identical second copy buys nothing and has to
    # be kept in step by hand forever. Keyman uses the phone form on tablets
    # when no tablet form is supplied, which is the same layout either way.
    # NOTE: the on-device iPad pass was done WITH the tablet form present, so
    # the phone-only build has not itself been confirmed on hardware.
    return {"phone": form}


def main():
    os.makedirs(SRC, exist_ok=True)

    kmn_path = os.path.join(SRC, "isv_latin.kmn")
    with open(kmn_path, "w", encoding="utf-8") as f:
        f.write(build_kmn())
    print("wrote %s" % kmn_path)

    tl_path = os.path.join(SRC, "isv_latin.keyman-touch-layout")
    with open(tl_path, "w", encoding="utf-8") as f:
        json.dump(build_touch_layout(), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote %s" % tl_path)

    kvks_path = os.path.join(SRC, "isv_latin.kvks")
    with open(kvks_path, "w", encoding="utf-8") as f:
        f.write(build_kvks())
    print("wrote %s" % kvks_path)


if __name__ == "__main__":
    main()
