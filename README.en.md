# Medžuslovjansky — Interslavic Keyboards

**[Medžuslovjansky](README.md) · 🇬🇧 English**

**The first keyboard set for the Interslavic language — and the first swipe-typing dictionary for it.**

Here you can download and install Interslavic keyboards for Windows, Android, iPhone/iPad, macOS and Linux. The swipe-typing dictionary carries **253 273 words** — inflected forms, not just lemmas.

Nobody had built this before.

---

## How to get it

These are files to download — **no registration, no account, no e-mail, no phone number.** You take them, install them, and type.

Questions and bugs → **GitHub Issues.** That is the only support channel.

---

## Windows

`.exe` installers for three layouts:

| Layout | Installer | Note |
|---|---|---|
| Interslavic **Latin** | `windows/…/setup.exe` | standard orthography |
| Interslavic **Cyrillic** | `windows/…/setup.exe` | |
| **Runic** | `windows/installers/runy_5/setup.exe` | `runy_5` = newest version |

**Installation:** open the layout's folder and run **`setup.exe`**. One UAC prompt — the layout adds itself to your language list.

Switch layouts: **Win + Space.**

### The MS letters

The letters specific to Interslavic sit under **AltGr**:

| AltGr + | gives |
|---|---|
| `c` | **č** |
| `s` | **š** |
| `z` | **ž** |
| `e` | **ě** |

Digraphs `dž`, `lj`, `nj` are typed as two keys (`d`+`ž` …).

> The layout carries **only** standard orthography — the four letters above plus punctuation (`„ ” – —`). The extended alphabet is not on the keyboard. Full character map: `docs/ms-latin-table.md`.

---

## Android

On Android you need no modded APK — two open-source apps cover everything.

### 1. Layouts — Unexpected Keyboard

**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** (GPL-3.0, F-Droid + Play Store) loads custom XML layouts. Three are provided here:

| File | Layout |
|---|---|
| `android/isv_latin.xml` | Latin |
| `android/isv_cyrillic.xml` | Cyrillic |
| `android/isv_runic.xml` | runic |

**Installation:**
1. Install Unexpected Keyboard (F-Droid or Play Store).
2. Enable it: **Settings → System → Languages & input → On-screen keyboard.**
3. In the app: gear icon → **Add an alternate layout → Custom layout.**
4. Paste the contents of the chosen `.xml` and confirm.

The Windows AltGr layer maps here to a **swipe to the top-right corner** of a key (`ne`); digits are a swipe to the top-left (`nw`).

### 2. Swipe typing — HeliBoard + dictionary

Unexpected Keyboard has no swipe typing. For that, use **[HeliBoard](https://github.com/Helium314/HeliBoard)** with our `.dict` dictionary — see the **Dictionary** section below.

---

## iPhone and iPad

iOS has no layout-file format — a keyboard there is a whole app. So we ship a package for **[Keyman](https://keyman.com/iphone-and-ipad/)**, a free App Store app that loads it.

1. Install **Keyman** from the App Store.
2. Open **`keyman/isv_latin.kmp`** on the device — Safari, e-mail or AirDrop. Choose "Open in Keyman".
3. **Settings → General → Keyboard → Keyboards → Add New Keyboard → Keyman.**

**The letters:** there is no AltGr on a phone, so **hold the key** — `c` → **č**, `s` → **š**, `z` → **ž**, `e` → **ě**. For capitals, Shift first, then hold.

⚠ **On iOS 16 and later you must turn on "Allow Full Access"** (Settings → General → Keyboard → Keyboards → Keyman), otherwise the system keyboard renders blank — no keys at all. This is an iOS bug, not a Keyman one; inside the Keyman app itself everything works without it. See [KB0109](https://help.keyman.com/knowledge-base/kb0109).

The same package also works on Android, Windows, macOS and Linux via Keyman.

Building from source:

```sh
npm install -g @keymanapp/kmc
python3 build_keyman.py                          # .kmn + touch layout
kmc build keyman/isv_latin/isv_latin.kpj         # -> keyman/isv_latin/build/
cp keyman/isv_latin/build/isv_latin.kmp keyman/  # published package
```

---

## macOS

Copy `mac/KBDMSSTD.keylayout` to `~/Library/Keyboard Layouts/`, then **Settings → Keyboard → Input Sources → + → Others**.

The letters sit under **Option** (Option+C → č, and so on).

---

## Linux

We don't duplicate — upstream already ships packages. See `linux/README.md`.

---

## Swipe dictionary

The `.dict` dictionary for **[HeliBoard](https://github.com/Helium314/HeliBoard)** gives you **Interslavic swipe typing** on your phone — with suggestions and autocorrect, which Unexpected Keyboard cannot do.

**253 273 words**, with inflection. The forms matter: when you swipe, you drag across the word you actually write (`slovami`), not the lemma (`slovo`). Without forms it rarely hits.

### Installation

1. **HeliBoard** — from [F-Droid](https://f-droid.org/packages/helium314.keyboard/) or [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
2. **Dictionary** — open `main_isv.dict` in a file manager and pick HeliBoard, or in its settings: *Languages & Layouts → language → `+` next to dictionaries.*
3. **Glide typing** — HeliBoard does **not** ship the gesture engine (Google never open-sourced it). You must side-load `libjni_latinimegoogle.so`, extracted from a Gboard APK: *Settings → Advanced → Load gesture typing library.* This is the one manual step, and it is a Google-code limitation, not ours.
4. **Layout** — MS Latin; base QWERTY, the letters `č š ž ě` via long-press.

---

## Contributing new words

This is **opt-in** and happens **on your own device.** By default nothing leaves your phone.

When swipe misses, you type the word by finger and add it to your phone's dictionary. If you allow it, such words can reach the **community dictionary** — for everyone.

**How it is protected:**

- **Pseudonym, never a name.** Identity = a pseudonym you choose yourself.
- **On-device filter.** Names, digits and foreign letters are dropped before anything is sent.
- **Withdrawable.** Pull your consent and your words drop out.
- **Aggregate only.** Data is reported in aggregate, never per person.
- **No account, no e-mail, no phone number.**

**Optional (for linguistic research only):** you may add an **age bracket** (`<20` / `20–34` / `35–54` / `55+`) and your **native language** — indicative only, to study where the vocabulary comes from. You are not required to give it.

### Important: ranking, not canon

Collected words produce a **popularity ranking** — they show what people need and cannot find. They do **not** automatically become official vocabulary. The canon stays with the Interslavic community's language authority. This system **measures and proposes — it does not replace it.**

---

## Licenses

Code and layouts under **MIT.**

- The `kbdmslat` (Latin) and `kbdmskir` (Cyrillic) layouts originate from [medzuslovjansky/keyboards](https://github.com/medzuslovjansky/keyboards). Copyright with the authors: **Adam Gola, Roberto Lombino jr.**
- The rest of the **code** — runic layouts, Android layouts, `docs/`, generators, apps — original, MIT.
- **Dictionary data:** the wordlist is word forms + corpus frequencies only (no definitions or translations), machine-generated from a lemma list compiled from various Interslavic lexical resources. **No ownership of, or license over, the underlying lexical data is claimed** — it is included solely to support this free, non-commercial tool for the Interslavic language.

Full text: [`LICENSE`](LICENSE).
