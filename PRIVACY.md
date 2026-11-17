# Privacy policy — Interslove keyboard

**Last updated: 2026-11-17 · Publisher: Interslove · Contact:
interslove.keyboard@gmail.com**

This policy covers the **Interslove** keyboard apps for Android and iPhone,
and the keyboard layouts and dictionaries published with them for Windows,
macOS, Linux and Keyman.

## The short version

**The keyboard has no access to the internet and sends nothing by itself.**
There is no account, no analytics, no advertising, no crash-reporting service
and no third-party SDK. What you type goes to the app you are typing in and
nowhere else.

Three optional features, all **off by default**, can prepare a file for the dictionary.
The file leaves your phone only when **you** send it with your own e-mail app.

## What the keyboard does with what you type

- Typed text is passed to the app you are typing in, like any keyboard. It is
  not stored, logged or transmitted.
- Word prediction, swipe typing, the word translator and the learning cards
  work **offline**, from dictionaries shipped inside the app and from data kept
  in the app's private storage on your device.
- Your own words, your learning cards and the counts that help the keyboard
  rank words for you stay on your device.

## Optional features (all off by default)

| Feature | What it keeps on your phone | What the file you may send contains |
|---|---|---|
| **New words** | Interslavic words you typed that the dictionary did not know | those words and how often you used them |
| **Popularity ranking** | how often you use Interslavic words the dictionary knows, and how often you type each punctuation mark | those words and counts, and the punctuation counts |
| **Gesture log** (hidden, for error reports) | the path of your finger during swipes and the words suggested and chosen | that log, plus the phone model and system version |

Each file also carries a random label such as `kbd-1a2b3c4d`, made by the app
when it is first used. It is not your phone's ID and is not linked to your
name; it keeps batches from one phone together, so that they can be deleted
on request.

When you tap *Send*, the app hands the file to your e-mail app. Your e-mail
app sends it, from your address, to interslove.keyboard@gmail.com, and you can
read it before sending. What arrives is used only to improve the dictionaries
and to fix the keyboard. It is not sold or shared with anyone.

## Deleting your data

Write to interslove.keyboard@gmail.com with your label (shown in the app's
settings), and every batch with that label is deleted. Clearing a list in the
app's settings deletes what is kept on your phone.

## Permissions

The Android app requests **no runtime permissions** and has no Internet
permission: no contacts, no storage, no location, no microphone. The iPhone
keyboard works without Full Access; Full Access, if you allow it, is used only
to share your settings and words between the keyboard and its own app.

Android and iOS show a generic warning when any third-party keyboard is
enabled ("this keyboard may be able to collect all the text you type"). They
show it for every keyboard. This one has no way to send anything anywhere.

## Data from the stores

If you install from Google Play or the App Store, the store gives the publisher the
same aggregated statistics it gives every developer (install counts, crash
counts by system version). Neither your identity nor your typing is part of
them. F-Droid and GitHub provide no data at all.

## Children

The keyboard has no accounts and no content shared with other people. The
optional features above involve sending e-mail, so they are meant for users
aged 13 and over.

## Changes

If a future version needs a new permission or changes what the optional files
contain, this policy will be updated **before** that version is released, and
the change will be listed in the release notes.

## Open source

The complete source code, including the dictionary sources, is public under
the MIT licence. Every statement above can be checked in the code.
