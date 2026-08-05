# KBDMSSTD — Medžuslovjansky (standard) — Windows layout

Standard-orthography Interslavic Latin keyboard. AltGr carries **č š ž ě** plus the
punctuation used in MS texts (`„ " ' " – —`). **No dead keys.** Appears under **Polish**
in the Windows language list (locale `pl-PL`, no phantom Slovenian).

Standard orthography only — the extended letters (`ų ė ę ȯ å ŕ ť ď ć đ ľ ń ś ź`) are
deliberately **not** on this layout, per `HOUSE_STYLE.md`. Matches the Android layout.

## Install

Right-click PowerShell → **Run as administrator**, then in this folder:

```powershell
.\install.ps1
```

Preview first without changing anything (no admin needed):

```powershell
.\install.ps1 -DryRun
```

After install: **Settings → Time & language → Language → Polish → Language options →
Add a keyboard → “Medzuslovjansky (standard)”**. Switch with **Win+Space**.

## Uninstall

```powershell
.\uninstall.ps1
```

## Contents

| Path | What |
|---|---|
| `amd64/KBDMSSTD.dll` | 64-bit layout DLL (System32 on x64 Windows) |
| `wow64/KBDMSSTD.dll` | 32-bit layout DLL (SysWOW64 on x64 Windows) |
| `i386/KBDMSSTD.dll` | 32-bit layout DLL (System32 on x86 Windows) |
| `ia64/KBDMSSTD.dll` | Itanium DLL (legacy, unused on modern PCs) |
| `KBDMSSTD.klc` | source (regenerate DLLs with MSKLC `kbdutool`, or MSKLC GUI) |

## Rebuild

Layout is generated from `../../../build_klc.py` (edit the map there, not the `.klc` by
hand). Compile the `.klc` → DLLs with the `kbdutool` bundled in MSKLC:

```
kbdutool -n -u -m KBDMSSTD.klc   # -m amd64 · -x x86 · -o wow64 · -i ia64
```

MIT — see the repo `LICENSE`. Scan-code/VK skeleton derived from the medzuslovjansky
upstream layout (© Adam Gola, Roberto Lombino jr.); character mapping and build own.
