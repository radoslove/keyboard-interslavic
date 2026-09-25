# Keyman touch layout — regression cases and proposed fixes

Findings refer to keyboard version 1.6. Recheck the current catalogue package
before reporting whether a fix has shipped.

## Numeric-layer output

Eleven keys on the `123` layer were found to fall back to unshifted US key output
because they lacked the required modifier or explicit touch-key rule:

| Label | Incorrect output |
|---|---|
| `_` | `-` |
| `@` | `2` |
| `$` | `4` |
| `#` | `3` |
| `%` | `5` |
| `&` | `7` |
| `(` | `9` |
| `)` | `0` |
| `*` | `8` |
| `+` | `=` |
| `\|` | `\` |

Proposed fix: generate explicit touch-only `T_` identifiers and output rules,
including shifted touch rules, using the existing `TOUCH_PUNCT` pattern in
`build_keyman.py`. Check every label/output pair on a real iOS device.

## Punctuation discoverability

Long-pressing the period exposes punctuation, including `?`, but it is difficult
to discover. Proposed improvements are a dedicated comma key and direct symbol
keys for commonly used punctuation. Preserve access to less common symbols.
The native Android symbol layout provides a useful reference.

Change the generator, regenerate the layout and package, and verify the result on
a device before updating a catalogue submission. A successful package build alone
does not establish that touch-layer dispatch is correct.
