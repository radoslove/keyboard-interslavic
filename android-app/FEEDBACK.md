# Android keyboard — feedback from live use

*Companion to `keyman/FEEDBACK_ios.md`. Jira: KAN-137 (Android: poprawki z testów na żywo). Lane: `dev-keyboard`.*

## 2026-09-08 · A55, v3.1 — owner writing a real e-mail (longest text so far)

Three related complaints, all in the "edit a swiped word" path:

### 1. Backspace after a swipe deletes the whole word when I only wanted to fix the ending
**Cause (by design, `KeyboardView.kt` `backspace()` ~L575):** a backspace while `swipeJustCommitted` wipes the entire `lastSwipeWord` ("that wasn't the word I wanted"). Correct for a wrong guess, wrong for a *nearly right* guess — and inflection is exactly the case where the stem is right and the ending is off (`pišem` → `pišeš`). In Interslavic this is the common case, not the edge case.

**Proposal:**
- Whole-word wipe only within a short window after the commit (≈1.2 s) **and** only on the first press; any later backspace is letter-by-letter.
- Keep the whole-word wipe reachable deliberately: long-press backspace already deletes by word (~L640) — that is enough.

### 2. After editing, a space appears "out of nowhere"
**Cause (probable, `pendingSpace` ~L515):** smart-space arms `pendingSpace` after a swipe; it is cleared on the swipe-delete path but **not when the user moves the cursor or edits elsewhere** (`onSelectionChanged` ~L552 only stores the span). Tap into an older word, type a letter → the owed space is paid in the wrong place.

**Proposal:** clear `pendingSpace` and `swipeJustCommitted` whenever the cursor moves anywhere other than the position right after the last commit (compare in `onSelectionChanged`), and on every delete path.

### 3. Undo for "I deleted too much"
**Proposal:** after any whole-word delete (swipe-reject or long-press), remember `lastDeleted` + whether a space preceded it, and show it as the first chip in the suggestion bar as **↶ word** for one action; tapping re-commits it (with the space) and restores `lastSwipeWord`. One-shot, no stack, disappears on the next key. Cheap to build, removes the fear that makes people stop using swipe.

### Done criteria
- Swipe `pišem`, wait 2 s, backspace ×1, type `š` → `pišeš` (not an empty field). *(2026-09-08: built in v3.2, on-device check pending.)*
- Swipe two words, tap into the first, type a letter → no space inserted before it.
- Long-press backspace on a word → chip **↶ word** appears; tap → word restored exactly, including the space before it.
- v3.2 build (also carries the new launcher icon).

## 2026-09-09 · Galaxy A26, v3.2 — first day on the fixed build

**Verdict so far:** "całkiem spoko" — the three edit fixes hold up in real use (on-device checks 1–5 from above still to be ticked one by one).

### 4. "Easier text copying"
Today the keyboard offers nothing for moving text around; copying a whole message means the system long-press → Select all → Copy dance, which is slow with a swipe keyboard that is otherwise fast.

**Proposal (v3.3): an edit panel, reachable from a key on the bottom row (long-press `123` or a dedicated ✂ key):**
- row 1: ◀ ▶ cursor arrows, **Select all**, **Copy**, **Cut**, **Paste**
- row 2: last 3 clipboard entries as chips (own in-app history, on-device only, cleared on app restart; no clipboard listener in background — stays true to "no permissions, nothing leaves the phone")
- All of it is `InputConnection.performContextMenuAction(android.R.id.selectAll / copy / cut / paste)` + `sendKeyEvent(DPAD_LEFT/RIGHT)`; no new permissions, ~1 evening.
- Bonus that costs nothing: long-press **space** + drag = move the cursor (HeliBoard/Gboard convention), which is half of "copying" in practice.

Done criteria: from an empty field, swipe three words, open panel, Select all → Copy → Paste twice → field holds the sentence three times; cursor arrows move one char per tap; clipboard chip re-inserts the last copied text.

## 2026-09-21 · Galaxy A55, v3.2 — first day on the second device

⚠ Device note: the **A26 is out** — its network stack no longer joins home WiFi,
the garage WiFi or an iPhone hotspot. The A55 is the Android test device from now
on. v3.2 was installed on it over the tailnet/LAN from `hp`.

**Verdict:** "pisze się coraz lepiej." Real Interslavic typed on it:
*kako se pisati sejčas? ne zle, jednako tu jest nadal trohu do popravy;)*

### 5. Long-press on `.` does nothing — NOT A BUG, by design

Reported as a surprise; it is the intended behaviour. `Layout.longPress()` returns
a value only for the four accented letters (`c s z e`) and the three digraphs
(`d l n`). The period has no popup, because `? ! : ; „ ” – — ’` are **direct keys**
on the `?123` layer (`symbolRows` row 3) — one tap, no picker.

That is the opposite of the Keyman layout, where the same punctuation hides under
a long-press on `.` and is invisible to a new user — which is exactly what
`keyman/FEEDBACK_ios.md` v1.7 sets out to fix. **The Android design is the one to
copy to iOS, not the other way round.**

No code change. Worth a line in the user guide instead: on `?123`, the third row
is the punctuation row.

### 6. A short glide `se` decodes as `sssr`  🔴 ranking bug

Swiping `s`→`e` put **`sssr`** in the field, offering `sssr` · `sssre` · `se`.

**Not junk data:** `sssr` is СССР and is a legitimate dictionary entry. The problem
is ranking. Measured from `main_isv.combined` and `Dictionary.decodeSwipeGeo`:

| word | f | frequency bonus = `ln(f+1) × FREQ_W(3.5)` |
|---|---|---|
| `se` | 205 | **18.65** |
| `sssr` | 26 | 11.54 |
| `sssre` | 26 | 11.54 |

So `se` starts **+7.11 ahead** and still lost, meaning its *geometry* score was
worse by more than that.

**Mechanism (hypothesis, testable offline):** a two-letter word has a two-point
ideal route, which cannot absorb a finger that lingers on the first key. `sssr`
has three consecutive `s` — a perfect sponge for exactly that dwell — and its
final `r` sits next to `e`. So a hesitant start on a short word is read as a
repeated letter. The shorter the word, the less shape information there is to
overrule it, and `se` is as short as words get.

**Where to fix:** `tools/swipe_eval.py` mirrors the shipped decoder, so this needs
no device round-trip. Candidate directions, in order of how principled they are:

1. **Require a velocity minimum for a repeated letter.** The machinery already
   exists (`velocityPivots`, `V_RADIUS`, `V_MIN_SEP`). A letter may only repeat
   where the finger genuinely dwelled, not merely where it passed slowly.
2. **Treat very short targets as a special case** — with almost no shape
   information, frequency should dominate harder for 2–3 letter candidates.
3. Raising `FREQ_W` is NOT the answer: 3.5 is the measured peak and the comment
   records that past it the score starts costing.

**Done criteria:** in the harness, a synthetic glide `s`→`e` with a slow start
returns `se` at top-1; and the existing top-1/top-3 numbers over the sample do not
regress. Only then to a device.

**Not a release blocker** — v3.2 is in daily use and the owner's verdict is that it
keeps getting better. This belongs in v3.3 next to the edit panel (§4).

## 2026-09-23 · A26, v3.3 potwierdzona na urządzeniu

Owner: „jest `_` w końcu". Drugi rząd warstwy `?123` ma 11 klawiszy i kończy się na
`_`, tuż obok `-`. Zamyka §3 z 2026-09-22. Pisane na niej po MS:
„_ a sejčas jest normalno ili..?"

⚠ Do zapamiętania, bo kosztowało godziny: na telefonie były zainstalowane **trzy**
klawiatury o mylących nazwach — nasza (`Medžuslovjansky`), nasz build debug
(`Medžuslovjansky (test)`, osobne `applicationId` przez `applicationIdSuffix=".debug"`,
więc NIE aktualizuje się razem z release) oraz obca `Interslavic QWERTY (latinica)`.
Dwa kolejne „nie działa" były w rzeczywistości pisaniem na cudzej klawiaturze.
**Znak rozpoznawczy naszej: `🌐` w dolnym rzędzie, brak klawisza emoji i zębatki.**

### 7. Trzy rodzaje myślnika to o dwa za dużo

Owner, przy tej samej okazji: „tych `-` nie potrzebne aż trzy rodzaje, oprócz `_`".

Dziś na `?123` są trzy: **`-`** (dywiz, rząd 2) oraz **en dash** i **em dash**
(rząd 3, obok cudzysłowów). Pochodzą z kanonicznej tabeli znaków MS
(`docs/ms-latin-table.md`), gdzie siedzą na AltGr na desktopie — więc na telefon
wjechały „bo były", a nie dlatego, że ktoś ich tam potrzebuje.

**Do rozważenia w v3.4:** zostawić `-` jako klawisz, a obie dłuższe kreski przenieść
pod long-press dywizu — tak robi większość klawiatur. Zwalnia to dwa miejsca w rzędzie 3.

⚠ To nie jest przestawienie napisu: **warstwa symboli nie ma dziś long-pressu w ogóle**
(`KeyboardView.kt:1059`, `if (symbols) return false`). Trzeba go tam najpierw włączyć —
co przy okazji odblokowuje każde późniejsze zagęszczanie tej warstwy.

⚠ Zmiana dotyka kanonu znaków → przed wykonaniem sprawdzić w `docs/ms-latin-table.md`,
czy obie dłuższe kreski mają zostać osiągalne bezpośrednio (cytowanie w MS ich używa).
