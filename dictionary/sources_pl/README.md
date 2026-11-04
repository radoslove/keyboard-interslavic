# Sources of the Polish wordlist (`main_pl.combined`, `pl_swipe.bin`)

The downloads are not committed (57 MB). To rebuild:

    curl -sSLfo dictionary/sources_pl/pl_full.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/pl/pl_full.txt
    curl -sSLfo dictionary/sources_pl/PoliMorf-0.6.7.tab.gz \
      "https://zil.ipipan.waw.pl/PoliMorf?action=AttachFile&do=get&target=PoliMorf-0.6.7.tab.gz"
    python3 tools/build_pl_wordlist.py
    python3 tools/build_pl_aglutynanty.py   # past-personal + conditional (needs PoliMorf)
    python3 tools/build_pl_names.py         # PESEL first names and surnames
    python3 build_swipe_dictionary.py pl

⚠ The three steps after `build_pl_wordlist.py` APPEND to the list, so running them
twice on the same file duplicates lines. Rebuild from `build_pl_wordlist.py` first.

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `pl_full` (OpenSubtitles) | word frequencies | CC BY-SA 4.0 |
| [PoliMorf](https://zil.ipipan.waw.pl/PoliMorf) 0.6.7 (SGJP + Morfologik) | which forms exist, their spelling | BSD 2-Clause |

The resulting wordlist and binary are a derivative of FrequencyWords and are
therefore distributed under **CC BY-SA 4.0**, not MIT.

## Imiona i nazwiska z rejestru PESEL (dodane 2026-11-01)

Lista budowana przez PoliMorf nie zawiera imion ani nazwisk, więc klawiatura radziła
sobie z `zabezpieczeniem`, a nie podpowiadała `Marek`. Źródłem są własne wykazy
rejestru PESEL publikowane przez Ministerstwo Cyfryzacji na dane.gov.pl — każde imię i
nazwisko osoby ŻYJĄCEJ razem z liczbą osób, które je noszą.

| Plik (pobierany, niecommitowany) | Zbiór | Stan |
|---|---|---|
| `pesel_imiona_meskie_2025.csv` · `pesel_imiona_zenskie_2025.csv` | dane.gov.pl, zbiór **1667** „lista imion występujących w rejestrze PESEL – osoby żyjące" | 2025-01-22 |
| `pesel_nazwiska_meskie_2026.csv` · `pesel_nazwiska_zenskie_2026.csv` | dane.gov.pl, zbiór **1681** „nazwiska" | 2026-01-20 |

Przefiltrowany wynik JEST commitowany — `names_pl.tsv` (2 206 imion od 200 osób w górę)
i `surnames_pl.tsv` (6 487 nazwisk od 1 000 osób w górę) — więc listę da się odtworzyć
bez pobierania. Budowa: `python tools/build_pl_names.py`.

⚠ Warunki ponownego użycia tych zbiorów **nie są tu jeszcze potwierdzone** (owner
2026-11-01: „olać na razie te licencje"). Do sprawdzenia przed publikacją wydania, bo
pozostałe źródła tej klawiatury mają licencje wypisane co do jednego.
