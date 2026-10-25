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
    /// holding it; they come first in the popup. The three Interslavic
    /// digraphs sit on `d` / `l` / `n`, as on Android (3.5.7) and in
    /// `android/isv_latin.xml`.
    static let accents: [Character: [String]] = language == "pl"
        ? ["a": ["ą"], "c": ["ć"], "e": ["ę"], "l": ["ł"], "n": ["ń"],
           "o": ["ó"], "s": ["ś"], "z": ["ż", "ź"]]
        : ["c": ["č"], "s": ["š"], "z": ["ž"], "e": ["ě"],
           "d": ["dž"], "l": ["lj"], "n": ["nj"]]

    /// Letters of OTHER languages, after the Interslavic one in the same
    /// popup (owner 2026-10-10: `mañana` in a letter meant switching
    /// keyboards). Polish first, then other Slavic, then the rest. These are
    /// for writing other languages - the Interslavic wordlist stays standard
    /// orthography (HOUSE_STYLE §1), and the first slot of every popup keeps
    /// its Interslavic letter so the old habit does not move.
    static let foreignAccents: [Character: [String]] = [
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
    static let periodAccents: [String] = ["…", "!", "„", "”", "\"", "'"]

    /// Held on `,`: the rest of the sentence punctuation.
    static let commaAccents: [String] = [";", ":", "-", "–", "(", ")"]

    /// Held on keys of the numeric layer; the layer itself keeps only what is
    /// used often enough to deserve a key.
    /// `?` and `!` left the letters (Android 3.5.16, owner 2026-10-14) and sit
    /// next to backspace on `123`; `¿` `¡` wait behind them there.
    static let numericAccents: [Character: [String]] = [
        "-": ["–", "—", "_", "+", "="],
        "&": ["%", "#", "*"],
        "€": ["$", "£"],
        "\"": ["„", "”", "«", "»"],
        "(": ["[", "{", "<"],
        ")": ["]", "}", ">"],
        "/": ["\\", "|"],
        ".": periodAccents,
        "?": ["¿"],
        "!": ["¡"],
    ]

    static let letterRows: [[Character]] = [
        Array("qwertyuiop"),
        Array("asdfghjkl"),
        Array("zxcvbnm"),
    ]

    /// `123` - rows 1-2 as on the system keyboard. Row 3 sits between `#+=`
    /// and backspace and runs from the rarest mark to the most used, so `?`
    /// and `!` are right next to backspace, nearest the thumb (Android 3.5.16).
    static let numericRows: [[Character]] = [
        Array("1234567890"),
        Array("-/:;()€&@\""),
        Array(".,'!?"),
    ]

    /// Second symbol page, `#+=` on the numeric layer - as on iOS.
    static let symbolRows: [[Character]] = [
        Array("[]{}#%^*+="),
        Array("_\\|~<>€£¥•"),
        Array(".,'!?"),
    ]

    /// Longpress variants for any key, or nil if the key has none. Own
    /// letters first; foreign Latin letters only while writing Latin - in
    /// Cyrillic or Glagolitic they would be Latin in the middle of the other
    /// script. Returned in Latin; the caller converts them with `Script.out`.
    static func variants(for key: Character, uppercase: Bool,
                         numericLayer: Bool = false, latin: Bool = true) -> [String]? {
        if numericLayer { return numericAccents[key] }
        let lower = Character(key.lowercased())
        let own = accents[lower] ?? []
        var all = own
        if latin {
            let isvOnPl: [Character: String] = ["c": "č", "s": "š", "z": "ž", "e": "ě"]
            let foreign = (foreignAccents[lower] ?? [])
                + (language == "pl" ? isvOnPl[lower].map { [$0] } ?? [] : [])
            for f in foreign where !all.contains(f) { all.append(f) }
        }
        if !all.isEmpty { return all.map { cased($0, uppercase) } }
        if key == "." { return periodAccents }
        if key == "," { return commaAccents }
        return nil
    }

    /// `ß` has no one-letter capital; a digraph capitalises its first letter
    /// only (`Dž`, `Lj`).
    private static func cased(_ s: String, _ uppercase: Bool) -> String {
        guard uppercase, s != "ß" else { return s }
        return s.prefix(1).uppercased() + s.dropFirst()
    }
}
