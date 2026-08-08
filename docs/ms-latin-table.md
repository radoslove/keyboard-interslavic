# Interslavic (latinica) — canonical keyboard character table

*Resolved 2026-08-05 on `galax`. Governs `windows/src/KBDMSSTD.klc`.
Orthography rules live in `C:\Projects\vault_002\Projects\INTERSLOVE\HOUSE_STYLE.md` —
this file is mechanics only and does not rule on which forms are correct.*

## Sources compared

| Source | What it is | Status |
|---|---|---|
| `Projects\INTERSLOVE\kbdmslat\KBDMSLAT.klc` | (c) 2013 Adam Gola / Roberto Lombino jr. — the upstream `medzuslovjansky/keyboards` layout | **installed and working on `galax`**, but defective (below) |
| `Projects\INTERSLOVE\kbdmskir\KBDMSKIR.klc` | same authors, Cyrillic | untouched, out of scope here |
| `HOUSE_STYLE.md` §1 | which letters we actually write | authority for the priority order |

**Rule applied — the last working artefact wins.** The Gola layout is a decision made
in code and installed on a real machine, so its scan-code / virtual-key skeleton is
reused verbatim. Only the character columns were re-decided.

## Defects found in the upstream layout

| # | Defect | Why it matters |
|---|---|---|
| 1 | **8 dead keys**, two of them on the BASE layer (`OEM_3`: backtick and tilde) | Plain `` ` `` and `~` need a second keystroke — breaks shells, code, markdown |
| 2 | **80 characters on AltGr**, only ~26 are Interslavic letters | The rest is typographic filler: `™ ® © ‰ ′ ″ § ¶ ° × ÷ ± − ∙` |
| 3 | **`ĺ` (l-acute) where Interslavic uses `ľ` (l-caron)** | Wrong letter — produces an unattested form |
| 4 | **No `ť`, no `ď`** | The extended block is incomplete |
| 5 | `LOCALENAME sl-SI` | Hijacks Slovenian; that is why "Slovenian" sits in the Windows switcher despite never being used |

## Our table (`KBDMSSTD.klc`)

Base and Shift layers stay plain ASCII, US-style. **No dead keys anywhere.**
All Interslavic letters live on AltGr, placed mnemonically (letter under its base letter).

### Standard alphabet — the priority

These four are what standard-orthography Interslavic actually needs and a Polish
keyboard cannot produce:

| Key | AltGr | AltGr+Shift |
|---|---|---|
| `E` | ě | Ě |
| `C` | č | Č |
| `S` | š | Š |
| `Z` | ž | Ž |

`dž` is deliberately **not** a dedicated key — it is `d` + AltGr+`Z`, two ordinary
keystrokes, and a ligature key would add a failure mode for no gain.

### Extended block — REMOVED 2026-08-05

⚠ **This block is no longer on any shipped layout.** It was drafted here for quoting
dictionary lemmas, then cut on 2026-08-05 when the owner ruled that HOUSE_STYLE §1
governs the keyboards too: if a letter is not written in new text, it does not get a
key. Verified against the artefacts — `windows/src/KBDMSSTD.klc` contains exactly
`Č č Ě ě Š š Ž ž – — ’ “ ” „` and nothing else.

The cut letters were `å ś ę ŕ ų ė ȯ ď đ ľ ť ć ź ń`. They are recorded here as history
only. To quote them, use a dictionary or the Cyrillic layout — not this keyboard.

### Punctuation

Kept because our MS texts genuinely use these; everything else Gola had on AltGr
was dropped.

| Key | AltGr | AltGr+Shift |
|---|---|---|
| `;` | „ | “ |
| `'` | ’ | ” |
| `-` | – (en dash) | — (em dash) |

## Resolved conflicts

| Cell | Gola | Ours | Why |
|---|---|---|---|
| `L` + AltGr | `ĺ` (U+013A, l-acute) | *(dropped 2026-08-05)* | Interslavic uses the caron, not the acute — but the whole extended block was then cut |
| `OEM_3` base | `` ` `` and `~` as **dead keys** | plain `` ` `` and `~` | A base-layer dead key is a defect, not a feature |
| Number row AltGr | 8 dead diacritic keys | nothing | Composing diacritics is not how this alphabet is typed |
| Locale | `sl-SI` (00000424) | **`pl-PL` (00000415)** | Appears under Polish, which is already in the language list — no phantom Slovenian |

## Open questions

Not blockers; flagged for `interslavic-tutor`.

1. **Cyrillic layout not reviewed.** `KBDMSKIR.klc` was not audited for the same class
   of defects. If the Cyrillic layout ever gets used, run this same comparison.
2. **`ĺ` vs `ľ` upstream.** Worth reporting to `medzuslovjansky/keyboards` — if the
   upstream really means `ĺ`, our reading of the alphabet is what needs revisiting.
3. ~~**Android parity.**~~ **RESOLVED 2026-08-08.** `android/isv_latin.xml` was diffed
   against this table and agrees: `e→ě  s→š  z→ž  c→č`, same mnemonic, standard-only.
   It additionally carries the digraphs `dž lj nj` on a swipe, which is a convenience,
   not a conflict. The only gap is punctuation — Android has no `„ ” – —`.

## Platform coverage

Every shipped layout carries the same four letters. Only the *reach* differs, because
each platform has a different spare modifier — and a phone has none at all.

| Platform | Artefact | Reach |
|---|---|---|
| Windows | `windows/src/KBDMSSTD.klc` | AltGr + letter |
| macOS | `mac/KBDMSSTD.keylayout` | Option + letter |
| Android | `android/isv_latin.xml` | swipe up-right |
| iOS / iPadOS | `keyman/isv_latin.kmp` | **longpress** |
| Linux | upstream | — |

⚠ The iOS package is the odd one out and it is worth knowing why: a touch layout may
only use the modifier layers `shift/ctrl/alt/ctrlshift/altshift/ctrlalt/ctrlaltshift`.
`rightalt` is **not** among them, so the longpress keys cannot re-use the AltGr rules.
They are separate `T_*` keys with their own rules — and both sets are emitted from the
one table in `build_keyman.py`, which is what keeps them from drifting apart.

## Build and install

```powershell
# on `galax` — open in MSKLC, then Project > Build DLL and Setup Package
& "C:\Program Files (x86)\Microsoft Keyboard Layout Creator 1.4\MSKLC.exe" `
    "C:\Projects\vault_002\Projects\INTERSLOVE\keyboards\windows\src\KBDMSSTD.klc"
```

Verify afterwards — never assume the installer worked:

```powershell
Get-ChildItem "HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts" |
  ForEach-Object { (Get-ItemProperty $_.PSPath)."Layout Text" } |
  Where-Object { $_ -match 'Medz|Medž' }
```

⚠ **Removing a layout from `Set-WinUserLanguageList` does not uninstall it.** The DLL
stays registered and the layout returns with one click. Uninstalling the Gola package
is done from Settings → Apps → Installed apps. Back the language list up first:

```powershell
Get-WinUserLanguageList | Export-Clixml "$env:USERPROFILE\langlist_backup.xml"
```
