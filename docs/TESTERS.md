# Testers — what to hand a person who agreed to try the keyboard

[TESTING.md](TESTING.md) is the checklist for *us*: every layout, every platform,
every key. This file is for *them*: one person, one phone, one file to download,
and plain words about what we want to hear back. The instruction itself is below
in three languages — Medžuslovjansky, English, Polish — ready to copy.

## Which file to give them

| Route | What it is | Who it is for |
|---|---|---|
| **[Release 3.5](https://github.com/radoslove/keyboard-interslavic/releases/latest/download/interslove-3.5.apk)** | **Give this one.** Release-signed, 6.1 MB, published 2026-11-04: six languages and 987 006 dictionary entries, Czech, the Polish past-personal and conditional forms, the glide-and-type rule decided by the wordlist, settings one hold of the globe away, and the build number on the settings screen. Downloads as `interslove-3.5.apk`, so the file in the Downloads folder says what it is. | Everyone - someone trying it out and someone reporting back alike. |
| [Test build](https://drive.google.com/open?id=1vEifcc55MWuHJkkpil3rJGUXqSX0L9q7) | ⚠ **Adds nothing over 3.5 any more**: the release was cut from the same tree, so the Drive file (3.5.39, debug-signed, 7 MB) is now OLDER than what the link above gives. It is worth handing out again only once a test build runs ahead of the release - then it installs as `com.radoslove.interslavic.debug`, i.e. **next to** the published app rather than replacing it. | Nobody, until 3.6 work starts. |

Checked 2026-11-04, each one by its own measurement:

- **F-Droid has nothing to give.** `f-droid.org/packages/com.radoslove.interslavic/`
  answers **404**; [MR !45568](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568)
  is not merged. Do not send anyone there.
- **Both links above download without an account** (the Drive file is shared
  `anyone: reader`; GitHub serves the asset directly).
- **iPhone and iPad: nothing to hand out yet.** The Keyman catalogue still
  serves `isv_latin` **1.6**, and eleven keys on its `123` layer emit the wrong
  character ([keyman/FEEDBACK_ios.md](../keyman/FEEDBACK_ios.md)). The native iOS
  app has no distribution channel at all. An iPhone tester gets a defect, not a
  keyboard — wait for 1.7.
- **Desktop** (Windows, macOS, Linux) is a layout, not an app: the README
  sections cover it, and the per-key checks live in
  [TESTING.md](TESTING.md#core-checks--windows--macos--linux).

**A tester meets two scare screens before the keyboard, and both are expected.**
First Drive itself: *"Google Drive can't scan this file for viruses. This file is
executable and may harm your computer"* with a **Download anyway** button — every
APK on Drive gets it, whoever made it. Then, after the tap, Play Protect may add
that the app is not from the Play Store, because a test build is debug-signed.
The instruction below names both, in all three languages; without that, the first
click is where people quietly give up.

Both screens disappear with a release-signed `3.5` published as a GitHub release:
GitHub serves the APK directly, with no interstitial, and one public link then
serves testers and ordinary users alike.

## What we want back, in this round

The things this round changed, in the order a tester is likely to meet them:

1. **Polish personal past tense and the conditional.** Until 3.5.39 the wordlist
   had `zrobił` but not `zrobiłem`, `chciał` but not `chciałbym`, `żeby` but not
   `żebym` — 32 951 past-tense forms carrying 325 personal ones and four
   conditionals. Ask for `zrobiłem`, `chciałbym`, `mógłbym`, `byłem`, `byliśmy`,
   `poszłam`, `wziąłem`, `żebym`.
2. **Two words joining into one.** Swipe a word, then immediately type the next
   word's first letter: the two must stay apart. Typing an ending onto the word
   just swiped (`pisa` + `ć`) must still extend it.
3. **Junk among the suggestions.** Rare dictionary words outranking the obvious
   one. Worth a screenshot: the gesture plus what was offered.
4. **Language and script switching** — the globe key across six languages, the
   `ABC` key across Latin, Cyrillic and Glagolitic.
5. **A missing word** — the `+` chip adds it; Settings → my words removes it
   again (since 3.5.29).

Ask for three things in every report, because a verdict without them cannot be
reproduced: what you did, what you expected, what the keyboard actually typed.

---

## Medžuslovjansky

> Poprobuj medžuslovjansku klaviaturu na telefonu s Androidom
>
> To jest klaviatura za medžuslovjansky jezyk, s pisanjem gestami: vodiš prstom po
> literah i klaviatura najde slovo v slovniku. Slovnik imaje 248 840
> medžuslovjanskyh slov, s formami, ne samo lematami. Klaviatura takože znaje
> poljsky, češsky, norvežsky, anglijsky i medžuskandinavsky jezyk, a klaviša
> globusa prěključaje medžu nimi.
>
> Klaviatura ničto ne posyla i ničto ne zapisyvaje. Bez registracije, bez konta,
> bez e-pošty, bez telefona. Vse ostaje na tvojem telefonu. Potrěbuje Android 7
> abo novějši.
>
> **1. Vozmi fajl**
>
> To jest probna versija 3.5.39, 7 MB. Ona ne zaměnjaje tvoju klaviaturu, a stoji
> pri njej kako samostojna aplikacija.
>
> **2. Instaluj**
>
> Otvori fajl. Telefon pyta, jestli dozvoliš instalaciju aplikacije iz neznanogo
> města. Dozvoli. Google Play Protect može ostrěgati, že aplikacija ne jest iz
> Play Store. To jest normalno: fajl ide prosto od nas.
>
> **3. Vključi klaviaturu**
>
> V nastavjenjah telefona najdi klaviatury i vključi Medžuslovjansky. Potom otvori
> kakuju aplikaciju za pisanje i izberi našu klaviaturu. Android pokaže ostrěženje
> o novoj klaviaturě. Tako jest u vsih klaviatur, prosto potvrdi.
>
> Na ekranu jezykov klaviatury prěključ na vrhu to jest sam Medžuslovjansky. Ne klikaj na njego: potom ostane jedino Medžuslovjansky, a telefon može izbrati inu klaviaturu. Pod njim jest spis ostalyh jezykov — jezyky izbiraj tam.
>
> **4. Čto probovati**
>
> Piši gestami tako, kako pišeš normalno, cěly denj.
> Napiši slovo gestom, a potom bystro napiši prvu literu novogo slova.
> Prěključaj jezyky klavišeju globusa, a pismo (latinica, kirilica, glagolica)
> klavišeju ABC.
> Ako slova ne jest v slovniku, dodaj je klavišeju plus.
>
> **5. Piši nam**
>
> Grěšky i pytanja idut na GitHub Issues, abo prosto osobě, ktora dala tebe ov
> link. Napiši, čto ty dělal, čto očekyval, i čto klaviatura napisala.

Every Interslavic word above was swept against the canonical dictionary.
Standard orthography only — the four letters `č š ž ě` and nothing from the
extended alphabet, the same rule the README states for the layouts. The
dictionary stores headwords etymologically (`međuslovjańsky`, `pisańje`,
`nastavjeńja`, `pytańje`, `normaľno`), so a lookup that folds only the query will
report those as missing; fold both sides. `medžuskandinavsky` and the other five
language names are the ones the app itself uses.

## English

> Try the Interslavic keyboard on your Android phone
>
> This is a keyboard for the Interslavic language, with swipe typing: you drag a
> finger across the letters and the keyboard finds the word in its dictionary. The
> dictionary holds 248,840 Interslavic words — inflected forms, not just lemmas.
> It also types Polish, Czech, Norwegian, English and Interscandinavian, and the
> globe key switches between them.
>
> The keyboard collects nothing and sends nothing. No registration, no account, no
> e-mail, no phone number. Everything stays on your device. Needs Android 7 or
> newer.
>
> **1. Get the file**
>
> This is test build 3.5.39, 7 MB. It does not replace the keyboard you use now —
> it installs next to it as a separate app.
>
> **2. Install it**
>
> Open the file. The phone will ask whether to allow installing an app from an
> unknown source — allow it. Google Play Protect may warn you that the app does
> not come from the Play Store. That is expected: the file comes straight from us.
>
> **3. Turn the keyboard on**
>
> In your phone settings find the list of keyboards and switch on Medzuslovjansky.
> Then open any app you type in and pick our keyboard. Android shows a notice that
> the app cannot start until the phone is unlocked — every third-party keyboard
> gets that notice, just confirm it.
>
> On that screen the switch at the top IS Interslavic itself. Do not tap it: only Interslavic would be left, and the phone may switch to a different keyboard. The list below it holds the other languages — choose them there.
>
> **4. What to try**
>
> Type by swiping, all day, the way you normally write.
> Swipe a word, then immediately type the first letter of the next word.
> Switch languages with the globe key, and the script (Latin, Cyrillic,
> Glagolitic) with the ABC key.
> If a word is missing from the dictionary, add it with the + key.
>
> **5. Tell us**
>
> Bugs and questions go to GitHub Issues, or straight to the person who sent you
> this link. Write what you did, what you expected, and what the keyboard actually
> typed.

## Polski

> Wypróbuj klawiaturę międzysłowiańską na telefonie z Androidem
>
> To klawiatura do języka międzysłowiańskiego, z pisaniem gestami: przesuwasz
> palcem po literach, a klawiatura znajduje słowo w słowniku. Słownik ma 248 840
> międzysłowiańskich słów, w odmianie, nie same hasła. Klawiatura obsługuje też
> polski, czeski, norweski, angielski i międzyskandynawski, a klawisz globusa
> przełącza między nimi.
>
> Klawiatura nic nie zbiera i nic nie wysyła. Bez rejestracji, bez konta, bez
> adresu e-mail, bez numeru telefonu. Wszystko zostaje na telefonie. Wymaga
> Androida 7 albo nowszego.
>
> **1. Weź plik**
>
> To wersja próbna 3.5.39, 7 MB. Nie zastępuje twojej klawiatury, tylko staje
> obok niej jako osobna aplikacja.
>
> **2. Zainstaluj**
>
> Otwórz plik. Telefon zapyta, czy pozwalasz zainstalować aplikację z nieznanego
> źródła. Pozwól. Google Play Protect może ostrzec, że aplikacja nie pochodzi ze
> Sklepu Play. Tak ma być: plik idzie wprost od nas.
>
> **3. Włącz klawiaturę**
>
> W ustawieniach telefona znajdź listę klawiatur i włącz Medzuslovjansky. Potem
> otwórz dowolną aplikację do pisania i wybierz naszą klawiaturę. Android pokaże
> ostrzeżenie, że aplikacja nie może się uruchomić, dopóki telefon nie zostanie
> odblokowany. Tak jest przy każdej klawiaturze innej firmy, po prostu potwierdź.
>
> Na tym ekranie przełącznik na górze to sam międzysłowiański. Nie klikaj w niego: zostanie wtedy tylko międzysłowiański, a telefon może włączyć inną klawiaturę. Pod nim jest lista pozostałych języków — tam je wybieraj.
>
> **4. Co sprawdzić**
>
> Pisz gestami przez cały dzień, tak jak piszesz normalnie.
> Napisz słowo gestem, a potem od razu wpisz pierwszą literę następnego słowa.
> Przełączaj języki klawiszem globusa, a pismo (łacińskie, cyrylica, głagolica)
> klawiszem ABC.
> Jeśli słowa nie ma w słowniku, dodaj je klawiszem plus.
>
> **5. Napisz do nas**
>
> Błędy i pytania idą na GitHub Issues, albo wprost do osoby, która dała ci ten
> link. Napisz, co robiłeś, czego się spodziewałeś i co klawiatura naprawdę
> napisała.

---

The three versions carry the same download link and the same five steps; paste
one of them, add the link from the table above, and send it. Keep the `+`-chip
and my-words lines — a word a tester adds by hand is the clearest record of what
the dictionary is still missing.
