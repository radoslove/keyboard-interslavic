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

    /// The next script this language has, wrapping round; the script key next to 🌐.
    @discardableResult
    static func next() -> Mode {
        let can = modes
        let m = can[((can.firstIndex(of: mode) ?? 0) + 1) % can.count]
        set(m)
        return m
    }

    /// The first three letters of the current alphabet - the same convention
    /// as `ABC` on `123` and the ABC/АБВ switch of Serbian keyboards.
    static var keyLabel: String { label(mode) }

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
        "f": "ⱇ", "g": "ⰳ", "h": "ⱈ", "i": "ⰻ", "k": "ⰽ", "l": "ⰾ", "m": "ⱞ",
        "n": "ⱀ", "o": "ⱁ", "p": "ⱂ", "r": "ⱃ", "s": "ⱄ", "š": "ⱎ", "t": "ⱅ",
        "u": "ⱆ", "v": "ⰲ", "y": "ⱐⰹ", "z": "ⰸ", "ž": "ⰶ",
    ]
    private static let jLigature: [Character: String] = ["a": "ⱝ", "u": "ⱓ", "o": "ⱖ"]
    private static let vowels: Set<Character> = ["a", "e", "ě", "i", "o", "u", "y"]

    /// Latin text -> the current script. Case follows the source:
    /// `Lj` -> `Љ`, `LJUBOV` -> `ЉУБОВ`.
    static func out(_ latin: String) -> String { convert(latin, mode) }

    static func convert(_ latin: String, _ m: Mode) -> String {
        if latin.isEmpty || m == .latin { return latin }
        return m == .cyrillic ? toCyrillic(latin) : toGlagolitic(latin)
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
            out += glagLetters[c].map { cased($0, src[i], caps) } ?? String(src[i])
            i += 1
        }
        return out
    }

    // MARK: Back to Latin (for wordlist lookups)

    private static let reverses: [String: [(String, String)]] = Dictionary(uniqueKeysWithValues:
        ["isv", "sr", "mk", "ru", "uk", "bg", "be", "sla"].map { lang in
            (lang, table(lang).map { ($0.1, $0.0) }.sorted { $0.0.count > $1.0.count })
        })
    private static var reverseCyrillic: [(String, String)] {
        reverses[Layout.language] ?? reverses["isv"]!
    }

    private static let reverseGlagolitic: [(String, String)] =
        ([("ⱐⰹ", "y"), ("ⱝ", "ja"), ("ⱓ", "ju"), ("ⱖ", "jo"), ("ⱐ", "j"), ("ⰹ", "j"), ("ⰿ", "m")]
         + glagLetters.filter { $0.key != "y" }.map { ($0.value, String($0.key)) })
        .sorted { $0.0.count > $1.0.count }

    /// Text in the current script -> Latin. Latin passes through.
    static func toLatin(_ s: String) -> String { back(s, mode) }

    static func back(_ s: String, _ m: Mode) -> String {
        if m == .latin || s.isEmpty { return s }
        let table = m == .cyrillic ? reverseCyrillic : reverseGlagolitic
        let chars = Array(s)
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
