# Medžuslovjanske tipkovnici · Interslavic Keyboards

**Prvy nabor tipkovnic za medžuslovjansky jezyk — i prvy slovnik za pisanje gestami.**
*The first keyboard set for the Interslavic language — and the first swipe-typing
dictionary for it.*

Tu možeš daunlodovati i instalovati tipkovnice (klaviatury) za medžuslovjansky jezyk na
Windows, Android, iPhone/iPad, macOS i Linux. Slovnik za pisanje gestami imaje
**253 273 slov** — s formami, ne samo lematami.
*Here you can download and install Interslavic keyboards for Windows, Android,
iPhone/iPad, macOS and Linux. The swipe-typing dictionary carries **253 273 words** —
inflected forms, not just lemmas.*

Nikto togo dosej ne napravil.
*Nobody had built this before.*

---

## Kako vzeti · How to get it

Ovo sut fajly za daunlodovanje — **bez registracije, bez konta, bez e-pošty, bez
telefona.** Vzimaješ, instaluješ, pišeš.
*These are files to download — **no registration, no account, no e-mail, no phone
number.** You take them, install them, and type.*

Pytanja i grěšky → **GitHub Issues.** To jest jediny kanal podpory.
*Questions and bugs → **GitHub Issues.** That is the only support channel.*

---

## Windows

Instalatory `.exe` za tri ukladji:
*`.exe` installers for three layouts:*

| Ukladj / Layout | Instalator | Uwaga / Note |
|---|---|---|
| Medžuslovjanska **latinica** (Latin) | `windows/…/setup.exe` | standardna ortografija |
| Medžuslovjanska **cyrilica** (Cyrillic) | `windows/…/setup.exe` | |
| **Runy** (runic) | `windows/installers/runy_5/setup.exe` | `runy_5` = najnovša versija |

**Instalacija:** odpri katalog ukladja i odpali **`setup.exe`**. Jedno okno UAC — ukladj se
sam dopiše do liste jezykov.
*Installation: open the layout's folder and run **`setup.exe`**. One UAC prompt — the
layout adds itself to your language list.*

Prěklučanje ukladjev: **Win + Space.**
*Switch layouts: **Win + Space.***

### Litery MS na tipkovnici · The MS letters

Specifične litery medžuslovjanskogo jezyka sut pod **AltGr**:
*The letters specific to Interslavic sit under **AltGr**:*

| AltGr + | Rezultat / gives |
|---|---|
| `c` | **č** |
| `s` | **š** |
| `z` | **ž** |
| `e` | **ě** |

Digrafy `dž`, `lj`, `nj` pišeš kako dva klavišy (`d`+`ž` …).
*Digraphs `dž`, `lj`, `nj` are typed as two keys (`d`+`ž` …).*

> Ukladj imaje **samo** standardnu ortografiju — četyri litery vyše plus interpunkciju
> (`„ ” – —`). Litery razšireneho alfabeta ne sut na tipkovnici. Polna karta znakov:
> `docs/ms-latin-table.md`.
> *The layout carries **only** standard orthography — the four letters above plus
> punctuation (`„ ” – —`). The extended alphabet is not on the keyboard. Full character
> map: `docs/ms-latin-table.md`.*

---

## Android

Na Androidu ne trěba modovanogo APK — dvě otvorjene aplikacije pokryvajut vse.
*On Android you need no modded APK — two open-source apps cover everything.*

### 1. Ukladji — Unexpected Keyboard

**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** (GPL-3.0, F-Droid +
Play Store) čita vlastne ukladji v XML. Tri ležat tu:
*Unexpected Keyboard loads custom XML layouts. Three are provided here:*

| Fajl / File | Ukladj / Layout |
|---|---|
| `android/isv_latin.xml` | latinica |
| `android/isv_cyrillic.xml` | cyrilica |
| `android/isv_runic.xml` | runy |

**Instalacija:**
1. Install Unexpected Keyboard (F-Droid or Play Store).
2. Enable it: **Settings → System → Languages & input → On-screen keyboard.**
3. In the app: gear icon → **Add an alternate layout → Custom layout.**
4. Paste the contents of the chosen `.xml` and confirm.

Warstva AltGr iz Windows tu odpovědaje **svaipu v desny gorny ugol** klaviša (`ne`); cifry
sut svaip v lěvy gorny (`nw`).
*The Windows AltGr layer maps here to a **swipe to the top-right corner** of a key (`ne`);
digits are a swipe to the top-left (`nw`).*

### 2. Pisanje gestami — HeliBoard + slovnik

Unexpected Keyboard ne imaje pisanja gestami. Za to služi
**[HeliBoard](https://github.com/Helium314/HeliBoard)** so našim slovnikom `.dict` — gl.
sekciju **Slovnik** niže.
*Unexpected Keyboard has no swipe typing. For that, use **HeliBoard** with our `.dict`
dictionary — see the **Dictionary** section below.*

---

## iPhone i iPad

iOS ne dopušča fajlov ukladja — tam tipkovnica jest cěla aplikacija. Zato idemo črěz
**[Keyman](https://keyman.com/iphone-and-ipad/)** — bezplatna aplikacija iz App Store,
ktora čita naše pakety.
*iOS has no layout-file format — a keyboard there is a whole app. So we ship a package
for **Keyman**, a free App Store app that loads it.*

1. Instaluj **Keyman** iz App Store.
   *Install **Keyman** from the App Store.*
2. Otvori paket **`keyman/isv_latin.kmp`** na svojem urędžaju —
   Safari, e-pošta abo AirDrop. Vybere se „Open in Keyman".
   *Open **`isv_latin.kmp`** on the device — Safari, e-mail or AirDrop. Choose
   "Open in Keyman".*
3. **Nastrojenja → Osnovne → Tipkovnica → Tipkovnici → Dodaj → Keyman.**
   *Settings → General → Keyboard → Keyboards → Add New Keyboard → Keyman.*

**Litery:** na telefonu ne jest AltGr, zato **drži tipku** — `c` → **č**, `s` → **š**,
`z` → **ž**, `e` → **ě**. Velike litery: najprvo Shift, potom drži.
*The letters: there is no AltGr on a phone, so **hold the key** — `c` → **č**,
`s` → **š**, `z` → **ž**, `e` → **ě**. For capitals, Shift first, then hold.*

⚠ „Full Access" **ne jest potrěbny**. Ne davaj jego.
*⚠ "Allow Full Access" is **not needed**. Don't grant it.*

Toj že paket rabotaje takože na Androidu, Windowsu, macOS i Linuxu črěz Keyman.
*The same package also works on Android, Windows, macOS and Linux via Keyman.*

Zbudovanje iz izvora / *building from source*:

```sh
npm install -g @keymanapp/kmc
python3 build_keyman.py                          # .kmn + touch layout
kmc build keyman/isv_latin/isv_latin.kpj         # -> keyman/isv_latin/build/
cp keyman/isv_latin/build/isv_latin.kmp keyman/  # publikovany paket
```

---

## macOS

`mac/KBDMSSTD.keylayout` — kopiruj do `~/Library/Keyboard Layouts/`, potom
**Nastrojenja → Tipkovnica → Izvory vhoda → + → Others**.
*Copy `mac/KBDMSSTD.keylayout` to `~/Library/Keyboard Layouts/`, then
Settings → Keyboard → Input Sources → + → Others.*

Litery sut pod **Option** (Option+C → č, itd.).
*The letters sit under **Option** (Option+C → č, and so on).*

---

## Linux

Ne dupliciramo — upstream jur izdal gotove pakety. Gl. `linux/README.md`.
*We don't duplicate — upstream already ships packages. See `linux/README.md`.*

---

## Slovnik za pisanje gestami · Swipe dictionary

Slovnik `.dict` za **[HeliBoard](https://github.com/Helium314/HeliBoard)** dava na telefonu
**pisanje gestami po medžuslovjansky** — s podpovědjami i avtokorekturoju, čego Unexpected
Keyboard ne umě.
*The `.dict` dictionary for HeliBoard gives you **Interslavic swipe typing** on your phone —
with suggestions and autocorrect, which Unexpected Keyboard cannot do.*

**253 273 slov**, s formami. Forma jest ključna: pri svaipu prěvlečeš palcem po slově, ktoro
faktično pišeš (`slovami`), ne po lemate (`slovo`). Bez form slovnik trafja rědko.
*253 273 words, with inflection. The forms matter: when you swipe, you drag across the word
you actually write (`slovami`), not the lemma (`slovo`). Without forms it rarely hits.*

### Instalacija · Installation

1. **HeliBoard** — from [F-Droid](https://f-droid.org/packages/helium314.keyboard/) or
   [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
2. **Dictionary** — open `main_isv.dict` in a file manager and pick HeliBoard, or in its
   settings: *Languages & Layouts → language → `+` next to dictionaries.*
3. **Glide typing** — HeliBoard does **not** ship the gesture engine (Google never
   open-sourced it). You must side-load `libjni_latinimegoogle.so`, extracted from a Gboard
   APK: *Settings → Advanced → Load gesture typing library.* This is the one manual step,
   and it is a Google-code limitation, not ours.
4. **Layout** — MS Latin; base QWERTY, the letters `č š ž ě` via long-press.

---

## Vklad — nove slova · Contributing new words

Ovo jest **dobrovoljno (opt-in)** i děje se **na tvojem telefone.** Standardno ne ide iz
telefona ničto.
*This is **opt-in** and happens **on your own device.** By default nothing leaves your
phone.*

Kogda swipe ne trafja, pišeš slovo palcem i dodavaješ ho do slovnika telefona. Ako
dozvoliš, taka slova mogut dojti do **občinskogo slovnika** — za vsih.
*When swipe misses, you type the word by finger and add it to your phone's dictionary. If
you allow it, such words can reach the **community dictionary** — for everyone.*

**Kako to jest zaščiteno · How it is protected:**

- **Psevdonim, nikogda ime.** Identifikacija = psevdonim, ktory sam vybereš.
  *Pseudonym, never a name. Identity = a pseudonym you choose yourself.*
- **Filtr na telefone.** Imena, cifry, obce litery ne opuščajut telefon — odsějut se prěd
  vysylanjem. *An on-device filter drops names, digits and foreign letters before anything
  is sent.*
- **Vklad jest vratny.** Vytegneš soglasje — tvoja slova vypadajut.
  *Contribution is withdrawable. Pull your consent and your words drop out.*
- **Samo zbirno.** Danne se raportujut samo zbirno, nikogda po osobě.
  *Aggregate only. Data is reported in aggregate, never per person.*
- **Bez konta, bez e-pošty, bez telefona.** *No account, no e-mail, no phone number.*

**Neobovezno (samo za jezykove badanje):** može dodati **vozrast** (`<20` / `20–34` /
`35–54` / `55+`) i **rodny jezyk** — orientaciono, za badanje pochodženja slovnika. Ne
trěba togo davati.
*Optional (for linguistic research only): you may add an **age bracket** (`<20` / `20–34` /
`35–54` / `55+`) and your **native language** — indicative only, to study where the
vocabulary comes from. You are not required to give it.*

### Važno: rang, ne kanon · Important: ranking, not canon

Zbirane slova dajut **poredok popularnosti** — pokazujut, čego ljudem trěba i čego ne
najdut. **Ne stavajut avtomatično oficialnym slovnikom.** Kanon ostavaje pri jezykovom
avtoritetu občiny medžuslovjanskogo jezyka. Ova sistema **měri i prědlagaje — ne zaměnjaje
ho.**
*Collected words produce a **popularity ranking** — they show what people need and cannot
find. They do **not** automatically become official vocabulary. The canon stays with the
Interslavic community's language authority. This system **measures and proposes — it does
not replace it.***

---

## Licencije · Licenses

Vse pod **MIT.** *Everything under **MIT.***

- Ukladji `kbdmslat` (latinica) i `kbdmskir` (cyrilica) izvorno iz projekta
  [medzuslovjansky/keyboards](https://github.com/medzuslovjansky/keyboards). Avtorske prava
  pri avtorah: **Adam Gola, Roberto Lombino jr.**
  *The `kbdmslat` (Latin) and `kbdmskir` (Cyrillic) layouts originate from
  medzuslovjansky/keyboards. Copyright with the authors: Adam Gola, Roberto Lombino jr.*
- Ostatok — runy, android ukladji, `docs/`, slovnik — vlastno, MIT.
  *The rest — runic layouts, Android layouts, `docs/`, the dictionary — original, MIT.*

Polny tekst: [`LICENSE`](LICENSE).
*Full text: [`LICENSE`](LICENSE).*
