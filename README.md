# Medžuslovjanske tipkovnici

**🌐 Medžuslovjansky · [English](README.en.md)**

**Prvy nabor tipkovnic za medžuslovjansky jezyk — i prvy slovnik za pisanje gestami.**

Tu možeš daunlodovati i instalovati tipkovnice (klaviatury) za medžuslovjansky jezyk na
Windows, Android, iPhone/iPad, macOS i Linux. Slovnik za pisanje gestami imaje
**253 273 slov** — s formami, ne samo lematami.

Nikto togo dotud ne napravil.

---

## Kako vzeti

Ovo sut fajly za daunlodovanje — **bez registracije, bez konta, bez e-pošty, bez
telefona.** Vzimaješ, instaluješ, pišeš.

Pytanja i grěšky → **GitHub Issues.** To jest jediny kanal podpory.

---

## Windows

Instalatory `.exe` za tri ukladji:

| Ukladj | Instalator | Uwaga |
|---|---|---|
| Medžuslovjanska **latinica** | `windows/…/setup.exe` | standardna ortografija |
| Medžuslovjanska **cyrilica** | `windows/…/setup.exe` | |
| **Runy** | `windows/installers/runy_5/setup.exe` | `runy_5` = najnovša versija |

**Instalacija:** odpri katalog ukladja i odpali **`setup.exe`**. Jedno okno UAC — ukladj se
sam dopiše do liste jezykov.

Prěključanje ukladjev: **Win + Space.**

### Litery MS na tipkovnici

Specifične litery medžuslovjanskogo jezyka sut pod **AltGr**:

| AltGr + | Rezultat |
|---|---|
| `c` | **č** |
| `s` | **š** |
| `z` | **ž** |
| `e` | **ě** |

Digrafy `dž`, `lj`, `nj` pišeš kako dva klaviši (`d`+`ž` …).

> Ukladj imaje **samo** standardnu ortografiju — četyri litery vyše plus interpunkciju
> (`„ ” – —`). Litery razširenogo alfabeta ne sut na tipkovnici. Polna karta znakov:
> `docs/ms-latin-table.md`.

---

## Android

Na Androidu ne trěba modovanogo APK — dvě otvorjene aplikacije pokryvajut vse.

### 1. Ukladji — Unexpected Keyboard

**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** (GPL-3.0, F-Droid +
Play Store) čita vlastne ukladji v XML. Tri ležat tu:

| Fajl | Ukladj |
|---|---|
| `android/isv_latin.xml` | latinica |
| `android/isv_cyrillic.xml` | cyrilica |
| `android/isv_runic.xml` | runy |

**Instalacija:**
1. Instaluj Unexpected Keyboard (F-Droid abo Play Store).
2. Vključi ju: **Nastrojenja → Sistem → Jezyky i vhod → Ekranna tipkovnica.**
3. V aplikaciji: ikona zubčatky → **Dodaj alternativny ukladj → Vlastny ukladj.**
4. Vstavi sodržanje izbranogo `.xml` i potvrdi.

Warstva AltGr iz Windows tu odpovědaje **svaipu v desny gorny ugol** klaviša (`ne`); cifry
sut svaip v lěvy gorny (`nw`).

### 2. Pisanje gestami — HeliBoard + slovnik

Unexpected Keyboard ne imaje pisanja gestami. Za to služi
**[HeliBoard](https://github.com/Helium314/HeliBoard)** so našim slovnikom `.dict` — gl.
sekciju **Slovnik** niže.

---

## iPhone i iPad

iOS ne dopušča fajlov ukladja — tam tipkovnica jest cěla aplikacija. Zato idemo črěz
**[Keyman](https://keyman.com/iphone-and-ipad/)** — bezplatna aplikacija iz App Store,
ktora čita naše pakety.

1. Instaluj **Keyman** iz App Store.
2. Otvori paket **`keyman/isv_latin.kmp`** na svojem uredžaju —
   Safari, e-pošta abo AirDrop. Vybere se „Open in Keyman".
3. **Nastrojenja → Osnovne → Tipkovnica → Tipkovnici → Dodaj → Keyman.**

**Litery:** na telefonu ne jest AltGr, zato **drži tipku** — `c` → **č**, `s` → **š**,
`z` → **ž**, `e` → **ě**. Velike litery: najprvo Shift, potom drži.

⚠ **Na iOS 16 i novšem trěba vključiti „Allow Full Access"** (Nastrojenja → Ogolne →
Tipkovnica → Tipkovnici → Keyman), inače sistemna tipkovnica jest prazdna — bez
klavišev. To jest grěška iOS, ne Keymana; v samoj aplikaciji Keyman vse rabotaje i bez
togo. Gl. [KB0109](https://help.keyman.com/knowledge-base/kb0109).

Toj že paket rabotaje takože na Androidu, Windowsu, macOS i Linuxu črěz Keyman.

Zbudovanje iz izvora:

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

Litery sut pod **Option** (Option+C → č, itd.).

---

## Linux

Ne dupliciramo — upstream jur izdal gotove pakety. Gl. `linux/README.md`.

---

## Slovnik za pisanje gestami

Slovnik `.dict` za **[HeliBoard](https://github.com/Helium314/HeliBoard)** dava na telefonu
**pisanje gestami po medžuslovjansky** — s podpovědjami i avtokorekturoju, čego Unexpected
Keyboard ne umě.

**253 273 slov**, s formami. Forma jest ključna: pri svaipu prěvlěčeš palcem po slově, ktoro
faktično pišeš (`slovami`), ne po lemate (`slovo`). Bez form slovnik trafja rědko.

### Instalacija

1. **HeliBoard** — iz [F-Droid](https://f-droid.org/packages/helium314.keyboard/) abo
   [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
2. **Slovnik** — odpri `main_isv.dict` v menedžeru fajlov i izberi HeliBoard, abo v jego
   nastrojenjah: *Jezyky i ukladji → jezyk → `+` pri slovnikah.*
3. **Pisanje gestami** — HeliBoard **ne** dostavja gesturny motor (Google nikogda ne otvoril
   jego izvor). Trěba sam side-loadovati `libjni_latinimegoogle.so`, iztegneny iz Gboard APK:
   *Nastrojenja → Napredno → Naloži biblioteku gesturnogo pisanja.* To jest jediny ručny
   krok, i to jest ograničenje Google-kodu, ne naše.
4. **Ukladj** — MS latinica; osnova QWERTY, litery `č š ž ě` črěz držanje klaviša.

---

## Vklad — nove slova

Ovo jest **dobrovoljno (opt-in)** i děje se **na tvojem telefone.** Standardno ne ide iz
telefona ničto.

Kogda swipe ne trafja, pišeš slovo palcem i dodavaješ ho do slovnika telefona. Ako
dozvoliš, taka slova mogut dojti do **občinskogo slovnika** — za vsih.

**Kako to jest zaščiteno:**

- **Psevdonim, nikogda ime.** Identifikacija = psevdonim, ktory sam vybereš.
- **Filtr na telefone.** Imena, cifry, obce litery ne opuščajut telefon — odsějut se prěd
  vysylanjem.
- **Vklad jest vratny.** Odzoveš zgodu — tvoja slova vypadajut.
- **Samo zbirno.** Dane se raportujut samo zbirno, nikogda po osobě.
- **Bez konta, bez e-pošty, bez telefona.**

**Neobvezno (samo za jezykovo badanje):** može dodati **vozrast** (`<20` / `20–34` /
`35–54` / `55+`) i **rodny jezyk** — orientaciono, za badanje pochodženja slovnika. Ne
trěba togo davati.

### Važno: rang, ne kanon

Zbirane slova dajut **poredok popularnosti** — pokazujut, čego ljudem trěba i čego ne
najdut. **Ne stavajut avtomatično oficialnym slovnikom.** Kanon ostavaje pri jezykovom
avtoritetu občiny medžuslovjanskogo jezyka. Ova sistema **měri i prědlagaje — ne zaměnjaje
ho.**

---

## Licencije

Vse pod **MIT.**

- Ukladji `kbdmslat` (latinica) i `kbdmskir` (cyrilica) izvorno iz projekta
  [medzuslovjansky/keyboards](https://github.com/medzuslovjansky/keyboards). Avtorske prava
  pri avtorah: **Adam Gola, Roberto Lombino jr.**
- Ostatok koda — runy, android ukladji, `docs/` — vlastno, MIT.
- **Slovnik (dane):** samo formy + čestoty (bez značenj/prěkladov) — vlastničstva nad leksikalnymi danymi ne tvrdimo. Podrobno: [`keyman/radoslove.isv.wordlist/LICENSE.md`](keyman/radoslove.isv.wordlist/LICENSE.md).

Polny tekst: [`LICENSE`](LICENSE).
