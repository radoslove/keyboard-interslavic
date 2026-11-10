# Sources of the Macedonian wordlist (`main_mk.combined`)

The downloads are not committed. To rebuild:

    curl -sSLfo dictionary/sources_mk/mk_50k.txt \
      https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/mk/mk_50k.txt
    curl -sSLfo dictionary/sources_mk/mk_MK.dic \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/mk/index.dic
    curl -sSLfo dictionary/sources_mk/mk_MK.aff \
      https://raw.githubusercontent.com/wooorm/dictionaries/main/dictionaries/mk/index.aff
    python -m pip install spylls
    python tools/build_mk_wordlist.py

| Source | What it gives | License |
|---|---|---|
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `mk_50k` (OpenSubtitles) | word forms and frequencies | CC BY-SA 4.0 |
| [hunspell-mk](https://github.com/wooorm/dictionaries/tree/main/dictionaries/mk) by Dimitrij Mijoski, read with [spylls](https://github.com/zverok/spylls) | which forms exist | **GPL-3** — lookup at build time, no text copied |

⚠ LibreOffice has no Macedonian dictionary, so this one comes from a different
collection and is GPL-3 rather than the tri-licence of the Croatian and Serbian
files. The arrangement is the same as for every other hunspell here: it is asked
"is this a word" while the list is built, and nothing out of it is shipped.

Built 2026-11-10: **41 394 forms**; hunspell rejected 7 049 of the 50 000 (14 %),
and 1 557 more were not pure Cyrillic or were stray single letters.

## The internal Latin

Macedonian is the first language here with no Latin orthography of its own, so
its wordlist is **transliterated when it is built**. Everything inside the
keyboard stays Latin — the wordlist, the glide decoder, the ranking, the learned
touch offset — and only the key labels and the text committed to the editor are
turned into Cyrillic (`Script.CYRILLIC_MK`).

The rule has to be reversible, and the usual transliteration is not: `љ њ џ` are
normally written `lj nj dž`, and Macedonian also spells those sequences as two
letters. Measured on the frequency list: **49 forms in 50 000 (0.098 %)** would
come back wrong, among them `илјада` and `илјади` — the word for *thousand* —
plus `волја`, `неволја`, `инјекција`, `надживее`. So each of those letters gets
one Latin character of its own:

| Cyrillic | internal | key |
|---|---|---|
| `љ` `њ` `џ` | `ǉ` `ǌ` `ǆ` | `q` `w` `x` — where these letters sit in the Cyrillic layouts of this family |
| `ѓ` `ќ` `ѕ` | `ǵ` `ḱ` `ẑ` | held on `g` `k` `z` |
| `ж` `ч` `ш` | `ž` `č` `š` | held on `z` `c` `s`, as everywhere else |

The pair `l` + `j` therefore stays a pair, and the digraph-key machinery that
Interslavic and Serbian use is switched OFF for Macedonian.

⚠ Also dropped by the builder: forms written in MIXED alphabets — a Cyrillic
word with a Latin `e` or `o` inside, which looks identical on screen. 551 such
pairs collide in the frequency list.

Five seeds, top-1 **77.9 %**, top-3 94.7 %. Lower than its neighbours for a
structural reason: three letters share the `z` key (`з ж ѕ`) and two each share
`g` and `k`, so more words compete for the same trail.

The resulting wordlist is a derivative of FrequencyWords and is therefore
distributed under **CC BY-SA 4.0**, not MIT.
