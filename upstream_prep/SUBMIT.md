# Keyman catalogue submission — checklist

Two separate PRs to two separate upstream repos. Both trees under this folder
are staged by `build_upstream.py` and mirror the upstream layout exactly, so
each one is a straight copy into a fork.

Neither catalogue contained any Interslavic entry when this was prepared
(checked 2026-08-08). This is the same first-in-the-world position as the
HeliBoard dictionary PR.

**Why this matters more than the GitHub release:** today a stranger has to find
this repo, download a `.kmp`, already know what Keyman is, use *Install From
File*, and then survive the Full Access trap. From the catalogue it is one tap
inside the Keyman app they already have.

## Identity

Submit as **Radoslove** — same identity as the public repo and the Codeberg
dictionary PR. The PRs must be opened by the owner; the work below is staged,
not sent.

---

## 1 · Keyboard → `keymanapp/keyboards`

Copy `keymanapp-keyboards/release/i/isv_latin/` into a fork at the same path.

- [ ] Fork `keymanapp/keyboards`
- [ ] Copy the folder to `release/i/isv_latin/`
- [ ] `kmc build release/i/isv_latin/isv_latin.kpj` — must be warning-clean
- [ ] PR

Placement follows the repo's own rule — grouped by first letter, since `isv` is
a language code and not a vendor prefix like `sil` or `nrc`. Compare
`release/i/inuktitut_latin`.

### State at staging

| Check | Result |
|---|---|
| `kmc build` | clean — 0 errors, 0 warnings, 0 hints |
| Compiler verdict | `platformSupport.ios = "full"` |
| Language tag | `isv-Latn` resolves natively; no workaround |
| Tested on device | iPad, iPadOS 26 — install, typing, longpress, capitals |
| Icon | present (`source/isv_latin.ico`) |
| Orthography | standard only, matches the Windows `.klc` and macOS `.keylayout` |

---

## 2 · Lexical model → `keymanapp/lexical-models`

Copy `keymanapp-lexical-models/release/radoslove/radoslove.isv.wordlist/` into
a fork at the same path.

- [ ] Fork `keymanapp/lexical-models`
- [ ] Copy the folder to `release/radoslove/radoslove.isv.wordlist/`
- [ ] `kmc build .../radoslove.isv.wordlist.kpj` — must be warning-clean
- [ ] PR

The id already follows their `<author>.<lang>.<name>` convention, so no rename
is needed. Compare `release/ait/ait.mnw.mon`.

Submit the **trimmed** model (39,777 forms, ~5 MB), not the full 248k. Keyman
ships to iOS, where a 33 MB trie does not fit in a keyboard extension.

---

## Expect review rounds

The upstream README says submissions go through "two to three rounds of
confirmation and testing". Budget for that rather than treating the PR as the
finish line.

## Not submitted

The Cyrillic and runic layouts stay in this repo only. They have never been
audited to the standard of `docs/ms-latin-table.md` — that is open question #1
in that file.
