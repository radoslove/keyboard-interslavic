import Foundation

/// Runes as a script of the keyboard, Norwegian only (owner 261118: "na razie
/// w samym norweskim"; other languages only if asked). Like Morse, everything
/// inside stays in letters and `Doc` turns the text into runes at the field.
///
/// Three rows, oldest first (owner 261127), each a transliteration of modern
/// Norwegian, not a historical spelling (tables shared 1:1 with Android
/// `Runes.kt`):
///  - `elder` (ᚨᛒᚲ): the elder futhark, c. 150-800, 24 runes. Letters it
///    lacks: v = w (ᚹ), y = u (ᚢ), q and c = k (ᚲ, the Old English rune
///    *cen* is c), and four letters as TWO runes: x ᚲᛊ, æ ᚨᛖ, ø ᛟᛖ, å ᚨᚨ.
///  - `young` (ᚠᚢᚦ): the long-branch younger futhark, c. 800-1100, 16
///    runes, so several letters share one (e i j -> ᛁ, o å -> ᚬ, d t -> ᛏ,
///    g k c q x -> ᚴ, b p -> ᛒ, u v w y ø -> ᚢ, s z -> ᛋ, a æ -> ᛅ).
///  - `medieval` (ᛆᛒᛍ): the Norwegian medieval runes, c. 1100-1500, the
///    younger futhark with the dotted runes; nearly one rune per letter,
///    `j` shares ᛁ with `i`, `å` takes ᚬ.
/// ⚠ Working tables (261118, elder 261127), not checked against a source table yet.
///
/// One letter may be two runes (elder row): the field counts runes, the
/// keyboard letters, and `Doc` translates. ⌫ after ᚲᛊ removes the whole `x`
/// while the keyboard's record of what it wrote (`tail`) lines up with the
/// field; text it did not write reads back rune by rune (ᚲᛊ = ks), because
/// `oe` and `ae` are ordinary Norwegian (noe, poet, Israel).
enum Runes {

    enum Row: String, CaseIterable { case elder = "runes_e", young = "runes_b", medieval = "runes_a" }

    static func label(_ r: Row) -> String {
        switch r {
        case .elder: return "ᚨᛒᚲ"
        case .young: return "ᚠᚢᚦ"
        case .medieval: return "ᛆᛒᛍ"
        }
    }

    static func has(_ lang: String) -> Bool { lang == "nb" }

    private static let elder: [Character: String] = [
        "a": "ᚨ", "b": "ᛒ", "c": "ᚲ", "d": "ᛞ", "e": "ᛖ", "f": "ᚠ", "g": "ᚷ", "h": "ᚺ",
        "i": "ᛁ", "j": "ᛃ", "k": "ᚲ", "l": "ᛚ", "m": "ᛗ", "n": "ᚾ", "o": "ᛟ", "p": "ᛈ",
        "q": "ᚲ", "r": "ᚱ", "s": "ᛊ", "t": "ᛏ", "u": "ᚢ", "v": "ᚹ", "w": "ᚹ", "x": "ᚲᛊ",
        "y": "ᚢ", "z": "ᛉ", "æ": "ᚨᛖ", "ø": "ᛟᛖ", "å": "ᚨᚨ", "ä": "ᚨᛖ", "ö": "ᛟᛖ",
    ]

    private static let medieval: [Character: String] = [
        "a": "ᛆ", "b": "ᛒ", "c": "ᛍ", "d": "ᛑ", "e": "ᛂ", "f": "ᚠ", "g": "ᚵ", "h": "ᚼ",
        "i": "ᛁ", "j": "ᛁ", "k": "ᚴ", "l": "ᛚ", "m": "ᛘ", "n": "ᚿ", "o": "ᚮ", "p": "ᛔ",
        "q": "ᛩ", "r": "ᚱ", "s": "ᛋ", "t": "ᛐ", "u": "ᚢ", "v": "ᚡ", "w": "ᚥ", "x": "ᛪ",
        "y": "ᛦ", "z": "ᛎ", "æ": "ᛅ", "ø": "ᚯ", "å": "ᚬ", "ä": "ᛅ", "ö": "ᚯ",
    ]

    private static let young: [Character: String] = [
        "a": "ᛅ", "b": "ᛒ", "c": "ᚴ", "d": "ᛏ", "e": "ᛁ", "f": "ᚠ", "g": "ᚴ", "h": "ᚼ",
        "i": "ᛁ", "j": "ᛁ", "k": "ᚴ", "l": "ᛚ", "m": "ᛘ", "n": "ᚾ", "o": "ᚬ", "p": "ᛒ",
        "q": "ᚴ", "r": "ᚱ", "s": "ᛋ", "t": "ᛏ", "u": "ᚢ", "v": "ᚢ", "w": "ᚢ", "x": "ᚴ",
        "y": "ᚢ", "z": "ᛋ", "æ": "ᛅ", "ø": "ᚢ", "å": "ᚬ", "ä": "ᛅ", "ö": "ᚢ",
    ]

    private static func table(_ r: Row) -> [Character: String] {
        switch r {
        case .elder: return elder
        case .young: return young
        case .medieval: return medieval
        }
    }

    /// Rune -> the letter it is read back as when the keyboard has no record
    /// of what was typed (text from before, or after the caret moved).
    /// A shared rune reads as the first letter of this order that has it
    /// (ᛁ = i, ᚴ = k, ᛏ = t, ᛅ = a, ᚬ = o; elder ᚹ = v, ᚲ = k, ᚢ = u).
    /// Two-rune letters are not read back: ᚲᛊ is `ks`.
    private static let backOrder: [Row: String] = [
        .elder: "abdefghiklmnoprstuvzcjqwy",
        .young: "aiukbtsofhlmnræøåcdegjpqvwxyz",
        .medieval: "abcdefghiklmnopqrstuvwxyzæøåj",
    ]
    private static let back: [Row: [Character: Character]] = {
        var out: [Row: [Character: Character]] = [:]
        for r in Row.allCases {
            var b: [Character: Character] = [:]
            for k in backOrder[r]! {
                if let v = table(r)[k], v.count == 1, b[v.first!] == nil { b[v.first!] = k }
            }
            out[r] = b
        }
        return out
    }()

    /// One character as written: its rune(s) (held accents on their base
    /// letter), or itself.
    static func rune(_ c: Character, _ r: Row) -> String {
        let low = Character(c.lowercased())
        if let v = table(r)[low] { return v }
        if let base = String(low).decomposedStringWithCanonicalMapping.unicodeScalars.first,
           let v = table(r)[Character(base)] { return v }
        return String(c)
    }

    static func encode(_ s: String, _ r: Row) -> String { s.map { rune($0, r) }.joined() }

    static func decodeLossy(_ s: String, _ r: Row) -> String {
        let b = back[r]!
        return String(s.map { b[$0] ?? $0 })
    }

    /// The letters this keyboard wrote last, lined up with the end of the
    /// text before the caret - so `ide` in ᚠᚢᚦ reads back as `ide`, not
    /// `idi`, and `x` in ᚨᛒᚲ as `x`, not `ks`. Dropped as soon as the field
    /// no longer ends with them.
    static var tail = ""
    static var tailRow: Row?

    /// The field's text as it was just before the keyboard's last own edit.
    /// Right after `insertText`/`deleteBackward` many apps still hand back
    /// that old text; for a moment it is taken as "not caught up yet", not
    /// as a field that changed under the keyboard.
    static var staleRaw: String?
    static var staleAt = Date.distantPast
    static func willEdit(_ raw: String) { staleRaw = raw; staleAt = Date() }
    private static func stale(_ raw: String) -> Bool {
        raw == staleRaw && Date().timeIntervalSince(staleAt) < 0.5
    }

    /// The record still lines up with the field.
    private static func aligned(_ raw: String, _ r: Row) -> Bool {
        tailRow == r && (raw.hasSuffix(encode(tail, r)) || stale(raw))
    }

    /// The last letters of `tail` that cover `runes` runes at its end.
    private static func tailCovering(_ runes: Int, _ r: Row) -> String {
        var i = tail.endIndex
        var n = 0
        while i > tail.startIndex, n < runes {
            i = tail.index(before: i)
            n += rune(tail[i], r).count
        }
        return String(tail[i...])
    }

    /// The text before the caret, read back as letters.
    static func read(_ raw: String, _ r: Row) -> String {
        if tailRow == r, !tail.isEmpty {
            let written = encode(tail, r)
            if raw.hasSuffix(written) { return decodeLossy(String(raw.dropLast(written.count)), r) + tail }
            // The field hands over only the last stretch of text.
            if written.hasSuffix(raw) { return tailCovering(raw.count, r) }
            // Not caught up with the last edit: keep the record.
            if stale(raw) { return decodeLossy(raw, r) }
        }
        tail = ""
        return decodeLossy(raw, r)
    }

    /// How many runes the last letter before the caret takes, with `raw` the
    /// field's text before the caret: a letter the record holds counts its
    /// own runes, older text is one rune per letter.
    static func runesOfLastLetter(_ raw: String, _ r: Row) -> Int {
        guard let last = tail.last, aligned(raw, r) else { return 1 }
        return rune(last, r).count
    }

    /// Before writing `s` after `raw`: keep the record only while it still
    /// lines up with the field.
    static func wrote(_ s: String, after raw: String, _ r: Row) {
        if !aligned(raw, r) { tail = "" }
        tailRow = r
        tail += s
        if tail.count > 400 { tail = String(tail.suffix(300)) }
    }
}
