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

    /// Punctuation our MS texts actually use, held on the `.` key. The comma
    /// used to lead this list, which put the most used mark at the far end of
    /// a popup that opens leftward from the right edge - it has its own key now.
    static let periodAccents: [Character] = ["?", "!", "„", "”", "'", ":", ";"]

    /// Held on keys of the numeric layer; the layer itself keeps only what is
    /// used often enough to deserve a key.
    static let numericAccents: [Character: [Character]] = [
        "-": ["–", "—", "_", "+", "="],
        "&": ["%", "#", "*"],
        "€": ["$", "£"],
        "\"": ["„", "”", "«", "»"],
        "(": ["[", "{", "<"],
        ")": ["]", "}", ">"],
        "/": ["\\", "|"],
    ]

    /// Digits held on the top letter row, shown small in the key corner.
    static let topRowDigits: [Character: Character] = Dictionary(
        uniqueKeysWithValues: zip("qwertyuiop", "1234567890"))

    static let letterRows: [[Character]] = [
        Array("qwertyuiop"),
        Array("asdfghjkl"),
        Array("zxcvbnm"),
    ]

    /// Same arrangement as the system keyboard's `123` layer, so the thumb
    /// finds `,` and `.` where it already expects them.
    static let numericRows: [[Character]] = [
        Array("1234567890"),
        Array("-/:;()€&@\""),
        Array(".,?!'"),
    ]

    /// Longpress variants for any key, or nil if the key has none.
    static func variants(for key: Character, uppercase: Bool) -> [Character]? {
        let lower = Character(key.lowercased())
        var out: [Character] = []
        if let accent = accents[lower] {
            out.append(uppercase ? Character(accent.uppercased()) : accent)
        }
        if let digit = topRowDigits[lower] { out.append(digit) }
        if !out.isEmpty { return out }
        if key == "." { return periodAccents }
        return numericAccents[key]
    }
}
