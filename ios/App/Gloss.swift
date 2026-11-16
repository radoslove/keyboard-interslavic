import Foundation

/// Offline word-by-word gloss for the app - the iOS twin of Android `Gloss.kt`
/// (3.9.39+), reading the SAME files (`gloss_<lang>.tsv` from the Polish
/// Wiktionary, `tools/build_gloss.py`, CC BY-SA 4.0).
///
/// A language other than Polish glosses INTO Polish (`Haus` -> `dom`); Polish
/// glosses into any language its entry has (`dom` -> `Haus`, `dům`, `дом`).
/// A gloss, not a translation: one word for one word, no grammar.
///
/// App only (11 MB, the extension's memory limit) and offline.
enum Gloss {
    private static var tables: [String: [String: String]] = [:]
    private static let lock = NSLock()

    static func url(_ lang: String) -> URL? { Bundle.main.url(forResource: "gloss_\(lang)", withExtension: "tsv") }

    static func has(_ lang: String) -> Bool { url(lang) != nil }

    /// Every form -> its line's gloss; the first line wins. Slow the first time
    /// (hundreds of thousands of forms): call it off the main thread.
    static func table(_ lang: String) -> [String: String] {
        lock.lock(); defer { lock.unlock() }
        if let t = tables[lang] { return t }
        var m: [String: String] = [:], folded: [String: String] = [:]
        if let u = url(lang), let text = try? String(contentsOf: u, encoding: .utf8) {
            text.enumerateLines { line, _ in
                guard let tab = line.firstIndex(of: "\t"), tab != line.startIndex else { return }
                let v = String(line[line.index(after: tab)...])
                let forms = line[..<tab].split(separator: " ").map(String.init)
                for k in forms where !k.isEmpty {
                    // Polish: a form under several entries keeps all their translations.
                    if let old = m[k] { if lang == "pl" { m[k] = old + "|" + v } } else { m[k] = v }
                }
                // Without diacritics too (`duzy` finds `duży`); a real spelling wins.
                for k in forms {
                    let f = fold(k)
                    if f != k, !f.isEmpty, folded[f] == nil { folded[f] = v }
                }
            }
        }
        for (k, v) in folded where m[k] == nil { m[k] = v }
        tables[lang] = m
        return m
    }

    /// `duży` -> `duzy`, `Straße` -> `Strasse`, `ł` -> `l`.
    static func fold(_ s: String) -> String {
        var out = ""
        for ch in s.decomposedStringWithCanonicalMapping.unicodeScalars {
            if ch.properties.generalCategory == .nonspacingMark { continue }
            switch ch {
            case "ł": out += "l"
            case "Ł": out += "L"
            case "ß": out += "ss"
            case "đ": out += "d"
            case "Đ": out += "D"
            default: out.unicodeScalars.append(ch)
            }
        }
        return out
    }

    /// The languages [from] can be glossed into; for Polish the keyboard's own first.
    static func targets(_ from: String, enabled: [String]) -> [String] {
        guard has(from) else { return [] }
        guard from == "pl" else { return ["pl"] }
        let all = ["de", "en", "nb", "cs", "sk", "sl", "hr", "sr", "bs", "mk", "ru", "uk", "bg", "be"]
        return all.filter { enabled.contains($0) } + all.filter { !enabled.contains($0) }
    }

    /// The keyboard's ticked languages (the keyboard writes them to the group).
    static var enabled: [String] { Shared.store.stringArray(forKey: "languages") ?? ["isv"] }

    /// The language a [from] word is glossed into: Polish for every other
    /// language, and for Polish the target last chosen in ✎ (Android `Learn.target`).
    static func target(_ from: String) -> String? {
        let ts = targets(from, enabled: enabled)
        let chosen = UserDefaults.standard.string(forKey: "gloss_target_\(from)")
        return ts.first { $0 == chosen } ?? ts.first { enabled.contains($0) && $0 != from } ?? ts.first
    }

    /// The gloss of one word for the learning cards, or nil. Loads the table:
    /// off the main thread.
    static func lookup(_ lang: String, _ w: String) -> String? {
        guard let to = target(lang), has(lang) else { return nil }
        return word(table(lang), w, from: lang, to: to)
    }

    /// German has no inflection tables in this source: a few endings are cut.
    private static let deEndings = ["en", "er", "es", "em", "e", "n", "s", "st", "t"]

    private static func cap(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }

    private static func find(_ t: [String: String], _ word: String, _ from: String) -> String? {
        let low = word.lowercased()
        if from == "de" {
            for k in [word, cap(low), low, fold(word)] { if let v = t[k] { return v } }
            for e in deEndings where low.count > e.count + 2 && low.hasSuffix(e) {
                let stem = String(low.dropLast(e.count))
                if let v = t[stem] ?? t[cap(stem)] { return v }
            }
            return nil
        }
        return t[low] ?? t[fold(low)]
    }

    /// One gloss for [word] in [to], or nil.
    static func word(_ t: [String: String], _ word: String, from: String, to: String) -> String? {
        guard let v = find(t, word, from) else { return nil }
        if from != "pl" { return v }
        for part in v.split(separator: "|") where part.hasPrefix("\(to)=") {
            return String(part.dropFirst(to.count + 1))
        }
        return nil
    }

    /// [text] in pieces, each with whether it was glossed: words not found stay
    /// as typed and are shown greyed, so nothing pretends to be a translation.
    static func pieces(_ t: [String: String], _ text: String, from: String, to: String) -> [(String, Bool)] {
        var out: [(String, Bool)] = []
        var word = "", gap = ""
        func flushWord() {
            guard !word.isEmpty else { return }
            let before = out.map(\.0).joined().trimmingCharacters(in: .whitespaces)
            if var g = Self.word(t, word, from: from, to: to) {
                let starts = before.isEmpty || ".!?\n".contains(before.last!)
                // A capital only where a sentence starts (`Das Haus` -> `To dom`).
                if starts && word.first!.isUppercase { g = cap(g) }
                else if from == "de" { g = g.prefix(1).lowercased() + g.dropFirst() }
                out.append((g, true))
            } else {
                out.append((word, false))
            }
            word = ""
        }
        for ch in text {
            if ch.isLetter {
                if !gap.isEmpty { out.append((gap, true)); gap = "" }
                word.append(ch)
            } else {
                flushWord()
                gap.append(ch)
            }
        }
        flushWord()
        if !gap.isEmpty { out.append((gap, true)) }
        return out
    }
}
