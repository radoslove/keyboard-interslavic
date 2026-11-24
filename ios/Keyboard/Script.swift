import Foundation

/// The script the keyboard WRITES in: Latin (default), Cyrillic or Glagolitic.
/// Ported from Android `Script.kt` (14a658f, 3.5.6); tables in
/// `docs/script-tables.md`.
///
/// Everything inside the keyboard stays Latin - the wordlist, the swipe
/// decoder, the user's own words. Only three things cross this boundary: key
/// labels, the text put into the document, and the text read back from before
/// the cursor (turned back into Latin to look it up). The letters sit on the
/// same keys as in Latin (c -> ц, hold for ч), so a swipe drawn for `ljubov` is
/// decoded exactly as before and written out as `љубов` or `ⰾⱓⰱⱁⰲ`.
///
/// Cyrillic = js-utils `latn2cyrl` core, standard letters only (lj -> љ,
/// nj -> њ, ě -> є, y -> ы, j -> ј; dž = д + ж). Glagolitic = the original
/// interslavic-dictionary.com converter ("v1"): m -> ⱞ, y -> ⱐⰹ,
/// ja/ju/jo -> ⱝ/ⱓ/ⱖ, j before another vowel -> ⰹ, a softening j after l/n with
/// no vowel after it -> ⱐ. Letters with no counterpart (q, w, x) pass through.
///
/// The choice lives in `Shared.store`: with Full Access the app can set it
/// too; without it the script key is the only switch - which is also how
/// Android uses it day to day.
enum Script {

    enum Mode: String, CaseIterable { case latin, cyrillic, glagolitic }

    /// The scripts a language offers (Android `Script.offered`, 3.8.8-3.8.11).
    /// Serbian is written in both alphabets, one to one, so it gets the script
    /// key with two modes and keeps ONE Latin wordlist; Macedonian has no Latin
    /// orthography, so it offers Cyrillic alone and opens in it.
    static func offered(_ lang: String) -> [Mode] {
        switch lang {
        case "isv": return [.latin, .cyrillic, .glagolitic]
        // Polish in Glagolitic: an add-on alphabet, Latin stays the default.
        case "pl": return [.latin, .glagolitic]
        case "sr": return [.latin, .cyrillic]
        // Proto-Slavic is Latin, but `ъ` `ь` live on `q` `f` inside, so it goes
        // through the same one-mode table as Russian; no script key is shown.
        case "mk", "ru", "uk", "bg", "be", "sla": return [.cyrillic]
        default: return [.latin]
        }
    }

    /// The modes of THIS keyboard's language.
    static var modes: [Mode] { offered(Layout.language) }

    /// Each language remembers its own script; Interslavic keeps the old key,
    /// so nobody's chosen alphabet is lost to the change.
    private static var key: String {
        Layout.language == "isv" ? "script" : "script_" + Layout.language
    }

    /// The chosen script of this keyboard's language.
    static var mode: Mode {
        let can = modes
        if can.count < 2 { return can[0] }      // Macedonian: Cyrillic, not Latin
        let m = Mode(rawValue: Shared.store.string(forKey: key) ?? "") ?? .latin
        return can.contains(m) ? m : .latin
    }

    static func set(_ m: Mode) { Shared.store.set(m.rawValue, forKey: key) }

    /// Morse (Android `Mode.MORSE`, 3.10.9): chosen like a script, stored in
    /// the same key, but never what `mode` returns - in Morse the keyboard works
    /// in the language's first script and `Doc` writes dots and dashes.
    static var morse: Bool {
        Morse.has(Layout.language) && Shared.store.string(forKey: key) == "morse"
    }
    static func setMorse() { Shared.store.set("morse", forKey: key) }

    /// Runes (Norwegian only, 261118), chosen like Morse: two rows.
    static var runes: Runes.Row? {
        guard Runes.has(Layout.language) else { return nil }
        return Runes.Row(rawValue: Shared.store.string(forKey: key) ?? "")
    }

    /// What the script key and the ⚙ panel offer, in order: the alphabets,
    /// the rune rows, Morse - as the stored values.
    static var choices: [String] {
        modes.map(\.rawValue)
            + (Runes.has(Layout.language) ? Runes.Row.allCases.map(\.rawValue) : [])
            + (Morse.has(Layout.language) ? ["morse"] : [])
    }
    static var choiceCount: Int { choices.count }
    /// The choice in use now.
    static var choice: String { morse ? "morse" : runes?.rawValue ?? mode.rawValue }
    static func setChoice(_ c: String) { Shared.store.set(c, forKey: key) }
    static func choiceLabel(_ c: String) -> String {
        if c == "morse" { return morseLabel }
        if let r = Runes.Row(rawValue: c) { return Runes.label(r) }
        return Mode(rawValue: c).map(label) ?? c
    }

    /// The next choice, wrapping round; the script key next to 🌐.
    @discardableResult
    static func next() -> Mode {
        let all = choices
        let i = all.firstIndex(of: choice) ?? 0
        setChoice(all[(i + 1) % all.count])
        return mode
    }

    /// The first three letters of the current alphabet - the same convention
    /// as `ABC` on `123` and the ABC/АБВ switch of Serbian keyboards.
    static var keyLabel: String { choiceLabel(choice) }
    /// `·−` (Morse for "A"; Android shows the same on its key).
    static let morseLabel = "·−"

    static func label(_ m: Mode) -> String {
        switch m {
        case .latin: return "ABC"
        case .cyrillic: return "АБВ"
        case .glagolitic: return "ⰀⰁⰂ"
        }
    }

    // MARK: Cyrillic - a flat table, two-letter keys tried first

    private static let cyrillicPairs: [(String, String)] = [
        ("lj", "љ"), ("nj", "њ"),
        ("a", "а"), ("b", "б"), ("c", "ц"), ("č", "ч"), ("d", "д"), ("e", "е"), ("ě", "є"),
        ("f", "ф"), ("g", "г"), ("h", "х"), ("i", "и"), ("j", "ј"), ("k", "к"), ("l", "л"),
        ("m", "м"), ("n", "н"), ("o", "о"), ("p", "п"), ("r", "р"), ("s", "с"), ("š", "ш"),
        ("t", "т"), ("u", "у"), ("v", "в"), ("y", "ы"), ("z", "з"), ("ž", "ж"),
    ]

    /// Serbian Cyrillic: the standard, fully reversible pairing of the two
    /// Serbian alphabets (Android `CYRILLIC_SR`). Not the Interslavic table:
    /// Serbian has no `ě`/`y`, has `ć` `đ` as letters, and `dž` is ONE letter.
    /// ⚠ A `d` ending a prefix before `ž` (`nadživeti` = `надживети`) comes out
    /// as `џ` - that needs the morpheme boundary, not a table; same on Android.
    private static let serbianPairs: [(String, String)] = [
        ("lj", "љ"), ("nj", "њ"), ("dž", "џ"),
        ("a", "а"), ("b", "б"), ("c", "ц"), ("č", "ч"), ("ć", "ћ"), ("d", "д"), ("đ", "ђ"),
        ("e", "е"), ("f", "ф"), ("g", "г"), ("h", "х"), ("i", "и"), ("j", "ј"), ("k", "к"),
        ("l", "л"), ("m", "м"), ("n", "н"), ("o", "о"), ("p", "п"), ("r", "р"), ("s", "с"),
        ("š", "ш"), ("t", "т"), ("u", "у"), ("v", "в"), ("z", "з"), ("ž", "ж"),
    ]

    /// Macedonian: the internal Latin of `tools/build_mk_wordlist.py` back into
    /// Cyrillic, one character on each side (Android `CYRILLIC_MK`). `ǉ ǌ ǆ`
    /// rather than `lj nj dž`, because Macedonian also spells those sequences
    /// as two letters (`илјада`), so a pair table could not be turned back.
    private static let macedonianPairs: [(String, String)] = [
        ("a", "а"), ("b", "б"), ("v", "в"), ("g", "г"), ("d", "д"), ("ǵ", "ѓ"),
        ("e", "е"), ("ž", "ж"), ("z", "з"), ("ẑ", "ѕ"), ("i", "и"), ("j", "ј"),
        ("k", "к"), ("l", "л"), ("ǉ", "љ"), ("m", "м"), ("n", "н"), ("ǌ", "њ"),
        ("o", "о"), ("p", "п"), ("r", "р"), ("s", "с"), ("t", "т"), ("ḱ", "ќ"),
        ("u", "у"), ("f", "ф"), ("h", "х"), ("c", "ц"), ("č", "ч"), ("ǆ", "џ"),
        ("š", "ш"),
    ]

    /// Russian on the phonetic layout (Android `CYRILLIC_RU`): the internal Latin
    /// of `tools/build_cyrillic_wordlist.py`, one character on each side. A key's
    /// letter is what it writes (я q, ш w, ы y, ч h, х x, ц c, й j); the held
    /// letters carry a mark that folds onto their key.
    private static let russianPairs: [(String, String)] = [
        ("a", "а"), ("b", "б"), ("v", "в"), ("g", "г"), ("d", "д"), ("e", "е"),
        ("ë", "ё"), ("ž", "ж"), ("z", "з"), ("i", "и"), ("j", "й"), ("k", "к"),
        ("l", "л"), ("m", "м"), ("n", "н"), ("o", "о"), ("p", "п"), ("r", "р"),
        ("s", "с"), ("t", "т"), ("u", "у"), ("f", "ф"), ("x", "х"), ("c", "ц"),
        ("h", "ч"), ("w", "ш"), ("ŵ", "щ"), ("ḅ", "ъ"), ("y", "ы"), ("ḃ", "ь"),
        ("ê", "э"), ("ü", "ю"), ("q", "я"),
    ]

    /// Ukrainian, homophonic (`tools/build_cyrillic_wordlist.py` UK_B): one
    /// character on each side; the apostrophe passes through as it is.
    private static let ukrainianPairs: [(String, String)] = [
        ("a", "а"), ("b", "б"), ("v", "в"), ("h", "г"), ("g", "ґ"), ("d", "д"),
        ("e", "е"), ("ê", "є"), ("ž", "ж"), ("z", "з"), ("y", "и"), ("i", "і"),
        ("ï", "ї"), ("j", "й"), ("k", "к"), ("l", "л"), ("m", "м"), ("n", "н"),
        ("o", "о"), ("p", "п"), ("r", "р"), ("s", "с"), ("t", "т"), ("u", "у"),
        ("f", "ф"), ("x", "х"), ("c", "ц"), ("č", "ч"), ("w", "ш"), ("ŵ", "щ"),
        ("ḃ", "ь"), ("ü", "ю"), ("q", "я"),
    ]

    /// Bulgarian, traditional phonetic (`tools/build_cyrillic_wordlist.py`
    /// BG_A): one character on each side.
    private static let bulgarianPairs: [(String, String)] = [
        ("a", "а"), ("b", "б"), ("w", "в"), ("g", "г"), ("d", "д"), ("e", "е"),
        ("v", "ж"), ("z", "з"), ("i", "и"), ("ì", "ѝ"), ("j", "й"), ("k", "к"),
        ("l", "л"), ("m", "м"), ("n", "н"), ("o", "о"), ("p", "п"), ("r", "р"),
        ("s", "с"), ("t", "т"), ("u", "у"), ("f", "ф"), ("h", "х"), ("c", "ц"),
        ("č", "ч"), ("š", "ш"), ("ŝ", "щ"), ("y", "ъ"), ("x", "ь"), ("ü", "ю"),
        ("q", "я"),
    ]

    /// Belarusian `by(phonetic)` (`tools/build_cyrillic_wordlist.py` BE_A,
    /// Android `CYRILLIC_BE` verbatim): one character on each side.
    private static let belarusianPairs: [(String, String)] = [
        ("a", "а"), ("b", "б"), ("w", "в"), ("g", "г"), ("d", "д"), ("e", "е"),
        ("ë", "ё"), ("v", "ж"), ("z", "з"), ("i", "і"), ("j", "й"), ("k", "к"),
        ("l", "л"), ("m", "м"), ("n", "н"), ("o", "о"), ("p", "п"), ("r", "р"),
        ("s", "с"), ("t", "т"), ("u", "у"), ("ŭ", "ў"), ("f", "ф"), ("h", "х"),
        ("c", "ц"), ("č", "ч"), ("š", "ш"), ("y", "ы"), ("x", "ь"), ("ê", "э"),
        ("ü", "ю"), ("q", "я"),
    ]

    /// Proto-Slavic: only the two yers differ from the internal Latin; every
    /// other letter passes through unchanged.
    private static let protoSlavicPairs: [(String, String)] = [("q", "ъ"), ("f", "ь")]

    /// The Cyrillic table of this keyboard's language.
    private static func table(_ lang: String) -> [(String, String)] {
        switch lang {
        case "sr": return serbianPairs
        case "mk": return macedonianPairs
        case "ru": return russianPairs
        case "uk": return ukrainianPairs
        case "bg": return bulgarianPairs
        case "be": return belarusianPairs
        case "sla": return protoSlavicPairs
        default: return cyrillicPairs
        }
    }
    /// Built once per language; the language now changes while the keyboard runs.
    private static let maps: [String: [String: String]] = Dictionary(uniqueKeysWithValues:
        ["isv", "sr", "mk", "ru", "uk", "bg", "be", "sla"].map { ($0, Dictionary(uniqueKeysWithValues: table($0))) })
    private static var cyrillicMap: [String: String] {
        maps[Layout.language] ?? maps["isv"]!
    }

    // MARK: Glagolitic (v1) - letters + the j rules

    private static let glagLetters: [Character: String] = [
        "a": "ⰰ", "b": "ⰱ", "c": "ⱌ", "č": "ⱍ", "d": "ⰴ", "e": "ⰵ", "ě": "ⱑ",
        // f = ⱚ and y = ⱏⰹ (hard yer + i) like Polish Glagolitic (owner 261124;
        // were ⱇ and ⱐⰹ - old text still reads back).
        "f": "ⱚ", "g": "ⰳ", "h": "ⱈ", "i": "ⰻ", "k": "ⰽ", "l": "ⰾ", "m": "ⱞ",
        "n": "ⱀ", "o": "ⱁ", "p": "ⱂ", "r": "ⱃ", "s": "ⱄ", "š": "ⱎ", "t": "ⱅ",
        "u": "ⱆ", "v": "ⰲ", "y": "ⱏⰹ", "z": "ⰸ", "ž": "ⰶ",
        // dzelo, from hold d (261124, like Polish Glagolitic): marker ẑ.
        "ẑ": "ⰷ",
        // the old ja ligature ⱝ, from hold q (261124): marker ȧ.
        "ȧ": "ⱝ",
        // a d that does NOT join a following z, from hold d: marker ḍ
        "ḍ": "ⰴ",
        // bare yers from the v key (layout 261124), markers like Polish
        "\u{02B9}": "ⱐ", "\u{02BA}": "ⱏ",
    ]
    /// ja is Ⱑ like in Polish Glagolitic (owner 261124: "żeby to ja zostało
    /// takie jak w pl"; it was ⱝ). Ⱑ is also ě, so reading back decides by
    /// the letter before it (`jaFromJat`).
    private static let jLigature: [Character: String] = ["a": "ⱑ", "u": "ⱓ", "o": "ⱖ"]
    private static let vowels: Set<Character> = ["a", "e", "ě", "i", "o", "u", "y"]

    /// Latin text -> the current script. Case follows the source:
    /// `Lj` -> `Љ`, `LJUBOV` -> `ЉУБОВ`.
    static func out(_ latin: String) -> String {
        // A titlo popup item: the letter in the script, the mark after it.
        if let t = Titlo.split(latin) { return Titlo.apply(convert(t.base, mode), t.mark) }
        return convert(latin, mode)
    }

    static func convert(_ latin: String, _ m: Mode) -> String {
        if latin.isEmpty || m == .latin { return latin }
        if m == .glagolitic && Layout.language == "pl" { return toGlagoliticPL(latin) }
        return m == .cyrillic ? toCyrillic(latin) : toGlagolitic(latin)
    }

    /// Polish written in Glagolitic: several Latin letters can become one
    /// Glagolitic one (cz -> ⱍ, sia -> ⱄⱑ), so a letter typed at the end of a
    /// word can change what is already on the screen. The keyboard then
    /// re-writes the whole word under the cursor (`rewritesWord`).
    static var rewritesWord: Bool { mode == .glagolitic && Layout.language == "pl" }

    /// Glyphs typed as such (x key, hold on o): the word re-write starts after
    /// the last of them, so they are never turned back into ⰴⰸ or ⱁ́.
    static let atomicGlyphs: Set<Character> = ["ⰷ", "Ⰷ", "ⱉ", "Ⱉ"]

    /// True when the glyph at `i` ends something typed as such: ⰷ, ⱉ, or the
    /// soft yer right after d (ⰴⱐ, held d) - the converter never makes ⰴⱐ itself.
    static func endsAtomic(_ w: [Character], _ i: Int) -> Bool {
        if atomicGlyphs.contains(w[i]) || Titlo.has(w[i]) { return true }
        return (w[i] == "ⱐ" || w[i] == "Ⱐ") && i > 0 && (w[i - 1] == "ⰴ" || w[i - 1] == "Ⰴ")
    }

    /// The v key in Polish Glagolitic writes the soft yer Ⱐ as a kreska, its
    /// hold the hard yer Ⱏ (layout v04). Inside they are the
    /// modifier letters ʹ ʺ (letters to Swift, so a word goes on through them);
    /// `softenPL` folds them into the letter before where Polish has one.
    static let softYerMark = "\u{02B9}"
    static let hardYerMark = "\u{02BA}"

    private static let softened: [(String, String)] = [
        ("s", "ś"), ("z", "ź"), ("c", "ć"), ("n", "ń"), ("r", "rz"), ("d", "ď"),
    ]

    /// s + ʹ = ś, z ź, c ć, n ń (so dz + ʹ = dź), r rz, d ď (ⰴⱐ); l + ʺ = ł.
    /// Any other pair keeps the marker, which writes a bare Ⱐ or Ⱏ.
    static func softenPL(_ latin: String) -> String {
        var out: [Character] = []
        for ch in latin {
            let s = String(ch)
            if s == softYerMark || s == hardYerMark, let prev = out.last {
                let lowPrev = String(prev).lowercased()
                let up = prev.isUppercase
                let table: [(String, String)] = s == softYerMark
                    ? softened : [("l", "ł")]
                if let (_, to) = table.first(where: { $0.0 == lowPrev }) {
                    out.removeLast()
                    let t = up ? to.prefix(1).uppercased() + to.dropFirst() : to
                    out.append(contentsOf: t)
                    continue
                }
            }
            out.append(ch)
        }
        return String(out)
    }

    /// The word space written in Polish Glagolitic: en space, wider than " ".
    static let wideSpace = "\u{2002}"

    // MARK: Polish Glagolitic - the add-on's canon (vault note glagolica_pl_261114)

    /// Longest first. Jotted vowels after a consonant mark its softness
    /// (siano -> ⱄⱑⱀⱁ), the soft yer ⱐ softens (ś ź ć ń dź rz), the hard yer ⱏ
    /// hardens (ł, y). Same table as the converter `pl2glag.py`.
    /// m = ⱞ like isv (owner 261124; was ⰿ, which still reads back).
    private static let polishGlag: [(String, String)] = [
        // Two forms kept beside the canon: x writes the
        // historical dzelo ⰷ, holding o offers otu ⱉ. Inside they are the
        // markers ẑ and ō, so the word re-write leaves them as typed.
        ("ẑ", "ⰷ"), ("ō", "ⱉ"),
        // d softened by the soft yer, offered beside dź: marker ď.
        ("ď", "ⰴⱐ"),
        // v key: bare yers where Polish has no letter for the pair (layout v04)
        ("\u{02B9}", "ⱐ"), ("\u{02BA}", "ⱏ"),
        ("ja", "ⱑ"), ("ia", "ⱑ"), ("ju", "ⱓ"), ("iu", "ⱓ"), ("ję", "ⱗ"), ("ię", "ⱗ"), ("ją", "ⱙ"), ("ią", "ⱙ"),
        ("sz", "ⱎ"), ("cz", "ⱍ"), ("ż", "ⰶ"), ("ą", "ⱘ"), ("ę", "ⱔ"),
        ("dż", "ⰴⰶ"), ("dź", "ⰷⱐ"), ("dz", "ⰷ"),   // dz is dzelo at once
        // marker ḍ: a d that must NOT join a following z (pod-zielić), from hold d
        ("ḍ", "ⰴ"), ("rz", "ⱃⱐ"),
        ("ś", "ⱄⱐ"), ("ź", "ⰸⱐ"), ("ć", "ⱌⱐ"), ("ń", "ⱀⱐ"),
        ("ł", "ⰾⱏ"), ("ó", "ⱁ\u{0301}"),
        // h = ⱈ like isv; ch is c + h (owner 261124; were ch = ⱈ, h = ⱒ)
        ("h", "ⱈ"), ("y", "ⱏⰹ"),
        ("a", "ⰰ"), ("b", "ⰱ"), ("c", "ⱌ"), ("d", "ⰴ"), ("e", "ⰵ"), ("f", "ⱚ"),
        ("g", "ⰳ"), ("i", "ⰻ"), ("j", "ⰹ"), ("k", "ⰽ"), ("l", "ⰾ"), ("m", "ⱞ"),
        ("n", "ⱀ"), ("o", "ⱁ"), ("p", "ⱂ"), ("r", "ⱃ"), ("s", "ⱄ"), ("t", "ⱅ"),
        ("u", "ⱆ"), ("w", "ⰲ"), ("v", "ⰲ"), ("z", "ⰸ"),   // v writes ⰲ like w
    ]

    private static func toGlagoliticPL(_ latin: String) -> String {
        let caps = isAllCaps(latin)
        let src = Array(latin)
        let low = Array(latin.lowercased())
        guard src.count == low.count else { return latin }
        var out = ""
        var i = 0
        outer: while i < low.count {
            for (lat, gl) in polishGlag {
                let n = lat.count
                guard i + n <= low.count, String(low[i..<i + n]) == lat else { continue }
                out += cased(gl, src[i], caps)
                i += n
                continue outer
            }
            out.append(src[i]); i += 1
        }
        return out
    }

    /// Polish d + z across a prefix (od-zyskać, nad-zwyczajny, pod-ziemie,
    /// przed-zamcze) are two sounds, not the one sound dz. The rule: the
    /// word starts with od / nad / pod / przed, z follows, and
    /// what follows the prefix is a word of its own -> that d gets the marker ḍ,
    /// which never joins a z (podziemie = ⰒⱁⰄⰸⰻⰵⰿⰻⰵ). podzielić (po + dzielić),
    /// odzież, nadzieja: "zielić", "zież", "zieja" are no words, so dz stays ⰷ.
    static func splitPrefixD(_ latin: String, known: (String) -> Bool) -> String {
        let low = latin.lowercased()
        for prefix in ["przed", "pod", "nad", "od"] {
            guard low.hasPrefix(prefix + "z") || low.hasPrefix(prefix + "ź") || low.hasPrefix(prefix + "ż") else { continue }
            let rest = String(latin.dropFirst(prefix.count))
            guard rest.count >= 3, known(rest) else { return latin }
            var chars = Array(latin)
            let i = prefix.count - 1
            chars[i] = chars[i].isUppercase ? "Ḍ" : "ḍ"
            return String(chars)
        }
        return latin
    }

    private static let polishVowels: Set<Character> = ["a", "ą", "e", "ę", "i", "o", "ó", "u", "y"]

    /// Back to Polish Latin. A jotted vowel reads `ia` after a consonant and
    /// `ja` elsewhere; `ⰾⱏⰹ` is `ly` (Polish has no `łj`), so typing `l` `y`
    /// keeps going instead of turning into `łj`.
    private static let reversePolishGlag: [(String, String)] =
        ([("ⰾⱏⰹ", "ly"), ("ⰴⰸⱐ", "dź"), ("ⰴⰶ", "dż"), ("ⰴⰸ", "dz"), ("ⰷⱐ", "dź"), ("ⰷ", "dz"), ("ⱉ", "ó"), ("ⰴⱐ", "dź"), ("ⰿ", "m"), ("ⱒ", "h")]
         + polishGlag.filter { !["ia", "iu", "ię", "ią", "ja", "ju", "ję", "ją", "dż", "dź", "dz", "v", "ẑ", "ō", "ď", "ḍ"].contains($0.0) }
            .map { ($0.1, $0.0) })
        .sorted { $0.0.count > $1.0.count }
    private static let polishJotted: [Character: String] = ["ⱑ": "a", "ⱓ": "u", "ⱗ": "ę", "ⱙ": "ą"]

    private static func backPolish(_ s: String) -> String {
        let chars = Array(s)
        var out = ""
        var i = 0
        outer: while i < chars.count {
            let low = Character(String(chars[i]).lowercased())
            if let v = polishJotted[low] {
                let afterConsonant = out.last.map { $0.isLetter && !polishVowels.contains(Character($0.lowercased())) } ?? false
                let lat = (afterConsonant ? "i" : "j") + v
                out += chars[i].isUppercase ? lat.prefix(1).uppercased() + lat.dropFirst() : lat
                i += 1; continue
            }
            for (scr, lat) in reversePolishGlag {
                let n = scr.count
                guard i + n <= chars.count else { continue }
                if String(chars[i..<i + n]).lowercased() == scr.lowercased() {
                    out += chars[i].isUppercase ? lat.prefix(1).uppercased() + lat.dropFirst() : lat
                    i += n
                    continue outer
                }
            }
            out.append(chars[i]); i += 1
        }
        return out
    }

    private static func cased(_ mapped: String, _ source: Character, _ allCaps: Bool) -> String {
        guard source.isUppercase else { return mapped }
        if allCaps { return mapped.uppercased() }
        return mapped.prefix(1).uppercased() + mapped.dropFirst()
    }

    private static func isAllCaps(_ s: String) -> Bool {
        s.contains(where: \.isLetter) && !s.contains(where: \.isLowercase)
    }

    private static func toCyrillic(_ latin: String) -> String {
        let caps = isAllCaps(latin)
        let chars = Array(latin)
        var out = ""
        var i = 0
        while i < chars.count {
            if i + 1 < chars.count {
                let two = String(chars[i...i + 1])
                if let hit = cyrillicMap[two.lowercased()] {
                    out += cased(hit, chars[i], caps); i += 2; continue
                }
            }
            let one = String(chars[i])
            out += cyrillicMap[one.lowercased()].map { cased($0, chars[i], caps) } ?? one
            i += 1
        }
        return out
    }

    private static func toGlagolitic(_ latin: String) -> String {
        let caps = isAllCaps(latin)
        let src = Array(latin)
        let low = Array(latin.lowercased())
        guard src.count == low.count else { return latin }   // lowercasing changed the length
        var out = ""
        var i = 0
        while i < src.count {
            let c = low[i]
            if c == "j" {
                let next: Character? = i + 1 < low.count ? low[i + 1] : nil
                let prev: Character? = i > 0 ? low[i - 1] : nil
                if let n = next, let lig = jLigature[n] {
                    out += cased(lig, src[i], caps); i += 2; continue
                } else if let n = next, vowels.contains(n) {
                    out += cased("ⰹ", src[i], caps)
                } else if prev == "l" || prev == "n" {
                    out += cased("ⱐ", src[i], caps)
                } else {
                    out += cased("ⰹ", src[i], caps)
                }
                i += 1
                continue
            }
            // Every d + z is dzelo ⰷ (owner 261124, variant 1 - in Interslavic
            // all of them are prefix seams: podzemny, nadzor, odzyv). ḍ keeps them apart.
            if c == "d", i + 1 < low.count, low[i + 1] == "z" {
                out += cased("ⰷ", src[i], caps); i += 2; continue
            }
            out += glagLetters[c].map { cased($0, src[i], caps) } ?? String(src[i])
            i += 1
        }
        return out
    }

    // MARK: Back to Latin (for wordlist lookups)

    /// Ⱑ reads as ja at the start of a word or after a vowel (moja, jablo),
    /// as ě after a consonant (město). Consonant + ja (polja, morja: 256 word
    /// pairs in the wordlist, 261124) therefore reads as ě - the price of the
    /// shared letter. Turned into ⱝ here, which the reverse table reads as ja.
    private static let glagVowels: Set<Character> = ["ⰰ", "ⰵ", "ⱑ", "ⰻ", "ⰹ", "ⱁ", "ⱆ", "ⱝ", "ⱓ", "ⱖ"]
    static func jaFromJat(_ s: String) -> String {
        var out = ""
        var prev: Character?
        for ch in s {
            let low = Character(ch.lowercased())
            if low == "ⱑ", prev.map({ !$0.isLetter || glagVowels.contains(Character($0.lowercased())) }) ?? true {
                out.append(ch.isUppercase ? "Ⱝ" : "ⱝ")
            } else {
                out.append(ch)
            }
            prev = ch
        }
        return out
    }

    private static let reverses: [String: [(String, String)]] = Dictionary(uniqueKeysWithValues:
        ["isv", "sr", "mk", "ru", "uk", "bg", "be", "sla"].map { lang in
            (lang, table(lang).map { ($0.1, $0.0) }.sorted { $0.0.count > $1.0.count })
        })
    private static var reverseCyrillic: [(String, String)] {
        reverses[Layout.language] ?? reverses["isv"]!
    }

    private static let reverseGlagolitic: [(String, String)] =
        ([("ⰷ", "dz"), ("ⱏⰹ", "y"), ("ⱐⰹ", "y"), ("ⱇ", "f"), ("ⱝ", "ja"), ("ⱓ", "ju"), ("ⱖ", "jo"), ("ⱐ", "j"), ("ⰹ", "j"), ("ⰿ", "m")]
         + glagLetters.filter { !["y", "ȧ", "ẑ", "ḍ", "\u{02B9}", "\u{02BA}"].contains($0.key) }.map { ($0.value, String($0.key)) })
        .sorted { $0.0.count > $1.0.count }

    /// Text in the current script -> Latin. Latin passes through.
    static func toLatin(_ s: String) -> String { back(s, mode) }

    static func back(_ s: String, _ m: Mode) -> String {
        if m == .latin || s.isEmpty { return s }
        if m == .glagolitic && Layout.language == "pl" { return backPolish(s) }
        let table = m == .cyrillic ? reverseCyrillic : reverseGlagolitic
        let chars = Array(m == .glagolitic ? jaFromJat(s) : s)
        var out = ""
        var i = 0
        outer: while i < chars.count {
            for (scr, lat) in table {
                let n = scr.count
                guard i + n <= chars.count else { continue }
                if String(chars[i..<i + n]).lowercased() == scr.lowercased() {
                    out += chars[i].isUppercase ? lat.prefix(1).uppercased() + lat.dropFirst() : lat
                    i += n
                    continue outer
                }
            }
            out.append(chars[i]); i += 1
        }
        return out
    }
}

/// The titlo (tytło), the Glagolitic abbreviation mark, held on every letter
/// while writing Glagolitic (owner 261121; Android to follow). Popup order
/// after the letter's own variants: plain, opening half, closing half.
///
/// A long titlo is STORED as the left half over every letter but the last and
/// the right half over the last: left half - bare middle - right half draws a
/// broken line (measured 261121 on Noto Sans Glagolitic). What the user
/// presses is not what is stored: the keyboard fills the halves itself.
///  - text selected + any titlo item: every run of letters in it gets one
///    titlo (plain over a single letter), titlos already there are replaced;
///  - opening half: shows at once over its letter, so an unclosed run stays
///    VISIBLE instead of being a hidden mode;
///  - closing half: finds that opening half before the caret and fills the
///    letters in between. No state is kept - the half line on screen is it.
enum Titlo {
    static let plain = "\u{0483}", open = "\u{FE2E}", close = "\u{FE2F}"
    static let marks = [plain, open, close]
    private static let scalars: Set<Unicode.Scalar> = ["\u{0483}", "\u{FE2E}", "\u{FE2F}"]

    /// Glagolitic only - the alphabet the titlo belongs to here.
    static var offered: Bool { Script.mode == .glagolitic && !Script.morse && Script.runes == nil }

    /// A popup item `a` + mark -> (`a`, mark); nil for anything else.
    static func split(_ s: String) -> (base: String, mark: String)? {
        let u = s.unicodeScalars
        guard u.count > 1, let last = u.last, scalars.contains(last) else { return nil }
        return (String(String.UnicodeScalarView(u.dropLast())), String(last))
    }

    static func has(_ c: Character) -> Bool { c.unicodeScalars.contains { scalars.contains($0) } }

    static func strip(_ s: String) -> String {
        String(String.UnicodeScalarView(s.unicodeScalars.filter { !scalars.contains($0) }))
    }

    /// A mark put on what ONE key wrote. A letter written with two glyphs
    /// (ś = ⱄⱐ, ł = ⰾⱏ, y = ⱏⰹ, isv lj = ⰾⱐ) takes it over both: plain and
    /// closing become a long titlo over the pair, the opening half goes over
    /// each glyph (owner 261123: "na obydwa od razu").
    static func apply(_ glyphs: String, _ mark: String) -> String {
        guard glyphs.count > 1, glyphs.allSatisfy(\.isLetter) else { return glyphs + mark }
        if mark == open { return glyphs.map { String($0) + open }.joined() }
        return over(glyphs)
    }

    /// One titlo over every run of letters: plain over a single letter, the
    /// halves over a longer one. Spaces and marks stay bare, so each word of a
    /// selection gets its own line.
    static func over(_ text: String) -> String {
        let chars = Array(strip(text))
        var out = ""
        var i = 0
        while i < chars.count {
            guard chars[i].isLetter else { out.append(chars[i]); i += 1; continue }
            var j = i
            while j < chars.count, chars[j].isLetter { j += 1 }
            if j - i == 1 {
                out += String(chars[i]) + plain
            } else {
                for k in i..<j { out += String(chars[k]) + (k == j - 1 ? close : open) }
            }
            i = j
        }
        return out
    }

    /// The open run before the caret: from the last letter carrying the
    /// opening half to the caret, or nil when none is open on this line.
    static func openRun(before text: String) -> String? {
        var run: [Character] = []
        for c in text.reversed().prefix(80) {
            if c == "\n" { return nil }
            if c.unicodeScalars.contains("\u{FE2F}") || c.unicodeScalars.contains("\u{0483}") { return nil }
            run.append(c)
            if c.unicodeScalars.contains("\u{FE2E}") { return String(run.reversed()) }
        }
        return nil
    }
}
