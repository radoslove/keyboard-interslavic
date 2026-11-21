import Foundation

/// Ready word sets for the learning tab - Android `Sets.kt` (3.10.18), reading
/// the SAME table (`android-app/.../assets/learn_sets.tsv`): a column per
/// language, so one set serves every language as the word and every other one
/// as its translation. A row whose id starts with `#` is the set's own name.
/// A set taken into learning becomes an ordinary category of cards.
enum Sets {
    struct Row { let set: String, id: String, cells: [String: String] }

    private static let rows: [Row] = {
        guard let u = Bundle.main.url(forResource: "learn_sets", withExtension: "tsv"),
              let text = try? String(contentsOf: u, encoding: .utf8) else { return [] }
        var lines = text.components(separatedBy: "\n")
        guard !lines.isEmpty else { return [] }
        let head = lines.removeFirst().trimmingCharacters(in: .newlines).components(separatedBy: "\t")
        return lines.compactMap { line in
            let f = line.trimmingCharacters(in: CharacterSet(charactersIn: "\r")).components(separatedBy: "\t")
            guard f.count >= 2 else { return nil }
            var cells: [String: String] = [:]
            for i in 2..<min(f.count, head.count) {
                let v = f[i].trimmingCharacters(in: .whitespaces)
                if !v.isEmpty { cells[head[i]] = v }
            }
            return Row(set: f[0], id: f[1], cells: cells)
        }
    }()

    /// The sets that have words in [lang], in file order.
    static func names(_ lang: String) -> [String] {
        var seen = Set<String>()
        return rows.filter { !$0.id.hasPrefix("#") && $0.cells[lang] != nil }.map(\.set)
            .filter { seen.insert($0).inserted }
    }

    /// A set's name in the screen language, else English.
    static func title(_ set: String) -> String {
        guard let r = rows.first(where: { $0.set == set && $0.id.hasPrefix("#") }) else { return set }
        // Serbian Cyrillic has no column: the Latin name, converted (the topics
        // stood in Latin on an otherwise Cyrillic screen, simulator 261118).
        if Ui.current == "sr-Cyrl", let sr = r.cells["sr"] { return Scripts.convert(sr, .cyrillic, "sr") }
        return r.cells[Ui.current] ?? r.cells["en"] ?? set
    }

    /// The set's words in [lang], each with its translation in [target].
    static func words(_ set: String, _ lang: String, _ target: String) -> [(String, String)] {
        rows.filter { $0.set == set && !$0.id.hasPrefix("#") }.compactMap { r in
            guard let w = r.cells[lang] else { return nil }
            return (w, r.cells[target] ?? r.cells["en"] ?? "")
        }
    }

    /// The language a set's words are translated into - Android `Learn.target`:
    /// Polish for every other language, for Polish the target chosen in ✎.
    static func target(_ lang: String) -> String {
        let t = lang == "pl" ? (Gloss.target("pl") ?? "en") : "pl"
        return t != lang ? t : (lang == "en" ? "pl" : "en")
    }
}
