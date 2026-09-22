# Szkic komentarza do MR F-Droida !45568

*Do wklejenia przez ownera pod `https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568`.
Napisane 2026-09-23. Odpowiada na dwie prośby `linsui` z 2026-09-22: „Unprotect your branch"
oraz „if you release a new version please update this MR".*

⚠ **Wkleić DOPIERO po wypchnięciu poprawionego przepisu** (`versionName`/`versionCode`/
`commit`/`CurrentVersion`/`CurrentVersionCode` na 3.3 w forku
`https://gitlab.com/radoslove/fdroiddata`). Przed pushem komentarz twierdziłby coś,
czego jeszcze nie ma.

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

On reproducibility: I rebuilt the published APK from the tagged commit on a clean tree and got the identical SHA-256 (`771f5dc9c2a0651b1e88f7b9227a446a1c8da59820ec45f3196120d1c7bc1863`), so the binary in the release is byte-for-byte what that commit produces here.

3.3 is also the first version I'd want users to get — the earlier releases were missing several text-editing fixes.

---

## Wariant krótszy

Gdyby powyższe było za długie: zostawić dwa punkty i link, a akapit o reprodukowalności
wyciąć — `linsui` sam odhaczył tę pozycję na liście kontrolnej 22.09, więc to informacja
dodatkowa, nie wymagana.
