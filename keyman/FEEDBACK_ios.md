# Keyman isv_latin — feedback from real devices

## 2026-09-05 · iPhone 13, iOS 26.6, Keyman from App Store, keyboard from the catalogue (first live iPhone test)

**Works:** install from catalogue, swipe-up on c/s/z/e → č š ž ě, Shift variants, dictionary suggestions. Owner: "śmiga ładnie".

**Gap reported:** "more characters, e.g. `:`". Current phone layout (`isv_latin.keyman-touch-layout`):
- default/shift layer: ONE punctuation key (`.`); `, ! ? „ ” ' " : ;` exist only as long-press subkeys under it → invisible to a new user, and the **comma** — the most frequent punctuation — costs a long-press.
- numeric layer: `$ @ # % & _ = | \ [ ( ) ] + - * /`, no `: ; ! ?` and no quotes; only `– —` as subkeys.

**Proposed change (touch layout v1.7, `build_keyman.py`):**
1. default + shift: add a dedicated **`,`** key left of `.` (shrink the space bar as Apple's own layout does); keep `.` long-press set.
2. numeric layer: add **`: ; ! ? " '`** as visible keys (replace the rarely used `|` `\` `[` `]`, keep them as long-press).
3. Consider a `#+=`-style second symbol layer if the row gets crowded.

**Lane:** `dev-keyboard` (executor); language content unchanged, no tutor review needed. Regenerate with `python3 build_keyman.py`, rebuild `.kmp`, bump version, then the same change goes upstream to `keymanapp/keyboards` (`release/i/isv_latin`) as a follow-up PR.

## 2026-09-21 · iPhone 13, keyboard 1.6 from the catalogue — daily use

Owner started using it for real. Two reports, and they are NOT the same kind of
problem — one is a genuine code bug, the other is only discoverability.

### A. `_` prints `-`, and ten more keys lie  🔴 REAL BUG, shipped in 1.6

On the `123` layer **no key carries a `layer` or `modifier` field**, and the `.kmn`
has no rules for plain punctuation (its only `K_HYPHEN` rules are the AltGr dashes).
So Keyman falls back to the underlying US layout **without shift** and prints the
unshifted character of that physical key:

| key shows | you get | | key shows | you get |
|---|---|---|---|---|
| `_` | `-` | | `(` | `9` |
| `@` | `2` | | `)` | `0` |
| `$` | `4` | | `*` | `8` |
| `#` | `3` | | `+` | `=` |
| `%` | `5` | | `\|` | `\` |
| `&` | `7` | | | |

Eleven keys, one cause. Note the collisions this creates: `_` and `-` both give
`-`; `+` and `=` both give `=`; `|` and `\` both give `\`.

**Contrast — the subkeys under `.` are correct**, because they DO carry
`"layer": "shift"` (`!` `?` `"` `:`). That is the mechanism the numeric layer is
missing.

**Fix:** give the eleven keys their own touch-only `T_` ids with explicit rules,
the pattern already proven for `č š ž ě` and for `„ ” – —` (`TOUCH_PUNCT` in
`build_keyman.py`, emitting both `+ [T_X]` and `+ [SHIFT T_X]`). That removes the
dependency on US fallback entirely instead of adding another modifier to reason
about — and the `.kmn`'s own comments record two device-paid traps from doing
modifier games.

⚠ This bug is **live in the Keyman catalogue** (published version 1.6). Anyone who
installs today gets it. Do not send the iOS link to an external tester until 1.7.

### B. `?` — NOT missing, just invisible  ✅ resolves the 2026-09-05 question

Settled on device: long-pressing `.` opens the popup with all nine subkeys
(`, ! ? „ ” ' " : ;`) and tapping `?` produces `?`. So the popup builds and the
`layer: "shift"` subkeys fire correctly.

The complaint was never that it was broken — it was that a `?` costs a hold on a
key that shows a dot, which no new user will guess. Short-press behaves normally.

**This is the v1.7 item already proposed on 2026-09-05, and the Android app shows
the target design:** there, `? ! : ; „ ” – — ’` are DIRECT keys on the third row of
the `?123` layer — one tap, no picker — and the letter layer exposes only `.` and
`,`. Copy that to iOS rather than inventing a third arrangement.

### Scope note

Parked deliberately: Android ships first, iOS follows once the Android build is
usable. 1.7 = A + B + the dedicated comma key from 2026-09-05, in ONE rebuild, and
it goes to the catalogue only after a device check — A is code-certain, but this
keyboard's history says touch-layout certainty is worth exactly one verification.
