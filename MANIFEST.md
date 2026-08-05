# MANIFEST — Interslavic Keyboards, release bundle v1

*Assembled 2026-08-05 (Jira KAN-98, part 1). Distribution model: download-only,
no registration / account / data. This file inventories what ships and what is
still pending.*

Source working repo: `C:\Projects\keyboards\` (outside the vault).
This distribution repo: `C:\Projects\vault_002\Projects\INTERSLOVE\keyboards\`.

---

## Bundle contents (file → role → source)

### Root
| File | Role | Source |
|---|---|---|
| `README.md` | User-facing install guide (MS + EN), download-only model | pre-existing in target (not modified) |
| `LICENSE` | MIT license, full text | pre-existing in target |
| `.gitattributes` | Enforces binary/CRLF handling (`.klc` etc.) | pre-existing in target |
| `MANIFEST.md` | This file | new |
| `build_klc.py` | Generator for KBDMSSTD `.klc` (Latin, house-style) | pre-existing in target |
| `build_autocorrect.py` | Generator for `ms_autocorrect.ahk` | pre-existing in target |
| `ms_autocorrect.ahk` | AHK v2 word-level autocorrect expander (Windows) | pre-existing in target |

### `android/` — Unexpected Keyboard XML layouts (3)
| File | Role | Source |
|---|---|---|
| `isv_latin.xml` | Latin (latinica) layout — **standard orthography only** | `C:\Projects\keyboards\android\isv_latin.xml` |
| `isv_cyrillic.xml` | Cyrillic (cyrilica) layout | `C:\Projects\keyboards\android\isv_cyrillic.xml` |
| `isv_runic.xml` | Runic (runy) layout | `C:\Projects\keyboards\android\isv_runic.xml` |

All three are **well-formed XML** (parsed OK). See orthography verdict below.

### `dictionary/` — swipe/glide dictionary (crown jewel)
| File | Role | Source |
|---|---|---|
| `main_isv.dict` | HeliBoard compiled swipe dictionary, **253 273 words with inflected forms** | `C:\Projects\keyboards\dictionary\main_isv.dict` |

**Confirmed a built binary, not a stub:** 1 192 248 bytes (~1.14 MB), header
`0x9BC13AFE` = AOSP compiled-dictionary magic (FORMAT_VERSION_2).

### `windows/` — layouts + installers
| Path | Role | Source |
|---|---|---|
| `installers/kbdmskir/` | **Cyrillic** ready installer (`kbdmskir.exe` 517 KB self-installer, `KBDMSKIR.klc`, `InfoCyr.html`, `InfoLat.html`). Official, © 2012 Roberto Lombino jr. | `C:\Projects\keyboards\windows\kbdmskir\` |
| `installers/kbdmsstd/` | **PENDING slot** for Windows Latin installer — see below | (owner-built, not present) |
| `installers/medzuslo/` | Early own Latin attempt (MSKLC output: dll/msi/setup) | pre-existing in target |
| `installers/runy_1…5/`, `runes_1/` | Runic installers (`runy_5` = newest) | pre-existing in target |
| `Medzusloviansky.klc`, `runes_1.klc`, `runy_2…5.klc`, `runy_*.txt`, `runes_1.jpg/png`, `src/KBDMSSTD.klc` | `.klc` sources + assets | pre-existing in target |

### `linux/`
| File | Role | Source |
|---|---|---|
| `README.md` | Pointer to upstream `.deb`/`.rpm` packages (v0.0.1) — not duplicated | `C:\Projects\keyboards\linux\README.md` |

### `docs/`
| File | Role | Source |
|---|---|---|
| `ms-latin-table.md` | Canonical Latin char table + KBDMSSTD defects/notes | pre-existing in target |

---

## What I added this session
- `android/` : 3 XML layouts (folder was empty / `.gitkeep`-only) — `.gitkeep` removed.
- `dictionary/` : created folder + `main_isv.dict`.
- `windows/installers/kbdmskir/` : 4 files (Cyrillic installer).
- `windows/installers/kbdmsstd/` : created empty slot + `.PENDING`.
- `linux/README.md` : content copied from source (folder was `.gitkeep`-only) — `.gitkeep` removed.
- `MANIFEST.md` : this file.

Everything left in the working tree — **not committed, not pushed, no new repo,
nothing sent upstream.**

---

## Orthography verification — Android Latin layout (HOUSE_STYLE gate)

NOW.md open question ("android XML not diffed against `docs/ms-latin-table.md`,
may carry extended glyphs like the Windows Latin problem"): **RESOLVED — clean.**

- The Latin layout's key mappings contain only `č ě š ž`
  (U+010D, U+011B, U+0161, U+017E) — all **standard** MS alphabet.
  `ě` is explicitly allowed by HOUSE_STYLE; it is NOT an extended letter.
- **Zero** extended glyphs (`ų ė ę ȯ å ŕ ť ď ć đ ľ ń ś ź`) in any key mapping.
- The layout is by design the STANDARD version (its own comment: "tylko cztery
  diakrytyki alfabetu standardowego (č š ž ě)… bez alfabetu etymologicznego").
- One false positive during the scan: `ę` (U+0119) appears **only** inside a
  Polish-language XML comment ("celowo pominięte" = "deliberately omitted") —
  Polish prose, not a mapped key. This is exactly the HOUSE_STYLE soft-glyph
  trap (`ę ć ń ś ź` = indistinguishable from Polish); scanning attribute values
  only, it is clean.

→ **No owner decision needed on Android Latin orthography.** (Cyrillic and Runic
layouts are different scripts, not subject to the Latin house-style rule.)

---

## Missing / waiting on owner

1. **KBDMSSTD Windows Latin installer — the only real gap.**
   `windows/installers/kbdmsstd/` holds only `.PENDING`. Owner builds the DLL +
   setup package in **MSKLC** (GUI, no headless build) from
   `windows/src/KBDMSSTD.klc`, drops the output into that folder, deletes the
   `.PENDING`. The old Gola Latin installer (`kbdmslat`) was **deliberately not
   shipped** — v1 Latin is KBDMSSTD.

2. **`docs/runic-table.md` — recommendation, not done (out of task scope).**
   The bundle ships runic layouts (Windows `runy_*` + `android/isv_runic.xml`),
   and `android/isv_runic.xml` references `docs/runic-table.md` in its comment,
   but that file exists only in the source repo (`C:\Projects\keyboards\docs\
   runic-table.md`), not in the target `docs/`. Consider copying it for the
   runic layouts to be self-documenting. Not copied here — flagging per task
   ("report, don't fix").

3. **Android orthography flag** — none. Verified clean (see above).

---

## Bundle status

**Complete except for the KBDMSSTD slot** (owner-built, by design). All three
platforms are represented: Windows (Cyrillic + Runic ready; Latin pending),
Android (3 layouts + swipe dictionary), Linux (upstream pointer). README = 0
"TODO", LICENSE present.
