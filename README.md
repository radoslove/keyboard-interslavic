# Medžuslovjanske klaviatury · Interslavic Keyboards

**Klaviatury za medžuslovjansky jezyk — i slovnik za pisanje gestami.**
*Keyboards for the Interslavic language — and a swipe-typing dictionary for it.*

Tu možeš daunlodovati i instalovati klaviatury za medžuslovjansky jezyk na
Windows, Android, iPhone/iPad, macOS i Linux. Slovnik za pisanje gestami imaje
**253 273 slov** — s formami, ne samo lematami.
*Here you can download and install Interslavic keyboards for Windows, Android,
iPhone/iPad, macOS and Linux. The swipe-typing dictionary carries **253 273 words** —
inflected forms, not just lemmas.*

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

Vsi razklady tu imajut je na tyh že klavišah — različny jest samo dostup, ne litery. Vsaka
platforma imaje drugy dostup, a telefon ne imaje nikakogo.
*Every layout here places them on the same base keys, so the habit carries across
platforms. Only the reach differs, because each platform has a different spare modifier —
and a phone has none at all.*

| Platforma / Platform | Dostup / How you reach them |
|---|---|
| Windows | **AltGr** + litera |
| macOS | **Option** + litera |
| Android | naša klaviatura: **dolgy pritisk** abo **svajp vgoru**; Unexpected Keyboard: **svajp v desny vrhny kut** klaviše / our keyboard: long-press or swipe up; Unexpected Keyboard: swipe to the top-right corner of the key |
| iPhone / iPad | **svajp vgoru** / swipe up |
| Linux | gl. `linux/README.md` |

Digrafy `dž`, `lj`, `nj` pišeš kako dvě klaviše (`d`+`ž` …) — vlastnoj klaviše ne imajut.
*Digraphs `dž`, `lj`, `nj` are typed as two keys (`d`+`ž` …) — they have no key of their
own. A ligature key would add a failure mode for no gain.*

> Razklady imajut **samo** standardnu ortografiju — četyri litery vyše plus interpunkciju
> (`„ ” – —`). Litery razšireneho alfabeta ne sut ni na jednom razkladu: ne pišut se v
> novom tekste, i klaviša za njih by samo zvala grěšky. Polna karta znakov:
> `docs/ms-latin-table.md`.
> *The layouts carry **only** standard orthography — the four letters above plus
> punctuation (`„ ” – —`). The extended alphabet is deliberately on no layout: it is not
> written in new text, and a key for it would only invite mistakes. Full character map:
> `docs/ms-latin-table.md`.*

---

## Windows

Medžuslovjanska latinica jest naša i instaluje se skriptom.
*The Interslavic Latin layout is ours and installs with a script.*

| Razklad / Layout | Instalacija / How to install |
|---|---|
| Medžuslovjanska **latinica** (Latin) | `windows/installers/kbdmsstd/` → `install.ps1` (PowerShell, kako administrator) |

Razklad se sam dopiše do spisa jezykov, pod polsky — tako stoji pri tvojih klaviaturah i
ne dodava fantomnogo jezyka. Prěključanje razkladov: **Win + Space.**
*The layout registers itself under Polish, so it appears next to your existing keyboards
rather than adding a phantom language. Switch layouts with **Win + Space.***

Odinstalovanje: `uninstall.ps1` v tom že katalogu. ⚠ Odstranjenje razklada iz spisa jezykov
Windows jego **ne** odinstaluje — koristi skript.
*To remove it later, `uninstall.ps1` sits in the same folder. ⚠ Removing a layout from the
Windows language list does **not** uninstall it — use the script.*

### Litery MS na klaviaturě · The MS letters

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

### Klaviatura za Android · Android keyboard

Vlastna klaviatura za Android: pisanje gestami, prědloženja iz slovnika, latinica, kirilica
i glagolica (klaviša ABC / АБВ / ⰀⰁⰂ), světla i temna tema. Takože jezyky:
medžuskandinavsky, poljsky, norvežsky i anglijsky — 🌐 prěključaje medžu nimi. Versija 3.4
ide do F-Droid; novějše versije sut ješče probne.
*Our own Android keyboard: swipe typing, suggestions from the dictionary, Latin, Cyrillic
and Glagolitic (the ABC / АБВ / ⰀⰁⰂ key), light and dark theme. Also Interscandinavian,
Polish, Norwegian and English — 🌐 switches between them. Version 3.4 is on its way to
F-Droid; newer versions are still test builds.*

Izvor: [`android-app/`](android-app/).
*Source: [`android-app/`](android-app/).*

### Drugy put · Another way

Ako hočeš ostati pri inoj klaviaturě, dvě otvorjene aplikacije takože pokryvajut vse.
*If you prefer to stay with another keyboard, two open-source apps also cover everything.*

### 1. Razklady — Unexpected Keyboard

**[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard)** (GPL-3.0, F-Droid +
Play Store) čita vlastne razklady v XML. Naš razklad:
*Unexpected Keyboard (GPL-3.0, F-Droid and Play Store) loads custom XML layouts. Ours:*

| Fajl / File | Razklad / Layout |
|---|---|
| `android/isv_latin.xml` | latinica / Latin |

**Instalacija / Installation:**
1. Instaluj Unexpected Keyboard (F-Droid abo Play Store).
   *Install Unexpected Keyboard (F-Droid or Play Store).*
2. Vključi go: **Settings → System → Languages & input → On-screen keyboard.**
   *Enable it: **Settings → System → Languages & input → On-screen keyboard.***
3. V aplikaciji: ikona ⚙ → **Add an alternate layout → Custom layout.**
   *In the app: gear icon → **Add an alternate layout → Custom layout.***
4. Vstavi sodržanje izbranogo fajla `.xml` i potvrdi.
   *Paste the contents of the chosen `.xml` and confirm.*

Sloj AltGr iz Windows tu odpovědaje **svajpu v desny vrhny kut** klaviše (`ne`); cifry
sut svajp v lěvy vrhny kut (`nw`).
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

iOS ne dopušča fajlov razklada — tam klaviatura jest cěla aplikacija. Zato idemo črěz
**[Keyman](https://keyman.com/iphone-and-ipad/)** — bezplatna aplikacija iz App Store,
ktora čita naše pakety.
*iOS has **no keyboard-layout file format** — a third-party keyboard there is a whole app.
So instead of a layout we ship a package for **Keyman**, a free App Store app that loads
it.*

1. Instaluj **Keyman** iz App Store.
   *Install **Keyman** from the App Store.*
2. Otvori paket **`keyman/isv_latin.kmp`** na svojem iPhonu abo iPadu — Safari, e-pošta abo
   AirDrop. Daunlodovanje ne jest instalovanje: v Keymane trěba vybrati
   **Install From File** i pokazati na paket.
   *Open **`keyman/isv_latin.kmp`** on the device — Safari, e-mail or AirDrop. Downloading
   is not installing: in Keyman choose **Install From File** and pick it.*
3. **Nastrojky → Osnovne → Klaviatura → Klaviatury → Dodaj → Keyman.**
   *Settings → General → Keyboard → Keyboards → Add New Keyboard → Keyman.*

### ⚠ Na iOS 16 i novějšem trěba vključiti „Allow Full Access"

**Nastrojky → Osnovne → Klaviatura → Klaviatury → Keyman → Allow Full Access.**
*Settings → General → Keyboard → Keyboards → Keyman → Allow Full Access.*

Bez togo klaviatura v drugyh aplikacijah jest **cělkom prazdna — bez klaviš**, a v samoj
aplikaciji Keyman vse rabotaje dalje; zato to vygledaje kako slomjeny paket. To jest
grěška iOS, ne Keymana. Gl. [KB0109](https://help.keyman.com/knowledge-base/kb0109).
*Without it the system keyboard renders **completely blank — no keys at all**, while
everything still works inside the Keyman app itself, which makes the symptom read as a
broken package. This is an iOS compatibility bug, not a Keyman one; see
[KB0109](https://help.keyman.com/knowledge-base/kb0109).*

### Kako pisati litery · Typing the letters

Na telefonu ne jest AltGr, zato dělaješ **svajp vgoru** od `c s z e` — `c` → **č**,
`s` → **š**, `z` → **ž**, `e` → **ě**. Vsaky směr vgoru rabotaje, ne trěba cěliti.
Velike litery: najprvo Shift, potom svajp.
***Swipe up** from `c s z e` — `c` → **č**, `s` → **š**, `z` → **ž**, `e` → **ě**. Any
upward direction works, so you do not have to aim. For capitals press Shift first, then
swipe.*

Drži klavišu — to jest drugy sposob, i imenno jego Keyman opisuje, ale na iOS rabotaje
slabo: Keyman čekaje, že prěsuneš prst na popup prěd tym, kako go pustiš, a popup jest za
vsaku klavišu na drugom městě — pustiš gdě-inde i dostaneš golu literu. To ne jest problem
našego razklada: klaviatura EuroLatin od samogo Keymana rabotaje tako že. Svajp togo
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
**Nastrojky → Klaviatura → Izvory vhoda → + → Others**.
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

**253 273 slov**, s formami. Forma jest ključna: pri svajpu tegneš prstom po slově, ktoro
faktično pišeš (`slovami`), ne po lemate (`slovo`). Bez form slovnik trafja rědko.
***253 273 words, with inflection.** The forms matter: when you swipe, you drag across the
word you actually write (`slovami`), not the lemma (`slovo`). Without forms it rarely hits.*

1. **HeliBoard** — iz [F-Droid](https://f-droid.org/packages/helium314.keyboard/) abo
   [GitHub releases](https://github.com/Helium314/HeliBoard/releases).
   *From F-Droid or GitHub releases.*
2. **Slovnik** — otvori `dictionary/main_isv.dict` i izberi HeliBoard, abo v jego
   nastrojkah: *Languages & Layouts → language → `+` pri slovnikah.*
   ***Dictionary** — open `dictionary/main_isv.dict` in a file manager and pick HeliBoard,
   or in its settings: Languages & Layouts → language → `+` next to dictionaries.*
3. **Pisanje gestami** — HeliBoard ne imaje mehanizma za pisanje gestami (Google go nikogda
   ne publikoval kako otvorjeny kod). Trěba ručno dodati `libjni_latinimegoogle.so`,
   vzety iz APK Gboard: *Settings → Advanced → Load gesture typing library.* To jest
   jediny ručny krok, i to jest ograničenje koda Google, ne naše.
   ***Glide typing** — HeliBoard does **not** ship the gesture engine (Google never
   open-sourced it). You must side-load `libjni_latinimegoogle.so`, extracted from a Gboard
   APK: Settings → Advanced → Load gesture typing library. This is the one manual step,
   and it is a Google-code limitation, not ours.*
4. **Razklad** — medžuslovjanska latinica; osnova QWERTY, litery `č š ž ě` na dolgom pritisku.
   ***Layout** — MS Latin; base QWERTY, the letters `č š ž ě` via long-press.*

### iPhone i iPad — prědskazanje slov · word prediction

iOS ne dava klaviaturam nikakogo API za pisanje gestami, zato svajp tam ne jest dostupny
i `.dict` vyše na iOS ne rabotaje. Dostupno jest prědskazanje: instaluj
`keyman/radoslove.isv-latn.wordlist.model.kmp` v Keymane i dostaneš podpovědji i
avtokorekturu nad klavišami.
*iOS gives third-party keyboards **no gesture-typing API at all**, so swipe typing is not
reachable there and the `.dict` above is useless on iOS. What is reachable is prediction:
install `keyman/radoslove.isv-latn.wordlist.model.kmp` in Keyman and you get word
suggestions and autocorrect above the keyboard.*

**39 777 form**, a ne polnyh 253 273. Polny spisok daje trie na 33 MB — vyše togo, čto
klaviatura na iOS može držati v pameti, i klaviatura by pala. Slova, ktore sut specifične
za medžuslovjansky jezyk, ostajut bez ogleda na častotu, ibo korpus jedva zna slova, o
ktoryh jest sam projekt.
***39 777 forms** rather than the full 253 273. The complete list compiles to a 33 MB trie,
past what an iOS keyboard extension can hold — it would take the keyboard down with it.
Vocabulary specific to Interslavic is kept regardless of frequency, because the underlying
corpus barely knows the words this project is about.*

### Vlastna aplikacija za iPhone — probna versija · Own iPhone app — test build

Bez API ot iOS jest možno pisati gestami vo vlastnoj aplikaciji, i taka aplikacija jest
v izvoru: [`ios/`](ios/). V njej sut dvě klaviatury — medžuslovjanska i poljska — i
prěključiti možno s 🌐. Ona ne prosi „Full Access" i ničego ne posylaje. Ješče ne jest
v App Store; do togo časa služi vyše opisany put črěz Keyman.
*Without an iOS API, swipe typing is still possible in an app's own keyboard, and that app
is in this repository: [`ios/`](ios/). It carries two keyboards — Interslavic and Polish —
switched with 🌐. It does not ask for Full Access and sends nothing. It is not in the App
Store yet; until then the Keyman route above stays.*

---

## Slova, ktorogo ne jest · Missing a word

Medžuslovjansky slovnik ne ima vsego. Ako pišeš slovo i jego ne jest — daj nam znati.
To jest cěly mehanizm dnes:
*Interslavic vocabulary is not complete. If you reach for a word and it is not there,
tell us — that is the entire mechanism today:*

**[GitHub Issues](https://github.com/radoslove/keyboard-interslavic/issues)** — slovo i
čto jesi hotěl rekti.
*Open a GitHub issue with the word and what you meant by it.*

Potom slovo ide do reda za prěgled i jest prověrjeno v slovniku: **slovnik go jur ima**,
**jest praviljno izvodjenje** ili **faktično ne jest**. Ostavše slova idut do lokalnogo
sloja pri glavnom slovniku.
*It then joins a review queue and is checked against the Interslavic dictionary, which
answers one of three ways — already attested, a regular derivation of something attested,
or genuinely absent. What survives review is added to a local layer that rides alongside
the main dictionary.*

### Rang, ne kanon · Ranking, not canon

Sbirane slova pokazujut, čego ljudem trěba i čego ne najdut. **Ne stavajut avtomatično
oficialnym slovnikom.** Kanon ostavaje pri jezykovom avtoritetu občiny. Ova sistema
**měri i prědlagaje — ne zaměnjaje go.**
*Collected words show what people need and cannot find. They do **not** automatically
become official vocabulary. The canon stays with the Interslavic community's language
authority. This system **measures and proposes — it does not replace it.***

### Planovano, ne napravjeno · Planned, not built

Sbiranje slov iz samoj klaviatury — na telefonu, dobrovoljno, pod psevdonimom, s filtrom
na imena i cifry, s pravom odstupiti, i samo sborno. Na Androidu to jest v
probnyh versijah (standardno izključeno; ničto ne odhodi bez tvojego izvoza). Na iOS
ješče ne jest.
*Collecting missing words from the keyboard itself — on device, opt-in, pseudonymous, with
names and digits filtered out before anything is sent, withdrawable, and reported only in
aggregate. On Android it is in the test builds (off by default; nothing leaves without your
export). On iOS it does not exist yet.*

⚠ Na iOS to bude v **aplikaciji**, ne v klaviaturě: klaviatura bez „Full Access" ne
može ni do seti, ni do občego kontejnera s svojeju aplikacijeju. Imenno ta izolacija jest
tym, čto ova klaviatura oběčaje — zato ostavaje, a prěnosi se zbiranje, ne obečanje.
*⚠ On iOS this will live in the **app**, not the keyboard: without "Full Access" a keyboard
extension can reach neither the network nor a shared container with its own app. That
isolation is exactly what this keyboard advertises, so it stays — and the collection moves
rather than the promise.*

---

## Blagodarjenje · Thanks

Adam Gola i Roberto Lombino jr. sut sdělali razklady klaviatury za Windows uže v 2013
godu, davno prěd tym projektom, i oficialny veb-sajt medžuslovjanskogo jezyka ješče
ukazyvaje na njih. Toj projekt jest izrasl iz jihnoj raboty: naš razklad za Windows
jest osnovany na skeletu jihnogo `KBDMSLAT.klc`. Jihne razklady:
[tyflonet.com/siciliano/klaviatury](https://tyflonet.com/siciliano/klaviatury/). Hvala vam!
*Adam Gola and Roberto Lombino jr. made Interslavic keyboard layouts for Windows back in
2013, long before this project, and the official Interslavic website still links to them.
This project grew out of their work: our Windows layout is built on the skeleton of their
`KBDMSLAT.klc`. Their layouts:
[tyflonet.com/siciliano/klaviatury](https://tyflonet.com/siciliano/klaviatury/). Thank you!*

---

## Licencije · Licenses

Kod i vlastne razklady klaviatury pod **MIT.** *Code and original layouts under **MIT.***

- Razklad klaviatury `kbdmslat` (latinica) izvorno iz
  [tyflonet.com/siciliano/klaviatury](https://tyflonet.com/siciliano/klaviatury/) (2013).
  Avtorske prava pri avtorah: **Adam Gola, Roberto Lombino jr.** Licencija MIT togo
  projekta jego ne obsegaje; licencija originalov ješče ne jest potvrdžena.
  *The `kbdmslat` (Latin) layout comes from
  [tyflonet.com/siciliano/klaviatury](https://tyflonet.com/siciliano/klaviatury/) (2013).
  Copyright with the authors: **Adam Gola, Roberto Lombino jr.** It is not covered by this
  repository's MIT licence; the licence of the originals is not yet confirmed.*
- Kirilica i glagolica: pravila transliteracije iz
  [medzuslovjansky/js-utils](https://github.com/medzuslovjansky/js-utils) (MIT),
  gl. [`docs/script-tables.md`](docs/script-tables.md).
  *Cyrillic and Glagolitic: transliteration rules from
  [medzuslovjansky/js-utils](https://github.com/medzuslovjansky/js-utils) (MIT); see
  [`docs/script-tables.md`](docs/script-tables.md).*
- Ostatok — android razklady, `docs/` — vlastno, MIT.
  *The rest — Android layouts, `docs/` — original, MIT.*
- Slovnik: [`android-app/DICTIONARY_DATA.md`](android-app/DICTIONARY_DATA.md).
  *The dictionary data is not under MIT; see
  [`android-app/DICTIONARY_DATA.md`](android-app/DICTIONARY_DATA.md).*
- Poljsky slovnik (`dictionary/main_pl.combined`, `ios/Keyboard/Resources/pl_swipe.bin`)
  jest izvedeny iz [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
  (CC BY-SA 4.0) i [PoliMorf](https://zil.ipipan.waw.pl/PoliMorf) (BSD-2), zato jest pod
  **CC BY-SA 4.0**, ne MIT. Gl. [`dictionary/sources_pl/README.md`](dictionary/sources_pl/README.md).
  *The Polish wordlist is derived from FrequencyWords (CC BY-SA 4.0) and PoliMorf (BSD-2),
  so it is under **CC BY-SA 4.0**, not MIT; see
  [`dictionary/sources_pl/README.md`](dictionary/sources_pl/README.md).*

Polny tekst: [`LICENSE`](LICENSE).
*Full text: [`LICENSE`](LICENSE).*

Avtorsko pravo © 2026 Radosław Kaczmarczyk. Licencija MIT obsegaje kod i vlastne
razklady klaviatury, ne ikonu i ne identifikatory aplikacije (`com.radoslove.*`).
Kopija projekta (fork) jest vitana, ale pod svojeju ikonoju. Oficialne versije sut samo te, ktore avtor publikuje iz togo projekta.
*Copyright © 2026 Radosław Kaczmarczyk. The MIT licence covers the code and the original
layouts, not the icon or the app identifiers (`com.radoslove.*`). A fork is welcome, but
with its own icon. The official builds are the ones published from this
repository and by this author.*
