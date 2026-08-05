# Kanoniczna tablica runiczna dla międzysłowiańskiego

**Status:** roboczy kanon, wyprowadzony z `runy_5.klc` (2024-09) — najnowszego z pięciu wariantów
i jedynego faktycznie zainstalowanego układu. Wymaga potwierdzenia przez `interslavic-tutor`
(patrz „Otwarte kwestie" na końcu).

Ta tablica jest **jedynym źródłem prawdy** dla wszystkich platform. Layouty Windows/Android
mają się do niej stosować; rozjazd między nimi to błąd do naprawienia, nie wariant.

---

## Tablica

| MS | Runa | Unicode | Nazwa runy | Klawisz |
|---|---|---|---|---|
| a | ᚨ | U+16A8 | ANSUZ | `A` |
| b | ᛓ | U+16D3 | BERKANAN (wariant) | `B` |
| c | ᛣ | U+16E3 | CALC | `C` |
| č | ᚳ | U+16B3 | CEN | ⚠ brak w runy_5 |
| d | ᛜ | U+16DC | INGWAZ | `D` |
| dž | — | — | — | ⚠ nierozstrzygnięte |
| e | ᛖ | U+16D6 | EHWAZ | `E` |
| ě | ᛠ | U+16E0 | EAR | `Shift+E` |
| f | ᚠ | U+16A0 | FEHU | `F` |
| g | ᚷ | U+16B7 | GEBO | `G` |
| h | ᚻ | U+16BB | HAEGL | `H` |
| i | ᛁ | U+16C1 | ISAZ | `I` |
| j | ᛃ | U+16C3 | JERAN | `J` |
| k | ᚲ | U+16B2 | KAUNA | `K` |
| l | ᛚ | U+16DA | LAUKAZ | `L` |
| lj | ᛚᛃ | — | dwuznak (l+j) | `L` `J` |
| m | ᛗ | U+16D7 | MANNAZ | `M` |
| n | ᚾ | U+16BE | NAUDIZ | `N` |
| nj | ᛝ | U+16DD | ING | `Shift+N` |
| o | ᛟ | U+16DF | OTHALAN | `O` |
| p | ᛈ | U+16C8 | PERTHO | `P` |
| r | ᚱ | U+16B1 | RAIDO | `R` |
| s | ᛋ | U+16CB | SOWILO | `S` |
| š | ᛯ | U+16EF | CWEORTH | `Shift+S` |
| t | ᛏ | U+16CF | TIWAZ | `T` |
| u | ᚢ | U+16A2 | URUZ | `U` |
| v | ᚡ | U+16A1 | V | `W` |
| y | ᛇ | U+16C7 | IWAZ | `Y` |
| z | ᛉ | U+16C9 | ALGIZ | `Z` |
| ž | ᛡ | U+16E1 | IOR | `Shift+Z` |

### Poza alfabetem MS

| Znak | Runa | Unicode | Klawisz | Uwaga |
|---|---|---|---|---|
| q | ᛩ | U+16E9 | `Q` | MS nie ma `q` — zapasowo |
| w | ᚹ | U+16B9 | `V` | WUNJO; MS nie ma `w` |
| x | x | — | `X` | zostaje łaciński |
| . | ᛫ | U+16EB | `.` | interpunkcja runiczna |
| : | ᛬ | U+16EC | `Shift+L` | interpunkcja runiczna |

---

## Jak rozstrzygnięto spory

Trzy źródła się nie zgadzały. Rozstrzygający głos ma `runy_5.klc` — bo to ostatnia iteracja
i realnie zainstalowany układ, czyli decyzja podjęta „w kodzie", nie w notatce.

| Litera | `Runes.md` | `runy_3.txt` | **`runy_5.klc`** | Kanon |
|---|---|---|---|---|
| b | ᛓ | ᛒ | **ᛓ** | ᛓ |
| c | ᛣ | ᚲ *(ᛣ na marginesie)* | **ᛣ** | ᛣ |
| k | ᚲ | *(puste)* | **ᚲ** | ᚲ |
| d | ᛜ | ᛞ | **ᛜ** | ᛜ |
| s | ᛋ | ᛋ *(ᛊ na marginesie)* | **ᛋ** | ᛋ |

Wniosek: `runy_5` konsekwentnie idzie za `Runes.md`, nie za `runy_3.txt`. Rozjazd `c`/`k`
(pusty klawisz `K` w notatce) był etapem przejściowym i został domknięty: **`c`=ᛣ CALC,
`k`=ᚲ KAUNA**. Tak samo `ᛊ` i `ᛒ` odrzucone.

---

## Otwarte kwestie ⚠

Do rozstrzygnięcia z `interslavic-tutor` — do tego czasu Android trzyma się tablicy powyżej,
żeby platformy się nie rozjechały.

1. **`č` nie ma klawisza w `runy_5`.** `Runes.md` daje ᚳ U+16B3 CEN i to jedyny kandydat —
   ale w zainstalowanym układzie po prostu nie da się wpisać `č`. Realna luka, nie spór.
   To samo `dž` (`Runes.md`: ᛞ, `runy_3.txt`: ᛢ — sprzeczne, `runy_5` milczy).

2. **`d` = ᛜ INGWAZ jest wątpliwe historycznie.** ᛜ to w futharku dźwięk **ng**, a nie `d`;
   standardowe `d` to ᛞ DAGAZ (U+16DE) — i tak ma `runy_3.txt`. `runy_5` wybrał ᛜ, więc kanon
   to na razie odzwierciedla, ale warto świadomie potwierdzić: to celowa decyzja czy pomyłka
   z podobnych glifów? Jeśli pomyłka, zmiana dotknie `d`, `dž` i `nj` naraz.

3. **`b` = ᛓ to wariant, nie podstawowa forma.** Standardowa BERKANAN to ᛒ U+16D2.
   Wybór ᛓ jest spójny w `Runes.md` i `runy_5`, więc wygląda na zamierzony — ale nietypowy.

4. **Dwuznaki bez własnych klawiszy.** `lj` składa się z ᛚ+ᛃ. `nj` ma własny znak ᛝ, więc
   asymetria jest wbudowana. Do przemyślenia przy layoutach mobilnych, gdzie long-press
   daje więcej miejsca niż fizyczna klawiatura.

---

## Źródła

- `windows/src/runy_5.klc` — układ rozstrzygający
- `windows/src/runy_3.txt` — notatka robocza z wariantami
- `C:\Projects\remote_vault\lang\Runes.md` — pierwotna tablica MS→runa
- Unicode Runic block: U+16A0–U+16FF
