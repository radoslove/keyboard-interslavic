# Szkic komentarza do MR F-Droida !45568

## Odłożone przez ownera — 2026-09-24

Aktualizacja 25.09: klucz został już odtworzony na `mc` z backupu `hetz`; certyfikat
i próbne podpisanie lokalnego APK 3.4 zweryfikowano. Nie opublikowano APK.
Publikacja tego komentarza nadal pozostaje odłożona.

**Nie wysyłać teraz.** Przy wznowieniu otworzyć
[MR !45568](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568)
w przeglądarce zalogowanej jako `radoslove`. Sprawdzić aktualny przepis, wydanie
i komentarze, dostosować szkic poniżej, a po wysłaniu zapisać link do odpowiedzi
w `MC_TODO_2026-09-23.md`. Ręczne wklejenie nie wymaga tokenu API.

Nowy kontekst, który trzeba uwzględnić przed publikacją:

- [Tester 23.09](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568#note_3889183213)
  potwierdził poprawny podpis i odtwarzalność 3.3, ale zgłosił pola haseł, RTL,
  backup danych słownych i pytanie o źródła/licencję słownika. `linsui` podziękował
  za raport; nie oznacza to zamknięcia zgłoszonych uwag.
- Kod 3.4 (`dd5cf49`) poprawia dopisywanie liter po swipe; nie rozwiązuje powyższych
  uwag. Kompilacja bez podpisu przeszła na `mc`, ale najnowszym opublikowanym
  wydaniem i wersją w przepisie F-Droida pozostaje 3.3.
- Poniższy szkic dotyczy aktualizacji do 3.3. Po opublikowaniu kolejnej wersji
  najpierw zaktualizować przepis i sprawdzić CI, dopiero potem zmienić treść.
  Osobna odpowiedź na raport testera powinna wskazywać faktycznie wykonane poprawki
  oraz konkretne źródła i warunki redystrybucji słownika. Nie oznaczać tych punktów
  jako rozwiązanych na podstawie samego zielonego CI.

## Szkic odpowiedzi o aktualizacji do 3.3

*Do wklejenia przez ownera pod `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568`.
Zaktualizowane 2026-09-23 po pushu przepisu 3.3 z `mc`. **Nieopublikowane:** zapisany
token GitLaba pozwala na push, ale API zwraca `403 insufficient_scope`.
Odpowiada na dwie prośby `linsui` z 2026-09-22: „Unprotect your branch"
oraz „if you release a new version please update this MR".*

Warunek wypchnięcia przepisu spełniony: `a7fcf075b5` aktualizuje pięć pól do 3.3,
`62e098c87c` usuwa nieużywany przez Android binarny zasób iOS podczas skanowania.
[Pipeline 2873220674](https://gitlab.com/radoslove/fdroiddata/-/pipelines/2873220674)
przeszedł w całości (9/9 zadań). Treść poniżej zachowano do aktualizacji przy wznowieniu.

---

Thanks for looking at this, and no worries about the queue.

Both things are done:

- **Branch unprotected.** `master` on the fork no longer has a protection rule, so you can push to the MR branch directly.
- **Updated to 3.3.** I released a new version, so the recipe now points at it:

```
versionName: '3.3'
versionCode: 33
commit: a379b491797a106556390473395cf3a63d63d681
CurrentVersion: '3.3'
CurrentVersionCode: 33
```

The release is at https://github.com/radoslove/keyboard-interslavic/releases/tag/v3.3 and the asset is `app-release.apk`, matching the `Binaries:` pattern.

The first CI run found an iOS-only binary dictionary at `ios/Keyboard/Resources/isv_swipe.bin`. I added that exact path to `scandelete`; the Android app does not use it.

The [full pipeline](https://gitlab.com/radoslove/fdroiddata/-/pipelines/2873220674) now passes. The [Linux CI build](https://gitlab.com/radoslove/fdroiddata/-/jobs/16668341716) successfully compared the rebuild with the published reference APK and verified the allowed signing key.

Separately, a native macOS arm64 rebuild differs in the compression of three launcher-foreground PNGs (the decompressed data matches), so the macOS build is not byte-identical. The Linux F-Droid reproducibility check above passes.

3.3 is also the first version I'd want users to get — the earlier releases were missing several text-editing fixes.

---

## Wariant krótszy

Gdyby powyższe było za długie: zostawić dwa punkty, link i informację o wyniku CI.
Odtworzenie klucza i podpisywanie na `mc` są potwierdzone od 25.09. Nie pisać,
że natywny build macOS jest identyczny z wydaniem z `hp`; tego nie potwierdzono.
Szczegóły są w `MC_TODO_2026-09-23.md`.
