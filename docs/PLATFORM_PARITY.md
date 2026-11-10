# Platform parity: iOS ↔ Android

The two apps aim to be the same keyboard. This table lists where they still differ.

## Android has, iOS does not yet

| Feature | Android | iOS |
|---|---|---|
| A glide that decodes to nothing closes the previous glide (no stale word on backspace or in the strip) | 3.7 | 0.5.6, awaiting test |
| `lj` / `nj` keys on `q` / `w` in Cyrillic and Glagolitic; a digraph word swipes over either route | 3.6 | 0.5.7, awaiting test |
| Frequency weighted linearly for Polish and Czech | 3.5 | to check |
| Language names in the app's own language | 3.5 | open |
| Interscandinavian (`isc`) as a keyboard language | 3.5 | Keyman package only |
| App screen in four languages | 3.5 | open |
| Symbol counter | 3.5 | blocked without Full Access |
| Theme choice (automatic / light / dark) in the host app | 3.5 | in code, needs the App Group |
| Host app follows the keyboard's theme | 3.6 | in-keyboard settings already do |
| "My words" list in the app, each removable | 3.5 | in code, needs the App Group |

## iOS has, Android does not yet

Nothing open (iOS 0.5.1 changes are in Android 3.6).

## Deliberate differences

- **Languages:** iOS has one keyboard extension per language; Android has one keyboard with
  languages ticked in the app and 🌐 cycling them.
- **Settings:** iOS asks for Full Access only to share data with its app (own words, theme,
  script). It has no network code and works fully without it.
- **Script key** (ABC / АБВ / ⰀⰁⰂ) shows for Interslavic only, on both platforms.
- **Slowest-sample glide anchors:** on Android; off on iOS, where the end of a glide is not
  sampled while the finger rests.
- **Doubled-letter penalty:** Android only; iOS has no per-word usage boost to pair it with.

## Shared

- Layout tables are kept in step by hand in `Layout.swift` and `Layout.kt`.
- Dictionary format differs: iOS reads a compact binary, Android parses `.combined` text.
