# Szkic komentarza do MR F-Droida !45568

*Do wklejenia przez ownera pod `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568`.
Zaktualizowane 2026-09-23 po pushu przepisu 3.3 z `mc`. **Nieopublikowane:** zapisany
token GitLaba pozwala na push, ale API zwraca `403 insufficient_scope`.
Odpowiada na dwie prośby `linsui` z 2026-09-22: „Unprotect your branch"
oraz „if you release a new version please update this MR".*

Warunek wypchnięcia przepisu spełniony: `a7fcf075b5` aktualizuje pięć pól do 3.3,
`62e098c87c` usuwa nieużywany przez Android binarny zasób iOS podczas skanowania.
[Pipeline 2873220674](https://gitlab.com/radoslove/fdroiddata/-/pipelines/2873220674)
przeszedł w całości (9/9 zadań). Treść poniżej jest gotowa do publikacji.

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
Nie pisać, że odtworzono klucz na `mc` albo uzyskano identyczny podpisany APK — tych
dwóch testów jeszcze nie ukończono. Szczegóły są w `MC_TODO_2026-09-23.md`.
