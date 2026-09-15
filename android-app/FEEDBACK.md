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
