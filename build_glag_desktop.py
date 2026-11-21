#!/usr/bin/env python3
"""
build_glag_desktop.py - Glagolitic keyboards for computers, from the PHONE's tables.

Two keyboards, two routes:

  1. Interslavic in Glagolitic (isv-Glag), NATIVE layouts:
       windows/src/KBDISVGL.klc      -> MSKLC kbdutool -> windows/installers/kbdisvgl/
       mac/ISVGLAG.keylayout
     One key = one output, like a tap on the phone. The letters are the ones the phone
     writes on the same keys (docs/script-tables.md, "Rows per script"): q = lj, w = nj,
     y = the two signs of v1, no x. The phone's HOLDS go to AltGr / Option.

  2. Polish in Glagolitic (pl-Glag), KEYMAN, because Polish Glagolitic needs context
     (c + z = cz, r + z = rz, j + a = ja ...), which neither .klc nor .keylayout can do:
       keyman/pl_glag/   -> kmc build -> keyman/pl_glag.kmp  (Windows, macOS, Linux)
     Typed Polish the usual way (AltGr letters as on the Polish programmer's layout);
     each key looks at the glyph before the cursor and rewrites it, so the result is
     the same as converting the whole word.

ONE TABLE. Nothing here is typed from memory: the letter tables are READ from the
Android sources (Script.kt GLAG_LETTERS / J_LIGATURE / POLISH_GLAG, Layout.kt isvOwn
and keyText). `--check` then proves the outputs against the phone (Android AND iOS
tables), against docs/script-tables.md, and against the Polish canon pl2glag.py.

USAGE
    python -X utf8 build_glag_desktop.py            # write the three sources
    python -X utf8 build_glag_desktop.py --check    # + all checks (exit 1 on a mismatch)

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


def read_android():
    script = (KT / "Script.kt").read_text(encoding="utf-8")
    layout = (KT / "Layout.kt").read_text(encoding="utf-8")
    consts = dict(re.findall(r'const val (\w+) = "([^"]*)"', script))

    letters = dict(re.findall(r"'(.)' to \"([^\"]+)\"",
                              kt_block(script, "private val GLAG_LETTERS = mapOf")))
    jlig = dict(re.findall(r"'(.)' to \"([^\"]+)\"",
                           kt_block(script, "private val J_LIGATURE = mapOf")))
    pl = []
    body = kt_block(script, "private val POLISH_GLAG: List<Pair<String, String>> = listOf")
    for m in re.finditer(r'(?:"([^"]+)"|(SOFT_YER_MARK|HARD_YER_MARK)) to "([^"]+)"', body):
        pl.append((m.group(1) if m.group(1) is not None else consts[m.group(2)], m.group(3)))
    isv_own = {k: re.findall(r'"([^"]+)"', v) for k, v in re.findall(
        r"'(.)' to listOf\(([^)]*)\)", kt_block(layout, "private val isvOwn: Map<Char, List<String>> = mapOf"))}
    pl_holds = {}
    for k, v in re.findall(r"'(.)' to listOf\(([^)]*)\)",
                           kt_block(layout, "private val polishGlagHolds: Map<Char, List<String>> = mapOf")):
        pl_holds[k] = [consts.get(x.strip(), None) or x.strip().strip('"')
                       for x in v.split(",") if x.strip()]
    pl_holds = {k: [consts["HARD_YER_MARK"] if x == "Script.HARD_YER_MARK" else x for x in v]
                for k, v in pl_holds.items()}
    # keyText: the two digraph keys of the non-Latin rows, and the Polish shortcuts
    kt = kt_block(layout, "fun keyText") if False else layout[layout.index("fun keyText"):]
    kt = kt[:kt.index("\n    }\n")]
    isv_keys = {}
    for k, v in re.findall(r"^\s*ch == '(.)' -> \"([^\"]+)\"", kt, re.M):
        isv_keys.setdefault(k, v)                      # the plain `ch == 'q' -> "lj"` lines
    pl_part = kt[kt.index("lang == Lang.PL -> when (ch)"):]
    pl_part = pl_part[:pl_part.index("}")]
    pl_keys = dict(re.findall(r"'(.)' -> \"([^\"]+)\"", pl_part))
    return dict(letters=letters, jlig=jlig, pl=pl, isv_own=isv_own, pl_holds=pl_holds,
                isv_keys={k: v for k, v in isv_keys.items() if k in "qw"}, pl_keys=pl_keys,
                consts=consts)


def read_ios():
    s = (SWIFT / "Script.swift").read_text(encoding="utf-8")
    def unesc(x):
        return re.sub(r"\\u\{([0-9A-Fa-f]+)\}", lambda m: chr(int(m.group(1), 16)), x)
    i = s.index("glagLetters: [Character: String] = [")
    letters = dict(re.findall(r'"(.)": "([^"]+)"', s[i:s.index("\n    ]", i)]))
    i = s.index("polishGlag: [(String, String)] = [")
    j = s.index("\n    ]", i)
    pl = [(unesc(a), unesc(b)) for a, b in re.findall(r'\("([^"]+)", "([^"]+)"\)', s[i:j])]
    return dict(letters=letters, pl=pl)


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

# AltGr cells that are NOT phone holds but the phone converter's j rules (ja ju jo)
# and the bare soft yer: PROPOSAL, owner decides before this is frozen.
ISV_ALTGR_EXTRA_KEYS = {"a": "ja", "u": "ju", "o": "jo", "j": "lj"}   # j: soft yer (tail of lj)


def isv_table(A):
    """Latin key -> (base, AltGr, source of AltGr), outputs in Glagolitic, lower case."""
    t = {}
    for k in "abcdefghijklmnopqrstuvwxyz":
        if k in A["isv_keys"]:
            base = isv_convert(A["isv_keys"][k], A)
        elif k == "x":
            base = None                                  # no x key in Glagolitic
        else:
            base = isv_convert(k, A)
        hold = A["isv_own"].get(k)
        alt, src = (isv_convert(hold[0], A), "hold " + hold[0]) if hold else (None, None)
        if alt is None and k in ISV_ALTGR_EXTRA_KEYS:
            lat = ISV_ALTGR_EXTRA_KEYS[k]
            alt = isv_convert(lat, A)
            if k == "j":
                alt = alt[-1]                            # ⱐ alone
                src = "proposal: soft yer ⱐ (tail of lj/nj)"
            else:
                src = "proposal: " + lat + " (v1 ligature)"
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


def write_keylayout(A, dst):
    import build_keylayout as bk                      # same base (digits, punctuation, keypad)
    t = isv_table(A)

    def ent(s):
        return "".join("&#x%04X;" % ord(c) for c in s)

    def keymap(idx, pick):
        rows = [f'    <keyMap index="{idx}">']
        for kc in sorted(bk.BASE):
            v = pick(kc)
            if v:
                rows.append(f'      <key code="{kc}" output="{ent(v)}"/>')
        rows.append("    </keyMap>")
        return "\n".join(rows)

    def letter(kc, col):
        k = MAC_LETTERS[kc]
        base, alt, _ = t[k]
        return {0: base, 1: up(base) if base else None, 2: alt, 3: up(alt) if alt else None}[col]

    maps = [
        keymap(0, lambda kc: letter(kc, 0) if kc in MAC_LETTERS else bk.BASE[kc][0]),
        keymap(1, lambda kc: letter(kc, 1) if kc in MAC_LETTERS else bk.BASE[kc][1]),
        keymap(2, lambda kc: letter(kc, 2) if kc in MAC_LETTERS else MAC_PUNCT_OPTION.get(kc, (None,))[0]),
        keymap(3, lambda kc: letter(kc, 3) if kc in MAC_LETTERS else (MAC_PUNCT_OPTION.get(kc, (None, None))[1])),
        keymap(4, lambda kc: letter(kc, 1) if kc in MAC_LETTERS else bk.BASE[kc][0]),
        # Command: plain US letters, so Cmd+C / Cmd+V keep working with a Glagolitic base
        keymap(5, lambda kc: bk.BASE[kc][0]),
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
# Desktop-only AltGr cells (no AltGr on the phone): PROPOSAL, owner decides.
#   v -> soft yer Ⱐ (the phone's v key = the kreska: s+Ⱐ = ś, r+Ⱐ = rz, d+Ⱐ = ď)
#   y -> hard yer Ⱏ (the phone's hold on v: l+Ⱏ = ł)
#   d -> a d that never joins a following z (the phone's held ḍ: od|zyskać)
PL_EXTRA = {"v": "ⱐ", "y": "ⱏ"}
NOJOIN = "nojoin"


def pl_tokens(A):
    """POLISH_GLAG, Latin token -> output, without the phone's internal markers."""
    markers = {"ẑ", "ō", "ď", "ḍ", A["consts"]["SOFT_YER_MARK"], A["consts"]["HARD_YER_MARK"]}
    return [(l, g) for l, g in A["pl"] if l not in markers]


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
    # phone shortcuts on keys Polish does not use (keyText): q = ja, x = ch
    for k, lat in A["pl_keys"].items():
        if lat in out:
            for shifted in (False, True):
                add((), ("c", k.upper() if shifted else k), [up(out[lat]) if shifted else out[lat]])
    for k, gl in PL_EXTRA.items():
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


PUNCT_KEYMAN = {"K_COLON": ("„", "“"), "K_QUOTE": ("’", "”"), "K_HYPHEN": ("–", "—")}


def simulate(rules, events):
    """Keyman semantics: of the rules for this key, the longest context that matches the
    end of the output wins; deadkeys match only where a rule names them."""
    by_key = {}
    for ctx, key, res in rules:
        by_key.setdefault(key, []).append((ctx, res))
    for v in by_key.values():
        v.sort(key=lambda r: -len(r[0]))
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


def write_keyman(A, root):
    rules = pl_rules(A)
    src = root / "source"
    src.mkdir(parents=True, exist_ok=True)
    lines = [
        f"c {PL_ID} - Polish written in Glagolitic (pl-Glag)",
        "c GENERATED by build_glag_desktop.py from Script.kt POLISH_GLAG - do not edit by hand",
        "c Canon: vault pl2glag.py = Android Script.glagoliticPL = iOS Script.polishGlag",
        "",
        f"store(&NAME) '{PL_NAME}'",
        "store(&COPYRIGHT) '© Interslove'",
        "store(&VERSION) '10.0'",
        "store(&KEYBOARDVERSION) '1.0'",
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
    html = f"""<!DOCTYPE html>
<html lang="pl"><head><meta charset="utf-8"><title>{PL_NAME}</title>
<style>body{{font:11pt "Segoe UI",sans-serif;margin:1em;line-height:1.45}} .g{{font-family:"Noto Sans Glagolitic","Segoe UI Historic";font-size:15pt}}</style>
</head><body>
<h1>{PL_NAME}</h1>
<p>Type Polish as usual; the keyboard writes Glagolitic. ą ć ę ł ń ó ś ż ź = AltGr + a c e l n o s z x.</p>
<p class="g">ⰳⱁ́ⱃⰰ ⰿⱁ́ⰹ ⱁ́ⱄⰿⱏⰹ · ⱎⱍⱔⱄⱐⱌⰻⰵ · ⱃⱐⰵⰽⰰ</p>
<p>AltGr+D: a d that does not join the next z (od|zyskać). AltGr+V: soft yer, AltGr+Y: hard yer.</p>
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

1.0 (2026-11-19)
----------------
* Created by Interslove
""", encoding="utf-8")
    return rules


# ---------------------------------------------------------------- checks


def check(A, I, klc_path, mac_path, rules):
    bad = 0

    def say(ok, msg):
        nonlocal bad
        bad += 0 if ok else 1
        print(("  OK   " if ok else "  FAIL ") + msg)

    print("1. Android == iOS (the two phone tables)")
    say(A["letters"] == I["letters"], f"GLAG_LETTERS == glagLetters ({len(A['letters'])} letters)")
    say(A["pl"] == I["pl"], f"POLISH_GLAG == polishGlag ({len(A['pl'])} entries)")

    print("2. Polish table == canon pl2glag.py (vault)")
    sys.path.insert(0, str(Path(r"C:\Projects\vault_002\Projects\INTERSLOVE\glagolica_pl")))
    try:
        import pl2glag
        canon = [(l, unicodedata.normalize("NFC", g)) for l, g in pl2glag.MAP]
        mine = [(l, unicodedata.normalize("NFC", g)) for l, g in pl_tokens(A)]
        say(dict(canon) == dict(mine),
            f"same Latin->Glagolitic pairs ({len(canon)} canon, {len(mine)} phone minus markers)")
        if dict(canon) != dict(mine):
            print("     only canon:", set(canon) - set(mine), " only phone:", set(mine) - set(canon))
    except ImportError as e:
        pl2glag = None
        say(False, f"pl2glag.py not found: {e}")

    print("3. isv layout == docs/script-tables.md 'Rows per script' (Glagolitic)")
    t = isv_table(A)
    doc = (HERE / "docs/script-tables.md").read_text(encoding="utf-8")
    row = re.search(r"^\| Glagolitic \| `([^`]*)` \| `([^`]*)` \| `([^`]*)` \|", doc, re.M)
    for latin_row, doc_row in zip(("qwertyuiop", "asdfghjkl", "zcvbnm"), row.groups()):
        mine = " ".join(t[k][0] for k in latin_row)
        say(mine == doc_row, f"{latin_row}: {mine}")
    say(t["x"][0] is None, "x: no output (no x key in Glagolitic)")
    print("   AltGr layer vs phone holds (Layout.isvOwn through the v1 converter):")
    for k in sorted(t):
        if t[k][1]:
            print(f"     AltGr+{k} = {t[k][1]:<3} {up(t[k][1]):<3}  ({t[k][2]})")

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
    say(okc == 26, f".klc: {okc}/26 letter keys read back equal to the table ({len(ligs)} ligatures)")
    import xml.etree.ElementTree as ET
    x = mac_path.read_text(encoding="utf-8")
    x = re.sub(r"<!DOCTYPE[^>]*>", "", x)
    x = re.sub(r"&#x00(0[0-9A-F]|1[0-9A-F]);", "", x)        # control keys: legal for macOS, not for ET
    root = ET.fromstring(x.encode("utf-8"))
    km = {int(m.get("index")): {int(e.get("code")): e.get("output") for e in m} for m in root.iter("keyMap")}
    okm = sum(1 for kc, lat in MAC_LETTERS.items()
              if km[0].get(kc) == t[lat][0] and km[2].get(kc) == t[lat][1])
    say(okm == 26, f".keylayout: {okm}/26 letter keys (base + Option) equal to the table")
    say(km[5].get(8) == "c", ".keylayout: Command+C stays Latin c (shortcuts work)")

    print("5. Polish: typing key by key (Keyman rules) == converting the whole word")
    words = []
    with open(HERE / "dictionary/main_pl.combined", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("word="):
                words.append(line[5:line.index(",f=")])
    skip = set("qxv")
    tested = mism = 0
    examples = []
    for w in words:
        if set(w.lower()) & skip or not w.isalpha():
            continue
        for form in (w, up(w)):
            tested += 1
            want = unicodedata.normalize("NFC", pl2glag.convert_word(form) if pl2glag else "")
            got = unicodedata.normalize("NFC", simulate(rules, events_for(form)))
            if got != want:
                mism += 1
                if len(examples) < 8:
                    examples.append((form, want, got))
    say(mism == 0, f"{tested} forms (lower + Capitalised, without q x v): {mism} differ")
    for e in examples:
        print("     ", e)
    for w in ("góra", "mój", "ósmy", "szczęście", "rzeka", "dzień", "jaźń", "Łódź", "chrząszcz", "siano", "byja"):
        print(f"     {w:<10} -> {simulate(rules, events_for(w))}   canon {pl2glag.convert_word(w)}")
    # the prefix rule needs a dictionary: Keyman cannot, AltGr+D does it by hand
    od = simulate(rules, [key_of("o"), ("alt", "d", False), key_of("z"), key_of("y"), key_of("s"),
                          key_of("k"), key_of("a"), ("alt", "c", False)])
    say(od == "ⱁⰴⰸⱏⰹⱄⰽⰰⱌⱐ", f"o AltGr+D z y s k a AltGr+C = {od} (odzyskać, as the phone)")
    sw = set(words)
    pref = sum(1 for w in words for p in ("przed", "pod", "nad", "od")
               if w.startswith(p) and w[len(p):len(p) + 1] in ("z", "ź", "ż")
               and len(w) - len(p) >= 3 and w[len(p):] in sw)
    print(f"     forms where the phone's prefix rule splits d|z (need AltGr+D here): {pref} of {len(words)}")

    print("6. isv: one key = one sign, against the phone converter (v1, whole word)")
    isvw = []
    with open(HERE / "dictionary/main_isv.combined", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("word="):
                isvw.append(line[5:line.index(",f=")])
    plain = sum(1 for w in isvw if "".join(isv_convert(c, A) for c in w.lower()) == isv_convert(w.lower(), A))
    print(f"     {plain} of {len(isvw)} forms ({100 * plain / len(isvw):.1f} %) come out right letter by letter;")
    print("     the rest need AltGr ja/ju/jo or q/w (lj nj), as a TAP on the phone does (docs/script-tables.md ⚠)")
    return bad


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    sys.path.insert(0, str(HERE))
    A = read_android()
    klc = HERE / "windows/src/KBDISVGL.klc"
    mac = HERE / "mac/ISVGLAG.keylayout"
    write_klc(A, klc)
    write_keylayout(A, mac)
    rules = write_keyman(A, HERE / "keyman" / PL_ID)
    print("wrote", klc)
    print("wrote", mac)
    print("wrote", HERE / "keyman" / PL_ID, f"({len(rules)} rules)")
    if a.check:
        bad = check(A, read_ios(), klc, mac, rules)
        print("ALL CHECKS PASS" if not bad else f"{bad} CHECK(S) FAILED")
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
