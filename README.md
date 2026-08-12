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

## Litery · The letters

Medžuslovjansky jezyk v standardnoj ortografiji potrěbuje četyri litery:
*Interslavic in standard orthography needs exactly four letters an ordinary Polish or US
keyboard cannot produce:*

**č š ž ě**

Vsi ukladji tu imajut je na tyh že klavišah — različny jest samo dostup, ne litery. Vsaka
platforma imaje drugy dostup, a telefon ne imaje nikakogo.
*Every layout here places them on the same base keys, so the habit carries across
platforms. Only the reach differs, because each platform has a different spare modifier —
and a phone has none at all.*

| Platforma / Platform | Dostup / How you reach them |
|---|---|
| Windows | **AltGr** + litera |
| macOS | **Option** + litera |
| Android | svaip v desny gorny ugol klaviša |
| iPhone / iPad | **prěvlečeš palcem v goru** / swipe up |
| Linux | gl. `linux/README.md` |

Digrafy `dž`, `lj`, `nj` pišeš kako dva klavišy (`d`+`ž` …) — vlastnogo klaviša ne imajut.
*Digraphs `dž`, `lj`, `nj` are typed as two keys (`d`+`ž` …) — they have no key of their
own. A ligature key would add a failure mode for no gain.*

> Ukladji imajut **samo** standardnu ortografiju — četyri litery vyše plus interpunkciju
> (`„ ” – —`). Litery razšireneho alfabeta ne sut ni na jednom ukladji: ne pišut se v
> novom tekste, i klaviš za nje by samo zval grěšky. Polna karta znakov:
> `docs/ms-latin-table.md`.
> *The layouts carry **only** standard orthography — the four letters above plus
> punctuation (`„ ” – —`). The extended alphabet is deliberately on no layout: it is not
> written in new text, and a key for it would only invite mistakes. Full character map:
> `docs/ms-latin-table.md`.*

---

## Windows

Instalatory za tri ukladji — ale ne instalujut se jednako: latinica jest naša i ide kako
skript, ostatne dvě sut gotove pakety iz upstreama.
*Three layouts, but they do not install the same way — the Latin one is ours and ships as
scripts, the other two are upstream packages with an installer.*

| Ukladj / Layout | Instalacija / How to install |
|---|---|
| Medžuslovjanska **latinica** (Latin) | `windows/installers/kbdmsstd/` → `install.ps1` (PowerShell, kako administrator) |
| Medžuslovjanska **cyrilica** (Cyrillic) | `windows/installers/kbdmskir/kbdmskir.exe` |
| **Runy** (runic) | `windows/installers/runy_5/setup.exe` |

Ukladj se sam dopiše do liste jezykov, pod polsky — tako stoji pri tvojih tipkovnicah i
ne dodava fantomnogo jezyka. Prěklučanje ukladjev: **Win + Space.**
*The layout registers itself under Polish, so it appears next to your existing keyboards
rather than adding a phantom language. Switch layouts with **Win + Space.***

Odinstalovanje: `uninstall.ps1` v tom že katalogu. ⚠ Odstranjenje ukladja iz listy jezykov
Windows jego **ne** odinstaluje — koristi skript.
*To remove it later, `uninstall.ps1` sits in the same folder. ⚠ Removing a layout from the
Windows language list does **not** uninstall it — use the script.*

### Litery MS na tipkovnici · The MS letters

Specifične litery medžuslovjanskogo jezyka sut pod **AltGr**:
*The letters specific to Interslavic sit under **AltGr**:*

| AltGr + | Rezultat / gives |
|---|---|
| `c` | **č** |
| `s` | **š** |
| `z` | **ž** |
| `e` | **ě** |

Interpunkcija jest takože pod AltGr:
*Punctuation lives on AltGr too:*

| AltGr + | Rezultat / gives |
|---|---|
| `;` | **„ “** |
| `'` | **’ ”** |
| `-` | **– —** |

---

## Android

Na Androidu ne trěba modovanogo APK — dvě otvorjene aplikacije pokryvajut vse.
*On Android you need no modded APK — two open-source apps cover everything.*

### 1. Ukladji — Unexpected Keyboard

**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** (GPL-3.0, F-Droid +
Play Store) čita vlastne ukladji v XML. Tri ležat tu:
*Unexpected Keyboard (GPL-3.0, F-Droid and Play Store) loads custom XML layouts. Three are
provided here:*

| Fajl / File | Ukladj / Layout |
|---|---|
| `android/isv_latin.xml` | latinica / Latin |
| `android/isv_cyrillic.xml` | cyrilica / Cyrillic |
| `android/isv_runic.xml` | runy / Runic |

**Instalacija / Installation:**
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
*iOS has **no keyboard-layout file format** — a third-party keyboard there is a whole app.
So instead of a layout we ship a package for **Keyman**, a free App Store app that loads
it.*

1. Instaluj **Keyman** iz App Store.
   *Install **Keyman** from the App Store.*
2. Otvori paket **`keyman/isv_latin.kmp`** na svojem urędžaju — Safari, e-pošta abo
   AirDrop. Daunlodovanje ne jest instalovanje: v Keymane trěba vybrati
   **Install From File** i pokazati na paket.
   *Open **`keyman/isv_latin.kmp`** on the device — Safari, e-mail or AirDrop. Downloading
   is not installing: in Keyman choose **Install From File** and pick it.*
3. **Nastrojenja → Osnovne → Tipkovnica → Tipkovnici → Dodaj → Keyman.**
   *Settings → General → Keyboard → Keyboards → Add New Keyboard → Keyman.*

### ⚠ Na iOS 16 i novšem trěba vklučiti „Allow Full Access"

**Nastrojenja → Osnovne → Tipkovnica → Tipkovnici → Keyman → Allow Full Access.**
*Settings → General → Keyboard → Keyboards → Keyman → Allow Full Access.*

Bez togo tipkovnica v drugyh aplikacijah jest **cělkom prazdna — bez klaviš**, a v samoj
aplikaciji Keyman vse rabotaje dalje; zato to vygledaje kako slomjeny paket. To jest
grěška iOS, ne Keymana. Gl. [KB0109](https://help.keyman.com/knowledge-base/kb0109).
*Without it the system keyboard renders **completely blank — no keys at all**, while
everything still works inside the Keyman app itself, which makes the symptom read as a
broken package. This is an iOS compatibility bug, not a Keyman one; see
[KB0109](https://help.keyman.com/knowledge-base/kb0109).*

### Kako pisati litery · Typing the letters

Na telefonu ne jest AltGr, zato **prěvlečeš palcem v goru** od `c s z e` — `c` → **č**,
`s` → **š**, `z` → **ž**, `e` → **ě**. Vsaky směr v goru rabotaje, ne trěba cěliti.
Velike litery: najprvo Shift, potom prěvlečeš.
***Swipe up** from `c s z e` — `c` → **č**, `s` → **š**, `z` → **ž**, `e` → **ě**. Any
upward direction works, so you do not have to aim. For capitals press Shift first, then
swipe.*

Drži tipku — to jest drugy sposob, i imenno jego Keyman opisuje, ale na iOS rabotaje
slabo: Keyman čekaje, že prěsuneš prst na popup prěd tym, kako ga pustiš, a popup jest za
vsaky klaviš na drugom městu — pustiš gdě-inde i dostaneš golu literu. To ne jest problem
našego ukladja: tipkovnica EuroLatin od samogo Keymana rabotaje tako že. Svaip togo
izběgaje.
*Holding the key also works and is the gesture Keyman documents — but on iOS it is
unreliable: Keyman expects you to slide onto the popup before releasing, the popup sits in
a different place for every key, and releasing anywhere else gives you the plain letter.
This is not specific to our layout; Keyman's own EuroLatin keyboard behaves the same way.
Swiping avoids the problem entirely.*

Toj že paket rabotaje takože na Androidu, Windowsu, macOS i Linuxu črěz Keyman.
*The same package also installs on Android, Windows, macOS and Linux through Keyman.*

### Zbudovanje iz izvora · Building from source

```sh
npm install -g @keymanapp/kmc
python3 build_keyman.py                          # .kmn + touch layout
python3 build_icon.py                            # ikona / keyboard icon
kmc build keyman/isv_latin/isv_latin.kpj         # -> keyman/isv_latin/build/
cp keyman/isv_latin/build/isv_latin.kmp keyman/  # publikovany paket

python3 build_keyman_wordlist.py                 # slovnik za podpovědji
kmc build keyman/radoslove.isv-latn.wordlist/radoslove.isv-latn.wordlist.kpj
cp keyman/radoslove.isv-latn.wordlist/build/*.model.kmp keyman/
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

## Slovnik · Dictionary

### Android — pisanje gestami · swipe typing

Slovnik `.dict` za **[HeliBoard](https://github.com/Helium314/HeliBoard)** dava na telefonu
**pisanje gestami po medžuslovjansky** — s podpovědjami i avtokorekturoju, čego Unexpected
Keyboard ne umě.
*The `.dict` dictionary for **HeliBoard** gives you **Interslavic swipe typing** on your
phone — with suggestions and autocorrect, which Unexpected Keyboard cannot do.*

**253 273 slov**, s formami. Forma jest ključna: pri svaipu prěvlečeš palcem po slově, ktoro
faktično pišeš (`slovami`), ne po lemate (`slovo`). Bez form slovnik trafja rědko.
***253 273 words, with inflection.** The forms matter: when you swipe, you drag across the
word you actually write (`slovami`), not the lemma (`slovo`). Without forms it rarely hits.*

1. **HeliBoard** — from [F-Droid](https://f-droid.org/packages/helium314.keyboard/) or
   [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
2. **Dictionary** — open `dictionary/main_isv.dict` in a file manager and pick HeliBoard,
   or in its settings: *Languages & Layouts → language → `+` next to dictionaries.*
3. **Glide typing** — HeliBoard does **not** ship the gesture engine (Google never
   open-sourced it). You must side-load `libjni_latinimegoogle.so`, extracted from a Gboard
   APK: *Settings → Advanced → Load gesture typing library.* This is the one manual step,
   and it is a Google-code limitation, not ours.
4. **Layout** — MS Latin; base QWERTY, the letters `č š ž ě` via long-press.

### iPhone i iPad — prědskazyvanje slov · word prediction

iOS ne dava tipkovnicam nikakogo API za pisanje gestami, zato svaip tam ne jest dostupny
i `.dict` vyše na iOS ne rabotaje. Dostupno jest prědskazyvanje: instaluj
`keyman/radoslove.isv-latn.wordlist.model.kmp` v Keymane i dostaneš podpovědji i
avtokorekturu nad klavišami.
*iOS gives third-party keyboards **no gesture-typing API at all**, so swipe typing is not
reachable there and the `.dict` above is useless on iOS. What is reachable is prediction:
install `keyman/radoslove.isv-latn.wordlist.model.kmp` in Keyman and you get word
suggestions and autocorrect above the keyboard.*

**39 777 form**, a ne polnyh 253 273. Polny spisok daje trie na 33 MB — vyše togo, čto
tipkovnica na iOS može držati v pameti, i tipkovnica by pala. Slova, ktore sut specifične
za medžuslovjansky jezyk, ostajut bez ogleda na častotu, ibo korpus jedva zna slova, o
ktoryh jest sam projekt.
***39 777 forms** rather than the full 253 273. The complete list compiles to a 33 MB trie,
past what an iOS keyboard extension can hold — it would take the keyboard down with it.
Vocabulary specific to Interslavic is kept regardless of frequency, because the underlying
corpus barely knows the words this project is about.*

---

## Slova, ktorogo ne jest · Missing a word

Medžuslovjansky slovnik ne ima vsego. Ako pišeš slovo i jego ne jest — daj nam znati.
To jest cěly mehanizm dnes:
*Interslavic vocabulary is not complete. If you reach for a word and it is not there,
tell us — that is the entire mechanism today:*

**[GitHub Issues](https://github.com/radoslove/keyboard-interslavic/issues)** — slovo i
čto jesi hotěl rekti.
*Open a GitHub issue with the word and what you meant by it.*

Potom slovo ide do reda za pregled i jest prověrjeno v slovniku: **slovnik ho jur ima**,
**jest praviljno izvodjenje** ili **faktično ne jest**. Ostavše slova idut do lokalnogo
sloja pri glavnom slovniku.
*It then joins a review queue and is checked against the Interslavic dictionary, which
answers one of three ways — already attested, a regular derivation of something attested,
or genuinely absent. What survives review is added to a local layer that rides alongside
the main dictionary.*

### Rang, ne kanon · Ranking, not canon

Zbirane slova pokazujut, čego ljudem trěba i čego ne najdut. **Ne stavajut avtomatično
oficialnym slovnikom.** Kanon ostavaje pri jezykovom avtoritetu občiny. Ova sistema
**měri i prědlagaje — ne zaměnjaje ho.**
*Collected words show what people need and cannot find. They do **not** automatically
become official vocabulary. The canon stays with the Interslavic community's language
authority. This system **measures and proposes — it does not replace it.***

### Planovano, ne napravjeno · Planned, not built

Zbirane slov iz samoj tipkovnice — na urędžaju, dobrovoljno, pod psevdonimom, s filtrom
na imena i cifry, s pravom vytegnuti soglasje, i samo zbirno. Togo dosej ne jest — i tako
tu jest napisano.
*Collecting missing words from the keyboard itself — on device, opt-in, pseudonymous, with
names and digits filtered out before anything is sent, withdrawable, and reported only in
aggregate. None of that exists yet, and this section will say so until it does.*

⚠ Na iOS to budet v **aplikaciji**, ne v tipkovnici: tipkovnica bez „Full Access" ne
može ni do seti, ni do občego kontejnera s svojeju aplikacijeju. Imenno ta izolacija jest
tym, čto ova tipkovnica obečaje — zato ostavaje, a prěnosi se zbiranje, ne obečanje.
*⚠ On iOS this will live in the **app**, not the keyboard: without "Full Access" a keyboard
extension can reach neither the network nor a shared container with its own app. That
isolation is exactly what this keyboard advertises, so it stays — and the collection moves
rather than the promise.*

---

## Licencije · Licenses

Vse pod **MIT.** *Everything under **MIT.***

- Ukladji `kbdmslat` (latinica) i `kbdmskir` (cyrilica) izvorno iz projekta
  [medzuslovjansky/keyboards](https://github.com/medzuslovjansky/keyboards). Avtorske prava
  pri avtorah: **Adam Gola, Roberto Lombino jr.**
  *The `kbdmslat` (Latin) and `kbdmskir` (Cyrillic) layouts originate from
  medzuslovjansky/keyboards. Copyright with the authors: **Adam Gola, Roberto Lombino jr.***
- Ostatok — runy, android ukladji, `docs/`, slovnik — vlastno, MIT.
  *The rest — runic layouts, Android layouts, `docs/`, the dictionary — original, MIT.*

Polny tekst: [`LICENSE`](LICENSE).
*Full text: [`LICENSE`](LICENSE).*
