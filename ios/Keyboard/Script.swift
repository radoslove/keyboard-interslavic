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

    private static let key = "script"

    /// The chosen script; always Latin on a keyboard that is not Interslavic.
    static var mode: Mode {
        guard Layout.language == "isv" else { return .latin }
        return Mode(rawValue: Shared.store.string(forKey: key) ?? "") ?? .latin
    }

    /// Latin -> Cyrillic -> Glagolitic -> Latin; the script key next to 🌐.
    @discardableResult
    static func next() -> Mode {
        let all = Mode.allCases
        let m = all[(all.firstIndex(of: mode)! + 1) % all.count]
        Shared.store.set(m.rawValue, forKey: key)
        return m
    }

    /// The first three letters of the current alphabet - the same convention
    /// as `ABC` on `123` and the ABC/АБВ switch of Serbian keyboards.
    static var keyLabel: String {
        switch mode {
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
    private static let cyrillic = Dictionary(uniqueKeysWithValues: cyrillicPairs)

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
                if let hit = cyrillic[two.lowercased()] {
                    out += cased(hit, chars[i], caps); i += 2; continue
                }
            }
            let one = String(chars[i])
            out += cyrillic[one.lowercased()].map { cased($0, chars[i], caps) } ?? one
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

    private static let reverseCyrillic: [(String, String)] =
        cyrillicPairs.map { ($0.1, $0.0) }.sorted { $0.0.count > $1.0.count }

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
