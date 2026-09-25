# Android keyboard — regression cases and open issues

Technical findings from testing. Related Keyman touch-layout issues are in
[keyman/FEEDBACK_ios.md](../keyman/FEEDBACK_ios.md). Version labels identify the
reported or changed source version, not a claim that every check has passed.

## Editing after a glide

The 3.2 changes added a short window for whole-word rejection, cleared pending
spacing on editing paths, and added recovery after deleting a word.
The 3.4 source change addresses an extra space when immediately extending a
just-glided word with a typed letter.

Verify on a real device:

- Glide a word, wait beyond the rejection window, then backspace: delete one
  letter rather than the whole word.
- Move the cursor into an earlier word and type: do not insert a deferred space.
- Delete a whole word and use its undo chip: restore the word and its spacing.
- Glide `pišem`, immediately type `š`: produce `pišemš` without an inserted space.
- After the correction window, type a new word: preserve normal word spacing.
- Glide consecutive words and type punctuation: preserve word boundaries and
  avoid a space before a comma.

## Symbols and editing controls

Version 3.3 added underscore beside the hyphen on the symbol layer.
The Android period key does not have a punctuation popup: punctuation is available
on the symbol layer. Verify the selected IME when reproducing layout reports;
debug, release and third-party keyboards may have similar names.

Proposals requiring separate implementation and verification:

- An editing panel with cursor movement and select/copy/cut/paste actions.
- Cursor movement by dragging the space bar.
- Better access to dash variants. Preserve the canonical character set and check
  symbol-layer long-press handling before moving any characters into a popup.

## Short-glide ranking

A glide intended as `se` was reported to prefer `sssr` / `sssre`. These are
existing dictionary entries; removing them would not address the ranking issue.
A possible cause is that initial dwell resembles repeated letters in a longer
candidate while a two-letter ideal route has little shape information.

Evaluate repeated-letter dwell constraints and short-word ranking separately.
Use `tools/swipe_eval.py` for regression measurements, then confirm on a real
device. An improvement on synthetic paths alone is insufficient.

## Public F-Droid tester feedback

The [September 23 report](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/45568#note_3889183213)
raised the following issues, which the 3.4 spacing change does not resolve:

- Suggestions remain active in password fields.
- Layout mirroring under Arabic system RTL.
- Potential inclusion of local word-usage data in Android backup.
- Missing enumeration of dictionary sources and redistribution terms.

Track each separately and verify the actual correction before calling it resolved.
Successful signing, compilation or reproducibility checks do not close these issues.
