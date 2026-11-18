import Foundation

/// Runes as a script of the keyboard, Norwegian only (owner 261118: "na razie
/// w samym norweskim, obie wersje do wyboru"; other languages only if asked).
/// Like Morse, everything inside stays in letters and `Doc` turns the text into
/// runes at the field. Every letter is ONE rune, so counts of characters stay
/// the same on both sides.
///
/// Two rows, both a transliteration of modern Norwegian, not a historical
/// spelling:
///  - `medieval` (ᛆᛒᛍ): the Norwegian medieval runes (c. 1100-1500, the
///    younger futhark with the dotted runes), nearly one rune per letter;
///    `j` shares ᛁ with `i`, `å` takes ᚬ (óss, the rune of Old Norse ǫ).
///  - `young` (ᚠᚢᚦ): the long-branch younger futhark of the Viking age, 16
///    runes, so several letters share one (e i j -> ᛁ, o å -> ᚬ, d t -> ᛏ,
///    g k c q x -> ᚴ, b p -> ᛒ, u v w y ø -> ᚢ, s z -> ᛋ, a æ -> ᛅ).
/// ⚠ Working tables (261118), not checked against a source table yet.
enum Runes {

    enum Row: String, CaseIterable { case medieval = "runes_a", young = "runes_b" }

    static func label(_ r: Row) -> String { r == .medieval ? "ᛆᛒᛍ" : "ᚠᚢᚦ" }

    static func has(_ lang: String) -> Bool { lang == "nb" }

    private static let medieval: [Character: Character] = [
        "a": "ᛆ", "b": "ᛒ", "c": "ᛍ", "d": "ᛑ", "e": "ᛂ", "f": "ᚠ", "g": "ᚵ", "h": "ᚼ",
        "i": "ᛁ", "j": "ᛁ", "k": "ᚴ", "l": "ᛚ", "m": "ᛘ", "n": "ᚿ", "o": "ᚮ", "p": "ᛔ",
        "q": "ᛩ", "r": "ᚱ", "s": "ᛋ", "t": "ᛐ", "u": "ᚢ", "v": "ᚡ", "w": "ᚥ", "x": "ᛪ",
        "y": "ᛦ", "z": "ᛎ", "æ": "ᛅ", "ø": "ᚯ", "å": "ᚬ", "ä": "ᛅ", "ö": "ᚯ",
    ]

    private static let young: [Character: Character] = [
        "a": "ᛅ", "b": "ᛒ", "c": "ᚴ", "d": "ᛏ", "e": "ᛁ", "f": "ᚠ", "g": "ᚴ", "h": "ᚼ",
        "i": "ᛁ", "j": "ᛁ", "k": "ᚴ", "l": "ᛚ", "m": "ᛘ", "n": "ᚾ", "o": "ᚬ", "p": "ᛒ",
        "q": "ᚴ", "r": "ᚱ", "s": "ᛋ", "t": "ᛏ", "u": "ᚢ", "v": "ᚢ", "w": "ᚢ", "x": "ᚴ",
        "y": "ᚢ", "z": "ᛋ", "æ": "ᛅ", "ø": "ᚢ", "å": "ᚬ", "ä": "ᛅ", "ö": "ᚢ",
    ]

    private static func table(_ r: Row) -> [Character: Character] { r == .medieval ? medieval : young }

    /// Rune -> the letter it is read back as when the keyboard has no record
    /// of what was typed (text from before, or after the caret moved).
    /// A shared rune reads as the first letter of this order that has it:
    /// the most likely Norwegian letter (ᛁ = i, ᚴ = k, ᛏ = t, ᛅ = a, ᚬ = o).
    private static let backOrder: [Row: String] = [
        .medieval: "abcdefghiklmnopqrstuvwxyzæøåj",
        .young: "aiukbtsofhlmnræøåcdegjpqvwxyz",
    ]
    private static let back: [Row: [Character: Character]] = {
        var out: [Row: [Character: Character]] = [:]
        for r in Row.allCases {
            var b: [Character: Character] = [:]
            for k in backOrder[r]! { if let v = table(r)[k], b[v] == nil { b[v] = k } }
            out[r] = b
        }
        return out
    }()

    /// One character as written: its rune (held accents on their base letter),
    /// or itself.
    static func rune(_ c: Character, _ r: Row) -> Character {
        let low = Character(c.lowercased())
        if let v = table(r)[low] { return v }
        if let base = String(low).decomposedStringWithCanonicalMapping.unicodeScalars.first,
           let v = table(r)[Character(base)] { return v }
        return c
    }

    static func encode(_ s: String, _ r: Row) -> String { String(s.map { rune($0, r) }) }

    static func decodeLossy(_ s: String, _ r: Row) -> String {
        let b = back[r]!
        return String(s.map { b[$0] ?? $0 })
    }

    /// The letters this keyboard wrote last, lined up with the end of the
    /// text before the caret - so `ide` written in runes reads back as `ide`,
    /// not `idi`. Dropped as soon as the field no longer ends with them.
    static var tail = ""
    static var tailRow: Row?

    /// The text before the caret, read back as letters.
    static func read(_ raw: String, _ r: Row) -> String {
        if tailRow == r, !tail.isEmpty {
            let written = encode(tail, r)
            if raw.hasSuffix(written) { return decodeLossy(String(raw.dropLast(tail.count)), r) + tail }
            // The field hands over only the last stretch of text.
            if written.hasSuffix(raw) { return String(tail.suffix(raw.count)) }
        }
        tail = ""
        return decodeLossy(raw, r)
    }

    /// Before writing `s` after `raw`: keep the record only while it still
    /// lines up with the field.
    static func wrote(_ s: String, after raw: String, _ r: Row) {
        if tailRow != r || !raw.hasSuffix(encode(tail, r)) { tail = "" }
        tailRow = r
        tail += s
        if tail.count > 400 { tail = String(tail.suffix(300)) }
    }
}
