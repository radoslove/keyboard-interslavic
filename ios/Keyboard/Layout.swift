import Foundation

/// The canonical character table, same one as `build_keyman.py` and
/// `windows/src/KBDMSSTD.klc`. Standard orthography only (HOUSE_STYLE §1):
/// the extended alphabet is deliberately absent everywhere, not just here.
///
/// A phone has no AltGr, so the four letters live on LONGPRESS of their base
/// key - `c` held gives `č`. That is the same mnemonic as AltGr+C on desktop,
/// which is the point: one habit across platforms.
enum Layout {

    /// Which keyboard this extension is: `isv` or `pl`. One code base, two
    /// entries in Settings -> Keyboards; the language comes from the
    /// extension's Info.plist (`KeyboardLanguage`), set per target.
    static let language: String =
        Bundle.main.object(forInfoDictionaryKey: "KeyboardLanguage") as? String ?? "isv"

    /// base letter -> the letters of THIS keyboard's language reached by
    /// holding it; they come first in the popup.
    static let accents: [Character: [Character]] = language == "pl"
        ? ["a": ["ą"], "c": ["ć"], "e": ["ę"], "l": ["ł"], "n": ["ń"],
           "o": ["ó"], "s": ["ś"], "z": ["ż", "ź"]]
        : ["c": ["č"], "s": ["š"], "z": ["ž"], "e": ["ě"]]

    /// Letters of OTHER languages, after the Interslavic one in the same
    /// popup (owner 2026-10-10: `mañana` in a letter meant switching
    /// keyboards). Polish first, then other Slavic, then the rest. These are
    /// for writing other languages - the Interslavic wordlist stays standard
    /// orthography (HOUSE_STYLE §1), and the first slot of every popup keeps
    /// its Interslavic letter so the old habit does not move.
    static let foreignAccents: [Character: [Character]] = [
        "a": ["ą", "á", "ä", "à", "â", "ã", "å", "æ"],
        "c": ["ć", "ç"],
        "d": ["ď", "đ"],
        "e": ["ę", "é", "è", "ê", "ë", "ė"],
        "i": ["í", "ì", "î", "ï"],
        "l": ["ł", "ľ", "ĺ"],
        "n": ["ń", "ň", "ñ"],
        "o": ["ó", "ô", "ö", "ò", "õ", "ø", "ő"],
        "r": ["ř", "ŕ"],
        "s": ["ś", "ß"],
        "t": ["ť"],
        "u": ["ú", "ů", "ü", "ù", "û", "ű"],
        "y": ["ý", "ÿ"],
        "z": ["ż", "ź"],
    ]

    /// Punctuation our MS texts actually use, held on the `.` key. The comma
    /// used to lead this list, which put the most used mark at the far end of
    /// a popup that opens leftward from the right edge - it has its own key now.
    /// The frequent marks live behind the three punctuation keys of the
    /// letter layer, so `123` is needed only for the rare ones.
    static let periodAccents: [Character] = ["…", "!", "„", "”", "\"", "'"]

    /// Held on `,`: the rest of the sentence punctuation.
    static let commaAccents: [Character] = [";", ":", "-", "–", "(", ")"]

    /// `?` has its own key next to the period; `!` waits behind it.
    static let questionAccents: [Character] = ["!", "¿", "¡"]

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

    /// Second symbol page, `#+=` on the numeric layer - as on iOS.
    static let symbolRows: [[Character]] = [
        Array("[]{}#%^*+="),
        Array("_\\|~<>€£¥•"),
        Array(".,?!'"),
    ]

    /// Longpress variants for any key, or nil if the key has none.
    static func variants(for key: Character, uppercase: Bool,
                         numericLayer: Bool = false) -> [Character]? {
        let lower = Character(key.lowercased())
        var out: [Character] = []
        let own = accents[lower] ?? []
        let foreign = (foreignAccents[lower] ?? []) + (language == "pl"
            ? [Character]([ "c": "č", "s": "š", "z": "ž", "e": "ě" ][lower].map { [$0] } ?? [])
            : [])
        for letter in own + foreign.filter({ !own.contains($0) }) {
            // `ß` has no single-letter capital; keep it as it is.
            let up = letter.uppercased()
            out.append(uppercase && up.count == 1 ? Character(up) : letter)
        }
        if !out.isEmpty { return out }
        if key == "." { return periodAccents }
        if key == "," && !numericLayer { return commaAccents }
        if key == "?" && !numericLayer { return questionAccents }
        return numericAccents[key]
    }
}
