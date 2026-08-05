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

### Extended block — for reading and quoting, not for writing

Retained in full so quoting dictionary lemmas and pre-2026-08-05 material stays
possible. Per HOUSE_STYLE §1 these are **not** written in new text.

| Key | AltGr | | Key | AltGr | | Key | AltGr |
|---|---|---|---|---|---|---|---|
| `A` | å Å | | `I` | ė Ė | | `T` | ť Ť |
| `Q` | ś Ś | | `O` | ȯ Ȯ | | `V` | ć Ć |
| `W` | ę Ę | | `D` | ď Ď | | `X` | ź Ź |
| `R` | ŕ Ŕ | | `F` | đ Đ | | `N` | ń Ń |
| `U` | ų Ų | | `L` | ľ Ľ | | | |

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
| `L` + AltGr | `ĺ` (U+013A, l-acute) | **`ľ` (U+013E, l-caron)** | Interslavic uses the caron; the acute is a different letter |
| `T` + AltGr | — (absent) | **`ť`** | The extended block must be complete to quote it |
| `D` + AltGr | — (absent) | **`ď`** | Ditto |
| `OEM_3` base | `` ` `` and `~` as **dead keys** | plain `` ` `` and `~` | A base-layer dead key is a defect, not a feature |
| Number row AltGr | 8 dead diacritic keys | nothing | Composing diacritics is not how this alphabet is typed |
| Locale | `sl-SI` (00000424) | **`pl-PL` (00000415)** | Appears under Polish, which is already in the language list — no phantom Slovenian |

## Open questions

Not blockers; flagged for `interslavic-tutor`.

1. **Cyrillic layout not reviewed.** `KBDMSKIR.klc` was not audited for the same class
   of defects. If the Cyrillic layout ever gets used, run this same comparison.
2. **`ĺ` vs `ľ` upstream.** Worth reporting to `medzuslovjansky/keyboards` — if the
   upstream really means `ĺ`, our reading of the alphabet is what needs revisiting.
3. **Android parity.** The Unexpected Keyboard XML built in the other session was not
   diffed against this table. They should agree key-for-key; `/keyboard-layout` Step 0
   applies whenever they are reconciled.

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
