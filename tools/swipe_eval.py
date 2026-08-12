#!/usr/bin/env python3
"""Offline swipe-decoder training/eval harness.

The device round-trip is too slow to tune a glide decoder. So we do it here:

  1. Rebuild the keyboard geometry from the app's layout (qwertyuiop / asdfghjkl
     / zxcvbnm, with the row-2 shift offset — same as KeyboardView).
  2. For a sample of real words, SYNTHESISE a finger path through their letter
     keys (interpolation + gaussian noise), the way a real swipe looks.
  3. Decode each path against the full wordlist and measure top-1 / top-3
     accuracy.

The decoder here mirrors what will go into Dictionary.kt, so tuning transfers.
The scoring is DTW — the whole path is aligned to the whole word, monotonically,
so letters must be visited IN ORDER and every path point is "explained". That
kills both failures we saw on device: wrong-order matches (sejčas -> sěče) and
short-word bias (možemo -> mi / muk / mekko).

    python swipe_eval.py            # eval on a default sample
    python swipe_eval.py možemo kako sejčas   # trace specific words

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
def synth_path(word, noise=22.0, per_seg=9):
    """A plausible finger path through the base-letter keys of `word`."""
    keys = [fold(c) for c in word if fold(c) in CENTERS]
    if len(keys) < 2:
        return None
    pts = []
    for a, b in zip(keys, keys[1:]):
        ax, ay = CENTERS[a]
        bx, by = CENTERS[b]
        for t in [k / per_seg for k in range(per_seg)]:
            x = ax + (bx - ax) * t + random.gauss(0, noise)
            y = ay + (by - ay) * t + random.gauss(0, noise)
            pts.append((x, y))
    ex, ey = CENTERS[keys[-1]]
    pts.append((ex + random.gauss(0, noise), ey + random.gauss(0, noise)))
    return pts


def sample_points(pts, cap=48):
    if len(pts) <= cap:
        return pts
    step = max(1, len(pts) // cap)
    out = pts[::step]
    if out[-1] != pts[-1]:
        out.append(pts[-1])
    return out


# ---- decoder: route-shape matching (owner's idea) ----------------------------
# Instead of "which keys did the finger cross", we MAP THE ROUTE: resample the
# finger's trajectory to N evenly-spaced points, resample each candidate word's
# IDEAL route (the polyline through its letter centres) to the same N points, and
# compare the two curves point-for-point. This is holistic (whole path), ordered
# (both traversed start->end), and it catches detours: možehmo's ideal route
# swings through `h`, which the finger writing možemo never did, so its curve
# diverges.
def resample(points, n):
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


def shape_dist(rp_a, rp_b):
    s = 0.0
    for a, b in zip(rp_a, rp_b):
        s += math.hypot(a[0] - b[0], a[1] - b[1])
    return s / len(rp_a)


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


RESAMPLE_N = 32


def decode(path, buckets, n=3, freq_w=1.5, end_tol=1.4 * KEY_W):
    rp = resample(path, RESAMPLE_N)
    if rp is None:
        return []
    ks = key_path(sample_points(path, 20))       # first-key anchor + length cap
    if len(ks) < 2:
        return []
    fk, lk = ks[0], ks[-1]
    hi = len(ks) + 2                             # word no longer than keys crossed
    lkc = CENTERS[lk]
    ranked = []
    for w, folded, fr in buckets.get(fk, ()):
        if len(folded) > hi:
            continue
        # END anchor: word's last letter near where the finger lifted.
        ec = CENTERS[folded[-1]]
        if math.hypot(ec[0] - lkc[0], ec[1] - lkc[1]) > end_tol:
            continue
        ideal = resample([CENTERS[c] for c in folded], RESAMPLE_N)
        score = -shape_dist(rp, ideal) + math.log(fr + 1) * freq_w
        ranked.append((score, w))
    ranked.sort(reverse=True)
    return [w for _, w in ranked[:n]]


# ---- run ---------------------------------------------------------------------
# The GATE: words a healthy decoder must keep returning in top-3 on a clean
# synthetic glide. A regression here fails the build.
MUST_HIT_TOP3 = ["slovo", "možemo", "sejčas", "člověk", "dělati", "pisati", "život"]
# WATCH: known-hard cases (short words / same-shape families) the dwell/context
# work targets. Reported, but they do NOT fail the gate yet.
WATCH_TOP3 = ["kako", "izdělati"]
MIN_TOP1 = 0.50
MIN_TOP3 = 0.65


def run_test(words, buckets):
    """Automated regression gate. Exits non-zero on failure (CI-friendly)."""
    by = {w: fr for w, fr in words}
    ok = True

    print("== gate words (must be in top-3) ==")
    for tg in MUST_HIT_TOP3:
        if tg not in by:
            print(f"  SKIP  {tg!r} (not in wordlist)"); continue
        path = synth_path(tg)
        top = decode(path, buckets, 3) if path else []
        in3 = tg in top
        status = "OK  " if (top and top[0] == tg) else ("top3" if in3 else "FAIL")
        if not in3:
            ok = False
        print(f"  {status} {tg!r:14} -> {top}")

    print("== watch (known-hard, not gated) ==")
    for tg in WATCH_TOP3:
        if tg not in by:
            continue
        path = synth_path(tg)
        top = decode(path, buckets, 3) if path else []
        status = "OK  " if (top and top[0] == tg) else ("top3" if tg in top else "miss")
        print(f"  {status} {tg!r:14} -> {top}")

    random.seed(7)
    pool = [w for w, fr in words if 3 <= len(fold_word(w)) <= 9]
    sample = list(dict.fromkeys(sorted(pool, key=lambda w: -by[w])[:80] +
                                random.sample(pool, 80)))
    t1 = t3 = tot = 0
    for tg in sample:
        path = synth_path(tg)
        if not path:
            continue
        top = decode(path, buckets, 3)
        tot += 1
        if top and top[0] == tg:
            t1 += 1
        if tg in top:
            t3 += 1
    a1, a3 = t1 / tot, t3 / tot
    print(f"\n== accuracy on {tot} words ==")
    print(f"  top-1 {a1:.0%} (min {MIN_TOP1:.0%})   top-3 {a3:.0%} (min {MIN_TOP3:.0%})")
    if a1 < MIN_TOP1 or a3 < MIN_TOP3:
        ok = False

    print("\nRESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)


def main():
    words = load_words()
    by_word = {w: fr for w, fr in words}
    buckets = build_buckets(words)
    print(f"loaded {len(words)} forms")

    if len(sys.argv) > 1 and sys.argv[1] == "test":
        run_test(words, buckets)
        return

    if len(sys.argv) > 1:
        for target in sys.argv[1:]:
            if target not in by_word:
                print(f"  {target!r}: NOT in wordlist"); continue
            path = synth_path(target)
            if path is None:
                print(f"  {target!r}: too short"); continue
            top = decode(path, buckets, 5)
            hit = "OK" if top and top[0] == target else ("top-k" if target in top else "MISS")
            print(f"  {target!r:16} -> {top}   [{hit}]")
        return

    # accuracy on a sample: high-freq words + random ones
    pool = [w for w, fr in words if 3 <= len(fold_word(w)) <= 9]
    hi = sorted(pool, key=lambda w: -by_word[w])[:100]
    rnd = random.sample(pool, 100)
    sample = list(dict.fromkeys(hi + rnd))
    top1 = topk = tot = 0
    for target in sample:
        path = synth_path(target)
        if path is None:
            continue
        top = decode(path, buckets, 3)
        tot += 1
        if top and top[0] == target:
            top1 += 1
        if target in top:
            topk += 1
    print(f"sample={tot}  top-1={top1/tot:.0%}  top-3={topk/tot:.0%}")


if __name__ == "__main__":
    main()
