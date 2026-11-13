import Foundation

/// The language of the app's own screen. The PHONE's first language picks it:
/// every language the table has gets its own text (Interslavic, English,
/// Polish, Norwegian, Czech, Slovak, Slovenian, Croatian, Serbian in the
/// script the phone uses, Bosnian, Macedonian, Russian, Ukrainian, Bulgarian,
/// Belarusian); any other language gets Interslavic with the English under it.
/// The texts live in `tools/ui_texts.json`, shared with Android, and
/// `tools/build_ui_texts.py` writes them into `UiTexts.swift`.
enum Ui {
    static let current: String = pick(Locale.preferredLanguages.first ?? "")

    /// `sr-Latn-RS` -> `sr`, `sr-RS` / `sr-Cyrl` -> `sr-Cyrl` (iOS writes
    /// Serbian in Cyrillic unless Latin is chosen), `nn`/`no` -> `nb`.
    static func pick(_ id: String) -> String {
        let parts = id.split(separator: "-").map { $0.lowercased() }
        guard let lang = parts.first else { return "isv" }
        switch lang {
        case "no", "nn", "nb": return "nb"
        case "sr": return parts.contains("latn") ? "sr" : "sr-Cyrl"
        default: return languages.contains(lang) ? lang : "isv"
        }
    }

    /// Text in the screen's language; falls back to English.
    static func t(_ key: String) -> String {
        guard let row = table[key] else { return key }
        return (row[current] ?? row["en"] ?? key).replacingOccurrences(of: "{n}", with: "16")
    }

    /// The English gloss under an Interslavic text, nil in any other language
    /// or when the Interslavic text IS the English one.
    static func gloss(_ key: String) -> String? {
        guard current == "isv", let row = table[key], let isv = row["isv"], let en = row["en"],
              isv != en else { return nil }
        return en
    }

    /// A heading: `Interslavic · English` in Interslavic mode, else one language.
    static func head(_ key: String) -> String { gloss(key).map { "\(t(key)) · \($0)" } ?? t(key) }

    /// A paragraph: the Interslavic text and the English under it.
    static func body(_ key: String) -> String { gloss(key).map { "\(t(key))\n\($0)" } ?? t(key) }
}
