#!/usr/bin/env python3
"""Mean top-1 / top-3 of `swipe_eval.py` over seeds 1-5.

    SWIPE_LANG=ru python tools/five_seeds.py

One run swings 7-9 points of top-1 between seeds, so layout and decoder
decisions are taken on this mean, never on a single run (2026-11-10).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import swipe_eval as se  # noqa: E402

words = se.load_words()
buckets = se.build_buckets(words)
sample = se.default_sample(words, k=100)
r1, r3 = [], []
for seed in range(1, 6):
    a1, a3, tot, dead = se.eval_sample(words, buckets, "pivot", sample, seed=seed,
                                       lift=se.TOUCH_LIFT, comp=se.TOUCH_LIFT)
    r1.append(a1); r3.append(a3)
    print(f"seed {seed}: top-1 {a1:.1%}  top-3 {a3:.1%}  nothing {dead:.0%}  (n={tot})")
print(f"{se.LANG} mean of 5: top-1 {sum(r1) / 5:.1%}  top-3 {sum(r3) / 5:.1%}")
