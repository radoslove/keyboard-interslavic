# Brand kit — Radoslove · Medžuslovjansky keyboard

*Status 2026-09-03: v1, rendered from `brand/src/*.svg` by `brand/render.sh` into `brand/out/`.
**Owner's pick: variant B (hearts)** — it is the app icon, the launcher icon and the mark on the feature graphic. A and C stay in `src/` as alternates.*

## Two marks, one family

| Mark | Used for | File |
|---|---|---|
| **App icon** (variant **B hearts** = chosen; A / C alternates) | Play Store icon, Android launcher, F-Droid / IzzyOnDroid, GitHub social preview | `out/icon_{a,b,c}_*_512.png` |
| **Radoslove publisher mark** (white R + red heart on navy) | GitHub/Codeberg avatar of `radoslove`, Play developer page, any "by Radoslove" signature | `out/radoslove_avatar_512.png` |

Same navy, same typeface, same flat style — the publisher mark and the app icon read as siblings without being the same thing.

## Palette (Interslavic flag colours, flattened)

| Role | Hex | Where |
|---|---|---|
| Navy (primary) | `#12328C` | feature graphic, avatar, A/C backgrounds |
| Navy dark | `#0B1F5C` | **app icon background (B)** = `ic_launcher_background`, gradient end |
| Blue accent | `#3A66D9` | the blue heart (variant B) — lighter so it survives on navy |
| Yellow | `#F5C400` | háček in variant A, subtitle on the feature graphic |
| Red | `#D5262C` | háček on the keycap (C), the Radoslove heart |
| White | `#FFFFFF` | letters, keycap |

Never use the extended-alphabet letters anywhere in brand text (house style §1) — the brand shows **č** because it is the first of the four standard letters.

## Icon variants

| | Idea | Reads at 48 px? | Note |
|---|---|---|---|
| **A · caron** | white `c`, yellow háček, navy field | ✅ very well | most "brand"; the háček is the whole story |
| **B · hearts** | four hearts in flag colours, tips at the centre | ✅ well | **CHOSEN** — continuity with the old F-Droid icon; says "Slavic"; used on the feature graphic |
| **C · keycap** | white keycap with navy `č`, red háček | ✅ well | says "keyboard" at a glance; alternate |

Default in `render.sh` is **B** (`ICON=b`); `ICON=a` / `ICON=c` switch the Android launcher set to an alternate. All three have adaptive foregrounds (`fg_*.svg`).

## Typeface

Helvetica Neue Bold (macOS system font, rendered by Quick Look). Fallback order in the SVGs: Helvetica Neue → Helvetica → Arial. For a fully reproducible build on non-Mac machines convert the glyphs to paths (Inkscape *Object → Path*) — not done yet, on purpose: v1 first.

## Google Play assets — what goes where

| Play field | Requirement | File |
|---|---|---|
| App icon | 512×512 PNG, ≤1 MB, no alpha needed | `out/icon_<pick>_512.png` |
| Feature graphic | 1024×500 PNG/JPG | `out/feature_graphic_1024x500.png` |
| Phone screenshots | 2–8, 16:9 or 9:16, 320–3840 px | **owner: 3–4 screenshots on the phone** (keyboard open in a chat app: long-press č, swipe trail, prediction bar, settings) |
| Short description | ≤80 chars | `fastlane/metadata/android/en-US/short_description.txt` |
| Full description | ≤4000 chars | `fastlane/metadata/android/en-US/full_description.txt` |
| Privacy policy URL | public URL | `https://github.com/radoslove/keyboard-interslavic/blob/main/PRIVACY.md` |
| Developer avatar | — | `out/radoslove_avatar_512.png` (also set as GitHub org avatar) |

## Android launcher icon (in the app)

`render.sh` also emits `out/mipmap-*/ic_launcher.png`, `ic_launcher_round.png` (legacy, API 24–25) and `ic_launcher_foreground.png` (adaptive, API 26+). They are copied into `android-app/app/src/main/res/mipmap-*/`, with `mipmap-anydpi-v26/ic_launcher.xml` + `values/colors.xml` (`ic_launcher_background` = navy) and `android:icon` / `android:roundIcon` in the manifest. `fastlane/metadata/android/en-US/images/icon.png` (F-Droid) = the same B render. Adaptive foreground keeps all detail inside the 66/108 safe circle.

## Re-render

```
bash brand/render.sh            # variant B launcher set + all store assets
ICON=a bash brand/render.sh     # alternate launcher set (a or c)
```

Requires macOS (`qlmanage`, `sips`). Non-square outputs: draw on a square canvas and crop (see `feature_graphic.svg` header) — Quick Look scales to the longest side.
