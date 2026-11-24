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

    // MARK: Undo (owner 261124, variant B: ↶ on the left of the bar)

    /// What the keyboard did to the field, at the level of the field's own
    /// characters (Morse, runes and Glagolitic included). Only the keyboard's
    /// own edits: an extension sees nothing of what the app or the user did.
    enum Step { case insert(String), delete(String), move(Int) }
    static var journal: [[Step]] = []
    /// Groups taken back by ↶, for ↷; any new edit drops them.
    static var redoStack: [[Step]] = []
    /// When the keyboard last changed the field (edit, undo, redo).
    static var lastOwnChangeAt = Date.distantPast
    static var lastStepAt = Date.distantPast
    /// The text before the caret right after the last recorded step; undo
    /// refuses when the field no longer ends like this (caret moved, app edited).
    static var tailAfter = ""
    static var recording = true
    static var onJournal: (() -> Void)?

    private func record(_ step: Step) {
        guard Doc.recording else { return }
        Doc.redoStack = []
        Doc.lastOwnChangeAt = Date()
        // One undo step = everything within 0.5 s (a glide's replacement,
        // a word typed in one go).
        if Date().timeIntervalSince(Doc.lastStepAt) > 0.5 || Doc.journal.isEmpty {
            Doc.journal.append([])
            if Doc.journal.count > 50 { Doc.journal.removeFirst() }
        }
        // Inserts in a row become one: a titlo mark typed after its letter
        // is one character with it, and ⌫ takes the two together.
        if case .insert(let s) = step, case .insert(let prev)? = Doc.journal[Doc.journal.count - 1].last {
            Doc.journal[Doc.journal.count - 1][Doc.journal[Doc.journal.count - 1].count - 1] = .insert(prev + s)
        } else {
            Doc.journal[Doc.journal.count - 1].append(step)
        }
        Doc.lastStepAt = Date()
    }

    private func noteTail() {
        guard Doc.recording else { return }
        Doc.tailAfter = String((proxy.documentContextBeforeInput ?? "").suffix(40))
        Doc.onJournal?()
    }

    /// Called from `textDidChange`, when the field's text is up to date
    /// (right after `insertText` many apps still report the old text, so the
    /// tail taken there was stale and ↶ refused every time - 261124). A change
    /// of ours refreshes the tail; one that is not ours and leaves the field
    /// different drops the journal.
    func fieldChanged() {
        let now = String((proxy.documentContextBeforeInput ?? "").suffix(40))
        if Date().timeIntervalSince(Doc.lastOwnChangeAt) < 0.6 {
            Doc.tailAfter = now
        } else if now != Doc.tailAfter, !(Doc.journal.isEmpty && Doc.redoStack.isEmpty) {
            Doc.journal = []; Doc.redoStack = []
        }
        Doc.onJournal?()
    }

    private func apply(_ group: [Step], back: Bool) {
        Doc.recording = false
        for step in back ? group.reversed() : group {
            switch (step, back) {
            case (.insert(let s), true), (.delete(let s), false): for _ in s { proxy.deleteBackward() }
            case (.delete(let s), true), (.insert(let s), false): proxy.insertText(s)
            case (.move(let n), _): proxy.adjustTextPosition(byCharacterOffset: back ? -n : n)
            }
        }
        Doc.recording = true
        Runes.tail = ""
        Doc.lastStepAt = .distantPast
        Doc.lastOwnChangeAt = Date()
        Doc.onJournal?()
    }

    /// Undoes the last group. False when there is nothing, or the field has
    /// changed under the keyboard since (then the journal is dropped).
    func undo() -> Bool {
        guard let group = Doc.journal.popLast() else { return false }
        Doc.redoStack.append(group)     // before apply: it refreshes the buttons
        apply(group, back: true)
        return true
    }

    /// Does again what ↶ took back.
    func redo() -> Bool {
        guard let group = Doc.redoStack.popLast() else { return false }
        Doc.journal.append(group)
        apply(group, back: false)
        return true
    }

    private func rawInsert(_ s: String) {
        record(.insert(s)); proxy.insertText(s); noteTail()
    }

    private func rawDelete() {
        if let sel = proxy.selectedText, !sel.isEmpty {
            record(.delete(sel))
        } else if let c = proxy.documentContextBeforeInput?.last {
            record(.delete(String(c)))
        }
        proxy.deleteBackward(); noteTail()
    }

    private func rawMove(_ n: Int) {
        record(.move(n)); proxy.adjustTextPosition(byCharacterOffset: n); noteTail()
    }

    func insertText(_ s: String) {
        if let r = runes {
            Runes.wrote(s, after: proxy.documentContextBeforeInput ?? "", r)
            rawInsert(Runes.encode(s, r))
            return
        }
        guard let lang = morse, !s.isEmpty else { rawInsert(s); return }
        let prev = proxy.documentContextBeforeInput?.last
        rawInsert(Morse.encode(s, lang, glyphBefore: prev.map { !($0 == " " || $0 == "\n" || $0 == "\t") } ?? false))
    }

    /// One LETTER back: its code and the gap before it.
    func deleteBackward() {
        if runes != nil {
            rawDelete()      // one rune = one letter
            if !Runes.tail.isEmpty { Runes.tail.removeLast() }
            return
        }
        guard let lang = morse else { rawDelete(); return }
        let raw = rawBefore
        guard let last = Morse.pieces(raw, lang).last else { rawDelete(); return }
        for _ in 0..<max(1, raw.count - last.start) { rawDelete() }
    }

    /// The caret by `n` letters.
    func adjustTextPosition(byCharacterOffset n: Int) {
        if runes != nil { Runes.tail = ""; rawMove(n); return }
        guard let lang = morse, n != 0 else { rawMove(n); return }
        if n < 0 {
            let raw = rawBefore
            let ps = Morse.pieces(raw, lang)
            let k = min(-n, ps.count)
            let to = k == 0 ? raw.count : ps[ps.count - k].start
            rawMove(-(raw.count - to))
        } else {
            let raw = proxy.documentContextAfterInput ?? ""
            let ps = Morse.pieces(raw, lang)
            let to = n >= ps.count ? raw.count : ps[n].start
            rawMove(to)
        }
    }
}
