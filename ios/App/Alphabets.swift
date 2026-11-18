import Foundation

/// The keyboard's own script and Morse conversion (`Keyboard/Script.swift`,
/// `Keyboard/Morse.swift`, compiled into the app too) reads the language from
/// `Layout.language`. The app has no keyboard layout, so it names the language
/// here before each conversion.
enum Layout {
    static var language = "isv"
}

/// The scripts a language offers, Morse last - Android `Script.choices`.
enum Scripts {
    enum Choice: Hashable {
        case script(Script.Mode), morse
        var name: String {
            switch self {
            case .script(let m): return Script.label(m)
            case .morse: return Script.morseLabel
            }
        }
    }

    static func choices(_ lang: String) -> [Choice] {
        Script.offered(lang).map { .script($0) } + (Morse.has(lang) ? [.morse] : [])
    }

    /// [latin] in script [m] of [lang]: a word card read in Cyrillic or Glagolitic.
    static func convert(_ latin: String, _ m: Script.Mode, _ lang: String) -> String {
        Layout.language = lang
        return Script.convert(latin, m)
    }
}

/// Alphabet sets - Android `Alphabets` (`Sets.kt`, 3.10.30): the letters of a
/// language shown in another of its scripts (Interslavic in Cyrillic and
/// Glagolitic, Polish in Glagolitic, Serbian in Cyrillic, every language with a
/// Morse table in Morse), the letter itself as the answer. Built from the
/// keyboard's own conversion, so nothing is stored.
enum Alphabets {
    private static let letters: [String: String] = [
        "isv": "a b c č ć d dž đ e ě f g h i j k l lj m n nj o p r s š t u v y z ž",
        "pl": "a ą b c ć d e ę f g h i j k l ł m n ń o ó p r s ś t u w y z ź ż",
        "cs": "a á b c č d ď e é ě f g h i í j k l m n ň o ó p r ř s š t ť u ú ů v y ý z ž",
        "sk": "a á ä b c č d ď e é f g h i í j k l ĺ ľ m n ň o ó ô p r ŕ s š t ť u ú v y ý z ž",
        "sl": "a b c č d e f g h i j k l m n o p r s š t u v z ž",
        "hr": "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        "bs": "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        "sr": "a b c č ć d dž đ e f g h i j k l lj m n nj o p r s š t u v z ž",
        "en": "a b c d e f g h i j k l m n o p q r s t u v w x y z",
        "nb": "a b c d e f g h i j k l m n o p q r s t u v w x y z æ ø å",
        "de": "a ä b c d e f g h i j k l m n o ö p q r s ß t u ü v w x y z",
        "es": "a b c d e f g h i j k l m n ñ o p q r s t u v w x y z",
        "it": "a b c d e f g h i l m n o p q r s t u v z",
        "fr": "a à b c ç d e é è f g h i j k l m n o p q r s t u v w x y z",
        "nl": "a b c d e f g h i j k l m n o p q r s t u v w x y z",
        "ro": "a ă â b c d e f g h i î j k l m n o p q r s ș t ț u v w x y z",
        "ru": "а б в г д е ё ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ы ь э ю я",
        "uk": "а б в г ґ д е є ж з и і ї й к л м н о п р с т у ф х ц ч ш щ ь ю я",
        "bg": "а б в г д е ж з и й к л м н о п р с т у ф х ц ч ш щ ъ ь ю я",
    ]

    /// A name that needs no translation: "ABC → ⰀⰁⰂ".
    static func title(_ lang: String, _ c: Scripts.Choice) -> String {
        Script.label(Script.offered(lang)[0]) + " → " + c.name
    }

    /// For [lang]: (title, [(letter in the other script, letter)]) per script.
    static func sets(_ lang: String) -> [(String, [(String, String)])] {
        guard let ls = letters[lang]?.split(separator: " ").map(String.init) else { return [] }
        let native = Scripts.Choice.script(Script.offered(lang)[0])
        return Scripts.choices(lang).filter { $0 != native }.compactMap { c in
            var seen = Set<String>()
            let cards: [(String, String)] = ls.compactMap { l in
                let front: String?
                switch c {
                case .morse: front = l.count == 1 ? Morse.code(l.first!, lang) : nil
                case .script(let m): front = Scripts.convert(l, m, lang)
                }
                // A letter whose code is its base letter's (Morse č = c) would
                // make the same card twice: the first one keeps it.
                guard let f = front, !f.trimmingCharacters(in: .whitespaces).isEmpty, f != l,
                      seen.insert(f).inserted else { return nil }
                return (f, l)
            }
            return cards.isEmpty ? nil : (title(lang, c), cards)
        }
    }
}
