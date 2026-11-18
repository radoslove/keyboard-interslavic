import UIKit

/// Morse code as one more script of the keyboard (Android `Morse.kt`, 3.10.9
/// and 3.10.24; stage 1: the keyboard writes Morse, the glide works as in every
/// other script).
///
/// Everything inside the keyboard stays in letters - dictionary, glide decoder,
/// completions, backspace by word. Morse exists only at the edge: `Doc` stands
/// between the keyboard and the text field, turns every text the keyboard
/// inserts into dots and dashes and reads the field back as letters.
///
/// Written the way it is SENT: the gaps of the timing become spaces - two
/// between letters, five between words (owner 261117: three and seven looked
/// too wide on a phone). Signs: `·` U+00B7 and `−` U+2212.
///
/// The tables are Android's, copied verbatim (sources in `Morse.kt`): ITU-R
/// M.1677-1, the Polish extension, German `ä ö ü`, the Yugoslav table in Latin
/// for hr/bs/sr, Russian/Ukrainian/Bulgarian. A letter with no code in its
/// language is sent as its base letter (`č` as `c`), as Czech does it.
/// ⚠ Interslavic `č š ž ě` have no source of their own (open, 261117).
enum Morse {

    static let dot: Character = "·"
    static let dash: Character = "−"
    static let letterGap = "  "
    static let wordGap = "     "

    /// From ITU notation (`.` and `-`) to the signs written here.
    private static func m(_ t: [String: String]) -> [String: String] {
        t.mapValues { $0.replacingOccurrences(of: ".", with: "·").replacingOccurrences(of: "-", with: "−") }
    }

    private static let itu = m([
        "a": ".-", "b": "-...", "c": "-.-.", "d": "-..", "e": ".", "f": "..-.",
        "g": "--.", "h": "....", "i": "..", "j": ".---", "k": "-.-", "l": ".-..",
        "m": "--", "n": "-.", "o": "---", "p": ".--.", "q": "--.-", "r": ".-.",
        "s": "...", "t": "-", "u": "..-", "v": "...-", "w": ".--", "x": "-..-",
        "y": "-.--", "z": "--..", "é": "..-..",
        "1": ".----", "2": "..---", "3": "...--", "4": "....-", "5": ".....",
        "6": "-....", "7": "--...", "8": "---..", "9": "----.", "0": "-----",
    ])

    private static let punct = m([
        ".": ".-.-.-", ",": "--..--", "?": "..--..", "'": ".----.", "/": "-..-.",
        "(": "-.--.", ")": "-.--.-", ":": "---...", "=": "-...-", "+": ".-.-.",
        "-": "-....-", "\"": ".-..-.", "@": ".--.-.",
        "!": "-.-.--", "_": "..--.-", "$": "...-..-", "&": ".-...",
    ])

    private static let polish = m([
        "ą": ".-.-", "ć": "-.-..", "ę": "..-..", "ł": ".-..-", "ń": "--.--",
        "ó": "---.", "ś": "...-...", "ź": "--..-", "ż": "--..-.",
    ])

    private static let german = m(["ä": ".-.-", "ö": "---.", "ü": "..--"])

    /// Yugoslav table, in Latin: Ђ đ, Ћ ć, Ч č, Ш š, Ж ž (Љ Њ Џ are two letters in Latin).
    private static let serboCroatianLatin = m([
        "đ": "-..--", "ć": "-.-..", "č": "-.--.", "š": "---.-", "ž": "--..-",
    ])

    private static let russian = m([
        "а": ".-", "б": "-...", "в": ".--", "г": "--.", "д": "-..", "е": ".",
        "ё": ".", "ж": "...-", "з": "--..", "и": "..", "й": ".---", "к": "-.-",
        "л": ".-..", "м": "--", "н": "-.", "о": "---", "п": ".--.", "р": ".-.",
        "с": "...", "т": "-", "у": "..-", "ф": "..-.", "х": "....", "ц": "-.-.",
        "ч": "---.", "ш": "----", "щ": "--.-", "ы": "-.--", "ь": "-..-",
        "э": "..-..", "ю": "..--", "я": ".-.-",
    ])

    private static let ukrainian = russian.filter { !["ё", "ы", "э"].contains($0.key) }
        .merging(m(["и": "-.--", "і": "..", "є": "..-..", "ї": ".---."])) { $1 }

    private static let bulgarian = russian.filter { !["ё", "ы", "э"].contains($0.key) }
        .merging(m(["ъ": "-..-", "ь": "-.--"])) { $1 }

    /// `ch` = `−−−−` (cs, pl, de) is read back, never written.
    private static let ch = m(["ch": "----"])

    private static func plus(_ a: [String: String], _ b: [String: String]) -> [String: String] {
        a.merging(b) { $1 }
    }

    /// The languages that offer Morse, and their table: letter -> code.
    private static let tables: [String: [String: String]] = {
        let latin = plus(itu, punct)
        let digits = itu.filter { $0.key.first!.isNumber }
        let raw: [String: [String: String]] = [
            "isv": latin,
            "pl": plus(latin, polish),
            "cs": latin, "sk": latin, "sl": latin,
            "hr": plus(latin, serboCroatianLatin),
            "bs": plus(latin, serboCroatianLatin),
            "sr": plus(latin, serboCroatianLatin),
            "de": plus(latin, german),
            "en": latin, "it": latin, "es": latin, "fr": latin, "nl": latin, "ro": latin, "nb": latin,
            "ru": plus(plus(russian, punct), digits),
            "uk": plus(plus(ukrainian, punct), digits),
            "bg": plus(plus(bulgarian, punct), digits),
        ]
        // A punctuation mark whose code is a letter of the language (Serbian
        // `č` = ITU `(`) would come back as that letter: it passes through as
        // itself instead.
        return raw.mapValues { t in
            let letters = Set(t.filter { punct[$0.key] == nil }.values)
            return t.filter { punct[$0.key] == nil || !letters.contains($0.value) }
        }
    }()

    /// Code -> letter for reading back.
    private static let decoding: [String: [String: String]] = {
        var out: [String: [String: String]] = [:]
        for (lang, t) in tables {
            var back: [String: String] = [:]
            for (k, v) in t where k != "ё" { back[v] = k }
            if ["cs", "sk", "pl", "de"].contains(lang), back[ch["ch"]!] == nil { back[ch["ch"]!] = "ch" }
            out[lang] = back
        }
        return out
    }()

    static func has(_ lang: String) -> Bool { tables[lang] != nil }

    private static func table(_ lang: String) -> [String: String] { tables[lang] ?? tables["isv"]! }

    /// The code of one character, or nil: own code, else the base letter's.
    static func code(_ c: Character, _ lang: String) -> String? {
        let t = table(lang)
        let low = String(c).lowercased()
        if let v = t[low] { return v }
        guard let base = low.decomposedStringWithCanonicalMapping.unicodeScalars.first else { return nil }
        return t[String(base)]
    }

    private static func isGap(_ c: Character) -> Bool { c == " " || c == "\n" || c == "\t" }

    /// Text -> Morse. `glyphBefore`: the field already ends with a letter's
    /// code, so the first letter here needs the gap before it.
    static func encode(_ text: String, _ lang: String, glyphBefore: Bool = false) -> String {
        var out = ""
        var prev = glyphBefore
        for c in text {
            if c == " " { out += wordGap; prev = false }
            else if isGap(c) { out.append(c); prev = false }
            else {
                if prev { out += letterGap }
                out += code(c, lang) ?? String(c)
                prev = true
            }
        }
        return out
    }

    /// One character of the read-back text and where it stands in the Morse
    /// text, in Characters: `start` includes the letter gap before it.
    struct Piece { let text: String; let start: Int; let end: Int }

    /// Morse -> pieces. Runs of spaces longer than the letter gap are a word
    /// space, shorter ones only separate letters; an unknown run of signs comes
    /// back as itself.
    static func pieces(_ s: String, _ lang: String) -> [Piece] {
        let back = decoding[lang] ?? decoding["isv"]!
        let a = Array(s)
        var out: [Piece] = []
        var i = 0, gapStart = -1
        while i < a.count {
            let c = a[i]
            if c == " " {
                var j = i
                while j < a.count && a[j] == " " { j += 1 }
                if j - i > letterGap.count { out.append(Piece(text: " ", start: i, end: j)) }
                else { gapStart = i }
                i = j
                continue
            }
            var j = i + 1
            if c == dot || c == dash {
                while j < a.count && (a[j] == dot || a[j] == dash) { j += 1 }
                let sig = String(a[i..<j])
                out.append(Piece(text: back[sig] ?? sig, start: gapStart >= 0 ? gapStart : i, end: j))
            } else {
                out.append(Piece(text: String(c), start: gapStart >= 0 ? gapStart : i, end: j))
            }
            i = j
            gapStart = -1
        }
        return out
    }

    static func decode(_ s: String, _ lang: String) -> String { pieces(s, lang).map(\.text).joined() }

    /// A key label: the letter over its code, so the keys can be learned.
    static func keyLabel(_ letter: String, _ lang: String) -> String {
        let code = letter.compactMap { Self.code($0, lang) }.joined(separator: " ")
        return code.isEmpty ? letter : letter + "\n" + code
    }
}

/// The text field as the keyboard sees it (Android `Morse.Conn`): in Morse the
/// keyboard writes and reads letters while the field holds dots and dashes;
/// counts the keyboard gives (letters to delete, caret moves) are turned into
/// Morse characters here. Outside Morse every call goes straight through.
struct Doc {
    let proxy: UITextDocumentProxy
    /// The language whose table to use, nil when not writing Morse.
    let morse: String?
    /// The rune row, nil when not writing runes (Norwegian only).
    var runes: Runes.Row? = nil

    /// The Morse text before the cursor, from its first whole piece: a long
    /// context may start in the middle of a code.
    private var rawBefore: String {
        let raw = proxy.documentContextBeforeInput ?? ""
        guard raw.count > 200, let cut = raw.firstIndex(of: " ") else { return raw }
        return String(raw[cut...])
    }

    var documentContextBeforeInput: String? {
        if let r = runes { return proxy.documentContextBeforeInput.map { Runes.read($0, r) } }
        guard let lang = morse else { return proxy.documentContextBeforeInput }
        return proxy.documentContextBeforeInput == nil ? nil : Morse.decode(rawBefore, lang)
    }

    var documentContextAfterInput: String? {
        if let r = runes { return proxy.documentContextAfterInput.map { Runes.decodeLossy($0, r) } }
        guard let lang = morse else { return proxy.documentContextAfterInput }
        return proxy.documentContextAfterInput.map { Morse.decode($0, lang) }
    }

    var selectedText: String? {
        if let r = runes { return proxy.selectedText.map { Runes.decodeLossy($0, r) } }
        guard let lang = morse else { return proxy.selectedText }
        return proxy.selectedText.map { Morse.decode($0, lang) }
    }

    var hasText: Bool { proxy.hasText }

    func insertText(_ s: String) {
        if let r = runes {
            Runes.wrote(s, after: proxy.documentContextBeforeInput ?? "", r)
            proxy.insertText(Runes.encode(s, r))
            return
        }
        guard let lang = morse, !s.isEmpty else { proxy.insertText(s); return }
        let prev = proxy.documentContextBeforeInput?.last
        proxy.insertText(Morse.encode(s, lang, glyphBefore: prev.map { !($0 == " " || $0 == "\n" || $0 == "\t") } ?? false))
    }

    /// One LETTER back: its code and the gap before it.
    func deleteBackward() {
        if runes != nil {
            proxy.deleteBackward()      // one rune = one letter
            if !Runes.tail.isEmpty { Runes.tail.removeLast() }
            return
        }
        guard let lang = morse else { proxy.deleteBackward(); return }
        let raw = rawBefore
        guard let last = Morse.pieces(raw, lang).last else { proxy.deleteBackward(); return }
        for _ in 0..<max(1, raw.count - last.start) { proxy.deleteBackward() }
    }

    /// The caret by `n` letters.
    func adjustTextPosition(byCharacterOffset n: Int) {
        if runes != nil { Runes.tail = ""; proxy.adjustTextPosition(byCharacterOffset: n); return }
        guard let lang = morse, n != 0 else { proxy.adjustTextPosition(byCharacterOffset: n); return }
        if n < 0 {
            let raw = rawBefore
            let ps = Morse.pieces(raw, lang)
            let k = min(-n, ps.count)
            let to = k == 0 ? raw.count : ps[ps.count - k].start
            proxy.adjustTextPosition(byCharacterOffset: -(raw.count - to))
        } else {
            let raw = proxy.documentContextAfterInput ?? ""
            let ps = Morse.pieces(raw, lang)
            let to = n >= ps.count ? raw.count : ps[n].start
            proxy.adjustTextPosition(byCharacterOffset: to)
        }
    }
}
