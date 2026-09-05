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
