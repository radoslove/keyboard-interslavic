#!/usr/bin/env python3
"""
build_glag_desktop.py - Glagolitic keyboards for computers, from the PHONE's tables.

Two keyboards, two routes:

  1. Interslavic in Glagolitic (isv-Glag), NATIVE layouts:
       windows/src/KBDISVGL.klc      -> MSKLC kbdutool -> windows/installers/kbdisvgl/
       mac/ISVGLAG.keylayout
     One key = one output, like a tap on the phone. The letters are the ones the phone
     writes on the same keys (docs/script-tables.md, "Rows per script"), layout 261124:
     q = ja Ⱑ, w = v Ⰲ, v = soft yer Ⱐ, x = dzelo Ⰷ, y = ⱏⰹ. AltGr / Option keep the
     261119 placements (c s z e d l n a u o j) and add v = hard yer Ⱏ (the phone's hold on v).

  2. Polish in Glagolitic (pl-Glag), KEYMAN, because Polish Glagolitic needs context
     (c + z = cz, r + z = rz, j + a = ja ...), which neither .klc nor .keylayout can do:
       keyman/pl_glag/   -> kmc build -> keyman/pl_glag.kmp  (Windows, macOS, Linux)
     Typed Polish the usual way (AltGr letters as on the Polish programmer's layout),
     q = ja Ⱑ and x = dzelo Ⰷ as on the phone (261124);
     each key looks at the glyph before the cursor and rewrites it, so the result is
     the same as converting the whole word.

ONE TABLE. Nothing here is typed from memory: the letter tables are READ from the
Android sources (Script.kt GLAG_LETTERS / J_LIGATURE / POLISH_GLAG, Layout.kt isvOwn,
isvGlagHolds, polishGlagHolds and keyText). `--check` then proves the outputs against the
phone (Android AND iOS tables), against docs/script-tables.md, and against the Polish
canon pl2glag.py - where m = ⰿ (canon) vs ⱞ (phone, 261124), the x key and the ǒ marker
are a NAMED known difference that prints but does not fail.

USAGE
    python -X utf8 build_glag_desktop.py            # write the three sources
    python -X utf8 build_glag_desktop.py --check    # + all checks (exit 1 on a mismatch)
    python -X utf8 build_glag_desktop.py --negative-control
        # builds into a temp folder, breaks one cell in each output and one phone entry,
        # and exits 0 only if the checks FAIL on them (repo files untouched)

⚠ Without --negative-control the script (re)writes the three sources in the repo.

.klc = UTF-16LE + CRLF (.gitattributes keeps git away from it).
"""
import argparse
import io
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
KT = HERE / "android-app/app/src/main/kotlin/com/radoslove/interslavic"
SWIFT = HERE / "ios/Keyboard"

# ---------------------------------------------------------------- reading the phone


def kt_block(text, start):
    """The text of `start ... (` up to its matching `)`."""
    i = text.index(start)
    j = text.index("(", i)
    depth = 0
    for k in range(j, len(text)):
        if text[k] == "(":
            depth += 1
        elif text[k] == ")":
            depth -= 1
            if depth == 0:
                return text[j + 1:k]
    raise ValueError(start)


def kt_unesc(x):
    r"""Kotlin / Swift string escapes: \u01c9 and \u{02B9}."""
    x = re.sub(r"\\u\{([0-9A-Fa-f]+)\}", lambda m: chr(int(m.group(1), 16)), x)
    return re.sub(r"\\u([0-9A-Fa-f]{4})", lambda m: chr(int(m.group(1), 16)), x)


def items(body, consts):
    """`"a", Script.HARD_YER_MARK, "b"` -> ['a', 'ʺ', 'b'] (Kotlin or Swift constant names)."""
    out = []
    for m in re.finditer(r'"((?:[^"\\]|\\.)*)"|(?:Script\.)?(\w+)', body):
        if m.group(1) is not None:
            out.append(kt_unesc(m.group(1)))
        elif m.group(2) in consts:
            out.append(consts[m.group(2)])
        else:
            raise ValueError("unknown constant " + m.group(2))
    return out


def holds(body, consts, quote):
    """`'q' to listOf(...)` (Kotlin) or `"q": [...]` (Swift) -> {key: [items]}."""
    pat = r"'(.)' to listOf\(([^)]*)\)" if quote == "'" else r'"(.)": \[([^\]]*)\]'
    return {k: items(v, consts) for k, v in re.findall(pat, body)}


def when_block(text, head):
    """The `'x' -> ...` arms of the `when (ch) {` that follows `head`."""
    i = text.index(head)
    j = text.index("{", i)
    return text[j + 1:text.index("}", j)]


def read_android():
    script = (KT / "Script.kt").read_text(encoding="utf-8")
    layout = (KT / "Layout.kt").read_text(encoding="utf-8")
    consts = {k: kt_unesc(v) for k, v in re.findall(r'const val (\w+) = "([^"]*)"', script)}

    def pairs_char(body):
        return {kt_unesc(k): kt_unesc(v) for k, v in re.findall(r"'((?:\\u[0-9A-Fa-f]{4})|.)' to \"([^\"]+)\"", body)}

    letters = pairs_char(kt_block(script, "private val GLAG_LETTERS = mapOf"))
    jlig = pairs_char(kt_block(script, "private val J_LIGATURE = mapOf"))
    pl = []
    body = kt_block(script, "private val POLISH_GLAG: List<Pair<String, String>> = listOf")
    body = re.sub(r"//[^\n]*", "", body)
    for m in re.finditer(r'(?:"([^"]+)"|(SOFT_YER_MARK|HARD_YER_MARK)) to "([^"]+)"', body):
        pl.append((kt_unesc(m.group(1)) if m.group(1) is not None else consts[m.group(2)],
                   kt_unesc(m.group(3))))
    isv_own = holds(kt_block(layout, "private val isvOwn: Map<Char, List<String>> = mapOf"), consts, "'")
    isv_glag_holds = holds(kt_block(layout, "private val isvGlagHolds: Map<Char, List<String>> = mapOf"),
                           consts, "'")
    pl_holds = holds(re.sub(r"//[^\n]*", "", kt_block(
        layout, "private val polishGlagHolds: Map<Char, List<String>> = mapOf")), consts, "'")
    # keyText: what a key WRITES in Glagolitic (Interslavic block and Polish block)
    kt = layout[layout.index("fun keyText"):]
    kt = kt[:kt.index("\n    }\n")]

    def arms(head):
        body = re.sub(r"//[^\n]*", "", when_block(kt, head))
        return {k: items(v, consts)[0] for k, v in re.findall(r"'(.)' -> ([^\n]+)", body)
                if k != "e" or "else" not in v}

    isv_keys = arms("isvGlag(script, lang) -> when (ch)")
    pl_keys = arms("lang == Lang.PL -> when (ch)")
    titlo = re.search(r'const val PLAIN = "([^"]*)"', (KT / "Titlo.kt").read_text(encoding="utf-8")).group(1)
    return dict(letters=letters, jlig=jlig, pl=pl, isv_own=isv_own, isv_glag_holds=isv_glag_holds,
                pl_holds=pl_holds, isv_keys=isv_keys, pl_keys=pl_keys, consts=consts, titlo=kt_unesc(titlo))


def read_ios():
    """iOS at HEAD (git), so a half-edited working tree on another machine cannot leak in."""
    import subprocess
    s = subprocess.run(["git", "-C", str(HERE), "show", "HEAD:ios/Keyboard/Script.swift"],
                       capture_output=True, check=True).stdout.decode("utf-8")
    lay = subprocess.run(["git", "-C", str(HERE), "show", "HEAD:ios/Keyboard/Layout.swift"],
                         capture_output=True, check=True).stdout.decode("utf-8")
    consts = {k: kt_unesc(v) for k, v in re.findall(r'static let (\w+) = "([^"]*)"', s)}
    i = s.index("glagLetters: [Character: String] = [")
    blk = re.sub(r"//[^\n]*", "", s[i:s.index("\n    ]", i)])
    letters = {kt_unesc(a): kt_unesc(b) for a, b in re.findall(r'"((?:\\u\{[0-9A-Fa-f]+\})|.)": "([^"]+)"', blk)}
    i = s.index("jLigature: [Character: String] = [") + len("jLigature: [Character: String] = [")
    jlig = {a: b for a, b in re.findall(r'"(.)": "([^"]+)"', s[i:s.index("]", i)])}
    i = s.index("polishGlag: [(String, String)] = [")
    j = s.index("\n    ]", i)
    blk = re.sub(r"//[^\n]*", "", s[i:j])
    pl = [(kt_unesc(a), kt_unesc(b)) for a, b in re.findall(r'\("([^"]+)", "([^"]+)"\)', blk)]
    i = lay.index("let glagHolds: [Character: [String]] = [")
    pl_holds = holds(re.sub(r"//[^\n]*", "", lay[i:lay.index("\n            ]", i)]), consts, '"')
    i = lay.index("let isvGlagHolds: [Character: [String]] = [")
    isv_glag_holds = holds(lay[i:lay.index("]\n", lay.index("hardYerMark", i))], consts, '"')
    titlo = kt_unesc(re.search(r'static let plain = "([^"]*)"', s).group(1))
    return dict(letters=letters, jlig=jlig, pl=pl, pl_holds=pl_holds, isv_glag_holds=isv_glag_holds,
                titlo=titlo)


# ---------------------------------------------------------------- v1 converter (port of Script.glagolitic)

def isv_convert(latin, A):
    vowels = set("aeěiouy")
    low = latin.lower()
    caps = any(c.isalpha() for c in latin) and not any(c.islower() for c in latin)

    def cased(m, src):
        if not src.isupper():
            return m
        return m.upper() if caps else m[0].upper() + m[1:]

    out, i = [], 0
    while i < len(latin):
        c = low[i]
        if c == "j":
            nxt = low[i + 1] if i + 1 < len(low) else None
            prev = low[i - 1] if i > 0 else None
            lig = A["jlig"].get(nxt) if nxt else None
            if lig:
                out.append(cased(lig, latin[i])); i += 2; continue
            if nxt and nxt in vowels:
                out.append(cased("ⰹ", latin[i]))
            elif prev in ("l", "n"):
                out.append(cased("ⱐ", latin[i]))
            else:
                out.append(cased("ⰹ", latin[i]))
            i += 1
            continue
        if c == "d" and i + 1 < len(low) and low[i + 1] == "z":     # every d + z = dzelo (261124)
            out.append(cased(A["letters"]["ẑ"], latin[i])); i += 2; continue
        m = A["letters"].get(c)
        out.append(cased(m, latin[i]) if m else latin[i])
        i += 1
    return "".join(out)


def up(s):
    """A capital = the first sign upper-case (Script.cased, word not all caps)."""
    return s[0].upper() + s[1:] if s else s


# ---------------------------------------------------------------- 1. isv-Glag layout

# VK name -> Latin key (letters only); the skeleton comes from KBDMSSTD.klc.
LETTER_VKS = {c.upper(): c for c in "abcdefghijklmnopqrstuvwxyz"}

PUNCT_ALTGR = {                     # same as KBDMSSTD (isv Latin on the computer)
    "OEM_1": ("„", "“"), "OEM_7": ("’", "”"), "OEM_MINUS": ("–", "—"),
}

# The titlo, U+0483, a COMBINING mark typed after the letter (the phone's Titlo.PLAIN,
# read from Titlo.kt / Script.swift and checked to be U+0483). Owner 261124: on BOTH
# AltGr+T and AltGr+` (grave key: OEM_3 on Windows, keycodes 50 and 10 = ` and § on the
# Mac). Shift+AltGr writes the same mark (more routes to one sign, never a dead cell).
TITLO = "҃"
TITLO_KEYS_WIN = ("OEM_3",)
TITLO_KEYS_MAC = (50, 10)
PUNCT_ALTGR.update({vk: (TITLO, TITLO) for vk in TITLO_KEYS_WIN})

# AltGr layer, layout 261124. The desktop has no hold, so the phone's held signs sit on
# AltGr. Placement is the OWNER's (decisions 261124): the 261119 cells kept where the sign
# still exists (c s z e d l n from the holds of the same key; a u o were the 261119
# j-ligature cells, now the phone's hold on q: ⱝ ⱓ ⱖ; j = the bare soft yer, KEPT although
# the v key writes it too - the owner wants more routes to one sign), v = hard yer Ⱏ (the
# phone's hold on v) and t = titlo. Values are Latin INSIDE (as on the phone), converted by
# the phone's own table; the titlo passes the converter unchanged.
ISV_ALTGR = {
    "c": "č", "s": "š", "z": "ž", "e": "ě", "d": "dž", "l": "lj", "n": "nj",
    "a": "ȧ", "u": "ju", "o": "jo", "j": "ʹ",
    "v": "ʺ", "t": TITLO,
}
ISV_ALTGR_NEW_261124 = {"v", "t"}


def phone_isv_signs(A):
    """Every Latin-inside value the phone can write in isv Glagolitic by a key or a hold."""
    s = set(A["isv_keys"].values())
    for v in list(A["isv_own"].values()) + list(A["isv_glag_holds"].values()):
        s.update(v)
    return s


def isv_table(A):
    """Latin key -> (base, AltGr, source of AltGr), outputs in Glagolitic, lower case."""
    t = {}
    for k in "abcdefghijklmnopqrstuvwxyz":
        base = isv_convert(A["isv_keys"].get(k, k), A)
        lat = ISV_ALTGR.get(k)
        alt = isv_convert(lat, A) if lat else None
        if lat is None:
            src = None
        elif lat == TITLO:
            src = "phone titlo (Titlo.PLAIN, under every letter's hold)  (NEW 261124)"
        else:
            where = [key for key, v in list(A["isv_own"].items()) + list(A["isv_glag_holds"].items())
                     if lat in v] or [key for key, v in A["isv_keys"].items() if v == lat]
            how = "hold" if any(lat in v for v in list(A["isv_own"].values())
                                + list(A["isv_glag_holds"].values())) else "key"
            src = f"phone {how} {'/'.join(sorted(set(where)))}: {lat}" + \
                ("  (NEW 261124)" if k in ISV_ALTGR_NEW_261124 else "  (261119 cell)")
        t[k] = (base, alt, src)
    return t


def klc_cell(s):
    if s is None:
        return "-1"
    return "%%" if len(s) > 1 else "%04x" % ord(s)


KLC_NAME = "KBDISVGL"
KLC_DISPLAY = "Glagolitic (Interslavic)"   # PLACEHOLDER, English - isv name via interslavic-tutor


def write_klc(A, dst):
    t = isv_table(A)
    src = (HERE / "windows/src/KBDMSSTD.klc").read_text(encoding="utf-16").splitlines()
    out, ligs, in_layout, skip_lig = [], [], False, False
    for line in src:
        if line.startswith("KBD\t"):
            out.append(f'KBD\t{KLC_NAME}\t"{KLC_DISPLAY}"'); continue
        if line.startswith("LAYOUT"):
            in_layout = True; out.append(line); continue
        if skip_lig:
            if line.startswith("KEYNAME"):
                skip_lig = False
            else:
                continue
        if line.startswith("LIGATURE"):            # the skeleton's own (d/t + acute): not ours
            skip_lig = True
            in_layout = False
            continue
        if in_layout and re.match(r"^(KEYNAME|LIGATURE|DEADKEY|DESCRIPTIONS|ENDKBD)", line):
            in_layout = False
        if line.strip() == "KEYNAME" and ligs:
            if True:
                out += ["LIGATURE", "", "//VK_\tMod#\tChar0\tChar1\tChar2\tChar3",
                        "//----\t\t----\t----\t----\t----\t----", ""]
                for vk, mod, s, note in ligs:
                    out.append(f"{vk}\t\t{mod}\t" + "\t".join("%04x" % ord(c) for c in s)
                               + f"\t\t// {note}")
                out += ["", ""]
        if in_layout and line.strip() and not line.lstrip().startswith("//"):
            f = [c for c in line.split("//")[0].split("\t") if c.strip()]
            if len(f) == 8:
                sc, vk, cap, b, s, c, a6, a7 = f
                if vk in LETTER_VKS:
                    base, alt, why = t[LETTER_VKS[vk]]
                    cells = [base, up(base) if base else None, alt, up(alt) if alt else None]
                    for mod, val in zip((0, 1, 3, 4), cells):
                        if val and len(val) > 1:
                            ligs.append((vk, mod, val, f"{LETTER_VKS[vk]} col{mod}"))
                    b, s = klc_cell(cells[0]), klc_cell(cells[1])
                    a6, a7 = klc_cell(cells[2]), klc_cell(cells[3])
                    cap = "1" if base else "0"
                    note = f"// {cells[0] or '-'} {cells[1] or '-'} | AltGr {cells[2] or '-'} {cells[3] or '-'}"
                elif vk in PUNCT_ALTGR:
                    p = PUNCT_ALTGR[vk]
                    a6, a7 = klc_cell(p[0]), klc_cell(p[1])
                    note = f"// AltGr: {p[0]} {p[1]}"
                else:
                    note = "//"
                out.append(f"{sc}\t{vk}\t{cap}\t{b}\t{s}\t{c}\t{a6}\t{a7}\t{note}")
                continue
        if re.match(r"^0409\t", line) and "Polish" not in line:
            out.append(f"0409\t{KLC_DISPLAY}"); continue
        out.append(line)
    dst.parent.mkdir(parents=True, exist_ok=True)
    with io.open(dst, "w", encoding="utf-16", newline="\r\n") as fh:
        fh.write("\n".join(out) + "\n")
    return t


# macOS: US ANSI keycodes (build_keylayout.py)
MAC_LETTERS = {
    0: "a", 1: "s", 2: "d", 3: "f", 4: "h", 5: "g", 6: "z", 7: "x", 8: "c", 9: "v",
    11: "b", 12: "q", 13: "w", 14: "e", 15: "r", 16: "y", 17: "t",
    31: "o", 32: "u", 34: "i", 35: "p", 37: "l", 38: "j", 40: "k", 45: "n", 46: "m",
}
MAC_PUNCT_OPTION = {41: ("„", "“"), 39: ("’", "”"), 27: ("–", "—")}
MAC_PUNCT_OPTION.update({kc: (TITLO, TITLO) for kc in TITLO_KEYS_MAC})


# Navigation and control keys, the SAME output in every keyMap (261124: the layout had no
# 123-126 at all, so the arrows were dead). Values: Apple's documented keylayout convention
# (TN2056 / Ukelele's U.S. template) - NOT read from an Apple-generated file, none was found
# on hp or mc. 76 (keypad enter) is U+0003 as Apple has it (was U+000D from the base).
MAC_NAV = {
    123: "\u001c", 124: "\u001d", 125: "\u001f", 126: "\u001e",       # left right down up
    115: "\u0001", 119: "\u0004", 116: "\u000b", 121: "\u000c",       # home end pgup pgdn
    117: "\u007f", 51: "\u0008", 36: "\u000d", 76: "\u0003",          # fwd del, delete, return, enter
    48: "\u0009", 53: "\u001b", 71: "\u001b", 114: "\u0005",          # tab, escape, clear, help
}
MAC_CONTROL = {33: "\u001b", 42: "\u001c", 30: "\u001d"}           # Ctrl+[ \ ]


def write_keylayout(A, dst):
    import build_keylayout as bk                      # same base (digits, punctuation, keypad)
    t = isv_table(A)
    # keycode 10 = the ISO key left of 1 (§ ±, as Apple's U.S. layout on ISO hardware): not in
    # build_keylayout's ANSI base, added so the titlo also sits on § (owner 261124)
    BASE = dict(bk.BASE)
    BASE.setdefault(10, ("§", "±"))

    def ent(s):
        return "".join("&#x%04X;" % ord(c) for c in s)

    def keymap(idx, pick):
        rows = [f'    <keyMap index="{idx}">']
        for kc in sorted(set(BASE) | set(MAC_NAV)):
            v = MAC_NAV[kc] if kc in MAC_NAV else pick(kc)   # navigation/control: same in EVERY map
            if v:
                rows.append(f'      <key code="{kc}" output="{ent(v)}"/>')
        rows.append("    </keyMap>")
        return "\n".join(rows)

    def letter(kc, col):
        k = MAC_LETTERS[kc]
        base, alt, _ = t[k]
        return {0: base, 1: up(base) if base else None, 2: alt, 3: up(alt) if alt else None}[col]

    maps = [
        keymap(0, lambda kc: letter(kc, 0) if kc in MAC_LETTERS else BASE[kc][0]),
        keymap(1, lambda kc: letter(kc, 1) if kc in MAC_LETTERS else BASE[kc][1]),
        keymap(2, lambda kc: letter(kc, 2) if kc in MAC_LETTERS else MAC_PUNCT_OPTION.get(kc, (None,))[0]),
        keymap(3, lambda kc: letter(kc, 3) if kc in MAC_LETTERS else (MAC_PUNCT_OPTION.get(kc, (None, None))[1])),
        keymap(4, lambda kc: letter(kc, 1) if kc in MAC_LETTERS else BASE[kc][0]),
        # Command: plain US letters, so Cmd+C / Cmd+V keep working with a Glagolitic base
        keymap(5, lambda kc: BASE[kc][0]),
        # Control: Latin control characters as Apple's U.S. layout (Ctrl+A = U+0001 ... so
        # Ctrl+A / Ctrl+E move to line start / end in Cocoa text); [ \ ] = U+001B 1C 1D
        keymap(6, lambda kc: MAC_CONTROL.get(kc) or (chr(ord(MAC_LETTERS[kc]) - 96) if kc in MAC_LETTERS
                                                     else BASE[kc][0])),
    ]
    xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE keyboard SYSTEM "file://localhost/System/Library/DTDs/KeyboardLayout.dtd">
<!-- GENERATED by build_glag_desktop.py from the phone's tables - do not edit by hand -->
<keyboard group="126" id="-20131" name="{KLC_DISPLAY}" maxout="2">
  <layouts>
    <layout first="0" last="0" mapSet="ANSI" modifiers="glagModifiers"/>
  </layouts>
  <modifierMap id="glagModifiers" defaultIndex="0">
    <keyMapSelect mapIndex="0"><modifier keys=""/></keyMapSelect>
    <keyMapSelect mapIndex="1"><modifier keys="anyShift caps?"/></keyMapSelect>
    <keyMapSelect mapIndex="2"><modifier keys="anyOption"/></keyMapSelect>
    <keyMapSelect mapIndex="3"><modifier keys="anyShift anyOption caps?"/></keyMapSelect>
    <keyMapSelect mapIndex="4"><modifier keys="caps"/></keyMapSelect>
    <keyMapSelect mapIndex="5"><modifier keys="command anyShift? anyOption? caps? anyControl?"/></keyMapSelect>
    <keyMapSelect mapIndex="6"><modifier keys="anyControl anyShift? anyOption? caps?"/></keyMapSelect>
  </modifierMap>
  <keyMapSet id="ANSI">
{chr(10).join(maps)}
  </keyMapSet>
</keyboard>
"""
    dst.write_text(xml, encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- 2. pl-Glag (Keyman)

# Polish letters typed with AltGr, as on the Polish (programmer's) layout.
PL_ALTGR = {"ą": "a", "ć": "c", "ę": "e", "ł": "l", "ń": "n", "ó": "o", "ś": "s", "ż": "z", "ź": "x"}
# Desktop-only AltGr cells (no AltGr on the phone), owner's decisions 261124:
#   q -> jo Ⱖ (the phone's first hold on q, marker ǒ; Shift = Ⱖ)
#   t -> titlo (also AltGr+` below, PUNCT_KEYMAN)
#   v -> soft yer Ⱐ (the phone's v key = the kreska: s+Ⱐ = ś, r+Ⱐ = rz, d+Ⱐ = ď)
#   y -> hard yer Ⱏ (the phone's hold on v: l+Ⱏ = ł)
#   d -> a d that never joins a following z (the phone's held ḍ: od|zyskać)
PL_EXTRA = {"v": "ⱐ", "y": "ⱏ", "t": TITLO}
PL_ALTGR_MARKERS = {"q": "ǒ"}         # AltGr key -> phone marker, output read from POLISH_GLAG
PL_PHONE_KEYS = ("q", "x")
NOJOIN = "nojoin"


def pl_tokens(A):
    """POLISH_GLAG, Latin token -> output, without the phone's internal markers."""
    markers = {"ẑ", "ō", "ǒ", "ď", "ḍ", A["consts"]["SOFT_YER_MARK"], A["consts"]["HARD_YER_MARK"]}
    return [(l, g) for l, g in A["pl"] if l not in markers]


def pl_convert(word, A):
    """Port of Android Script.glagoliticPL: POLISH_GLAG in list order, first match wins."""
    caps = any(c.isalpha() for c in word) and not any(c.islower() for c in word)
    low = word.lower()
    if len(low) != len(word):
        return word
    out, i = [], 0
    while i < len(low):
        for lat, gl in A["pl"]:
            if low.startswith(lat, i):
                src = word[i]
                out.append(gl if not src.isupper() else gl.upper() if caps else gl[0].upper() + gl[1:])
                i += len(lat)
                break
        else:
            out.append(word[i]); i += 1
    return "".join(out)


def key_of(ch):
    """Latin letter -> key event ('c', char) / ('alt', key, shifted)."""
    low = ch.lower()
    if low in PL_ALTGR:
        return ("alt", PL_ALTGR[low], ch != low)
    return ("c", ch)


def pl_rules(A):
    """Rules (context tuple, key event, output tuple). Context/output items are strings,
    the deadkey is the item ('dk',). Keyman tries the longest context first."""
    toks = pl_tokens(A)
    out = dict(toks)
    rules = []

    def add(ctx, key, res):
        rules.append((tuple(ctx), key, tuple(res)))

    # single keys
    for lat, gl in toks:
        if len(lat) != 1:
            continue
        for shifted in (False, True):
            ch = lat.upper() if shifted else lat
            res = up(gl) if shifted else gl
            add((), key_of(ch), [res])
    # phone keys Polish does not use (keyText): q = ja Ⱑ, x = dzelo Ⰷ (261124, was ch).
    # The phone's v (soft yer kreska) stays AltGr+V here: a Polish typist types v as v.
    full = dict(A["pl"])
    for k in PL_PHONE_KEYS:
        gl = full[A["pl_keys"][k]]
        for shifted in (False, True):
            add((), ("c", k.upper() if shifted else k), [up(gl) if shifted else gl])
    for k, gl in list(PL_EXTRA.items()) + [(k, full[m]) for k, m in PL_ALTGR_MARKERS.items()]:
        add((), ("alt", k, False), [gl]); add((), ("alt", k, True), [up(gl)])
    for p in PUNCT_KEYMAN:
        pass
    # two-letter tokens: context = output of the first letter, key = the second
    pairs = []
    for lat, gl in toks:
        if len(lat) != 2:
            continue
        a, b = lat
        if a not in out or b not in out:
            continue
        if gl == out[a] + out[b]:
            continue                                  # dż = ⰴ + ⰶ already
        pairs.append((a, b, gl))
        for cu in (False, True):
            for ku in (False, True):
                ctx = up(out[a]) if cu else out[a]
                add([ctx], key_of(b.upper() if ku else b), [up(gl) if cu else gl])
    # never merge into a sign that ENDS a longer output (y = ⱏⰹ is not j)
    for a, b, gl in pairs:
        for lat2, gl2 in toks:
            if lat2 != a and len(gl2) > len(out[a]) and gl2.endswith(out[a]):
                for cu in (False, True):
                    for ku in (False, True):
                        ctx = up(gl2) if cu else gl2
                        sb = out[b]
                        add([ctx], key_of(b.upper() if ku else b), [ctx, up(sb) if ku else sb])
    # AltGr+D: a d that never joins (od|zyskać, pod|ziemie)
    d = out["d"]
    for cu in (False, True):
        dd = up(d) if cu else d
        add((), ("alt", "d", cu), [dd, ("dk",)])
        for b in ("z", "ź"):
            for ku in (False, True):
                sb = up(out[b]) if ku else out[b]
                add([dd, ("dk",)], key_of(b.upper() if ku else b), [dd, sb])
    return rules


PUNCT_KEYMAN = {"K_COLON": ("„", "“"), "K_QUOTE": ("’", "”"), "K_HYPHEN": ("–", "—"),
                "K_BKQUOTE": (TITLO, TITLO)}       # titlo on AltGr+` too (owner 261124)


_SIM_INDEX = {}


def simulate(rules, events):
    """Keyman semantics: of the rules for this key, the longest context that matches the
    end of the output wins; deadkeys match only where a rule names them."""
    cached = _SIM_INDEX.get(id(rules))
    if cached is None or cached[0] is not rules:
        by_key = {}
        for ctx, key, res in rules:
            by_key.setdefault(key, []).append((ctx, res))
        for v in by_key.values():
            v.sort(key=lambda r: -len(r[0]))
        _SIM_INDEX[id(rules)] = (rules, by_key)
    else:
        by_key = cached[1]
    out = []
    for ev in events:
        hit = None
        for ctx, res in by_key.get(ev, []):
            n = len(ctx)
            if n == 0 or tuple(out[-n:]) == ctx:
                hit = (n, res); break
        if hit is None:
            out.append(ev[1] if ev[0] == "c" else "?")
            continue
        n, res = hit
        if n:
            del out[-n:]
        out.extend(res)
    return "".join(x for x in out if isinstance(x, str))


def events_for(word):
    return [key_of(ch) for ch in unicodedata.normalize("NFC", word)]


def km_str(items):
    parts = []
    for it in items:
        if isinstance(it, tuple):
            parts.append(f"dk({NOJOIN})")
        else:
            parts += ["U+%04X" % ord(c) for c in it]
    return " ".join(parts)


def km_key(ev):
    if ev[0] == "c":
        ch = ev[1]
        return "'%s'" % ch if ch not in "'\\" else '"%s"' % ch
    _, k, sh = ev
    return "[%sRALT K_%s]" % ("SHIFT " if sh else "", k.upper())


PL_ID = "pl_glag"
PL_NAME = "Glagolitic (Polish)"   # PLACEHOLDER, English
PL_VERSION = "1.2"                # 1.0 = 261119, 1.1 = phone layout 261124, 1.2 = owner's AltGr 261124


def write_keyman(A, root):
    rules = pl_rules(A)
    src = root / "source"
    src.mkdir(parents=True, exist_ok=True)
    lines = [
        f"c {PL_ID} - Polish written in Glagolitic (pl-Glag)",
        "c GENERATED by build_glag_desktop.py from Script.kt POLISH_GLAG - do not edit by hand",
        "c Phone table (layout 261124): Android Script.glagoliticPL = iOS Script.polishGlag",
        "c Keys: q = ja, x = dzelo, AltGr+V soft yer, AltGr+Y hard yer, AltGr+D d that does not join z,",
        "c       AltGr+Q jo, AltGr+T and AltGr+` titlo (combining, after the letter)",
        "",
        f"store(&NAME) '{PL_NAME}'",
        "store(&COPYRIGHT) '© Interslove'",
        "store(&VERSION) '10.0'",
        f"store(&KEYBOARDVERSION) '{PL_VERSION}'",
        "store(&TARGETS) 'windows macosx linux'",
        "",
        "begin Unicode > use(main)",
        "",
        "group(main) using keys",
        "",
    ]
    seen = set()
    for ctx, key, res in sorted(rules, key=lambda r: (-len(r[0]), str(r[1]))):
        line = (km_str(ctx) + " " if ctx else "") + "+ " + km_key(key) + " > " + \
            ("context " + km_str(res[len(ctx):]) if ctx and res[:len(ctx)] == ctx else km_str(res))
        if line not in seen:
            seen.add(line); lines.append(line)
    lines += ["", "c punctuation as on the Interslavic Latin keyboard (KBDMSSTD)"]
    for vk, (a, b) in PUNCT_KEYMAN.items():
        lines.append(f"+ [RALT {vk}] > U+{ord(a):04X}")
        lines.append(f"+ [SHIFT RALT {vk}] > U+{ord(b):04X}")
    (src / f"{PL_ID}.kmn").write_text("\n".join(lines) + "\n", encoding="utf-8-sig", newline="\r\n")

    (src / f"{PL_ID}.kps").write_text(f"""<?xml version="1.0" encoding="utf-8"?>
<Package>
  <System>
    <KeymanDeveloperVersion>18.0.253.0</KeymanDeveloperVersion>
    <FileVersion>7.0</FileVersion>
  </System>
  <Options>
    <ReadMeFile>readme.htm</ReadMeFile>
    <LicenseFile>..\\LICENSE.md</LicenseFile>
    <WelcomeFile>welcome.htm</WelcomeFile>
    <FollowKeyboardVersion/>
  </Options>
  <Info>
    <Name URL="https://github.com/radoslove/keyboard-interslavic">{PL_NAME}</Name>
    <Copyright URL="">© Interslove</Copyright>
    <Author URL="">Interslove</Author>
    <Description URL="https://github.com/radoslove/keyboard-interslavic">Polish in Glagolitic: type Polish as usual (AltGr letters as on the Polish programmer's layout), the keyboard writes Glagolitic - cz, sz, rz, dz and ja/ia join by context.</Description>
  </Info>
  <Files>
    <File><Name>..\\build\\{PL_ID}.kmx</Name></File>
    <File><Name>welcome.htm</Name></File>
    <File><Name>readme.htm</Name></File>
    <File><Name>..\\LICENSE.md</Name></File>
  </Files>
  <Keyboards>
    <Keyboard>
      <ID>{PL_ID}</ID>
      <Languages>
        <Language ID="pl-Glag">Polish (Glagolitic)</Language>
      </Languages>
    </Keyboard>
  </Keyboards>
</Package>
""", encoding="utf-8", newline="\r\n")
    sample = " · ".join(" ".join(pl_convert(w, A) for w in grp.split())
                        for grp in ("góra mój ósmy", "szczęście", "rzeka", "chleb", "dzień"))
    html = f"""<!DOCTYPE html>
<html lang="pl"><head><meta charset="utf-8"><title>{PL_NAME}</title>
<style>body{{font:11pt "Segoe UI",sans-serif;margin:1em;line-height:1.45}} .g{{font-family:"Noto Sans Glagolitic","Segoe UI Historic";font-size:15pt}}</style>
</head><body>
<h1>{PL_NAME}</h1>
<p>Type Polish as usual; the keyboard writes Glagolitic. ą ć ę ł ń ó ś ż ź = AltGr + a c e l n o s z x.</p>
<p class="g">{sample}</p>
<p>Q: ja (Ⱑ). X: dzelo (Ⰷ). ch joins by itself (Ⱈ), h stays Ⱒ.</p>
<p>AltGr+D: a d that does not join the next z (od|zyskać). AltGr+V: soft yer, AltGr+Y: hard yer, AltGr+Q: jo (Ⱖ).</p>
<p>AltGr+T or AltGr+`: titlo, typed after the letter.</p>
<p>Font: Noto Sans Glagolitic or Segoe UI Historic.</p>
</body></html>
"""
    (src / "welcome.htm").write_text(html, encoding="utf-8")
    (src / "readme.htm").write_text(html, encoding="utf-8")
    (root / f"{PL_ID}.kpj").write_text("""<?xml version="1.0" encoding="utf-8"?>
<KeymanDeveloperProject>
  <Options>
    <Version>2.0</Version>
    <CompilerWarningsAsErrors>True</CompilerWarningsAsErrors>
    <CheckFilenameConventions>True</CheckFilenameConventions>
  </Options>
</KeymanDeveloperProject>
""", encoding="utf-8", newline="\r\n")
    lic = HERE / "keyman/isv_latin/LICENSE.md"
    (root / "LICENSE.md").write_text(lic.read_text(encoding="utf-8"), encoding="utf-8")
    (root / ".gitignore").write_text("/build/\n*.kpj.user\n", encoding="utf-8", newline="\n")
    (root / "README.md").write_text(f"""{PL_NAME} keyboard
==============

Polish in Glagolitic (pl-Glag). Generated by `build_glag_desktop.py` from the phone's
table (`Script.kt` POLISH_GLAG); do not edit `source/` by hand.

Build: `python -X utf8 build_glag_desktop.py && kmc build keyman/{PL_ID}/{PL_ID}.kpj`

Supported Platforms
-------------------
 * Windows
 * macOS
 * Linux
""", encoding="utf-8")
    (root / "HISTORY.md").write_text(f"""{PL_NAME} Change History
====================

1.2 (2026-11-24)
----------------
* AltGr+Q = jo ⱖ, titlo U+0483 on AltGr+T and AltGr+` (owner's decisions)

1.1 (2026-11-24)
----------------
* Follows the phone layout of 2026-11-24: m = ⱞ, x = dzelo ⰷ (was ch), ch joins from c + h

1.0 (2026-11-19)
----------------
* Created by Interslove
""", encoding="utf-8")
    return rules


# ---------------------------------------------------------------- checks

# The Polish canon pl2glag.py converts TEXT; the phone keyboard also has KEY MARKERS that
# no text contains (x key = ẑ, hold q = ǒ, the other internal markers). Those are compared
# out (pl_tokens) and named below; the check proves the canon really has no such input.
# Since 261124 (canon m = ⱞ) there is NO known difference in the letter pairs: any
# difference fails.
KEYBOARD_ONLY_MARKERS = {
    "ẑ": "the x key (dzelo ⰷ)",
    "ǒ": "the first hold on q (jo ⱖ); on the desktop AltGr+Q",
}


def prefix_order(pairs):
    """Order that matters for longest-first matching: (a, b) where a is a proper prefix of b."""
    lat = [l for l, _ in pairs]
    pos = {l: i for i, l in enumerate(lat)}
    return {(a, b, pos[a] < pos[b]) for a in lat for b in lat if a != b and b.startswith(a)}


def read_words(name):
    words = []
    with open(HERE / "dictionary" / name, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("word="):
                words.append(line[5:line.index(",f=")])
    return words


def check(A, I, klc_path, mac_path, rules, km_root=None):
    bad = 0
    failed = []

    def say(ok, msg):
        nonlocal bad
        bad += 0 if ok else 1
        if not ok:
            failed.append(msg)
        print(("  OK   " if ok else "  FAIL ") + msg)

    print("1. Android (working tree) == iOS (HEAD), the phone tables")
    say(A["letters"] == I["letters"], f"GLAG_LETTERS == glagLetters ({len(A['letters'])} = {len(I['letters'])} letters)")
    say(A["jlig"] == I["jlig"], f"J_LIGATURE == jLigature ({len(A['jlig'])} = {len(I['jlig'])})")
    say(dict(A["pl"]) == dict(I["pl"]) and len(A["pl"]) == len(I["pl"]),
        f"POLISH_GLAG == polishGlag as pairs ({len(A['pl'])} = {len(I['pl'])} entries)")
    say(prefix_order(A["pl"]) == prefix_order(I["pl"]),
        "POLISH_GLAG order where it matters (a token before the longer token it starts)")
    if A["pl"] != I["pl"]:
        moved = [a for a, b in zip(A["pl"], I["pl"]) if a != b]
        print(f"     (list order differs only for {sorted({l for l, _ in moved})}: single-token markers, no effect)")
    say(A["isv_glag_holds"] == I["isv_glag_holds"], f"isvGlagHolds equal ({A['isv_glag_holds']})")
    say(A["pl_holds"] == I["pl_holds"], f"polishGlagHolds == glagHolds ({len(A['pl_holds'])} keys)")

    print("2. Polish phone table == canon pl2glag.py (vault)")
    sys.path.insert(0, str(Path(r"C:\Projects\vault_002\Projects\INTERSLOVE\glagolica_pl")))
    try:
        import pl2glag
        canon = {l: unicodedata.normalize("NFC", g) for l, g in pl2glag.MAP}
        mine = {l: unicodedata.normalize("NFC", g) for l, g in pl_tokens(A)}
        diff = sorted(k for k in set(canon) | set(mine) if canon.get(k) != mine.get(k))
        say(not diff, f"same Latin->Glagolitic pairs ({len(canon)} canon, {len(mine)} phone minus markers)"
            + (f": differ {[(k, canon.get(k), mine.get(k)) for k in diff]}" if diff else ""))
        full = dict(A["pl"])
        for mk, why in KEYBOARD_ONLY_MARKERS.items():
            ok = mk not in canon and pl2glag.convert_word(mk) == mk and mk in full
            say(ok, f"marker {mk} -> {full.get(mk)} = {why}: keyboard marker, not text; "
                    f"the canon text converter has no input for it")
        say(A["pl_keys"].get("x") == "ẑ" and "x" not in canon,
            "x: the phone's x key writes marker ẑ; the canon has no x (text never needs it)")
    except ImportError as e:
        pl2glag = None
        say(False, f"pl2glag.py not found: {e}")

    print("3. isv layout == docs/script-tables.md 'Rows per script' (Glagolitic)")
    t = isv_table(A)
    doc = (HERE / "docs/script-tables.md").read_text(encoding="utf-8")
    row = re.search(r"^\| Glagolitic \| `([^`]*)` \| `([^`]*)` \| `([^`]*)` \|", doc, re.M)
    for latin_row, doc_row in zip(("qwertyuiop", "asdfghjkl", "zxcvbnm"), row.groups()):
        mine = " ".join(t[k][0] for k in latin_row)
        say(mine == doc_row, f"{latin_row}: {mine}")
    signs = phone_isv_signs(A)
    say(all(v in signs or v == TITLO for v in ISV_ALTGR.values()),
        "every AltGr cell writes a sign the phone writes by a key or a hold (titlo: Titlo.PLAIN)")
    print("   AltGr layer (phone source):")
    for k in sorted(t):
        if t[k][1]:
            print(f"     AltGr+{k.upper()} = {t[k][1]:<3} {up(t[k][1]):<3}  ({t[k][2]})")
    not_on_desktop = sorted(s for s in signs
                            if s not in A["isv_keys"].values() and s not in ISV_ALTGR.values()
                            and len(s) == 1 and s not in "abcdefghijklmnopqrstuvwxyz")
    print(f"     phone signs with no desktop cell (proposals only): {not_on_desktop}")

    print("4. what was WRITTEN (.klc read back, .keylayout parsed)")
    k = klc_path.read_text(encoding="utf-16")
    ligs = {}
    for m in re.finditer(r"^(\w)\t\t(\d)\t([0-9a-f\t]+?)\t\t//", k, re.M):
        ligs[(m.group(1), int(m.group(2)))] = "".join(chr(int(h, 16)) for h in m.group(3).split("\t"))
    okc = 0
    for line in k.splitlines():
        f = [c for c in line.split("//")[0].split("\t") if c.strip()]
        if len(f) == 8 and f[1] in LETTER_VKS:
            key = LETTER_VKS[f[1]]
            base, alt, _ = t[key]
            want = [base, up(base) if base else None, alt, up(alt) if alt else None]
            got = []
            for mod, cell in zip((0, 1, 3, 4), (f[3], f[4], f[6], f[7])):
                got.append(None if cell == "-1" else ligs.get((f[1], mod)) if cell == "%%" else chr(int(cell, 16)))
            if got == want:
                okc += 1
            else:
                print("     ", key, want, got)
    want_ligs = sum(1 for key in t for v in (t[key][0], t[key][1]) if v and len(v) > 1) * 2
    say(okc == 26, f".klc: {okc}/26 letter keys read back equal to the table")
    say(len(ligs) == want_ligs, f".klc: {len(ligs)} ligature rows (expected {want_ligs})")
    import xml.etree.ElementTree as ET
    x = mac_path.read_text(encoding="utf-8")
    # well-formedness (control-char entities are legal for macOS, not for ET: blanked here only)
    ET.fromstring(re.sub(r"&#x00(0[0-9A-F]|1[0-9A-F]);", "",
                         re.sub(r"<!DOCTYPE[^>]*>", "", x)).encode("utf-8"))
    # the real read: every key with its output decoded, control characters INCLUDED
    km = {}
    for idx, body in re.findall(r'<keyMap index="(\d+)">(.*?)</keyMap>', x, re.S):
        km[int(idx)] = {int(c): re.sub(r"&#x([0-9A-Fa-f]+);", lambda m: chr(int(m.group(1), 16)), o)
                        for c, o in re.findall(r'<key code="(\d+)" output="([^"]*)"/>', body)}
    okm = sum(1 for kc, lat in MAC_LETTERS.items()
              if km[0].get(kc) == t[lat][0] and km[2].get(kc) == t[lat][1]
              and km[1].get(kc) == up(t[lat][0]))
    say(okm == 26, f".keylayout: {okm}/26 letter keys (base + Shift + Option) equal to the table")
    cmd = sum(1 for kc, lat in MAC_LETTERS.items() if km[5].get(kc) == lat)
    say(cmd == 26, f".keylayout: Command map has {cmd}/26 Latin letters (Cmd+C / Cmd+V work)")
    say(A["titlo"] == I["titlo"] == TITLO, f"titlo: Android Titlo.PLAIN == iOS Titlo.plain == U+{ord(TITLO):04X}")
    oem = []
    for line in k.splitlines():
        f = [c for c in line.split("//")[0].split("\t") if c.strip()]
        if len(f) == 8 and f[1] in TITLO_KEYS_WIN:
            oem.append((f[6], f[7]))
    say(oem == [("%04x" % ord(TITLO),) * 2] * len(TITLO_KEYS_WIN),
        f".klc: AltGr / Shift+AltGr on {TITLO_KEYS_WIN} = titlo ({oem}); AltGr+T via the letter table")
    tk = [kc for kc in (17,) + TITLO_KEYS_MAC if km[2].get(kc) == TITLO and km[3].get(kc) == TITLO]
    say(len(tk) == 3 and km[0].get(10) == "§", f".keylayout: Option / Shift+Option = titlo on keycodes {tk} (t, `, §); § base = {km[0].get(10)}")
    navbad = [(i, kc) for i in sorted(km) for kc, v in MAC_NAV.items() if km[i].get(kc) != v]
    say(not navbad and len(km) == 7,
        f".keylayout: navigation/control ({len(MAC_NAV)} keys, arrows 123-126) in every one of {len(km)} keyMaps"
        + (f"; missing/wrong {navbad[:6]}" if navbad else ""))
    ctl = {lat: km.get(6, {}).get(kc) for kc, lat in MAC_LETTERS.items() if lat in "ae"}
    say(ctl == {"a": "\u0001", "e": "\u0005"} and km.get(6, {}).get(33) == "\u001b",
        f".keylayout: Control map Ctrl+A = U+0001, Ctrl+E = U+0005, Ctrl+[ = U+001B (line start/end in Cocoa)")

    print("5. Polish: typing key by key (Keyman rules) == the phone converter on the whole word")
    words = read_words("main_pl.combined")
    skip = set("qx")                          # q and x write ja / dzelo, not their Latin letter
    tested = mism = 0
    examples = []
    # A one-letter capital (`Ć`) is also an ALL-CAPS word, which the phone writes ⰜⰠ while
    # a key can only write the first sign capital (Ⱌⱐ); one-letter words: lower case only.
    for w in words:
        if set(w.lower()) & skip or not w.isalpha():
            continue
        for form in ((w, up(w)) if len(w) > 1 else (w,)):
            tested += 1
            want = unicodedata.normalize("NFC", pl_convert(form, A))
            got = unicodedata.normalize("NFC", simulate(rules, events_for(form)))
            if got != want:
                mism += 1
                if len(examples) < 8:
                    examples.append((form, want, got))
    say(mism == 0 and tested > 1000, f"{tested} forms (lower + Capitalised, without q x): {mism} differ")
    for e in examples:
        print("     ", e)
    if pl2glag:
        cm = sum(1 for w in words if w.isalpha() and not set(w.lower()) & skip and
                 unicodedata.normalize("NFC", pl2glag.convert_word(w))
                 != unicodedata.normalize("NFC", pl_convert(w, A)))
        say(cm == 0, f"phone converter == canon convert_word (no exception): {cm} differ")
    for w in ("góra", "mój", "ósmy", "szczęście", "rzeka", "dzień", "jaźń", "Łódź", "chrząszcz", "chleb",
              "hak", "mama", "siano", "byja"):
        print(f"     {w:<10} -> {simulate(rules, events_for(w))}")
    xq = simulate(rules, [("c", "q"), ("c", "x"), ("c", "Q"), ("c", "X")])
    say(xq == "ⱑⰷⰡⰇ", f"q x Q X = {xq} (ja, dzelo, as the phone keys)")
    aq = simulate(rules, [("alt", "q", False), ("alt", "q", True), key_of("m"), ("alt", "t", False),
                          ("alt", "t", True)])
    say(aq == "ⱖ" + "ⱖ".upper() + "ⱞ" + TITLO * 2, f"AltGr+Q Shift+AltGr+Q m AltGr+T Shift+AltGr+T = {aq} (jo, Jo, m + titlo x2)")
    kmn = ((km_root or HERE / "keyman" / PL_ID) / "source" / f"{PL_ID}.kmn").read_text(encoding="utf-8-sig")
    bq = [l for l in kmn.splitlines() if "K_BKQUOTE]" in l]
    say(len(bq) == 2 and all(l.endswith(f"U+{ord(TITLO):04X}") for l in bq),
        f".kmn: AltGr+` and Shift+AltGr+` = titlo ({len(bq)} rules)")
    od = simulate(rules, [key_of("o"), ("alt", "d", False), key_of("z"), key_of("y"), key_of("s"),
                          key_of("k"), key_of("a"), ("alt", "c", False)])
    say(od == "ⱁⰴⰸⱏⰹⱄⰽⰰⱌⱐ", f"o AltGr+D z y s k a AltGr+C = {od} (odzyskać, as the phone)")
    sw = set(words)
    pref = sum(1 for w in words for p in ("przed", "pod", "nad", "od")
               if w.startswith(p) and w[len(p):len(p) + 1] in ("z", "ź", "ż")
               and len(w) - len(p) >= 3 and w[len(p):] in sw)
    print(f"     forms where the phone's prefix rule splits d|z (need AltGr+D here): {pref} of {len(words)}")

    print("6. isv: one key = one sign, against the phone converter (whole word)")
    isvw = read_words("main_isv.combined")
    plain = sum(1 for w in isvw if "".join(isv_convert(c, A) for c in w.lower()) == isv_convert(w.lower(), A))
    print(f"     {plain} of {len(isvw)} forms ({100 * plain / len(isvw):.1f} %) come out right letter by letter;")
    print("     the rest need AltGr ju/jo, q (ja), x (dz) or v (lj nj), as a TAP on the phone does")
    return bad, failed


def negative_control(A):
    """Build into a temp folder, break one cell of each output and one phone entry, and
    require the checks to FAIL on exactly those. The repo files are not touched."""
    import copy
    import tempfile
    tmp = Path(tempfile.mkdtemp(prefix="glag_neg_"))
    klc, mac = tmp / "KBDISVGL.klc", tmp / "ISVGLAG.keylayout"
    write_klc(A, klc)
    write_keylayout(A, mac)
    rules = write_keyman(A, tmp / PL_ID)
    I = read_ios()
    print("== baseline in the temp folder (must PASS) ==")
    base_bad, _ = check(A, I, klc, mac, rules, tmp / PL_ID)
    # 1. one .klc cell: the F key base ⱚ -> the old ⱇ
    k = klc.read_text(encoding="utf-16").replace("\tF\t1\t%04x\t" % ord("ⱚ"), "\tF\t1\t%04x\t" % ord("ⱇ"), 1)
    with io.open(klc, "w", encoding="utf-16", newline="") as fh:
        fh.write(k)
    # 2. one .keylayout cell: Option+v (hard yer ⱏ) -> nothing
    m = mac.read_text(encoding="utf-8")
    m = m.replace('<key code="9" output="&#x2C4F;"/>', "", 1)
    # 2b. right arrow dropped from the first keyMap
    m = m.replace('<key code="124" output="&#x001D;"/>', "", 1)
    mac.write_text(m, encoding="utf-8")
    # 3. one phone entry (Android side): GLAG_LETTERS f -> ⱇ
    B = copy.deepcopy(A)
    B["letters"]["f"] = "ⱇ"
    # 4. one Keyman rule: c + h no longer joins
    rules2 = [r for r in rules if not (r[0] == ("ⱌ",) and r[1] == ("c", "h"))
              and r[1] != ("alt", "q", False)]          # 5. AltGr+Q (jo) gone
    print("== broken: .klc F, .keylayout Option+V and arrow 124, Android letter f, Keyman ch, Keyman AltGr+Q (must FAIL) ==")
    bad, failed = check(B, I, klc, mac, rules2, tmp / PL_ID)
    expect = ["GLAG_LETTERS", ".klc:", ".keylayout: 2", "navigation/control", "forms (lower", "AltGr+Q"]
    caught = [e for e in expect if any(e in f for f in failed)]
    print(f"== negative control: baseline {base_bad} fail(s); broken run {bad} fail(s); "
          f"caught {len(caught)}/{len(expect)}: {caught}")
    return base_bad == 0 and len(caught) == len(expect)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--negative-control", action="store_true")
    a = ap.parse_args()
    sys.path.insert(0, str(HERE))
    A = read_android()
    if a.negative_control:
        ok = negative_control(A)
        print("NEGATIVE CONTROL OK - the gate fails on a broken cell" if ok else "NEGATIVE CONTROL BROKEN")
        sys.exit(0 if ok else 1)
    klc = HERE / "windows/src/KBDISVGL.klc"
    mac = HERE / "mac/ISVGLAG.keylayout"
    write_klc(A, klc)
    write_keylayout(A, mac)
    rules = write_keyman(A, HERE / "keyman" / PL_ID)
    print("wrote", klc)
    print("wrote", mac)
    print("wrote", HERE / "keyman" / PL_ID, f"({len(rules)} rules)")
    if a.check:
        bad, _ = check(A, read_ios(), klc, mac, rules)
        print("ALL CHECKS PASS" if not bad else f"{bad} CHECK(S) FAILED")
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
