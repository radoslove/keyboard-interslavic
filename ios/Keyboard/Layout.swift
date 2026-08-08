import Foundation

/// The canonical character table, same one as `build_keyman.py` and
/// `windows/src/KBDMSSTD.klc`. Standard orthography only (HOUSE_STYLE §1):
/// the extended alphabet is deliberately absent everywhere, not just here.
///
/// A phone has no AltGr, so the four letters live on LONGPRESS of their base
/// key - `c` held gives `č`. That is the same mnemonic as AltGr+C on desktop,
/// which is the point: one habit across platforms.
enum Layout {

    /// base letter -> the accented letter reached by holding it
    static let accents: [Character: Character] = [
        "c": "č",
        "s": "š",
        "z": "ž",
        "e": "ě",
    ]

    /// Punctuation our MS texts actually use, held on the `.` key.
    static let periodAccents: [Character] = [",", "!", "?", "„", "”", "'", ":", ";"]

    /// Dashes, held on `-` in the numeric layer.
    static let hyphenAccents: [Character] = ["–", "—"]

    static let letterRows: [[Character]] = [
        Array("qwertyuiop"),
        Array("asdfghjkl"),
        Array("zxcvbnm"),
    ]

    static let numericRows: [[Character]] = [
        Array("1234567890"),
        Array("@#$%&*()"),
        Array("-/:;'\"?!"),
    ]

    /// Longpress variants for any key, or nil if the key has none.
    static func variants(for key: Character, uppercase: Bool) -> [Character]? {
        if let accent = accents[Character(key.lowercased())] {
            let letter = uppercase ? Character(accent.uppercased()) : accent
            return [letter]
        }
        if key == "." { return periodAccents }
        if key == "-" { return hyphenAccents }
        return nil
    }
}
