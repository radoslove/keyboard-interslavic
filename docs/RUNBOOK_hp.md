# RUNBOOK `hp` — weryfikacja windowsowej połowy pakietu Keyman

*Zlecenie dla instancji `dev-keyboard` pracującej **na `hp`**. Napisane 2026-08-09 na `mc`.*

---

## Kto to pisze i dlaczego na ślepo

Piszę z `mc`. **Nie mam wyjścia SSH na `hp`** — klucz ed25519 z `mc` nie jest wdrożony na
żadnej maszynie floty. Nie widzę, co u Ciebie stoi i co zdążyłeś zmienić.

**Stan `hp` był nieznany w chwili pisania.** Zakładam tylko tyle, że stawiasz tam środowisko
i że repo `keyboard-interslavic` masz albo będziesz miał. **Setup jest Twój — nie ma go w tym
dokumencie.** Żadnego node/kmc/pythona/JDK, żadnego klonowania. Dwa zestawy instrukcji na
jedną maszynę to konflikt, nie pomoc.

Ten dokument zawiera wyłącznie to, **czego nie masz skąd wiedzieć**, bo powstało dziś na `mc`.

---

## Po co to robimy — luka

`keyman/isv_latin.kmp` deklaruje w `store(&TARGETS)`:

```
windows macosx linux web iphone ipad androidphone androidtablet mobile desktop tablet
```

…a kompilator raportuje `platformSupport` dla wszystkich. **Testowany był wyłącznie iPad.**

Windowsowa połowa tego, co poszło do publicznego katalogu Keymana
(`keymanapp/keyboards#4092`, otwarte, czeka na opiekunów) **nigdy nie stanęła na Windowsie**.
To jest realne ryzyko wobec opiekunów i wobec ludzi, którzy to pobiorą. `hp` jest jedyną
maszyną, która może to zamknąć.

---

## Co jest w gicie, a co tylko na `mc` — ustaw sobie oczekiwania

| Miejsce | Wersja klawiatury |
|---|---|
| `main` = `f580dd7` | 1.2 |
| `app-popup-picker` = `182bc43` (wypchnięta) | **1.3** |
| drzewo robocze na `mc` | 1.5 — **11 plików niezacommitowanych**, z gita tego nie dostaniesz |

**To Cię nie blokuje.** Sprawdziłem na `mc`: reguły `RALT` w `isv_latin.kmn` są **identyczne**
w 1.3 i 1.5 — między tymi wersjami zmieniły się wyłącznie flicki, czyli warstwa dotykowa,
której Windows nie używa. Windows czyta z pakietu `isv_latin.kmx`, zbudowany z tych samych
reguł.

> Test z **1.3** jest pełnoprawnym testem windowsowej połowy. Nie czekaj na 1.5, nie proś o
> przesłanie niezacommitowanych plików. **W raporcie napisz `1.3`** — testujesz to, co masz.

Oznaczenia niżej: 🟢 tylko odczyt · 🟡 zmienia plik lokalny · 🔴 **decyzja ownera, nie Twoja**.

---

## 1 🟢 Ustal punkt wyjścia (2 minuty, zanim cokolwiek zainstalujesz)

```powershell
hostname
$Repo = 'C:\Projects\keyboard-interslavic'   # podmien na swoja realna sciezke
Set-Location $Repo
git rev-parse --abbrev-ref HEAD; git log --oneline -1; git status --porcelain
```

**Oczekiwane:** `hp`, HEAD na `182bc43` (albo `f580dd7`), `git status --porcelain` pusty.

**Zatrzymaj się i zgłoś, jeśli:** `hostname` ≠ `hp` (zły adresat dokumentu) **albo**
`git status --porcelain` cokolwiek zwraca, a nie wiesz, co to jest — na `mc` leży 11
nieprzejrzanych plików, nie chcemy drugiego niezależnego stosu zmian na `hp`.

```powershell
# czy KBDMSSTD jest zarejestrowany - potrzebne do punktu 2.5
Get-ChildItem 'HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts' |
  ForEach-Object { $p = Get-ItemProperty $_.PSPath
    [pscustomobject]@{ KLID=$_.PSChildName; Text=$p.'Layout Text'; File=$p.'Layout File' } } |
  Where-Object { $_.File -like 'KBDMS*' -or $_.Text -match 'Medz|Medž' }
```

**Oczekiwane:** wiersz `KBDMSSTD.dll` / `Medzuslovjansky (standard)` / KLID ~`a0000415`
(zbudowany na `hp` 2026-08-05). Zero wierszy = punkt 2.5 odpada, zapisz „nie dotyczy" —
**nie instaluj go w ramach tego zlecenia.**

```powershell
# czy Keyman juz stoi i czy nasz pakiet nie jest juz zainstalowany
Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
  'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*' -EA SilentlyContinue |
  Where-Object DisplayName -match 'Keyman' | Select-Object DisplayName, DisplayVersion, InstallLocation
Get-ChildItem "$env:LOCALAPPDATA\Keyman" -Recurse -Filter 'isv_latin.kmx' -EA SilentlyContinue
```

⚠ **Jeśli `isv_latin.kmx` już tam jest — zatrzymaj się i zgłoś**, z jaką wersją (`kmp.json`
obok). Instalowanie drugiej wersji tego samego ID na wierzch = nie będzie wiadomo, co
właściwie testowałeś.

---

## 2 — Weryfikacja pakietu `.kmp` na Windowsie ← **priorytet, główny sens dokumentu**

### 2.1 🟢 Najpierw zajrzyj do pakietu, potem instaluj

```powershell
$Kmp = "$Repo\keyman\isv_latin.kmp"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead($Kmp)
$z.Entries | Select-Object Name, Length
$r = New-Object IO.StreamReader($z.GetEntry('kmp.json').Open()); $r.ReadToEnd()
$r.Dispose(); $z.Dispose()
```

**Oczekiwane:** `isv_latin.kmx`, `isv_latin.js`, `isv_latin.kvk`, `isv_latin.ico`,
`welcome.htm`, `readme.htm`, `LICENSE.md`, `kmp.json`, `kmp.inf`; w `kmp.json`
`keyboards[0].version` = `1.3`.

⚠ **`isv_latin.kmx` to plik, którego używa Windows** (`.js` jest dla iOS/web). Brak `.kmx`
albo `Length` = 0 → windowsowa połowa pakietu jest pusta, **dalej nie idź, zgłoś to jako
wynik** — to byłoby dokładnie odkrycie, po które tu jesteśmy.

### 2.2 🔴 Instalacja Keymana i pakietu — potwierdzenie ownera

Instalacja oprogramowania i pakietu zmienia stan maszyny. **Zapytaj, nie rób z własnej
inicjatywy.** Do przekazania ownerowi: Keyman for Windows jest darmowy (`keyman.com/windows`);
nasz pakiet ma `store(&VERSION) '10.0'`, więc **wejdzie także na starsze wydania** — nie ma
potrzeby gonić najnowszego.

Po zgodzie: dwuklik na `keyman\isv_latin.kmp` w Eksploratorze → Keyman Configuration
potwierdza instalację. Wariant CLI dopiero po sprawdzeniu, że to wydanie ma taki przełącznik
(składnia bywa różna):

```powershell
& 'C:\Program Files (x86)\Keyman\Keyman Desktop\kmshell.exe' -?
```

**Oczekiwane:** *Medžuslovjansky (latinica)* na liście w Keyman Configuration.
**Zgłoś, jeśli:** brak wpisu po instalacji, albo komunikat o `minKeymanVersion` — zapisz go
**dosłownie**, to jest wynik testu, nie awaria.

### 2.3 🔴 Ustaw bazowy układ na **Polski (Programisty)** — na tym stoi cały test

⚠⚠ **Nie testuj pakietu, mając wybrany KBDMSSTD.** On sam daje `AltGr+C = č`. Wynik byłby
**nierozróżnialny** od działania pakietu i bezwartościowy.

Polski (Programisty) daje natywnie **inne** litery — stąd jednoznaczny dyskryminator:

| Klawisz | Polski (Programisty) sam | Z aktywnym pakietem Keyman |
|---|---|---|
| AltGr+C | **ć** | **č** |
| AltGr+S | **ś** | **š** |
| AltGr+Z | **ż** | **ž** |
| AltGr+E | **ę** | **ě** |
| AltGr+`;` | *(nic)* | **„** |

**Widzisz `č`, a nie `ć` → zadziałał nasz pakiet.** W raporcie **musi** być zapisane, na jakim
układzie bazowym testowałeś — bez tego wynik jest niepełny.

Klawiatura Keymana jest warstwą **na** układzie systemowym, nie zamiast niego: Win+Space
przełącza układ Windows, przełącznik Keymana na pasku — klawiaturę Keymana.

### 2.4 🟢 Właściwy test — **Notatnik**, ręcznie

Nie Word, nie VS Code: autokorekta i podmiana cudzysłowów sfałszują wiersze 3–4.

| # | Wciśnij | Oczekiwane |
|---|---|---|
| 1 | AltGr+C / S / Z / E | `č š ž ě` |
| 2 | Shift+AltGr+C / S / Z / E | `Č Š Ž Ě` |
| 3 | AltGr+`;` / `'` / `-` | `„` / `’` / `–` |
| 4 | Shift+AltGr+`;` / `'` / `-` | `“` / `”` / `—` |
| 5 | AltGr+T / D / L | **nic** (`ť ď ľ` celowo nieobecne) |
| 6 | `` ` `` i `~` | każdy za **jednym** naciśnięciem (zero martwych klawiszy) |
| 7 | zwykłe zdanie po angielsku | normalny tekst |
| 8 | **wyłącz** klawiaturę Keymana, powtórz #1 | `ć ś ż ę` |

**#5 dowodzi standard-only** (HOUSE_STYLE §1) — gdyby wyszły `ť ď ľ`, wraca wycofany blok
rozszerzony. **#8 to kontrola negatywna** — dowodzi, że wiersze 1–4 pochodziły od Keymana, a
nie od czegoś innego w systemie. Oba są obowiązkowe.

**Zgłoś, jeśli:** `ć` zamiast `č` przy włączonej klawiaturze (pakiet się nie aktywował albo
reguły `RALT` nie łapią) · **nic** z AltGr+C (reguła jest, modyfikator nie dopasowuje) ·
zawieszenie przy przełączaniu — to dokładnie ta klasa defektu, przez którą odrzuciliśmy układ
Goli, opisz jak najdokładniej.

### 2.5 🟢 Kolizja z zarejestrowanym `KBDMSSTD`

**Tylko jeśli punkt 1 wykazał KBDMSSTD.** Przełącz układ bazowy na *Medzuslovjansky
(standard)* przy **nadal włączonej** klawiaturze Keymana i powtórz wiersze 1–4.

**Oczekiwane:** te same znaki. Obie warstwy mapują to samo, nakładanie się jest nieszkodliwe.

**Zgłoś, jeśli:** znak podwojony (`čč`), inny znak, znikająca interpunkcja, zawieszenie
przełącznika. ⚠ **Nie „naprawiaj" tego odinstalowaniem czegokolwiek** — 🔴 decyzja ownera.
Usunięcie układu z listy języków i tak nie odinstalowuje DLL-a; to dwie niezależne warstwy.

---

## 3 🟡 Cross-check jednej tabeli znaków — `.klc` ↔ `.kmn`

Jedna tabela obowiązuje wszystkie platformy. Desktopową połowę implementują dwa pliki:
`windows/src/KBDMSSTD.klc` (→ DLL) i `keyman/isv_latin/source/isv_latin.kmn` (→ `.kmx`).
**Nic tego nie egzekwuje.** `build_keyman.py` twierdzi w komentarzu, że „mirroruje" `.klc`,
ale komentarz to nie test — a dziś ruszany był **wyłącznie** `build_keyman.py` (flicki,
bump do 1.5), więc rozjazd jest realną możliwością.

**Wynik z `mc` (2026-08-09, pliki z `app-popup-picker`): parzystość zachowana, 7/7.** Czyli
spodziewaj się zgodności; rozjazd byłby niespodzianką i sam w sobie wynikiem.

Tabela odniesienia, gdyby Pythona nie było:

| Klawisz | KLC pola 7/8 | Znaki | `.kmn` |
|---|---|---|---|
| `C` | `010d` / `010c` | č Č | `[RALT K_C]` / `[SHIFT RALT K_C]` |
| `S` | `0161` / `0160` | š Š | `[RALT K_S]` |
| `Z` | `017e` / `017d` | ž Ž | `[RALT K_Z]` |
| `E` | `011b` / `011a` | ě Ě | `[RALT K_E]` |
| `OEM_1` (`;`) | `201e` / `201c` | „ “ | `[RALT K_COLON]` |
| `OEM_7` (`'`) | `2019` / `201d` | ’ ” | `[RALT K_QUOTE]` |
| `OEM_MINUS` (`-`) | `2013` / `2014` | – — | `[RALT K_HYPHEN]` |

⚠ **Zanim uruchomisz skrypt:** polska konsola zabije Pythona na `print('č')`
(`UnicodeEncodeError`, cp852/cp1250):

```powershell
chcp 65001
$env:PYTHONIOENCODING = 'utf-8'
```

⚠ **Pułapka wbudowana w skrypt, nie upraszczaj jej z powrotem:** wiersze `.klc` mają
**10 albo 11 surowych komórek** (część ma dodatkowy tabulator po VK). Indeksowanie po
pozycji przesuwa znaki o kolumnę w lewo i po cichu daje bzdury. Skrypt filtruje puste
komórki i bierze **8 pól logicznych**.

Zapisz jako `$Repo\check_layout_parity.py` — **nowy plik, nie commituj**:

```python
#!/usr/bin/env python3
"""check_layout_parity.py - czy warstwa AltGr .klc i .kmn daja te same znaki."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
KLC = os.path.join(HERE, "windows", "src", "KBDMSSTD.klc")
KMN = os.path.join(HERE, "keyman", "isv_latin", "source", "isv_latin.kmn")

VK_TO_K = {
    "C": "K_C", "S": "K_S", "Z": "K_Z", "E": "K_E",
    "OEM_1": "K_COLON", "OEM_7": "K_QUOTE", "OEM_MINUS": "K_HYPHEN",
}


def parse_klc(path):
    with open(path, encoding="utf-16-le") as f:
        text = f.read()
    out = {}
    for line in text.splitlines():
        line = line.split("//")[0]
        # 8 pol LOGICZNYCH - surowe wiersze maja 10 lub 11 komorek
        f_ = [c for c in line.split("\t") if c.strip()]
        if len(f_) < 8:
            continue
        vk = f_[1]
        if vk not in VK_TO_K:
            continue

        def ch(cell):
            cell = cell.strip()
            return None if cell in ("-1", "") else chr(int(cell, 16))

        out[VK_TO_K[vk]] = (ch(f_[6]), ch(f_[7]))
    return out


def parse_kmn(path):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    out = {}
    pat = re.compile(r"\+\s*\[(SHIFT\s+)?RALT\s+(K_\w+)\]\s*>\s*'(.)'")
    for shift, key, val in pat.findall(text):
        lo, hi = out.get(key, (None, None))
        out[key] = (lo, val) if shift else (val, hi)
    return out


def main():
    klc, kmn = parse_klc(KLC), parse_kmn(KMN)
    keys = sorted(set(klc) | set(kmn))
    bad = []
    print("%-10s %-14s %-14s" % ("key", "KLC (Windows)", "KMN (Keyman)"))
    for k in keys:
        a, b = klc.get(k), kmn.get(k)
        if a != b:
            bad.append(k)
        print("%-10s %-14s %-14s%s" % (k, a, b, "  ok" if a == b else "  MISMATCH"))
    print("\n%d keys compared" % len(keys))
    if bad:
        print("DISAGREE: " + ", ".join(bad))
        sys.exit(1)
    print("PARITY OK - Windows and Keyman type the same characters")


if __name__ == "__main__":
    main()
```

```powershell
Set-Location $Repo; py -3 check_layout_parity.py; $LASTEXITCODE
```

**Oczekiwane** — dokładnie to wypisał na `mc`:

```
7 keys compared
PARITY OK - Windows and Keyman type the same characters
```
`$LASTEXITCODE` = `0`.

**Zatrzymaj się i zgłoś, jeśli:**
- `MISMATCH` → tabela się rozjechała. **Nie naprawiaj sam** — podaj klawisz i obie wartości.
  Która strona jest prawdziwa, rozstrzyga owner (a jeśli sporny jest sam znak —
  `interslavic-tutor`, nie Ty i nie ja).
- `0 keys compared` → parser nic nie znalazł; prawie na pewno `.klc` przestał być UTF-16LE.
  Sprawdź: `[IO.File]::ReadAllBytes("$Repo\windows\src\KBDMSSTD.klc")[0..1]` musi dać
  `255 254`. Jeśli nie — git zniszczył plik mimo `.gitattributes` (`*.klc -text`).
  **Nie konwertuj go z powrotem**, zgłoś.
- `UnicodeEncodeError` → nie ustawiłeś `chcp 65001`.

**Jeśli parzystość jest OK — na tym koniec strony Windows.** Nie przebudowuj DLL-i.
Rekompilacja identycznego `.klc` przez `kbdutool` to tylko szansa na zepsucie czegoś, co
działa. Przebudowa instalatora ma sens **wyłącznie** po `MISMATCH` **i** po rozstrzygnięciu
ownera, że zmienia się strona windowsowa — a wtedy to osobne 🔴 zlecenie, nie ten dokument.

---

## 4 — Wynik: gdzie zapisać, jak zaraportować

🟡 Nowy plik (nie dopisuj do istniejących dokumentów):
`C:\Projects\keyboard-interslavic\docs\verification_windows_2026-08-09.md`
(ścieżkę zbuduj z realnego `$Repo`, datę z dnia wykonania)

Minimum treści:

1. `hostname`, data, gałąź/commit, **wersja z `kmp.json`**;
2. **na jakim układzie bazowym** robiłeś test 2.4 — bez tego wynik jest niepełny;
3. tabela 2.4 wypełniona, z rozróżnieniem „nie sprawdzone" / „sprawdzone, nie działa";
4. wynik 2.5 albo „nie dotyczy";
5. wynik `check_layout_parity.py` + exit code;
6. wszystko, co odbiegało od tego zlecenia.

Ownerowi, krótko i w tej kolejności: **czy windowsowa połowa działa (tak/nie/częściowo) → na
czym testowana → co odbiegało → co wymaga jego decyzji.** Ścieżki pełne i bezwzględne,
maszyna (`hp`) przy każdym wyniku.

Formuła: **„zainstalowany i przetestowany ręcznie na `hp`"** — nie „działa na Windowsie".
Jedna maszyna, jedno wydanie Keymana, jeden układ bazowy.

### Czego NIE robić samemu

| Nie rób | Dlaczego |
|---|---|
| `git commit` / `git push` | na `mc` leży 11 nieprzejrzanych plików; **owner** je przegląda |
| aktualizacji `keymanapp/keyboards#4092` i `lexical-models#351` | publikacja do społeczności = wyłącznie owner; oba PR-y mają wersję sprzed flicków i czekają na potwierdzenie z urządzenia |
| odinstalowania KBDMSSTD, układu Goli ani żadnego innego | 🔴 decyzja ownera; usunięcie z listy języków i tak nie odinstalowuje DLL-a |
| przebudowy/podmiany DLL-i w `windows/installers/` | 🔴 j.w., i bezcelowe przy `PARITY OK` |
| ręcznej edycji `.klc`, `.kmn`, `.keyman-touch-layout` | to pliki **generowane** — zmienia się tabelę albo generator |
| poprawiania formy interslawiańskiej, która wygląda dziwnie | nie ten lane — zgłoś do `interslavic-tutor` |
| wnioskowania o iOS z wyniku na Windowsie | inny plik w pakiecie (`.kmx` vs `.js`), inna warstwa, inne gesty |

---

## Czego to zlecenie NIE obejmuje

1. **Setupu środowiska na `hp`** — celowo wycięty, robisz go Ty.
2. **iOS / iPadOS.** `hp` fizycznie połączy się z iPadem (USB-A → Lightning), ale **Xcode na
   Windowsie nie istnieje**. Longpress i flicki są testowalne **tylko na urządzeniu**, przez
   Keymana z App Store.
3. **Warstwy dotykowej w ogóle.** Windows nie używa `isv_latin.keyman-touch-layout`. Zielony
   wynik tutaj **nie jest** dowodem na cokolwiek dotyczącego telefonu ani tabletu.
4. **Wersji 1.5.** Testujesz 1.3. Reguły `RALT` są identyczne (sprawdzone), ale formalnie to
   1.3 — tak zapisz.
5. **Modelu leksykalnego** (`radoslove.isv-latn.wordlist.model.kmp`, 39 777 form). Predykcja i
   swipe to funkcje mobilne; Keyman na Windowsie ich nie używa.
6. **Androida.** `adb` po kablu z `hp` jest możliwy, ale to osobny tor i osobne zlecenie.
7. **macOS i Linuksa.** Deklaracja `macosx`/`linux` w `store(&TARGETS)` zostanie **równie
   nieprzetestowana** po wykonaniu tego zlecenia, co przed. Ta sama luka, inne platformy.
8. **Cyrylicy** (`KBDMSKIR`) — nieaudytowana, poza zakresem.
9. **Regresji w czasie.** Jednorazowy test, jedna maszyna, jedno wydanie Keymana.
10. **Poprawności interslawiańskiej.** Sprawdzamy **mechanikę** — czy klawisz daje znak, który
    obiecuje tabela. O tym, które formy są poprawne, rozstrzyga `interslavic-tutor`.
11. **Stanu `hp`.** Powtórzone świadomie: nieznany w chwili pisania. Jeśli punkt 1 pokaże
    obraz niepasujący do niczego powyżej — to znaczy, że zlecenie trzeba poprawić, a nie że
    rzeczywistość jest zła.
