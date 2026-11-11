#!/usr/bin/env python3
"""Offline swipe-decoder training/eval harness.

The device round-trip is too slow to tune a glide decoder. So we do it here:

  1. Rebuild the keyboard geometry from the app's layout (qwertyuiop / asdfghjkl
     / zxcvbnm, with the row-2 shift offset — same as KeyboardView).
  2. For a sample of real words, SYNTHESISE a finger path through their letter
     keys — now with a TIME STAMP on every sample and a realistic speed profile,
     because a digitizer reports at a fixed rate and the finger slows near the
     letters it means.
  3. Decode each path against the full wordlist and measure top-1 / top-3.

Three decoders live here so a change can be measured against what actually
ships, not against an older idea:

    arc    — arc-length resample, no dwell.  The harness's original decoder.
    dwell  — index (time) resample + ideal-route-with-dwell.  MIRRORS the
             shipped Dictionary.kt / KeyboardView.kt.  This is the baseline.
    pivot  — dwell + explicit VELOCITY MINIMA (see below).

## Velocity minima

Dwell is already used implicitly: the trail is time-sampled, so a letter the
finger lingered on contributes more points, and index-resampling keeps them.
That is a blunt instrument — it says "somewhere around here was slow".

A minimum of the speed curve says something sharper: *this point* is where the
finger turned or hesitated, i.e. a letter the writer meant. Reading them out
gives an ordered set of pivots, and a candidate word is then judged on two
extra questions the shape distance cannot ask:

  - does every pivot sit on one of the word's letters?  (no unexplained pause)
  - does every letter of the word have a pivot near it? (no invented letter)

The second is what separates `možemo` from `možehmo`: the detour through `h`
lies close enough to the path that shape distance barely notices, but nothing
in the finger's speed ever suggests a letter there.

    python swipe_eval.py                 # accuracy on a default sample
    python swipe_eval.py test            # regression gate (CI: exit code)
    python swipe_eval.py ab              # arc vs dwell vs pivot, same paths
    python swipe_eval.py možemo kako     # trace specific words

Language-agnostic: swap WORDLIST + the KEY layout and it trains any language.
"""

import math
import os
import random
import sys

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
# Which wordlist to measure. The decoder behaves DIFFERENTLY per language
# (frequency scaling below), so the language is part of every measurement:
#     SWIPE_LANG=pl python tools/swipe_eval.py
LANG = os.environ.get("SWIPE_LANG", "isv")
WORDLIST = os.path.join(HERE, "..", "dictionary", "main_%s.combined" % LANG)

random.seed(1)

# ---- keyboard geometry (mirrors KeyboardView) --------------------------------
KEY_W = 108.0
KEY_H = 150.0
BOARD_W = KEY_W * 10
LATIN_ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
# SWIPE_SCRIPT picks the keyboard the geometry mirrors (2026-11-08):
#   latin        what it always was
#   cyrl, glag   `q` and `w` carry the `lj` / `nj` digraph keys - `љ` and `њ`
#                on the Serbian/Macedonian positions, `ⰾⱐ` and `ⱀⱐ` in
#                Glagolitic - so both keys stay where Latin has them and a
#                glide over one of them counts as the PAIR (`folds` below,
#                mirroring `Dictionary.foldWord`, which keeps the route over
#                `l`+`j` as well). `x` has no letter in either script, so that
#                one key goes and row 3 is padded: the keys that stay keep the
#                LATIN width, with shift and backspace growing instead, which
#                is what `Layout.letterRows` + `KeyboardView.spacer` build on
#                the phone.
SCRIPT = os.environ.get("SWIPE_SCRIPT", "latin")
# SWIPE_ROWS=qwertz swaps `y` and `z`, as the Czech and Slovak keyboards do
# (KeyRows, owner 2026-11-10). Latin script only; the grid stays the same.
QWERTZ = os.environ.get("SWIPE_ROWS", "qwerty") == "qwertz"
if QWERTZ:
    LATIN_ROWS = ["qwertzuiop", "asdfghjkl", "yxcvbnm"]
DIGRAPH_KEYS = SCRIPT != "latin"
# Serbian Cyrillic (JUS I.K1.003, added 2026-11-10) differs from the Interslavic
# one in both directions: the national layout is QWERTZ, and `џ` is a LETTER
# there, which the standard puts on the `x` position - so that key stays and
# carries the `dž` digraph, exactly as `q` and `w` carry `lj` and `nj`. Only `y`
# goes. Interslavic writes the same sound `дж`, two letters, and keeps no `x`.
SR_CYRL = DIGRAPH_KEYS and LANG == "sr"
# Macedonian is Cyrillic-only, so it needs no SWIPE_SCRIPT: the wordlist is
# already the internal Latin and the keyboard always draws these rows. The
# digraph machinery stays OFF - `ǉ ǌ ǆ` are single characters there, and `l`+`j`
# really is two letters (`илјада`).
MK = LANG == "mk"
# Russian, phonetic (ЯШЕРТЫ): Cyrillic-only as well, its letters on all 26 Latin
# keys, so the rows are the Latin QWERTY ones; no digraph keys.
RU = LANG in ("ru", "uk", "bg", "be")   # Ukrainian, Bulgarian, Belarusian: same QWERTY grid, no digraph keys
if MK or RU:
    DIGRAPH_KEYS = False
ROWS = (["qwertzuiop", "asdfghjkl", "xcvbnm"] if MK
        else ["qwertyuiop", "asdfghjkl", "zxcvbnm"] if RU
        else LATIN_ROWS if SCRIPT == "latin"
        else ["qwertzuiop", "asdfghjkl", "xcvbnm"] if SR_CYRL
        else ["qwertyuiop", "asdfghjkl", "zcvbnm"])


def build_centers():
    c = {}
    units = float(len(LATIN_ROWS[0]))     # a letter row spans ten key widths
    # row 0: 10 keys fill the width; a shorter row is padded at both ends
    pad = (len(LATIN_ROWS[0]) - len(ROWS[0])) / 2.0
    for i, ch in enumerate(ROWS[0]):
        c[ch] = ((pad + i + 0.5) * BOARD_W / units, 0.5 * KEY_H)
    # row 1: 9 keys fill the width (the same row in every script)
    for i, ch in enumerate(ROWS[1]):
        c[ch] = ((i + 0.5) * BOARD_W / 9.0, 1.5 * KEY_H)
    # row 2: shift + letters + backspace = the 10 widths of the Latin top row,
    # so shift(1.5) + 7 + backspace(1.5) in Latin, 2.0 + 6 + 2.0 without `x`
    side = (len(LATIN_ROWS[0]) - len(ROWS[2])) / 2.0
    for i, ch in enumerate(ROWS[2]):
        c[ch] = ((side + i + 0.5) * BOARD_W / units, 2.5 * KEY_H)
    return c


CENTERS = build_centers()


# Mirrors Dictionary.foldBase: every held letter folds to the KEY it lives on.
# It used to list the four Interslavic ones only, which silently broke any
# measurement on another language's wordlist - a Czech `říká` was synthesised as
# a path through `k` alone, and the resulting 54% top-1 said nothing about the
# keyboard. If a letter is missing here, the harness lies rather than fails.
_FOLD = {}
for _src, _dst in [("čć", "c"), ("šś", "s"), ("žżź", "z"), ("ěęéèê", "e"),
                   ("ąàá", "a"), ("łľĺ", "l"), ("ńň", "n"), ("óòôǫ", "o"),
                   ("íì", "i"), ("ý", "y"), ("úů", "u"), ("řŕ", "r"),
                   ("ť", "t"), ("ďđ", "d"),
                   # Norwegian holds ä ö on its own æ ø keys; everywhere else
                   # (Slovak `ä`) they sit on a and o, as in Dictionary.foldBase.
                   ("ä", "æ" if LANG in ("nb", "isc") else "a"),
                   ("ö", "ø" if LANG in ("nb", "isc") else "o"),
                   # Macedonian's internal Latin (tools/build_mk_wordlist.py):
                   # one character per Cyrillic letter, folded onto the key it
                   # is drawn on - `ǉ ǌ ǆ` are the `q w x` keys themselves,
                   # `ǵ ḱ ẑ` are held on `g k z`. Mirrors Dictionary.foldBase.
                   ("ǉ", "q"), ("ǌ", "w"), ("ǆ", "x"),
                   ("ǵ", "g"), ("ḱ", "k"), ("ẑ", "z"),
                   # Russian's internal Latin (tools/build_cyrillic_wordlist.py):
                   # ё on e, щ on w, ь ъ on b, ю on u (э `ê`, ж `ž` above).
                   ("ë", "e"), ("ŵ", "w"), ("ḃḅ", "b"), ("ü", "u"),
                   # Ukrainian's internal Latin: ŝ on s, ĝ on g, ï on i.
                   ("ŝ", "s"), ("ĝ", "g"), ("ï", "i"),
                   # Belarusian ў (u-short) `ŭ`, held on u.
                   ("ŭ", "u")]:
    for _ch in _src:
        _FOLD[_ch] = _dst
# The Ukrainian apostrophe is written but never drawn: it folds to nothing.
_FOLD["'"] = ""


def fold(ch):
    return _FOLD.get(ch, ch)


def _collapse(w):
    """The route over the digraph KEYS.

    `dž` is matched on the raw letters, before folding, because folding turns
    `ž` into `z` and `d`+`z` do meet across a prefix (`odzvanjati`), where they
    are two letters and no `џ`. `Dictionary.pairKey` makes the same distinction
    on the phone.
    """
    s = w.replace("dž", "\x01") if SR_CYRL else w
    f = "".join(fold(c) for c in s)
    return f.replace("lj", "q").replace("nj", "w").replace("\x01", "x")


def fold_word(w):
    # Cyrillic: `lj` and `nj` are ONE key each (`љ` on `q`, `њ` on `w`), and in
    # Serbian `dž` is one more (`џ` on `x`), so the route over them is one
    # waypoint, not two - `Dictionary.foldWord` does the same on the phone.
    return _collapse(w) if DIGRAPH_KEYS else "".join(fold(c) for c in w)


def folds(w):
    """Every route a word can be glided along.

    One, normally. In Cyrillic a word with `lj`/`nj` has TWO - over the `љ`/`њ`
    key, or over `l` and `j`, because both are on the board - and the decoder
    takes whichever fits better (`Dictionary.foldPlain`, 3.6.15).
    """
    plain = "".join(fold(c) for c in w)
    if not DIGRAPH_KEYS:
        return (plain,)
    collapsed = _collapse(w)
    return (collapsed,) if collapsed == plain else (collapsed, plain)


# ---- synthetic swipe ---------------------------------------------------------
# Points are (x, y, t_ms). Time matters: the whole point of the pivot decoder is
# that a real glide is NOT uniform in speed.
SAMPLE_MS = 8.0        # ~125 Hz, a normal Android digitizer
V_CRUISE = 1.9         # px/ms between letters (~1 key width per 60 ms)
V_FLOOR = 0.35         # px/ms — a "stop" is never truly zero; calibrated so a
                       # synthetic glide is ~125 samples, like a 1 s swipe at 125 Hz
DIP_WIDTH = 0.34       # how much of a segment the slow-down around a letter covers
P_PASSTHROUGH = 0.35   # share of interior letters the finger does NOT slow for


def _dwell_factors(n, rng):
    """How hard the finger brakes at each waypoint: 1 = near stop, 0 = cruise.

    Endpoints always brake — the finger genuinely starts and stops there. Interior
    letters are deliberately unreliable: a third of them are passed at full speed
    and the rest brake by a random amount. Without that sloppiness the harness
    would be generating exactly the signal the decoder looks for, and the eval
    would be measuring its own assumption rather than the decoder.
    """
    d = []
    for i in range(n):
        if i == 0 or i == n - 1:
            d.append(1.0)
        elif rng.random() < P_PASSTHROUGH:
            d.append(rng.uniform(0.0, 0.12))
        else:
            d.append(rng.uniform(0.45, 1.0))
    return d


def synth_path(word, noise=22.0, tremor=3.0, rng=None, lift=0.0, keys=None):
    """A plausible finger path through the base-letter keys of `word`.

    Walks the polyline in fixed TIME steps at a speed that dips near the
    waypoints the writer means, so slow stretches come out densely sampled —
    the same way a real touchscreen reports them.

    TWO noise sources, because they are physically different and mixing them
    destroys the signal: `noise` is AIM error, one offset per letter (the finger
    lands off-centre and stays there), while `tremor` is per-sample digitizer
    jitter and is small. Applying aim-sized noise per sample — which is what a
    distance-sampled synthesiser gets away with — turns the trail into a cloud
    and every decoder in here reads 15%.
    """
    rng = rng or random
    # `keys` draws a GIVEN key sequence instead of the word's own route. Needed
    # because `fold_word` folds `lj` onto the `љ` key in Cyrillic, so passing an
    # unfolded word here cannot express "the finger went over `l` and `j`" - it
    # gets folded again (paid for 2026-11-08: three variants of a measurement
    # came out identical because every one of them drew the same gesture).
    keys = [k for k in (keys if keys is not None else fold_word(word)) if k in CENTERS]
    if len(keys) < 2:
        return None
    aimed = [(CENTERS[k][0] + rng.gauss(0, noise),
              CENTERS[k][1] + rng.gauss(0, noise)) for k in keys]
    dwell = _dwell_factors(len(keys), rng)
    pts = []
    t = 0.0
    for i in range(len(aimed) - 1):
        ax, ay = aimed[i]
        bx, by = aimed[i + 1]
        seg = math.hypot(bx - ax, by - ay)
        if seg <= 0:
            continue
        s = 0.0
        guard = 0
        while s < seg and guard < 4000:
            guard += 1
            u = s / seg
            # Gaussian brake bumps at each end of the segment.
            near_a = math.exp(-((u / DIP_WIDTH) ** 2))
            near_b = math.exp(-(((1.0 - u) / DIP_WIDTH) ** 2))
            slow = dwell[i] * near_a + dwell[i + 1] * near_b
            v = max(V_FLOOR, V_CRUISE * (1.0 - min(1.0, slow)))
            pts.append((ax + (bx - ax) * u + rng.gauss(0, tremor),
                        ay + (by - ay) * u + rng.gauss(0, tremor), t))
            dt = SAMPLE_MS * rng.uniform(0.85, 1.15)   # digitizer jitter
            s += v * dt
            t += dt
    ex, ey = aimed[-1]
    pts.append((ex + rng.gauss(0, tremor), ey + rng.gauss(0, tremor), t))
    # TOUCH LIFT: a real touch is reported ABOVE the key the thumb means - the
    # contact patch is the pad of the finger, the phone reports its centroid,
    # and the thumb comes from below. Measured at 0.35 key widths on 34 labelled
    # iPhone gestures (ios/Keyboard/SwipeDecoder.swift). Synthetic paths run
    # through the exact centres, so this is the one knob that lets the harness
    # ask what that offset does to a decoder that ignores it.
    if lift:
        off = lift * KEY_W
        pts = [(x, y - off, t) for x, y, t in pts]
    return pts


def sample_points(pts, cap=48):
    if len(pts) <= cap:
        return pts
    step = max(1, len(pts) // cap)
    out = pts[::step]
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


# ---- resampling --------------------------------------------------------------
def resample(points, n):
    """Arc-length resample: even spacing in DISTANCE. Discards dwell."""
    if len(points) < 2:
        return None
    cum = [0.0]
    for i in range(1, len(points)):
        cum.append(cum[-1] + math.hypot(points[i][0] - points[i - 1][0],
                                        points[i][1] - points[i - 1][1]))
    total = cum[-1]
    if total == 0:
        return [points[0]] * n
    step = total / (n - 1)
    out = []
    j = 0
    for i in range(n):
        t = i * step
        while j < len(cum) - 2 and cum[j + 1] < t:
            j += 1
        seg = cum[j + 1] - cum[j]
        f = 0.0 if seg == 0 else (t - cum[j]) / seg
        out.append((points[j][0] + (points[j + 1][0] - points[j][0]) * f,
                    points[j][1] + (points[j + 1][1] - points[j][1]) * f))
    return out


def index_resample(points, n):
    """Resample by INDEX (time), keeping dense regions dense — preserves dwell.

    Mirrors KeyboardView.indexResample.
    """
    if len(points) < 2:
        return None
    step = (len(points) - 1) / (n - 1)
    return [points[min(len(points) - 1, max(0, round(i * step)))] for i in range(n)]


def ideal_with_dwell(folded, dwell=3, per_seg=5):
    """A word's ideal route with equal dwell at each letter.

    Mirrors KeyboardView.idealWithDwell.
    """
    seq = [CENTERS[c] for c in folded if c in CENTERS]
    if len(seq) != len(folded) or len(seq) < 2:
        return None
    pts = []
    for i in range(len(seq) - 1):
        a, b = seq[i], seq[i + 1]
        pts.extend([a] * dwell)
        for k in range(per_seg):
            t = k / per_seg
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    pts.extend([seq[-1]] * dwell)
    return pts


def shape_dist(rp_a, rp_b):
    s = 0.0
    for a, b in zip(rp_a, rp_b):
        s += math.hypot(a[0] - b[0], a[1] - b[1])
    return s / len(rp_a)


# ---- velocity minima ---------------------------------------------------------
# Tunables, all measured below rather than guessed.
V_SMOOTH = 5           # moving-average width over the speed curve
V_RADIUS = 3           # a minimum must be the slowest sample within +/- this
V_REL = 0.80           # ...and slower than this share of the local mean speed
V_MIN_SEP = 4          # samples; closer minima collapse into the slower one
PIVOT_CAP = 1.6 * KEY_W    # one bad pivot must not swamp the score
W_LETTER = 0.55        # weight: every letter wants a pivot near it
W_PIVOT = 0.30         # weight: every pivot wants a letter near it


def speed_curve(pts):
    """Per-sample speed in px/ms, smoothed. len == len(pts)."""
    n = len(pts)
    if n < 3:
        return None
    raw = [0.0] * n
    for i in range(1, n):
        dt = pts[i][2] - pts[i - 1][2]
        if dt <= 0:
            dt = SAMPLE_MS
        raw[i] = math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]) / dt
    raw[0] = raw[1]
    half = V_SMOOTH // 2
    out = []
    for i in range(n):
        lo, hi = max(0, i - half), min(n, i + half + 1)
        out.append(sum(raw[lo:hi]) / (hi - lo))
    return out


def velocity_minima(pts):
    """Where the finger slowed enough to mean something.

    Returns (index, confidence) pairs. Confidence is how DEEP the minimum is
    relative to the surrounding speed — 1.0 for a dead stop, 0.0 for a dimple in
    an otherwise constant glide. It is the safety valve: someone who swipes fast
    and flat produces minima with no depth, their confidence collapses, and the
    pivot term fades out instead of inventing structure that is not there.

    First and last sample always count, at full confidence: a glide starts and
    ends at rest, and those two are the anchors the decoder already trusts.
    """
    n = len(pts)
    if n < 5:
        return [(0, 1.0), (n - 1, 1.0)] if n >= 2 else []
    v = speed_curve(pts)
    if v is None:
        return [(0, 1.0), (n - 1, 1.0)]
    cand = []
    for i in range(1, n - 1):
        lo, hi = max(0, i - V_RADIUS), min(n, i + V_RADIUS + 1)
        if v[i] > min(v[lo:hi]):
            continue
        wlo, whi = max(0, i - V_SMOOTH * 2), min(n, i + V_SMOOTH * 2 + 1)
        local = sum(v[wlo:whi]) / (whi - wlo)
        if local <= 0 or v[i] > V_REL * local:
            continue
        cand.append((i, max(0.0, min(1.0, 1.0 - v[i] / local))))
    # Collapse runs: a flat slow stretch produces a cluster of near-equal minima,
    # and they all mean ONE letter.
    merged = []
    for i, conf in cand:
        if merged and i - merged[-1][0] < V_MIN_SEP:
            if v[i] < v[merged[-1][0]]:
                merged[-1] = (i, conf)
        else:
            merged.append((i, conf))
    return [(0, 1.0)] + [(i, c) for i, c in merged if 0 < i < n - 1] + [(n - 1, 1.0)]


def pivot_penalty(pivots, folded):
    """How badly the word and the pivots disagree, in px.

    Two directions, both capped, both weighted by the minimum's confidence: an
    unexplained pause (a pivot with no letter) and an invented letter (a letter
    with no pause). The letter side is what separates `mozemo` from `mozehmo` —
    the detour through `h` sits close enough to the path that shape distance
    barely notices, but nothing in the finger's speed suggests a letter there.
    """
    cs = [CENTERS[c] for c in folded if c in CENTERS]
    if len(cs) != len(folded) or not pivots:
        return None
    conf_sum = sum(c for _, _, _, c in pivots)
    if conf_sum <= 0:
        return 0.0
    # A word is only asked to explain pivots as far as the evidence goes.
    trust = min(1.0, conf_sum / max(1, len(pivots)))
    letter_pen = 0.0
    for cx, cy in cs:
        d = min(math.hypot(cx - px, cy - py) for px, py, _, _ in pivots)
        letter_pen += min(d, PIVOT_CAP)
    pivot_pen = 0.0
    for px, py, _, conf in pivots:
        d = min(math.hypot(cx - px, cy - py) for cx, cy in cs)
        pivot_pen += conf * min(d, PIVOT_CAP)
    return trust * (W_LETTER * (letter_pen / len(cs)) +
                    W_PIVOT * (pivot_pen / conf_sum))


# ---- wordlist ----------------------------------------------------------------
def load_words():
    words = []
    with open(WORDLIST, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s.startswith("word="):
                continue
            fi = s.find(",f=")
            if fi < 0:
                continue
            w = s[5:fi]
            fr = int(s[fi + 3:]) if s[fi + 3:].isdigit() else 0
            if w:
                words.append((w, fr))
    return words


def build_buckets(words):
    """Group words by the first key of every route they can be glided along."""
    buckets = {}
    for w, fr in words:
        for folded in folds(w):
            if len(folded) < 2 or len(folded) > 24:
                continue
            if any(c not in CENTERS for c in folded):
                continue
            buckets.setdefault(folded[0], []).append((w, folded, fr))
    return buckets


def nearest_key(p):
    return min(CENTERS, key=lambda k: (p[0] - CENTERS[k][0]) ** 2 +
                                      (p[1] - CENTERS[k][1]) ** 2)


def key_path(pts):
    """Deduped sequence of keys the trail passes over — same idea as the app."""
    seq = []
    for p in pts:
        k = nearest_key(p)
        if not seq or seq[-1] != k:
            seq.append(k)
    return seq


def nearest_keys(p, limit, reach=KEY_W * 1.3):
    """Keys a glide could plausibly have started/ended on. Mirrors nearestKeys."""
    d = [(math.hypot(p[0] - c[0], p[1] - c[1]), k) for k, c in CENTERS.items()]
    return [k for dist, k in sorted(d) if dist <= reach][:limit]


# ---- anchors -----------------------------------------------------------------
# Which keys a glide is ALLOWED to have started and ended on. Both are hard
# gates: the start chooses the bucket, the end discards every word that does not
# finish there. Today each is read off ONE sample - the first and the last - so a
# single noisy point decides whether the right word may compete at all.
#
#   point  what ships: nearest keys to the first / last sample (the default)
#   slow   nearest keys to the SLOWEST sample in a window at that end
#   near   keys that came within reach ANYWHERE in that window, nearest first
#
# SWIPE_ANCHOR=slow SWIPE_ANCHOR_SIDE=end SWIPE_ANCHOR_MS=120 python tools/swipe_eval.py
# Mirrors KeyboardView.anchorPoint since 3.6.1: "slow" is what ships. Set
# SWIPE_ANCHOR=point to measure the old single-sample anchors again.
ANCHOR_MODE = os.environ.get("SWIPE_ANCHOR", "slow")
ANCHOR_SIDE = os.environ.get("SWIPE_ANCHOR_SIDE", "both")
ANCHOR_MS = float(os.environ.get("SWIPE_ANCHOR_MS", "120"))   # mirrors KeyboardView.ANCHOR_MS


def _anchor_span(path):
    """How far back each anchor may look, in ms.

    Never more than a THIRD of the gesture, so the two windows cannot meet. A
    quick flick - `se` is one - can last under 240 ms end to end, and with a
    flat 120 ms each side the start anchor was free to pick a sample from the
    end of the path and vice versa. Measured before the clamp: `se` at 90 ms
    decoded as `rese`, `pisati` at 240 ms as `porastes`.
    """
    span = path[-1][2] - path[0][2]
    return min(ANCHOR_MS, span / 3.0) if span > 0 else 0.0


def _anchor_window(path, at_end):
    """Indices within the allowed span of the chosen end, always at least one."""
    ms = _anchor_span(path)
    if at_end:
        t_end = path[-1][2]
        lo = len(path) - 1
        for i in range(len(path) - 1, -1, -1):
            if t_end - path[i][2] > ms:
                break
            lo = i
        return range(lo, len(path))
    t0 = path[0][2]
    hi = 1
    for i in range(len(path)):
        if path[i][2] - t0 > ms:
            break
        hi = i + 1
    return range(0, hi)


def anchor_keys(path, at_end, speeds=None):
    side_on = ANCHOR_SIDE == "both" or ANCHOR_SIDE == ("end" if at_end else "start")
    p_ext = path[-1] if at_end else path[0]
    if ANCHOR_MODE == "point" or not side_on:
        return nearest_keys(p_ext, ANCHOR_KEYS)

    win = _anchor_window(path, at_end)
    if ANCHOR_MODE == "slow":
        v = speeds or speed_curve(path)
        if not v:
            return nearest_keys(p_ext, ANCHOR_KEYS)
        i = min(win, key=lambda j: v[j])
        return nearest_keys(path[i], ANCHOR_KEYS)

    if ANCHOR_MODE == "near":
        # Closest this key ever came during the window, not at one instant.
        best = {}
        for j in win:
            x, y = path[j][0], path[j][1]
            for k, c in CENTERS.items():
                d = math.hypot(x - c[0], y - c[1])
                if d < best.get(k, 1e9):
                    best[k] = d
        reach = KEY_W * 1.3
        return [k for d, k in sorted((d, k) for k, d in best.items() if d <= reach)][:ANCHOR_KEYS]

    raise SystemExit("unknown SWIPE_ANCHOR=%r" % ANCHOR_MODE)


# ---- decoders ----------------------------------------------------------------
RESAMPLE_N = 32
DWELL_N = 28           # mirrors KeyboardView.DWELL_N
ANCHOR_KEYS = 3        # mirrors KeyboardView.ANCHOR_KEYS
FREQ_W = 3.5           # mirrors Dictionary.FREQ_W
FREQ_LIN = 0.15        # mirrors Dictionary.FREQ_LIN
DOUBLE_W = 10.0        # mirrors Dictionary.DOUBLE_W
# Mirrors Dictionary.LINEAR_FREQ. A list whose byte was log-scaled from a corpus
# COUNT must pull linearly in the byte, or scoring takes the log twice and the
# frequency signal nearly vanishes. Override to test a language's move:
#     SWIPE_LINEAR=pl,cs SWIPE_LANG=pl python tools/swipe_eval.py
LINEAR_FREQ = set(filter(None, os.environ.get("SWIPE_LINEAR", "isc,nb,en").split(",")))


def freq_term(f):
    """Mirrors Dictionary.freqTerm - in PIXELS of path deviation."""
    log = math.log(f + 1) * FREQ_W
    return max(log, f * FREQ_LIN) if LANG in LINEAR_FREQ else log


def doubles(folded):
    """Mirrors Dictionary.doubles: a doubled letter draws the same line as one."""
    return sum(1 for a, b in zip(folded, folded[1:]) if a == b)
TOUCH_LIFT = 0.35      # mirrors KeyboardView.TOUCH_LIFT / SwipeDecoder.defaultTouchLift


MODES = ("arc", "dwell", "dwellpivot", "pivot")


def decode(path, buckets, n=3, mode="pivot", lift_comp=0.0):
    """How the finger's trail is compared to a word's ideal route:

      arc         arc-length resample, no dwell.       The harness's original.
      dwell       index (time) resample + dwell ideal. MIRRORS what ships today.
      dwellpivot  dwell + velocity minima.
      pivot       arc + velocity minima.               Dwell read explicitly
                  instead of smuggled in through the sampling.
    """
    use_arc = mode in ("arc", "pivot")
    use_pivots = mode in ("pivot", "dwellpivot")
    if len(path) < 3:
        return []
    # Compensation for the touch lift, mirroring SwipeDecoder.swift (and
    # KeyboardView.TOUCH_LIFT): push the whole trail back DOWN before anything
    # reads it, so the anchor keys and the shape are judged where the thumb
    # aimed, not where the glass reported it.
    if lift_comp:
        off = lift_comp * KEY_W
        path = [(p[0], p[1] + off, p[2]) for p in path]
    ks = key_path(sample_points(path, 20))
    if len(ks) < 2:
        return []
    hi = len(ks) + 2                              # word no longer than keys crossed
    spd = speed_curve(path) if ANCHOR_MODE == "slow" else None
    starts = set(anchor_keys(path, False, spd)) or {ks[0]}
    ends = set(anchor_keys(path, True, spd))

    rp = resample(path, RESAMPLE_N) if use_arc else index_resample(path, DWELL_N)
    if rp is None:
        return []
    pivots = ([(path[i][0], path[i][1], path[i][2], c)
               for i, c in velocity_minima(path)] if use_pivots else None)

    ranked = []
    for first in starts:
        for w, folded, fr in buckets.get(first, ()):
            if len(folded) > hi:
                continue
            if ends and folded[-1] not in ends:
                continue
            if use_arc:
                ideal = resample([CENTERS[c] for c in folded], RESAMPLE_N)
            else:
                raw = ideal_with_dwell(folded)
                ideal = index_resample(raw, DWELL_N) if raw else None
            if ideal is None:
                continue
            score = -shape_dist(rp, ideal) + freq_term(fr) - doubles(folded) * DOUBLE_W
            if pivots:
                pen = pivot_penalty(pivots, folded)
                if pen is not None:
                    score -= pen
            ranked.append((score, w))
    ranked.sort(key=lambda t: (-t[0], t[1]))
    seen, out = set(), []
    for _, w in ranked:
        if w not in seen:
            seen.add(w); out.append(w)
        if len(out) >= n:
            break
    return out


# ---- run ---------------------------------------------------------------------
# The GATE: words a healthy decoder must keep returning in top-3 on a clean
# synthetic glide. A regression here fails the build.
MUST_HIT_TOP3 = ["slovo", "možemo", "sejčas", "člověk", "dělati", "pisati", "život"]
# WATCH: known-hard cases (short words / same-shape families) the dwell/context
# work targets. Reported, but they do NOT fail the gate yet.
WATCH_TOP3 = ["kako", "izdělati"]
# The synthetic path is random — aim error and which letters the finger bothers
# to slow for are both drawn per glide. One draw per gate word is a coin flip:
# `dělati` and `pisati` each failed a single draw while hitting 7 of 8, in BOTH
# decoders, which would have read as a regression that was never there. So each
# gate word is swiped GATE_DRAWS times and has to land most of them.
# A real glide is FAST. The harness synthesises generous timings, so these
# shapes are squeezed and must still decode as themselves - the gate that was
# missing when the anchor windows began to overlap on short gestures.
FAST_WORDS = ["se", "to", "je", "pisati", "slovo"]
FAST_MS = (240, 150, 90)
FAST_DRAWS = 6
FAST_MS_PER_LETTER = 40
FAST_MIN_HITS = 4
GATE_DRAWS = 8
GATE_MIN_HITS = 5
MIN_TOP1 = 0.65
MIN_TOP3 = 0.82


def eval_sample(words, buckets, mode, sample, seed=7, lift=0.0, comp=0.0):
    """top-1 / top-3 / nothing-at-all on `sample`.

    Same seed => same paths across modes. `lift` synthesises the paths as a real
    thumb reports them (high); `comp` is what the shipped decoder subtracts back.
    `dead` counts the gestures that returned NO candidate at all - on the phone
    those are the glides where nothing is typed and the user swipes again
    (KeyboardView.commitSwipeResult: `if (words.isEmpty()) { clearSlots(); return }`).
    """
    rng = random.Random(seed)
    t1 = t3 = tot = dead = 0
    for tg in sample:
        path = synth_path(tg, rng=rng, lift=lift)
        if not path:
            continue
        top = decode(path, buckets, 3, mode=mode, lift_comp=comp)
        tot += 1
        if not top:
            dead += 1
        if top and top[0] == tg:
            t1 += 1
        if tg in top:
            t3 += 1
    return t1 / tot, t3 / tot, tot, dead / tot


def default_sample(words, k=80):
    by = {w: fr for w, fr in words}
    rng = random.Random(7)
    pool = [w for w, fr in words if 3 <= len(fold_word(w)) <= 9]
    return list(dict.fromkeys(sorted(pool, key=lambda w: -by[w])[:k] +
                              rng.sample(pool, k)))


def run_test(words, buckets, mode="pivot"):
    """Automated regression gate. Exits non-zero on failure (CI-friendly)."""
    by = {w: fr for w, fr in words}
    ok = True
    rng = random.Random(1)

    print(f"== gate words ({GATE_MIN_HITS}+/{GATE_DRAWS} draws in top-3) ==   [mode={mode}]")
    for tg in MUST_HIT_TOP3:
        if tg not in by:
            print(f"  SKIP  {tg!r} (not in wordlist)"); continue
        hits, first = 0, None
        for _ in range(GATE_DRAWS):
            path = synth_path(tg, rng=rng)
            top = decode(path, buckets, 3, mode=mode) if path else []
            if first is None:
                first = top
            if tg in top:
                hits += 1
        if hits < GATE_MIN_HITS:
            ok = False
        status = "OK  " if hits >= GATE_MIN_HITS else "FAIL"
        print(f"  {status} {tg!r:14} {hits}/{GATE_DRAWS}  e.g. {first}")

    print("== watch (known-hard, not gated) ==")
    for tg in WATCH_TOP3:
        if tg not in by:
            continue
        hits, first = 0, None
        for _ in range(GATE_DRAWS):
            path = synth_path(tg, rng=rng)
            top = decode(path, buckets, 3, mode=mode) if path else []
            if first is None:
                first = top
            if tg in top:
                hits += 1
        print(f"       {tg!r:14} {hits}/{GATE_DRAWS}  e.g. {first}")

    # The synthetic paths are generous in TIME - `pisati` comes out 1880 ms, and
    # nobody swipes like that. That blind spot let an anchor change ship that
    # broke every quick flick: the two anchor windows overlapped, so the start
    # was read off the end of the path. Squeeze the same gestures and insist
    # they still decode.
    print("== fast flicks (same shapes, squeezed in time) ==")
    for tg in FAST_WORDS:
        if tg not in by:
            continue
        row = []
        # Below roughly 40 ms per letter the digitiser has fewer than three
        # samples per transition, which is less than the shape can be described
        # with - demanding recovery there would be demanding it from physics,
        # not from the decoder. `pisati` in 90 ms is eleven samples for five
        # transitions.
        speeds = [ms for ms in FAST_MS if ms >= FAST_MS_PER_LETTER * len(tg)] or [max(FAST_MS)]
        for ms in speeds:
          hits = 0
          for draw in range(FAST_DRAWS):
            path = synth_path(tg, rng=random.Random(100 + draw))
            if not path:
                continue
            t0, t1 = path[0][2], path[-1][2]
            span = (t1 - t0) or 1.0
            # A fast gesture has FEWER samples, not the same ones crammed
            # together: the digitiser keeps reporting every ~SAMPLE_MS whatever
            # the finger does. Compressing time alone produced sub-millisecond
            # spacing and its own artefacts - `slovo` "failed" on an instrument
            # error before this line existed.
            keep = max(4, int(round(ms / SAMPLE_MS)))
            step = (len(path) - 1) / float(keep - 1)
            idx = sorted({int(round(i * step)) for i in range(keep)})
            squeezed = [(path[j][0], path[j][1], t0 + (path[j][2] - t0) / span * ms)
                        for j in idx]
            top = decode(squeezed, buckets, 3, mode=mode)
            if tg in top:
                hits += 1
          # Same contract as the gate words above: top-3, several draws, a
          # majority. One draw is a coin flip - `slovo` failed at 150 ms and
          # passed at 90 ms on single draws, which is noise wearing a verdict's
          # clothes. What this must catch is the anchors COLLAPSING, and that
          # returns words with nothing in common with the target at all.
          row.append(f"{ms}ms {hits}/{FAST_DRAWS}")
          if hits < FAST_MIN_HITS:
                ok = False
        print(f"       {tg!r:14} " + "   ".join(row))

    sample = default_sample(words)
    a1, a3, tot, _ = eval_sample(words, buckets, mode, sample,
                                 lift=TOUCH_LIFT, comp=TOUCH_LIFT)
    print(f"\n== accuracy on {tot} words ==")
    print(f"  top-1 {a1:.0%} (min {MIN_TOP1:.0%})   top-3 {a3:.0%} (min {MIN_TOP3:.0%})")
    if a1 < MIN_TOP1 or a3 < MIN_TOP3:
        ok = False

    print("\nRESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


def run_lift(words, buckets, mode="pivot"):
    """What the touch lift costs, and what compensating for it buys back.

    Reproduces the owner's standing complaint ("the swipe does not catch, I have
    to do it two or three times") as a number: a thumb whose touches land high
    pushes the first and last letters into the row ABOVE, the anchor sets
    (`starts`, `ends`) then never contain the real letters, the decoder returns
    NOTHING, and KeyboardView types nothing at all. The `nothing` column is that
    silence - every one of those is a glide the user has to repeat.
    """
    sample = default_sample(words)
    print(f"touch lift on {len(sample)} words, mode={mode} — identical paths per row\n")
    print(f"  {'lift':>6} {'compensated':>12} {'top-1':>7} {'top-3':>7} {'nothing':>9}")
    for lift in (0.0, 0.2, 0.35, 0.5):
        for comp in (0.0, TOUCH_LIFT):
            a1, a3, tot, dead = eval_sample(words, buckets, mode, sample,
                                            lift=lift, comp=comp)
            tag = f"-{comp:.2f}" if comp else "no"
            print(f"  {lift:6.2f} {tag:>12} {a1:6.0%} {a3:6.0%} {dead:8.0%}")
    print("\nThe lift is a property of the FINGER, so the synthetic paths get it")
    print("applied on purpose; a path through the exact key centres cannot show")
    print("what a real thumb costs. 0.35 is the figure measured on 34 labelled")
    print("iPhone gestures (ios/Keyboard/SwipeDecoder.swift, 2026-10-07).")


def run_ab(words, buckets):
    """Same synthetic paths, three decoders. The only honest way to read a gain."""
    sample = default_sample(words)
    print(f"A/B on {len(sample)} words — identical paths per mode\n")
    print(f"  {'mode':11} {'top-1':>7} {'top-3':>7}")
    base1 = base3 = None
    for mode in MODES:
        a1, a3, tot, _ = eval_sample(words, buckets, mode, sample)
        if mode == "dwell":
            base1, base3 = a1, a3
        delta = ""
        if base1 is not None and mode != "dwell":
            delta = f"   ({a1 - base1:+.0%} / {a3 - base3:+.0%} vs shipped)"
        print(f"  {mode:11} {a1:6.0%} {a3:6.0%}{delta}")
    print("\nNote: synthetic paths are generated with a speed profile, so the")
    print("pivot gain here is an UPPER bound. The device is the verdict.")


# ---- real glides ------------------------------------------------------------
# Everything above this line runs on SYNTHETIC paths, and on 2026-09-03 that
# model was caught lying: it scored `pisati` 15-25 points ahead of `prati` on
# every simulated attempt while the phone produced `prati` ten times in a row.
# A model of a gesture is not a gesture, so the test keyboard now records the
# real ones (medzuucenje `gestures`) and this replays them.
#
#     python swipe_eval.py real            # score the decoder on real glides
#     python swipe_eval.py real --list     # just show what has been collected
#
# The DSN is read from the medzuucenje checkout, never passed on a command line.
MEDZUUCENJE_ENV = os.path.expanduser(
    "~/Projects/medzuucenje/.env")


def load_gestures(limit=500):
    """Labelled glides: those where the user told us what they actually wanted.

    Pulled through `psql` in a container rather than a Python driver - the tool
    then needs nothing installed and works the same way the rest of this fleet
    is operated. The DSN is read from the medzuucenje checkout and handed over on
    stdin-free argv only inside the container, never printed.
    """
    import json as _json
    import subprocess
    dsn = ""
    with open(MEDZUUCENJE_ENV, encoding="utf-8") as f:
        for line in f:
            if line.startswith("MEDZUUCENJE_DSN="):
                dsn = line.split("=", 1)[1].strip()
    if not dsn:
        raise SystemExit(f"no MEDZUUCENJE_DSN in {MEDZUUCENJE_ENV}")
    sql = (
        "select coalesce(json_agg(g), '[]') from ("
        " select id, device, trail, keys, candidates, committed, chosen, outcome"
        " from gestures where jsonb_array_length(trail) > 4"
        f" order by id desc limit {int(limit)}) g"
    )
    out = subprocess.run(
        ["docker", "run", "--rm", "postgres:18-alpine",
         "psql", dsn.replace("-pooler", ""), "-tAc", sql],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit("could not read gestures:\n" + out.stderr.strip()[:400])
    rows = _json.loads(out.stdout.strip() or "[]")
    return [(r["id"], r["device"], r["trail"], r["keys"], r["candidates"],
             r["committed"], r["chosen"], r["outcome"]) for r in rows]


def replay(rows, mode="pivot"):
    """Decode each recorded glide with THAT phone's key geometry.

    Coordinates are meaningless without the geometry they were drawn on - key
    sizes differ per device - so CENTERS is swapped per row. The word list and
    its buckets do not depend on the coordinates, only on which letters exist,
    so they stay valid across the swap.
    """
    global CENTERS
    words = load_words()
    buckets = build_buckets(words)
    saved = CENTERS
    hit1 = hit3 = labelled = 0
    misses = []
    try:
        for gid, device, trail, keys, cands, committed, chosen, outcome in rows:
            if not keys:
                continue
            CENTERS = {k: (float(v[0]), float(v[1])) for k, v in keys.items()}
            path = [(float(p[0]), float(p[1]), float(p[2])) for p in trail]
            want = chosen or (committed if outcome == "kept" else None)
            if not want:
                continue           # deleted and never resolved - no ground truth
            labelled += 1
            top = decode(path, buckets, 3, mode=mode)
            if top and top[0] == want:
                hit1 += 1
            if want in top:
                hit3 += 1
            else:
                misses.append((gid, want, committed, top))
    finally:
        CENTERS = saved
    return labelled, hit1, hit3, misses


# ---- digraph keys (Cyrillic and Glagolitic): both routes must decode --------------------------
# The `lj`/`nj` keys on `q` and `w` (`љ` `њ` in Cyrillic, `ⰾⱐ` `ⱀⱐ`
# in Glagolitic) mean a word with `lj`/`nj` can be glided TWO ways,
# over the new key or over the two letters, and the owner asked for both to
# work (2026-11-08). This gate watches that, with the negative control that
# makes it believable: with only the collapsed route in the dictionary - which
# is what build 3.6.14 shipped - the two-letter gesture must FAIL. A gate nobody
# has seen fail looks exactly like a dead one.
DIGRAPH_WORDS = ["ljudi", "bolje", "denj", "veljmi", "cibulje", "nositelja",
                 "tečenju", "mljaskavše"]
# Serbian: the same gate over its own words, with `dž` words carrying the third
# digraph key. `džep` `džak` `džem` are the ones the owner tests by hand.
DIGRAPH_WORDS_SR = ["ljudi", "ljubav", "njegov", "konj", "bolje", "nedelja",
                    "džep", "džak", "džem"]
DIGRAPH_DRAWS = 6
DIGRAPH_MIN_TOP3 = 0.75      # both routes, over all draws
DIGRAPH_CONTROL_MAX = 0.20   # the one-route dictionary on the two-letter gesture


def _plain_fold(w):
    return "".join(fold(c) for c in w)


def run_digraph(words):
    if not DIGRAPH_KEYS:
        print("digraph gate: only meaningful with SWIPE_SCRIPT=cyrl - skipped")
        return
    wanted = DIGRAPH_WORDS_SR if SR_CYRL else DIGRAPH_WORDS
    present = [w for w in wanted if any(x == w for x, _ in words)]
    missing = [w for w in wanted if w not in present]
    if missing:
        print(f"  ⚠ not in the wordlist, skipped: {missing}")
    both = build_buckets(words)
    global folds
    plain_only = None
    keep = folds
    try:
        folds = lambda w: (keep(w)[0],)          # collapsed route only = 3.6.14
        plain_only = build_buckets(words)
    finally:
        folds = keep

    print(f"== digraph keys, {SCRIPT} ({len(present)} words x {DIGRAPH_DRAWS} draws) ==")
    tally = {"key": [0, 0], "letters": [0, 0], "control": [0, 0]}
    for w in present:
        for name, buckets, keys in (
            ("key", both, fold_word(w)),
            ("letters", both, _plain_fold(w)),
            ("control", plain_only, _plain_fold(w)),
        ):
            rng = random.Random(7)
            for _ in range(DIGRAPH_DRAWS):
                path = synth_path(w, rng=rng, lift=TOUCH_LIFT, keys=keys)
                top = decode(path, buckets, 3, mode="pivot", lift_comp=TOUCH_LIFT) if path else []
                if top and top[0] == w:
                    tally[name][0] += 1
                if w in top:
                    tally[name][1] += 1
    n = len(present) * DIGRAPH_DRAWS
    ok = True
    for name, label, limit, worse in (
        ("key", "glide over the digraph key", DIGRAPH_MIN_TOP3, False),
        ("letters", "glide over the two letters", DIGRAPH_MIN_TOP3, False),
        ("control", "CONTROL: one-route dictionary, two-letter glide", DIGRAPH_CONTROL_MAX, True),
    ):
        h1, h3 = tally[name]
        share = h3 / n if n else 0.0
        good = share <= limit if worse else share >= limit
        ok = ok and good
        print(f"  {label:<46} top-1 {h1/n:4.0%}  top-3 {share:4.0%}  "
              f"{'ok' if good else 'FAIL'}")
    if not ok:
        print("  digraph gate FAILED")
        sys.exit(1)
    print("  digraph gate ok")


def run_real(argv):
    rows = load_gestures()
    if not rows:
        print("no glides recorded yet - swipe on the test build first")
        return
    if "--list" in argv:
        print(f"{len(rows)} recorded glides (newest first)\n")
        for gid, device, trail, keys, cands, committed, chosen, outcome in rows[:30]:
            print(f"  #{gid:<5} {device:<12} {outcome:<10} "
                  f"committed={committed!r:14} wanted={chosen!r:14} "
                  f"{len(trail):3d} pts  offered={cands[:3]}")
        return
    labelled, h1, h3, misses = replay(rows)
    if not labelled:
        print(f"{len(rows)} glides recorded, but none carry a verdict yet.")
        print("A glide counts once you either keep it or pick the right word "
              "from the strip - deleting it says what was wrong, not what was right.")
        return
    print(f"== {labelled} labelled real glides ==")
    print(f"  top-1 {h1/labelled:.0%}   top-3 {h3/labelled:.0%}")
    if misses:
        print(f"\n== {len(misses)} the decoder never offered ==")
        for gid, want, committed, top in misses[:15]:
            print(f"  #{gid:<5} wanted {want!r:14} got {committed!r:14} -> {top}")


def main():
    words = load_words()
    by_word = {w: fr for w, fr in words}
    buckets = build_buckets(words)
    print(f"loaded {len(words)} forms")

    argv = sys.argv[1:]
    mode = "pivot"
    if argv and argv[0].startswith("--mode="):
        mode = argv.pop(0).split("=", 1)[1]

    if argv and argv[0] == "test":
        run_test(words, buckets, mode)
        return
    if argv and argv[0] == "ab":
        run_ab(words, buckets)
        return
    if argv and argv[0] == "real":
        run_real(argv[1:])
        return
    if argv and argv[0] == "lift":
        run_lift(words, buckets, mode)
        return
    if argv and argv[0] == "digraph":
        run_digraph(words)
        return

    if argv:
        rng = random.Random(1)
        for target in argv:
            if target not in by_word:
                print(f"  {target!r}: NOT in wordlist"); continue
            path = synth_path(target, rng=rng)
            if path is None:
                print(f"  {target!r}: too short"); continue
            piv = velocity_minima(path)
            top = decode(path, buckets, 5, mode=mode)
            hit = "OK" if top and top[0] == target else ("top-k" if target in top else "MISS")
            keys = "".join(nearest_key(path[i]) for i, _ in piv)
            print(f"  {target!r:16} -> {top}   [{hit}]")
            print(f"  {'':16}    {len(path)} samples, pivots at {keys!r}")
        return

    sample = default_sample(words, k=100)
    a1, a3, tot, dead = eval_sample(words, buckets, mode, sample,
                                    lift=TOUCH_LIFT, comp=TOUCH_LIFT)
    print(f"sample={tot}  mode={mode}  top-1={a1:.0%}  top-3={a3:.0%}  nothing={dead:.0%}")


if __name__ == "__main__":
    main()
