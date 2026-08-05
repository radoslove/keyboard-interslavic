# Linux

Nie ma tu kopii — upstream `medzuslovjansky/keyboards` ma Linuksa **zrobionego i wydanego**,
jako jedyną platformę. Nie ma sensu tego duplikować.

## Skąd wziąć

Release **v0.0.1** (2026-04-21): https://github.com/medzuslovjansky/keyboards/releases

| Paczka | Dystrybucje |
|---|---|
| `isv-keyboard_0.0.1-1_all.deb` | Debian / Ubuntu |
| `isv-keyboard-0.0.1-1.fc42.noarch.rpm` | Fedora |

Paczka `.deb` ma podpis `.asc`; klucz publiczny leży w upstreamie w `linux/keys/public.gpg`.

## Co zawiera

Rozszerzoną łacinkę MS jako layout XKB (`isv`), zgodną z międzynarodowymi układami
łacińskimi. Instaluje się przez `debian/postinst`, który łata `evdev.xml`.

## Runy

Upstream nie ma run — na Linuksa nie ma ich jeszcze nigdzie. Do zrobienia z
`docs/runic-table.md` jako źródła, jeśli kiedyś będzie potrzeba.
