# Linux (XKB)

This repo ships its **own standard-orthography** Interslavic XKB layout — the
`isv` symbols file in this folder. Base + Shift are plain US QWERTY (no dead
keys); **AltGr (Right Alt)** gives `č š ž ě` on C/S/Z/E, plus the punctuation
`„ " ’ ” – —`. Standard orthography only — no extended/etymological letters —
matching the Windows, Android and macOS layouts in this repo.

## Install — system-wide (needs root)

1. Copy the layout into XKB:
   ```sh
   sudo cp isv /usr/share/X11/xkb/symbols/isv
   ```
2. Register it so it appears in Settings — add this inside `<layoutList>` in
   `/usr/share/X11/xkb/rules/evdev.xml`:
   ```xml
   <layout>
     <configItem>
       <name>isv</name>
       <shortDescription>isv</shortDescription>
       <description>Interslavic (standard)</description>
       <languageList><iso639Id>isv</iso639Id></languageList>
     </configItem>
   </layout>
   ```
3. Select **“Interslavic (standard)”** in your desktop keyboard settings, or on
   X11 apply it directly:
   ```sh
   setxkbmap isv
   ```

## Install — user-level (no root, X11)

```sh
mkdir -p ~/.xkb/symbols
cp isv ~/.xkb/symbols/isv
setxkbmap -I"$HOME/.xkb" isv -print | xkbcomp -I"$HOME/.xkb" - "$DISPLAY"
```

## Quick test

Apply with `setxkbmap isv`, then **AltGr+C** → `č`, **AltGr+S** → `š`,
**AltGr+Z** → `ž`, **AltGr+E** → `ě`. (AltGr = **Right Alt**.)

## The upstream package (alternative)

Upstream `medzuslovjansky/keyboards` also ships a Linux `isv` layout
(release v0.0.1, `.deb` + `.rpm`) — but it uses the **extended** Latin
orthography. The layout here is the **standard-only** counterpart, consistent
with the other platforms in this repo. Both use the id `isv`, so use one or the
other (a user-level install overrides a system one).

## Runes

No runic Linux layout yet — it can be generated from `docs/runic-table.md` if a
need comes up.
