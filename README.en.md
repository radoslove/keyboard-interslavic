# Interslavic Keyboards

*[Medžuslovjanska versija · Interslavic version →](README.md)*

**The first keyboard set for the Interslavic language — and the first swipe-typing
dictionary for it.**

Download and install Interslavic keyboards for **Windows, Android, iPhone/iPad, macOS and
Linux.** The swipe dictionary carries **253,273 words** — inflected forms, not just lemmas.

Nobody had built this before.

These are files to download — **no registration, no account, no e-mail, no phone number.**
Take them, install them, type.

Questions and bugs → **GitHub Issues.** That is the only support channel.

---

## The letters

Interslavic in standard orthography needs exactly four letters a Polish or US keyboard
cannot produce:

**č š ž ě**

Every layout here places them the same way, on the same base keys, so the habit carries
across platforms. Only the *reach* differs, because each platform has a different spare
modifier — and a phone has none at all.

| Platform | How you reach them |
|---|---|
| Windows | `AltGr` + the letter |
| macOS | `Option` + the letter |
| Android | swipe up-right on the key |
| iPhone / iPad | **swipe up** on the key |
| Linux | see `linux/README.md` |

`dž` has no key of its own — it is `d` then `ž`, two ordinary keystrokes. A ligature key
would add a failure mode for no gain.

The **extended alphabet** (`ų ė ȯ ŕ ť ď ľ đ` and friends) is deliberately **not** on any
layout. It is not written in new text, and a key for it would only invite mistakes.

---

## Windows

Three layouts, but they do not install the same way — the Latin one is ours and ships
as scripts, the other two are upstream packages with an installer.

| Layout | How to install |
|---|---|
| Interslavic **Latin** (recommended) | `windows/installers/kbdmsstd/` → right-click `install.ps1` → **Run with PowerShell** as administrator |
| Interslavic **Cyrillic** | `windows/installers/kbdmskir/kbdmskir.exe` |
| **Runic** | `windows/installers/runy_5/setup.exe` |

The Latin layout registers itself under Polish, so it appears next to your existing
keyboards rather than adding a phantom language. Switch layouts with **Win + Space**.

To remove it later, `uninstall.ps1` sits in the same folder. ⚠ Removing a layout from
the Windows language list does **not** uninstall it — use the script.

Punctuation lives on AltGr too: `;` → `„ “`, `'` → `’ ”`, `-` → `– —`.

---

## Android

No modded APK needed — two open-source apps cover everything.

### Layouts — Unexpected Keyboard

[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) (GPL-3.0, F-Droid and
Play Store) loads custom XML layouts. Three are provided:

| File | Layout |
|---|---|
| `android/isv_latin.xml` | Latin |
| `android/isv_cyrillic.xml` | Cyrillic |
| `android/isv_runic.xml` | Runic |

1. Install Unexpected Keyboard.
2. Enable it: **Settings → System → Languages & input → On-screen keyboard.**
3. In the app: gear icon → **Add an alternate layout → Custom layout.**
4. Paste the contents of the chosen `.xml` and confirm.

The Windows AltGr layer maps to a **swipe to the top-right corner** of a key; digits are a
swipe to the top-left.

### Swipe typing — HeliBoard + the dictionary

Unexpected Keyboard has no swipe typing. For that, use **HeliBoard** with our `.dict` —
see [Dictionary](#dictionary) below.

---

## iPhone and iPad

iOS has **no keyboard-layout file format**. A third-party keyboard there is a whole app,
so instead of a layout we ship a package for
**[Keyman](https://keyman.com/iphone-and-ipad/)** — a free App Store app that loads it.

1. Install **Keyman** from the App Store.
2. Open **`keyman/isv_latin.kmp`** on the device — Safari, e-mail or AirDrop. Downloading
   is not installing: in Keyman choose **Install From File** and pick it.
3. **Settings → General → Keyboard → Keyboards → Add New Keyboard → Keyman.**

### ⚠ On iOS 16 and later you must enable "Allow Full Access"

**Settings → General → Keyboard → Keyboards → Keyman → Allow Full Access.**

Without it the system keyboard renders **completely blank — no keys at all**, while
everything still works inside the Keyman app itself, which makes the symptom read as a
broken package. This is an iOS compatibility bug, not a Keyman one; see
[KB0109](https://help.keyman.com/knowledge-base/kb0109).

### Typing the letters

**Swipe up** from `c s z e`. Any upward direction works, so you do not have to aim. For
capitals press Shift first, then swipe.

Holding the key also works and is the gesture Keyman documents — but on iOS it is
unreliable: Keyman expects you to slide onto the popup before releasing, the popup sits in
a different place for every key, and releasing anywhere else gives you the plain letter.
This is not specific to our layout; Keyman's own EuroLatin keyboard behaves the same way.
Swiping avoids the problem entirely.

The same package also installs on Android, Windows, macOS and Linux through Keyman.

### Building from source

```sh
npm install -g @keymanapp/kmc
python3 build_keyman.py                          # rules + touch layout
python3 build_icon.py                            # keyboard icon
kmc build keyman/isv_latin/isv_latin.kpj
cp keyman/isv_latin/build/isv_latin.kmp keyman/

python3 build_keyman_wordlist.py                 # prediction wordlist
kmc build keyman/radoslove.isv-latn.wordlist/radoslove.isv-latn.wordlist.kpj
cp keyman/radoslove.isv-latn.wordlist/build/*.model.kmp keyman/
```

---

## macOS

Copy `mac/KBDMSSTD.keylayout` to `~/Library/Keyboard Layouts/`, then
**Settings → Keyboard → Input Sources → + → Others**. The letters sit under **Option**
(Option+C → č, and so on).

---

## Linux

We don't duplicate — upstream already ships packages. See `linux/README.md`.

---

## Dictionary

### Android — swipe typing

The `.dict` dictionary for **[HeliBoard](https://github.com/Helium314/HeliBoard)** gives
you **Interslavic swipe typing** with suggestions and autocorrect.

**253,273 words, with inflection.** The forms matter: when you swipe, you drag across the
word you actually write (`slovami`), not the lemma (`slovo`). Without forms it rarely hits.

1. **HeliBoard** — from [F-Droid](https://f-droid.org/packages/helium314.keyboard/) or
   [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
2. **Dictionary** — open `dictionary/main_isv.dict` in a file manager and pick HeliBoard,
   or in its settings: *Languages & Layouts → language → `+` next to dictionaries.*
3. **Glide typing** — HeliBoard does **not** ship the gesture engine (Google never
   open-sourced it). You must side-load `libjni_latinimegoogle.so`, extracted from a Gboard
   APK: *Settings → Advanced → Load gesture typing library.* This is the one manual step,
   and it is a Google-code limitation, not ours.

### iPhone and iPad — word prediction

iOS gives third-party keyboards **no gesture-typing API at all**, so swipe typing is not
reachable there and the `.dict` above is useless on iOS. What is reachable is prediction:
install `keyman/radoslove.isv-latn.wordlist.model.kmp` in Keyman and you get word suggestions
and autocorrect above the keyboard.

**39,777 forms** rather than the full 253k. The complete list compiles to a 33 MB trie,
past what an iOS keyboard *extension* can hold — it would take the keyboard down with it.
Vocabulary specific to Interslavic is kept regardless of frequency, because the underlying
corpus barely knows the words this project is about.

---

## Missing a word?

Interslavic is a living vocabulary and the dictionary does not have everything. If you
reach for a word and it is not there, tell us — that is the entire mechanism today:

**[Open a GitHub issue](https://github.com/radoslove/keyboard-interslavic/issues)** with
the word and what you meant by it.

What happens next: it joins a review queue and is checked against the Interslavic
dictionary, which answers one of three ways — already attested, a regular derivation of
something attested, or genuinely absent. What survives review is added to a local layer
that rides alongside the main dictionary.

**Ranking, not canon.** Collected words show what people need and cannot find. They do
**not** automatically become official vocabulary — the canon stays with the Interslavic
community's language authority. This measures and proposes; it does not replace them.

### Planned, not built

Collecting missing words from the keyboard itself — on device, opt-in, pseudonymous, with
names and digits filtered out before anything is sent, withdrawable, and reported only in
aggregate. None of that exists yet, and this section will say so until it does.

On iOS it will have to live in the **app**, not the keyboard: a keyboard extension without
"Full Access" can reach neither the network nor a shared container with its own app. That
isolation is exactly what this keyboard advertises, so it stays — and the collection moves
rather than the promise.

## Licenses

Everything here is MIT unless a folder says otherwise. The Cyrillic and runic Windows
layouts originate from `medzuslovjansky/keyboards`; copyright stays with their authors,
Adam Gola and Roberto Lombino jr.
