# ĆWICZENIE: odtworzenie klucza podpisującego na obcej maszynie

**Aktualizacja 2026-09-25:** na `mc` odtworzono klucz z backupu na `hetz`,
potwierdzono certyfikat i podpisano lokalny APK 3.4. Wynik i aktualna procedura:
[`MC_TODO_2026-09-23.md`](MC_TODO_2026-09-23.md). Backup zawiera również hasła JKS
w `keystore.properties`; nowy `tools/sign_apk_from_backup.py` wykorzystuje je
w pamięci. Odtworzony keystore pozostaje na `mc` (oprócz `hp`), z prawami `0600`.
Nie opublikowano APK. FileVault na `mc` pozostaje wyłączony i wymaga osobnego
dokończenia. Instrukcje i obserwacje niżej są historyczne; dla `mc` pierwszeństwo
ma raport z 25.09.

*Dowieść, że backup klucza `medzuslovjansky` działa **bez udziału `hp`**.
Instrukcje per maszyna: `len` (Ubuntu), `com` (Windows), `galax` (⚠ patrz ostrzeżenie).
Stan 17.09.2026: wszystkie trzy offline — odpalić, gdy któraś wstanie.*

---

## Po co, skoro backup jest już zweryfikowany

17.09 sprawdzono go end-to-end — **ale z `hp`, czyli z maszyny, która ma oryginał**.
To dowodzi, że archiwum jest zdrowe i hasło pasuje. **Nie dowodzi**, że człowiek bez
dostępu do `hp` odtworzy z tego działający klucz.

Scenariusz, pod który ćwiczymy: **dysk w `hp` padł.** Zostaje sześć kopii `.tar.gpg`
i hasło w menedżerze haseł. Czy to wystarczy, żeby wydać v3.3? Dopóki nikt tego nie
przeszedł, odpowiedź brzmi „chyba tak".

### Zasada nadrzędna 🔴

**Ani jeden plik nie może pochodzić z `hp`.** Archiwum ciągniesz z `~/keystore-backup/`
na `hetz`, `host`, `ubu`, `rpi4` albo `rpi5`. Hasło bierzesz z menedżera haseł.

Wolno użyć `hp` jako terminala (wygodnie, bo stamtąd jest SSH wszędzie) — ale jeśli
w trakcie sięgniesz po plik z jego dysku, ćwiczenie **nic nie zmierzyło**, nawet gdy
technicznie się udało. Zapisz to wtedy uczciwie w raporcie.

### Czego to ćwiczenie szuka

Nie tego, czy `gpg` umie odszyfrować plik — to już wiadomo. Szukamy rzeczy, które
wychodzą wyłącznie na obcej maszynie:

| Pułapka | Gdzie uderzy |
|---|---|
| **Ścieżka w `keystore.properties`** | `storeFile=C:/Users/radok/keystores/…` — zaszyta ścieżka z `hp`. Na `len` Gradle jej nie znajdzie; na `com`/`galax` katalog nie istnieje. **Najbardziej prawdopodobna przyczyna porażki.** |
| **Hasło** | Czy naprawdę jest w menedżerze haseł, a nie tylko w głowie i w `password.txt` na `hp`? |
| **Wersja GPG** | Archiwum: AES-256, S2K SHA-512 × 65 011 712. Starszy `gpg` może zachować się inaczej. |
| **Format JKS** | JKS jest przestarzały (JDK woli PKCS12). Nowszy JDK czyta, ale ostrzega. |
| **Brak narzędzi** | `keytool` = JDK; `apksigner` = Android SDK. Na Windowsie dochodzi brak `gpg`. |

---

# Wybór maszyny

| | `len` | `com` | `galax` |
|---|---|---|---|
| **OS** | Ubuntu 24.04.3 LTS | Windows | Windows + WSL |
| **Sprzęt** | Lenovo i5 7gen, 16 GB, 365 GB wolne | Ryzen 7 5700X, 32 GB, RTX 4070 | Galaxy Book3 360 |
| **Dostęp z `hp`** | `ssh rado@len` (klucz `hp` już wgrany) | Tailscale `100.86.176.24` | Tailscale `100.103.134.76` |
| **Wartość ćwiczenia** | 🟢 **najwyższa** — Linux obnaża pułapkę ze ścieżką | 🟢 wysoka — Windows, czyli odbudowa 1:1 jak `hp` | 🔴 **odradzam** |
| **Rekomendacja** | **zacznij tutaj** | dobry drugi przebieg | patrz niżej |

### ⚠ `galax` — dlaczego odradzam

Z `hardware_inventory.md` i `NOW.md`: `galax` jest **przygotowywany jako maszyna
siostrzeńca (klasa 1)**, zastąpiony przez `mc`. Czyli ta maszyna **wyjdzie z domu
i trafi do dziecka**.

Ćwiczenie kładzie na dysku **jawny klucz podpisujący**. Jedno przeoczone sprzątanie
i klucz, który zabezpieczaliśmy przez cały wieczór, jedzie w plecaku do pierwszaka.
Jeśli mimo to `galax` — sprzątanie jest obowiązkowe i **weryfikowane** (sekcja na końcu),
a najlepiej rób to w WSL, nie w Windowsie: wtedy sprzątasz jeden katalog.

---

# `len` — Ubuntu 24.04  🟢 zalecana

Login `rado`, klucz `hp` już w `authorized_keys`. ⚠ Z `hardware_inventory.md`: `len`
bywa **dual-homed** (WiFi + eth na tej samej sieci = konflikt tras). Jeśli SSH się
zawiesza, to jest pierwszy podejrzany, nie ćwiczenie.

### Preflight

```sh
ssh rado@len
gpg --version | head -1
tar --version | head -1
which keytool || sudo apt install -y default-jdk-headless
```

### Poziom 1 🟢 — tożsamość klucza (~10 min)

```sh
mkdir -p ~/drill && cd ~/drill
scp claude@100.91.132.98:~/keystore-backup/medzuslovjansky-keystore-backup.tar.gpg .
sha256sum medzuslovjansky-keystore-backup.tar.gpg
#   oczekiwane: 01a836bc3fd60df222334d058eabffd0b735806c3a77b4ecc1fff853d6f43728

gpg --decrypt medzuslovjansky-keystore-backup.tar.gpg | tar -xf -
keytool -list -v -keystore medzuslovjansky-release.jks -alias medzuslovjansky | grep SHA256
```

**Zaliczone, gdy** odcisk =
`5F:A8:1C:D2:FD:62:CB:DD:35:80:07:6B:94:1C:17:11:B4:DD:C6:25:60:9B:5C:80:EC:7C:13:5A:56:E3:B9:8A`
(ten sam co w `docs/DISTRIBUTION.md`). Inny → **to nie ten klucz, STOP.**

### Poziom 2 🟡 — klucz naprawdę podpisuje (~45 min)

```sh
cd ~/drill && echo test > t.txt && jar cf t.jar t.txt
jarsigner -keystore medzuslovjansky-release.jks t.jar medzuslovjansky
jarsigner -verify -verbose -certs t.jar | grep -A2 'X.509'
```

**Zaliczone, gdy** podpis przechodzi weryfikację, a certyfikat to
`CN=Medzuslovjansky Keyboard, O=Radoslove, C=PL`.

### Poziom 3 🔴 — pełna odbudowa wydania (~2–3 h)

⚠ `len` hostuje już `substances` — **nie ruszaj tamtego stacku**, pracuj tylko w `~/drill`.

```sh
git clone https://github.com/radoslove/keyboard-interslavic ~/drill/repo
cd ~/drill/repo/android-app
cp ~/drill/keystore.properties .
sed -i "s|^storeFile=.*|storeFile=$HOME/drill/medzuslovjansky-release.jks|" keystore.properties
#   ^ TO JEST TA PUŁAPKA. Zanotuj w raporcie, że trzeba to było zrobić.
./gradlew assembleRelease
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

**Zaliczone, gdy** `apksigner` wypisze SHA-256 `5fa81cd2…56e3b98a` — czyli APK zbudowany
obcą maszyną jest podpisany tożsamo z tym, co mają użytkownicy i co F-Droid ma przypięte
w `AllowedAPKSigningKeys`. To jest moment, w którym ryzyko „nie da się już wydać
aktualizacji" znika naprawdę.

---

# `com` — Windows  🟢

PowerShell. Windows 10+ ma wbudowane `scp` i `tar`; **`gpg` trzeba mieć** — z Git for
Windows (`C:\Program Files\Git\usr\bin\gpg.exe`) albo z Gpg4win.

### Preflight

```powershell
gpg --version | Select-Object -First 1
tar --version
(Get-Command keytool -ErrorAction SilentlyContinue).Source
```

Brak `gpg` → Git for Windows. Brak `keytool` → JDK (na `hp` stoi Eclipse Adoptium 21).

### Poziom 1 🟢

```powershell
mkdir ~/drill; cd ~/drill
scp claude@100.91.132.98:~/keystore-backup/medzuslovjansky-keystore-backup.tar.gpg .
Get-FileHash medzuslovjansky-keystore-backup.tar.gpg -Algorithm SHA256 | Format-List
#   oczekiwane: 01A836BC3FD60DF222334D058EABFFD0B735806C3A77B4ECC1FFF853D6F43728

gpg --decrypt medzuslovjansky-keystore-backup.tar.gpg -o backup.tar
tar -xf backup.tar
keytool -list -v -keystore medzuslovjansky-release.jks -alias medzuslovjansky | Select-String SHA256
```

⚠ Na Windowsie **nie potokuj `gpg | tar`** — `tar` potraktuje `C:` jako zdalny host
i padnie `Cannot connect to C: resolve failed` (sprawdzone 17.09 na `hp`). Zapisz do
pliku, rozpakuj, a `backup.tar` **skasuj od razu** — to jawny klucz.

### Poziomy 2 i 3

Jak przy `len`, z dwiema różnicami: `./gradlew.bat` zamiast `./gradlew`, a w
`keystore.properties` ścieżka `storeFile=C:/Users/<ty>/drill/medzuslovjansky-release.jks`
— **ukośniki w przód**, Gradle nie strawi `\`.

---

# `galax` — Windows + WSL  🔴 tylko świadomie

Przeczytaj najpierw ostrzeżenie wyżej. Jeśli mimo to — rób **w WSL**, nie w Windowsie:
cały ćwiczebny bałagan siedzi wtedy w jednym katalogu i sprzątasz go jedną komendą.

```sh
wsl
# dalej dokładnie jak sekcja `len`
```

---

# `mc` — MacBook Air M4 (macOS, arm64)  🟢🟢 najmocniejszy cel

*Dopisane 2026-09-22 na prośbę ownera: „przygotuj wszystko żeby się przenieść na `mc`,
to wtedy będzie wiadomo na 100% że jest ok z tym kluczem i systemem w razie czego".*

⚠ **To nie jest zwykłe ćwiczenie — to PRZEPROWADZKA.** Różnica jest w sprzątaniu:
na `len`/`com` klucz po ćwiczeniu się niszczy, **na `mc` klucz ZOSTAJE**, bo `mc` ma
się stać drugą maszyną wydawniczą. Patrz „Sprzątanie" niżej — dla `mc` obowiązuje
inaczej.

## Dlaczego `mc` jest lepszym celem niż `len` i `com`

| | `com` | `len` | **`mc`** |
|---|---|---|---|
| Inny OS niż `hp` | ✗ Windows | ✓ Linux | ✓ macOS |
| **Inna architektura CPU** | ✗ x64 | ✗ x64 | ✓ **arm64** |
| Ma zostać maszyną wydawniczą | ✗ | ✗ | ✓ (secondary/travel + build-box Apple) |

Inne CPU to nie kosmetyka: **żaden z pozostałych celów nie sprawdza, czy build jest
reprodukowalny poza x86.**

## ⚠ Co to naprawdę testuje — i dlaczego akurat teraz

F-Droid **nie ufa naszemu APK na słowo**. Przepis ma `Binaries` +
`AllowedAPKSigningKeys`, czyli **oni odbudowują aplikację u siebie, na Linuksie, i
porównują z plikiem, który opublikowaliśmy**. `linsui` odhaczył „Enable Reproducible
Builds" 22.09 i zapowiedział testy.

Czyli build na `mc` jest **próbą generalną testu, który F-Droid i tak nam zrobi** —
tylko że my zobaczymy wynik pierwsi. Jeśli hash się nie zgodzi na `mc`, jest duża
szansa, że nie zgodzi się i u nich.

**Wartość docelowa (v3.3, zbudowana na `hp` 22.09):**
```
771f5dc9c2a0651b1e88f7b9227a446a1c8da59820ec45f3196120d1c7bc1863
```
*(v3.2 dla porównania: `82d4d772e80cd58b5c35ba1537c73fea52f60ebb2fb0823b4aa3c6497565b2d7`
— ten sam wynik uzyskano na `hp` dwukrotnie, w odstępie dwóch tygodni.)*

## Skrót: `tools/bootstrap_build_machine.sh`

Poziomy 1 i 3 są zautomatyzowane — ten sam skrypt obsłuży później `len` i `com`:

```bash
./tools/bootstrap_build_machine.sh                # tylko sprawdzenie wymagan
./tools/bootstrap_build_machine.sh --key hetz     # + odtworzenie klucza z backupu
./tools/bootstrap_build_machine.sh --key hetz --build   # + build i porownanie hasha
```

Co robi sam: sprawdza JDK/SDK/narzędzia, ściąga archiwum z wybranego hosta
(**`hp` jest jawnie zabronione jako źródło**), weryfikuje sumę archiwum i odcisk
certyfikatu, tworzy `keystore.properties` ze ścieżką właściwą dla TEGO systemu,
buduje i porównuje hash z `docs/INSTALL.md`.

Czego NIE robi: nie instaluje niczego i **nigdy nie dotyka haseł** — zakłada plik
z pustymi polami, a hasła wpisujesz z menedżera. Napisany pod **bash 3.2**, bo tyle
ma macOS.

## Preflight

⚠ **`mc` jest teraz OFFLINE** (tailnet `100.79.220.17`, ostatnio widziany 22.09
~20:10). Uśpiony, nie zgubiony — obudzić przed startem.

⚠ **JDK musi być 21, NIE 26.** To udokumentowana pułapka tego projektu
(skill `android-keyboard-dev`); nowszy JDK potrafi zmienić wynik builda, co przy
teście reprodukowalności jest dokładnie tym, czego nie chcemy. Na `hp` build szedł
Temurin 21.0.12.

```bash
# na `mc`
java -version                 # ma pokazać 21.x  (brew install --cask temurin@21)
echo $ANDROID_HOME            # potrzebne: platform-34 + build-tools 34.0.0
git --version
gpg --version
```

Xcode na `mc` jest, ale do Androida jest nieistotny.

⚠ **Nie licz na Tailscale SSH do `mc`.** Tailscale jest tam w wersji **sandboxowanej
(App Store)**, więc `tailscale up --ssh` nie działa — to ta sama przyczyna, dla której
CLI musi być skryptem-wrapperem wywołującym binarkę z bundla (zwykły symlink pada na
`Fatal error: The current bundleIdentifier is unknown to the registry`, NOW.md
2026-08-07). Wejście zdalne: **System Settings → General → Sharing → Remote Login**,
potem `ssh rado@100.79.220.17` z `hp`. Zmierzone 2026-09-22: port 22 wygasa, dopóki
Remote Login jest wyłączony.

Android SDK na macOS (Temurin 21 już zainstalowany 2026-09-22):

```bash
brew install --cask android-commandlinetools
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"
```

## Poziom 1 🟢 — tożsamość klucza (~10 min)

🔴 **Archiwum NIE może pochodzić z `hp`.** Ciągnij z `hetz`, `host`, `ubu`, `rpi4`
albo `rpi5` — hasło z menedżera haseł, wpisywane w `pinentry`, nigdy w linii poleceń.

```bash
scp claude@100.91.132.98:~/keystore-backup/medzuslovjansky-keystore-backup.tar.gpg .
shasum -a 256 medzuslovjansky-keystore-backup.tar.gpg
#   oczekiwane: 01a836bc3fd60df222334d058eabffd0b735806c3a77b4ecc1fff853d6f43728
gpg -d medzuslovjansky-keystore-backup.tar.gpg | tar xv
keytool -list -v -keystore medzuslovjansky-release.jks -alias medzuslovjansky
#   odcisk SHA-256 ma być: 5F:A8:1C:D2:…:56:E3:B9:8A
```

## Poziom 2 🟡 — klucz naprawdę podpisuje

Jak w sekcji `len`: podpisać cokolwiek i zweryfikować `apksigner verify --print-certs`.

## Poziom 3 🔴 — pełna odbudowa wydania (~1–2 h) ← **to jest ten, o który chodzi**

```bash
git clone https://github.com/radoslove/keyboard-interslavic.git
cd keyboard-interslavic && git checkout <commit v3.3>
mkdir -p ~/keystores && mv ~/medzuslovjansky-release.jks ~/keystores/
```

⚠ **TU JEST TA PUŁAPKA — `keystore.properties` nie przyjedzie z gita** (jest
w `android-app/.gitignore`, sprawdzone 22.09: nieśledzony). Trzeba go napisać od zera,
a zaszyta w nim ścieżka jest **windowsowa**:

```properties
# hp:  storeFile=C:/Users/radok/keystores/medzuslovjansky-release.jks
# mc:
storeFile=/Users/<user>/keystores/medzuslovjansky-release.jks
storePassword=<z menedżera haseł>
keyAlias=medzuslovjansky
keyPassword=<z menedżera haseł>
```

```bash
cd android-app && ./gradlew --no-daemon clean assembleRelease
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

## Jak czytać wynik

| Wynik | Co znaczy | Co robić |
|---|---|---|
| **Hash = `771f5dc9…`** | 🟢 Klucz odtworzony, system przenośny, **build reprodukowalny między OS-ami i architekturami**. `hp` przestaje być pojedynczym punktem awarii, a test F-Droida najpewniej przejdzie | zapisać w raporcie, `mc` = druga maszyna wydawnicza |
| **Hash inny, podpis OK** | 🟡 Klucz żyje, ale build nie jest reprodukowalny między maszynami | ⚠ **zbadać PRZED merge'em w F-Droidzie** — porównać `unzip -l` obu APK, sprawdzić wersje JDK/AGP/build-tools. To samo uderzy u nich |
| **Nie da się odszyfrować / zły odcisk** | 🔴 Backup nie spełnia swojej funkcji | natychmiast: nowy backup z `hp`, dopóki `hp` żyje |

## Sprzątanie — dla `mc` INACZEJ niż dla `len`/`com`

Sekcja „Sprzątanie" niżej każe zniszczyć klucz po ćwiczeniu. **Dla `mc` to nie
obowiązuje** — tam klucz ma zostać, o to chodzi w przeprowadzce. Zamiast tego:

- ⚠ `keystore.properties` **nigdy do gita** (jest ignorowany — nie „poprawiać" tego),
- usunąć samo **archiwum `.tar.gpg`** i rozpakowane śmieci spoza `~/keystores/`,
- 🔴 zaktualizować inwentarz: od tej chwili **jawny klucz jest na DWÓCH maszynach**
  (`hp` i `mc`), nie na jednej. To jest poprawa odporności, ale i podwojona
  powierzchnia — `mc` jest maszyną podróżną, więc szyfrowanie dysku i blokada ekranu
  przestają być opcjonalne.

# Sprzątanie 🔴 — NIE POMIJAĆ

Ćwiczenie właśnie położyło **jawny klucz podpisujący** na maszynie, która go nie miała.
Niedokończone ćwiczenie jest **gorsze niż jego brak**: zamiast jednej maszyny z kluczem
masz dwie, a o drugiej nikt nie pamięta.

**Linux / WSL:**
```sh
rm -rf ~/drill
history -c
find ~ \( -name 'medzuslovjansky-release.jks' -o -name '*keystore-backup*' \) 2>/dev/null
```

**Windows:**
```powershell
Remove-Item -Recurse -Force ~/drill
Clear-History
Get-ChildItem ~ -Recurse -Include medzuslovjansky-release.jks,*keystore-backup* -ErrorAction SilentlyContinue
```

Ostatnia komenda w obu wariantach to **weryfikacja sprzątania**, nie ozdobnik — ma nie
zwrócić niczego. Na maszynie ćwiczebnej zostaje: **nic**. Zaszyfrowany `.tar.gpg` też
nie — od tego jest `~/keystore-backup/` na pięciu maszynach.

---

# Raport po ćwiczeniu

Jedna linijka w `NOW.md`, a w niej koniecznie:

- **która maszyna** i **z którego źródła** ciągnięty backup (użyj innego niż poprzednio),
- **do którego poziomu** doszło,
- **czy sięgnąłeś po cokolwiek z `hp`** (jeśli tak — ćwiczenie oblane, napisz wprost),
- **czy sprzątanie zweryfikowane**,
- **co nie zadziałało od pierwszego razu** ← najcenniejsza część. Ćwiczenie, które
  „poszło gładko", zwykle znaczy, że ktoś po cichu obszedł problem.

---

# Czego to ćwiczenie NIE obejmuje

- **Utraty hasła.** Backup chroni przed padnięciem dysku, nie przed zapomnieniem hasła.
  Jedyna odpowiedź to menedżer haseł i to, żeby hasło tam faktycznie było.
- **Google Play App Signing.** Gdyby kiedyś zostało włączone, Google trzyma klucz
  dystrybucyjny, a nasz staje się tylko kluczem *upload* — wtedy ten dokument do
  przepisania (patrz ostrzeżenie w `docs/DISTRIBUTION.md`).
- **Rotacji klucza.** Nie da się. To jest właśnie powód, dla którego to ćwiczymy.
