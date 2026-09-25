# Repository contents

User installation instructions are in [README.md](README.md) and
[docs/INSTALL.md](docs/INSTALL.md). This inventory describes repository components;
release availability and artifact hashes belong to the corresponding release.

| Path | Contents |
|---|---|
| `android/` | Unexpected Keyboard XML layouts |
| `android-app/` | Native Android IME source and Gradle project |
| `ios/` | Native iOS application and keyboard extension |
| `keyman/` | Keyman keyboard and lexical-model sources and packages |
| `dictionary/` | Wordlist data and compiled dictionary artifacts |
| `windows/src/` | Native Windows layout source |
| `windows/installers/` | Windows installers, layout binaries and install scripts |
| `mac/` | macOS keyboard layout |
| `linux/` | Linux installation guidance |
| `brand/` | Source artwork and generated store assets |
| `fastlane/metadata/android/` | Android listing text, screenshots and changelogs |
| `upstream_prep/` | Staged upstream catalogue submissions |
| `tools/` | Build, recovery and verification utilities |
| `docs/` | Public technical documentation |

The Latin character map is defined in
[docs/ms-latin-table.md](docs/ms-latin-table.md). Generators such as
`build_klc.py`, `build_keylayout.py` and `build_keyman.py` produce platform artifacts;
edit their inputs rather than generated output. The wordlist generator uses the
repository data or an explicitly selected `ISV_WORDLIST` input.

Private operational notes, local agent memory, account details and machine
configuration are not repository artifacts. Do not include them in commits.
