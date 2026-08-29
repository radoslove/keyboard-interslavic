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
WORDLIST = os.path.join(HERE, "..", "dictionary", "main_isv.combined")

random.seed(1)

# ---- keyboard geometry (mirrors KeyboardView) --------------------------------
KEY_W = 108.0
KEY_H = 150.0
BOARD_W = KEY_W * 10
ROWS = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]


def build_centers():
    c = {}
    # row 0: 10 keys fill the width
    for i, ch in enumerate(ROWS[0]):
        c[ch] = ((i + 0.5) * BOARD_W / 10.0, 0.5 * KEY_H)
    # row 1: 9 keys fill the width
    for i, ch in enumerate(ROWS[1]):
        c[ch] = ((i + 0.5) * BOARD_W / 9.0, 1.5 * KEY_H)
    # row 2: shift(1.5) + 7 letters + backspace(1.5) = 10 weight
    for i, ch in enumerate(ROWS[2]):
        c[ch] = ((1.5 + i + 0.5) * BOARD_W / 10.0, 2.5 * KEY_H)
    return c


CENTERS = build_centers()


def fold(ch):
    return {"č": "c", "š": "s", "ž": "z", "ě": "e"}.get(ch, ch)


def fold_word(w):
    return "".join(fold(c) for c in w)


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


def synth_path(word, noise=22.0, tremor=3.0, rng=None):
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
    keys = [fold(c) for c in word if fold(c) in CENTERS]
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
    """Group words by first base letter; precompute folded form once."""
    buckets = {}
    for w, fr in words:
        folded = fold_word(w)
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


# ---- decoders ----------------------------------------------------------------
RESAMPLE_N = 32
DWELL_N = 28           # mirrors KeyboardView.DWELL_N
ANCHOR_KEYS = 3        # mirrors KeyboardView.ANCHOR_KEYS
FREQ_W = 2.5           # mirrors Dictionary.decodeSwipeGeo


MODES = ("arc", "dwell", "dwellpivot", "pivot")


def decode(path, buckets, n=3, mode="pivot"):
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
    ks = key_path(sample_points(path, 20))
    if len(ks) < 2:
        return []
    hi = len(ks) + 2                              # word no longer than keys crossed
    starts = set(nearest_keys(path[0], ANCHOR_KEYS)) or {ks[0]}
    ends = set(nearest_keys(path[-1], ANCHOR_KEYS))

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
            score = -shape_dist(rp, ideal) + math.log(fr + 1) * FREQ_W
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
GATE_DRAWS = 8
GATE_MIN_HITS = 5
MIN_TOP1 = 0.65
MIN_TOP3 = 0.82


def eval_sample(words, buckets, mode, sample, seed=7):
    """top-1 / top-3 on `sample`. Same seed => same paths across modes."""
    rng = random.Random(seed)
    t1 = t3 = tot = 0
    for tg in sample:
        path = synth_path(tg, rng=rng)
        if not path:
            continue
        top = decode(path, buckets, 3, mode=mode)
        tot += 1
        if top and top[0] == tg:
            t1 += 1
        if tg in top:
            t3 += 1
    return t1 / tot, t3 / tot, tot


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

    sample = default_sample(words)
    a1, a3, tot = eval_sample(words, buckets, mode, sample)
    print(f"\n== accuracy on {tot} words ==")
    print(f"  top-1 {a1:.0%} (min {MIN_TOP1:.0%})   top-3 {a3:.0%} (min {MIN_TOP3:.0%})")
    if a1 < MIN_TOP1 or a3 < MIN_TOP3:
        ok = False

    print("\nRESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


def run_ab(words, buckets):
    """Same synthetic paths, three decoders. The only honest way to read a gain."""
    sample = default_sample(words)
    print(f"A/B on {len(sample)} words — identical paths per mode\n")
    print(f"  {'mode':11} {'top-1':>7} {'top-3':>7}")
    base1 = base3 = None
    for mode in MODES:
        a1, a3, tot = eval_sample(words, buckets, mode, sample)
        if mode == "dwell":
            base1, base3 = a1, a3
        delta = ""
        if base1 is not None and mode != "dwell":
            delta = f"   ({a1 - base1:+.0%} / {a3 - base3:+.0%} vs shipped)"
        print(f"  {mode:11} {a1:6.0%} {a3:6.0%}{delta}")
    print("\nNote: synthetic paths are generated with a speed profile, so the")
    print("pivot gain here is an UPPER bound. The device is the verdict.")


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
    a1, a3, tot = eval_sample(words, buckets, mode, sample)
    print(f"sample={tot}  mode={mode}  top-1={a1:.0%}  top-3={a3:.0%}")


if __name__ == "__main__":
    main()
