# Testing checklist

How to verify each keyboard layout + the swipe dictionary actually work — as a
**new user, on a clean device**. Follow only the platform README, as if you'd
never seen this project. If a step is unclear or missing, that's a doc bug worth
fixing → note it under [What to report](#what-to-report).

Every desktop layout (Windows / macOS / Linux) shares the same **core checks**.
Android is different (tap + swipe) and adds the dictionary.

---

## Core checks — Windows / macOS / Linux

The modifier that carries the letters:

| Platform | Modifier |
|---|---|
| Windows | **AltGr** (Right Alt) |
| macOS | **Option** (⌥) |
| Linux | **AltGr** (Right Alt) |

| # | Do | Expect | ✓ |
|---|---|---|---|
| 1 | Add + select the layout (per README) | Appears under the right language; switches **without hang** | ☐ |
| 2 | Type `` ` `` and `~` | Both in **one** press (no dead keys) | ☐ |
| 3 | Mod + **C / S / Z / E** | `č š ž ě` | ☐ |
| 4 | Shift + Mod + **C / S / Z / E** | `Č Š Ž Ě` | ☐ |
| 5 | Mod + **; / ' / -** | `„` / `’` / `–` | ☐ |
| 6 | Shift + Mod + **; / ' / -** | `"` / `”` / `—` | ☐ |
| 7 | **Standard-only:** Mod + **T / D / L** | **Nothing** (`ť ď ľ` deliberately absent) | ☐ |
| 8 | Type a normal English sentence | Plain US QWERTY, nothing weird | ☐ |

**Check #7 is the important one** — it proves the layout is standard-orthography
only (matches the house style). If `ť ď ľ` appear, you're on the wrong (extended)
layout.

---

## Windows — KBDMSSTD

1. `install.ps1` (elevated) per `windows/installers/kbdmsstd/README.md`
2. Settings → Language → **Polish** → add keyboard → **“Medzuslovjansky (standard)”**
3. Switch with **Win+Space**
4. Run the **Core checks** above (Mod = AltGr)
5. If it doesn't appear in the add-keyboard list → sign out/in

---

## macOS — KBDMSSTD.keylayout *(when the Mac arrives)*

1. Copy `mac/KBDMSSTD.keylayout` → `~/Library/Keyboard Layouts/`
2. System Settings → Keyboard → Input Sources → **+** → **“Medzuslovjansky (standard)”**
3. Switch via the input-source menu (or ⌃Space)
4. Run the **Core checks** (Mod = **Option**)
5. This layout has never been runtime-tested on real hardware — **watch check #3–7 closely**

---

## Linux — XKB `isv`

1. Install per `linux/README.md` (system or user-level)
2. `setxkbmap isv` (X11) or pick **“Interslavic (standard)”** in settings
3. Run the **Core checks** (Mod = AltGr / Right Alt)

---

## Android — two separate tools

Android has **two** independent pieces. Test both.

### A. Unexpected Keyboard — the tap + corner-swipe layout

1. Install Unexpected Keyboard (F-Droid), enable it
2. Add a custom layout, paste `android/isv_latin.xml`
3. Verify:

| Do | Expect | ✓ |
|---|---|---|
| Swipe **up-right** on C / S / Z / E | `č š ž ě` | ☐ |
| Swipe **up-left** on the top row | digits | ☐ |
| Tap normally | plain letters | ☐ |

(Also try `isv_cyrillic.xml` and `isv_runic.xml` the same way.)

### B. HeliBoard — the glide/swipe **dictionary** (the crown jewel)

1. Install HeliBoard (F-Droid)
2. Load `dictionary/main_isv.dict` (file manager → open with HeliBoard, or
   Languages & Layouts → language → `+` at dictionaries)
3. Load the gesture-typing library (Settings → Advanced → *Load gesture typing
   library*) — HeliBoard ships without one
4. HeliBoard → Text correction → **“Add words to personal dictionary” = ON**
   (needed for the lexicon loop later)
5. The real test — **glide across whole words**, don't tap:

| Glide across | Should land on | why it matters | ✓ |
|---|---|---|---|
| s‑l‑o‑v‑a‑m‑i | **slovami** | inflected form, not the lemma `slovo` | ☐ |
| d‑o‑b‑r‑a‑m‑i | **dobrami** | Instr. pl. of `dobro` | ☐ |
| b‑u‑d‑e‑m | **budem** | common verb form | ☐ |
| p‑i‑š‑e‑m | **pišem** | uses `š` mid-word | ☐ |

If inflected forms come up (not only lemmas), the dictionary is doing its job —
that's the whole reason it ships forms.

> The `.dict` is already public in this repo (`dictionary/main_isv.dict`) — you
> can test HeliBoard **today**, without waiting for the Helium314 merge. The
> merge only adds it to HeliBoard's built-in list.

---

## What to report

Testing as a stranger is the point — capture friction so it's right for others:

- **README step unclear / missing** → note it (biggest value; fix the doc)
- **A word doesn't come up on glide** → note it (candidate for the lexicon loop later)
- **Layout won't appear** → try sign-out/in first, then note if it persists
- **Wrong letter, or `ť ď ľ` reachable** → that's a real layout bug, flag it

Per device, jot: platform · install OK? · checks passed/failed · doc gaps.
