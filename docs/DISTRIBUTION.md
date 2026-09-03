# Distribution roadmap — "easy, trustworthy, safe on every device"

*Owner-facing. Public user guide = [`INSTALL.md`](INSTALL.md). Status as of 2026-09-03.*

**Doctrine (owner, 2026-09-03):** we never send files to people — every install
goes through a link a stranger would trust: an app store, the publisher's own
site, or our GitHub. Goal: the easiest possible install on ANY device.

## Where we stand

| Platform | Trusted route today | Gap |
|---|---|---|
| iPhone / iPad | ✅ Keyman (App Store) + catalogue `isv_latin` | none for taps; swipe = own app, far future |
| Android (taps) | ✅ Keyman (Google Play) + catalogue | none |
| Android (swipe app) | ⚠ GitHub Releases APK only | **Play Store**, F-Droid (MR pending), IzzyOnDroid |
| Windows | ✅ Keyman for Windows (keyman.com, signed) | native layout = unsigned `install.ps1` |
| macOS | ✅ Keyman for macOS | native `.keylayout` = manual copy (acceptable) |
| Linux | ✅ Keyman for Linux | XKB = manual copy (acceptable) |

Keyman catalogue entries (both live): <https://keyman.com/keyboards/isv_latin>
— keyboard PR `keymanapp/keyboards#4092` merged 2026-08-25, model PR
`keymanapp/lexical-models#351` merged 2026-08-10.

## Steps, in order — links to act on

### 1. Google Play — own swipe app  🔴 (unlocks the store AND the coming sideload verification)

**Status 2026-09-03: developer account CREATED** (personal, owner `radoradek2@gmail.com`, public developer e-mail = same, private contact = main account, fee paid). Remaining: ID verification, phone verification, invite the main account as admin, then create the app and open the closed test.

| | |
|---|---|
| Sign up | <https://play.google.com/console/signup> |
| Cost | **25 USD, one-time** (→ `ZAKUPY.md`) |
| Needs from owner | Google account, **ID verification** (document + selfie), payment card, a public developer e-mail (a dedicated address, not the private one) |
| Gate for new personal accounts | **Closed test: ≥ 12 testers, 14 consecutive days**, then apply for production access — <https://support.google.com/googleplay/android-developer/answer/14151465> |
| Testers | Matija + his September group = the tester pool. The closed-test link is a *normal Play link* — exactly what we want to hand people |
| Why it is not optional | Google's **Android developer verification** will block unverified sideloaded APKs on certified devices (rollout from 2026): <https://developer.android.com/developer-verification>. The Play account IS the verification |
| Package | `com.radoslove.interslavic`, signed with our release key (cert SHA-256 `5fa81c…b98a`). ⚠ Play may push *Play App Signing* — keep our upload key = current key so F-Droid `AllowedAPKSigningKeys` stays valid |
| Listing needs | 512×512 icon, 1024×500 feature graphic, ≥ 2 phone screenshots, short + full description (MS + EN), privacy policy URL (a one-paragraph page: "no data collected" — can live in the repo as `PRIVACY.md`), data-safety form (all "no") |

### 2. IzzyOnDroid — bridge while Play/F-Droid pend  🟡 (0 cost, days)

| | |
|---|---|
| What | Curated F-Droid-compatible repo that indexes APKs straight from GitHub Releases. Users add the repo in the F-Droid client once. |
| Info + criteria | <https://apt.izzysoft.de/fdroid/index/info> |
| Submit | Issue "Add app" on <https://gitlab.com/IzzyOnDroid/repo> (issue tracker of that project; template asks for repo URL + APK asset name) |
| Requirements | APK ≤ 30 MB (ours 2.4 MB ✅), open source ✅, release asset with stable name ✅ (`app-release.apk`), no trackers ✅ |

### 3. F-Droid — already submitted  🟡 (waiting)

| | |
|---|---|
| MR | <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568> — open since 2026-08-12, no maintainer action yet |
| Action | Ping politely in the MR after ~4 weeks (→ ~2026-09-10) if still untouched; make sure `v3.1` tag + commit in the recipe still match `main` |

### 4. Windows — native layout as a real, signed installer  🟢 (later)

| | |
|---|---|
| Microsoft Store | ❌ **not possible** — a keyboard layout is a system DLL registered with admin rights; MSIX-sandboxed Store apps cannot do that. Don't chase it |
| Step 4a: MSI | Build the MSKLC setup (`.msi` + `setup.exe`) for `KBDMSSTD` instead of `install.ps1` — same tool that produced `windows/installers/medzuslo/*.msi`. MSKLC 1.4: <https://www.microsoft.com/download/details.aspx?id=102134> (on `hp`, `C:\Programs\Microsoft Keyboard Layout Creator 1.4\`) |
| Step 4b: code signing | The SmartScreen "unknown publisher" warning is the whole "looks suspicious" problem. Options: **SignPath Foundation** (free for OSS after project review) <https://about.signpath.io/product/open-source>; commercial OV cert ~200–400 €/yr (not worth it at this scale) |
| Step 4c: winget | Manifest PR to <https://github.com/microsoft/winget-pkgs> (installer may stay on GitHub Releases; Microsoft reviews the manifest). Docs: <https://learn.microsoft.com/windows/package-manager/package/>. Then `winget install Radoslove.InterslavicKeyboard` |
| Until then | `INSTALL.md` recommends Keyman for Windows; native layout labelled "advanced", warning explained |

### 5. Nice-to-have

- ✅ `PRIVACY.md` in repo (2026-09-03). ✅ Brand kit + Play graphics: `brand/BRAND.md`, `brand/out/` (2026-09-03). ✅ Launcher icon wired into the app (needs a v3.2 build).
- README: one line at the top → "Install guide: `docs/INSTALL.md`".
- MS translation of `INSTALL.md` (house style, DB-verified by `interslavic-tutor`).
- Publish the APK SHA-256 in each GitHub release note (copy from CI or `shasum -a 256`).

## Done criteria

- A stranger with any of the five platforms can install the keyboard following `INSTALL.md` alone, without receiving a file from us.
- Android swipe app installable from Google Play (at least closed test) → then production.
- Windows native layout: signed MSI + winget, or explicitly deprecated in favour of Keyman.
