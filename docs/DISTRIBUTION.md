# Distribution roadmap — "easy, trustworthy, safe on every device"

*Owner-facing. Public user guide = [`INSTALL.md`](INSTALL.md). Status as of 2026-09-03.*

**Doctrine (owner, 2026-09-03):** we never send files to people — every install
goes through a link a stranger would trust: an app store, the publisher's own
site, or our GitHub. Goal: the easiest possible install on ANY device.

## 🔴 GitHub Releases NIE jest kanałem dla ludzi (owner 2026-09-23)

**Zasada:** nikogo nie odsyłamy po plik na GitHuba. Owner, wprost i po raz drugi:
„z githuba nie będziemy nikomu kazać ściągać". Pierwsze sformułowanie tej reguły jest
z 2026-09-03: *„NIE przesyłać plików mailem — tylko linki do normalnego pobrania"*,
a cel projektu brzmi **„instalacja jak najłatwiej na KAŻDYM urządzeniu"**.

**Czym więc jest wydanie na GitHubie:** infrastrukturą. Przepis F-Droida ciągnie z niego
binarkę (`Binaries: …/v%v/app-release.apk`) i porównuje z własnym buildem. Wydanie musi
istnieć i mieć właściwą nazwę pliku — **ale to nie znaczy, że wysyłamy tam użytkownika.**

**Kanały dla ludzi**, w kolejności gotowości:
1. **F-Droid** — docelowy, `linsui` 22.09: „mostly ready", ale „may take a long time".
2. **Google Play** — konto założone 2026-09-03, zamknięty test wymaga ≥12 testerów × 14 dni.
3. **IzzyOnDroid** — pomost „0 zł, dni", wciąż niezrobiony.
4. **Keyman** (iOS + reszta platform) — ⚠ wersja 1.6 w katalogu ma błąd 11 klawiszy,
   czeka na 1.7.

⚠ Owner o obecnych drogach: „te keymanowo androidowe są lipne jakieś". Czyli dopóki
któryś z powyższych kanałów nie jest realnie gotowy, **nie promujemy instalacji w ogóle** —
owner sam zapisuje się na kurs i tam będzie promował, gdy będzie czym.


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
| Action | ⚠ Bump the recipe to the version we actually publish BEFORE merge — see below |

**Maintainer verdict, `linsui`, 2026-09-22** (read in a browser — the notes API
returns 401 even on this public MR, and the GitLab notification mail goes to the
`radoslove` account, not to the mailbox we can search):

> This MR is mostly ready. We'll test it later. If everything works well we'll
> merge it. Meantime if you release a new version please update this MR.
> Currently we have lots of MRs waiting for test so it may take a long time.

and, separately:

> Unprotect your branch.

✅ He also ticked **Enable Reproducible Builds** on the checklist himself.

Three things follow:

1. 🔴 **Unprotect `master` on the `radoslove/fdroiddata` fork** — Settings →
   Repository → Protected branches. While it is protected, maintainers cannot
   push fixes to the MR branch, which is why he asked. Owner's account, owner's
   click.
2. **Publishing a new version means updating this MR** — he asked for it
   explicitly, which settles the question below: bump the recipe rather than
   letting it merge at 3.1.
3. ⏳ **F-Droid is NOT the fast channel.** "It may take a long time" is their own
   estimate, with a queue of MRs waiting for test. Anyone who needs the keyboard
   soon — an external tester, say — gets the APK from GitHub Releases. F-Droid is
   the durable channel, not the quick one.

Earlier rounds, for the record: `linsui` asked on 2026-08-13 for a full commit
hash instead of a tag/branch, `Binaries` + `AllowedAPKSigningKeys`, fastlane
metadata, and *"please keep your signing key safe with backup"* — all done, the
last one on 2026-09-17. On 2026-08-20: "Change the category".

**The recipe, read back from the MR diff on 2026-09-22** (the notes API is 401 even
though the MR is public, so the comments have to be read in a browser):

```yaml
Binaries: .../releases/download/v%v/app-release.apk
Builds:
  - versionName: '3.1'   versionCode: 31   commit: c7833ff74843dfcad21c3550ce6f74a6a6d542ab
    subdir: android-app/app   gradle: [yes]   scandelete: [windows]
AllowedAPKSigningKeys: 5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a
AutoUpdateMode: Version    UpdateCheckMode: Tags
CurrentVersion: '3.1'      CurrentVersionCode: 31
```

Three consequences, all binding on how we publish:

1. ⚠ **`Binaries` builds its URL from `v%v`.** The git tag must be exactly `vX.Y`
   and the release asset must be named exactly **`app-release.apk`**. Any other
   asset name (`isv-keyboard-3.3.apk`, say) is a 404 to F-Droid. v3.1 already
   follows this, so the pattern is proven — do not "improve" the filename.
2. `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` means that **once merged,
   F-Droid picks up later releases from the tags by itself.** No new MR per
   version.
3. The recipe still pins **3.1** (August). Merging as-is would make the first
   F-Droid build the one WITHOUT any of the editing fixes. Bump `versionName`,
   `versionCode`, `commit`, `CurrentVersion` and `CurrentVersionCode` to whatever
   we publish, so the first build users get is the good one.

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
