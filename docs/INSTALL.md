# Install the Interslavic keyboard — every device, official sources only

*Medžuslovjansky tipkovnica — instalacija na vsakom urědženju, samo iz oficialnyh
istočnikov. (MS version of this page: pending review by the language authority.)*

This page is the **single trusted entry point**. Every link below goes to an
official app store, to the publisher's own site, or to this project's GitHub.
Nothing here asks you for an account, an e-mail address, or a phone number.

> **The one rule:** never install this keyboard from a file someone sent you in
> a chat or an e-mail, and never from a download site. If a link is not on this
> page, it is not ours.

---

## Pick your device

| Device | Easiest route (no files) | Extra option |
|---|---|---|
| **iPhone / iPad** | [Keyman](#iphone--ipad) from the App Store | — |
| **Android** | [Keyman](#android--route-a-keyman-taps) from Google Play | [Swipe keyboard app](#android--route-b-swipe-typing-app) (our own) |
| **Windows** | [Keyman](#windows) from keyman.com | [Native layout](#windows--advanced-native-layout) (advanced) |
| **macOS** | [Keyman](#macos) from keyman.com | [`.keylayout` file](#macos--advanced-keylayout-file) (advanced) |
| **Linux** | [Keyman](#linux) from keyman.com / your distro | [XKB layout](#linux--advanced-xkb) (advanced) |

All routes give you the same four letters — **č š ž ě** — on the same base keys
(C, S, Z, E), plus the quotation marks and dashes `„ ” – —`. Only the
modifier differs per platform.

**What is Keyman?** A free, open-source keyboard platform by [SIL Global](https://keyman.com/about/),
in every app store since 2010. Our keyboard is in its **official catalogue**:
<https://keyman.com/keyboards/isv_latin> — together with the prediction
dictionary (*Medžuslovjansky slovnik*). Keyman is the trusted route on every
platform; the "advanced" options exist for people who want a native layout
without any extra app.

---

## iPhone / iPad

1. Install **Keyman** from the App Store: <https://keyman.com/iphone-and-ipad/>
   (or search "Keyman" in the App Store — publisher **SIL Global**).
2. Open Keyman → **Settings → Installed Languages → +** → search
   **Interslavic** → install **Medžuslovjansky (latinica)**.
3. Add the keyboard to iOS: **Settings → General → Keyboard → Keyboards →
   Add New Keyboard → Keyman**.
4. **Allow Full Access** for Keyman (same screen, tap *Keyman*). On iOS 16 and
   newer the Keyman keyboard shows **no keys at all** until this is enabled —
   it is a documented iOS limitation, not a security feature of our layout:
   <https://help.keyman.com/knowledge-base/kb0109>
5. In any app, hold the 🌐 key → **Keyman** → type. **Swipe up** on C/S/Z/E
   for č š ž ě. Predictions appear once the *Medžuslovjansky slovnik* is
   installed (Keyman offers it automatically for the language).

---

## Android — route A: Keyman (taps)

1. Install **Keyman** from Google Play: <https://keyman.com/android/>
   (publisher **SIL Global**).
2. In Keyman: **+ Add keyboard** → search **Interslavic** →
   **Medžuslovjansky (latinica)**. Accept the dictionary when offered.
3. **Android Settings → System → Languages & input → On-screen keyboard →
   Manage keyboards** → enable **Keyman**.
4. Switch keyboards with the keyboard icon in the navigation bar.
   Long-press C/S/Z/E → č š ž ě.

No swipe typing on this route — Keyman does not do gestures.

## Android — route B: swipe-typing app

Our own keyboard app: **swipe (glide) typing with a 253 273-form Interslavic
dictionary, fully offline, no permissions, MIT licence.**

Package name: `com.radoslove.interslavic`

**Where to get it — in order of preference:**

| Source | Status |
|---|---|
| **Google Play** | 🔜 coming (closed test first — write to us to be a tester) |
| **F-Droid** | ⏳ submitted, awaiting F-Droid review (`fdroiddata` MR !45568) |
| **IzzyOnDroid** repo (F-Droid client) | 🔜 coming |
| **GitHub Releases** — <https://github.com/radoslove/keyboard-interslavic/releases/latest> | ✅ available now |

### Installing the GitHub release safely

1. Open the **Releases** link above **on the phone** and download
   `app-release.apk` from the newest release.
2. Android will ask to allow installs from your browser — allow it once for
   this install; you can turn it off again afterwards.
3. Before you tap *Install*, you can **verify the file** (recommended, takes a
   minute — see [Verify what you downloaded](#verify-what-you-downloaded)).
4. Enable it: **Settings → System → Languages & input → On-screen keyboard →
   Manage keyboards** → **Medžuslovjansky**.

The app **requests no permissions** (no internet, no contacts, no storage).
Android may still show the generic "this keyboard may collect what you type"
notice — it shows that for *every* third-party keyboard; ours has no network
access to send anything anywhere.

### Updates

The app cannot update itself (no network). Until the store listings are live,
check the Releases page now and then; each release lists what changed.

---

## Windows

1. Download **Keyman for Windows** from <https://keyman.com/windows/>
   (signed installer, publisher **SIL Global**).
2. Keyman Configuration → **Keyboard Layouts → Install keyboard** → search
   **Interslavic** → **Medžuslovjansky (latinica)**.
3. Switch with the Keyman icon in the tray (or `Win`+`Space`).
   **AltGr** + C/S/Z/E → č š ž ě.

## Windows — advanced: native layout

A native Windows keyboard layout (`KBDMSSTD`, built with Microsoft's own
Keyboard Layout Creator) — no extra app, appears under Polish in the language
bar. Source and DLLs: <https://github.com/radoslove/keyboard-interslavic/tree/main/windows/installers/kbdmsstd>

**Be aware before you install:**

- It needs **administrator rights** (every keyboard layout does — it is a
  system DLL).
- The installer is **not code-signed yet**, so **SmartScreen will warn about
  an "unknown publisher"**. That warning is about the missing signature, not
  about malware — but it is also exactly why we recommend Keyman for now.
  A signed installer (`.msi`) and a `winget` listing are on the roadmap.
- Read `README.md` in that folder; `install.ps1` / `uninstall.ps1` are plain
  text — you can open and read them before running.

---

## macOS

1. Download **Keyman for macOS** from <https://keyman.com/mac/> (publisher
   **SIL Global**).
2. Keyman Configuration → **Download keyboard** → search **Interslavic** →
   **Medžuslovjansky (latinica)**.
3. **System Settings → Keyboard → Input Sources** → add **Keyman**.
   **Option** + C/S/Z/E → č š ž ě.

## macOS — advanced: `.keylayout` file

A native Apple layout file, no app at all:
<https://github.com/radoslove/keyboard-interslavic/blob/main/mac/KBDMSSTD.keylayout>
(*Download raw*). Copy it to `~/Library/Keyboard Layouts/`, log out and in,
then add it under **System Settings → Keyboard → Input Sources → Others**.
It is a plain XML file — you can open it in a text editor and read it.

---

## Linux

1. Install **Keyman for Linux**: <https://keyman.com/linux/> (Ubuntu/Debian
   packages from SIL's repository; also in several distros' own repos).
2. `km-config` → **Download keyboard** → **Interslavic** →
   **Medžuslovjansky (latinica)**, then add it as an input source in your
   desktop's keyboard settings. **AltGr** + C/S/Z/E → č š ž ě.

## Linux — advanced: XKB

A plain XKB symbols file, installed with a copy into `/usr/share/X11/xkb/symbols/`:
<https://github.com/radoslove/keyboard-interslavic/tree/main/linux> — steps in
`linux/README.md`.

---

## Verify what you downloaded

Only needed for the **files** (Android APK, Windows DLLs, macOS keylayout).
App-store routes (Keyman) are verified by the store.

**Android APK — current release `v3.1`:**

| What | Value |
|---|---|
| File | `app-release.apk` |
| SHA-256 | `92f792920ca1130b70333ab5f2c9881dc050527d239d365924e0b1ec1fe35432` |
| Signing-certificate SHA-256 | `5fa81cd2fd62cbdd3580076b941c1711b4ddc625609b5c80ec7c135a56e3b98a` |

The signing-certificate fingerprint is the same one registered in our F-Droid
recipe — F-Droid will refuse the app if a build is ever signed by a different
key. It stays the same across releases; the file hash changes every release.

How to compute the file hash:

```
# Windows (PowerShell)
Get-FileHash .\app-release.apk -Algorithm SHA256

# macOS / Linux
shasum -a 256 app-release.apk
```

On Android itself: the app **Hash Droid** or **Checksum** (both on F-Droid)
computes SHA-256 of a downloaded file.

---

## What this keyboard does and does not do

- **Offline.** No network permission, on any platform. Nothing you type leaves
  the device.
- **No account, no e-mail, no telemetry, no ads.**
- **Open source**, MIT licence — every file, including the dictionary source,
  is in the repository: <https://github.com/radoslove/keyboard-interslavic>
- **Standard orthography only** — č š ž ě and punctuation. No extended-alphabet
  letters on any layout, on purpose.

---

## Problems and questions

**GitHub Issues** is the only support channel:
<https://github.com/radoslove/keyboard-interslavic/issues>

A testing checklist for reviewers lives in
[`TESTING.md`](../TESTING.md). If a step on *this* page was unclear or
missing, that is a documentation bug — please report it too.
