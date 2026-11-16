import Foundation

/// Spaced repetition for the app's learning tab - the iOS twin of Android
/// `Learn.kt` (3.9.44), with the SAME file shape (`learn.json`, key
/// `lang\tword`, fields `tr deck cat note ex reps ivl ease due`), so one day an
/// export from either phone reads on the other.
///
/// The cards are the words the user chose to LEARN - typed into the tab or
/// tapped under the writing box - not the `＋` words: those are words the
/// keyboard was missing, which the user usually knows. A `＋` word becomes a
/// card only when the user puts it there.
///
/// Three grades, a small SM-2: 0 "don't know" starts over and comes back in a
/// minute, 1 "so-so" grows slowly, 2 "know" multiplies by the ease.
///
/// App only: the keyboard extension never compiles this file (it lives in
/// `App/`, not `Shared/`). On-device only; nothing here touches the network.
enum Learn {

    struct Card: Identifiable, Equatable {
        let lang: String
        let word: String
        var tr = ""
        var deck = false
        var cat = ""
        var note = ""
        var ex = ""
        var reps = 0
        var ivl = 0.0          // days
        var ease = 2.5
        var due: Int64 = 0     // epoch ms; 0 = new, due now

        var id: String { Learn.key(lang, word) }
        var isNew: Bool { reps == 0 && due == 0 }
    }

    private static let day: Int64 = 86_400_000
    private static let minute: Int64 = 60_000

    static func key(_ lang: String, _ word: String) -> String { "\(lang)\t\(word)" }

    static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    /// Next to the `＋` words: the App Group container, else the app's own files.
    private static var folder: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Shared.group)
            ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }
    private static var file: URL { folder.appendingPathComponent("learn.json") }
    private static var logFile: URL { folder.appendingPathComponent("learn_log.tsv") }

    private static func load() -> [String: Card] {
        guard let data = try? Data(contentsOf: file),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: [String: Any]]
        else { return [:] }   // a broken file must not take the app down
        var out: [String: Card] = [:]
        for (k, o) in root {
            guard let t = k.firstIndex(of: "\t"), t != k.startIndex else { continue }
            var c = Card(lang: String(k[..<t]), word: String(k[k.index(after: t)...]))
            c.tr = o["tr"] as? String ?? ""
            c.deck = o["deck"] as? Bool ?? false
            c.cat = o["cat"] as? String ?? ""
            c.note = o["note"] as? String ?? ""
            c.ex = o["ex"] as? String ?? ""
            c.reps = (o["reps"] as? NSNumber)?.intValue ?? 0
            c.ivl = (o["ivl"] as? NSNumber)?.doubleValue ?? 0
            c.ease = (o["ease"] as? NSNumber)?.doubleValue ?? 2.5
            c.due = (o["due"] as? NSNumber)?.int64Value ?? 0
            out[k] = c
        }
        return out
    }

    /// Written whole through a temporary file (`.atomic`), as Android.
    private static func save(_ cards: [String: Card]) {
        var root: [String: [String: Any]] = [:]
        for (k, c) in cards {
            var o: [String: Any] = ["reps": c.reps, "ivl": c.ivl, "ease": c.ease, "due": c.due]
            if !c.tr.isEmpty { o["tr"] = c.tr }
            if c.deck { o["deck"] = true }
            if !c.cat.isEmpty { o["cat"] = c.cat }
            if !c.note.isEmpty { o["note"] = c.note }
            if !c.ex.isEmpty { o["ex"] = c.ex }
            root[k] = o
        }
        if let data = try? JSONSerialization.data(withJSONObject: root) {
            try? data.write(to: file, options: .atomic)
        }
    }

    /// The cards being learned, in [order] of languages, then alphabetically.
    static func cards(order: [String]) -> [Card] {
        load().values.filter(\.deck).sorted {
            let a = order.firstIndex(of: $0.lang) ?? .max, b = order.firstIndex(of: $1.lang) ?? .max
            return a != b ? a < b : $0.word.lowercased() < $1.word.lowercased()
        }
    }

    static func card(_ lang: String, _ word: String) -> Card {
        load()[key(lang, word)] ?? Card(lang: lang, word: word)
    }

    /// Put [word] among the cards; a card met before keeps its history.
    /// [tr], when given, becomes the card's translation unless it has one.
    static func add(_ lang: String, _ word: String, tr: String = "") {
        let w = word.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !w.isEmpty else { return }
        var c = card(lang, w)
        c.deck = true
        if c.tr.isEmpty { c.tr = tr }
        update(c)
    }

    /// Out of the deck; its data stays for a later return.
    static func drop(_ card: Card) {
        var c = card
        c.deck = false
        update(c)
    }

    static func update(_ card: Card) {
        var all = load()
        all[card.id] = card
        save(all)
    }

    /// Due now, the longest-overdue first, new words last.
    static func due(_ cards: [Card], now: Int64 = now()) -> [Card] {
        cards.filter { $0.due <= now }.sorted { a, b in
            a.isNew != b.isNew ? !a.isNew : a.due < b.due
        }
    }

    static func categories(_ cards: [Card]) -> [String] {
        Array(Set(cards.map(\.cat).filter { !$0.isEmpty })).sorted { $0.lowercased() < $1.lowercased() }
    }

    /// Apply a grade (0 don't know, 1 so-so, 2 know) and store the card.
    @discardableResult
    static func grade(_ card: Card, _ g: Int, now: Int64 = now()) -> Card {
        var c = card
        switch g {
        case 0:
            c.reps = 0
            c.ivl = 0
            c.ease = max(1.3, c.ease - 0.2)
            c.due = now + minute
        case 1:
            c.ivl = c.reps == 0 ? 1 : max(1, c.ivl * 1.2)
            c.ease = max(1.3, c.ease - 0.15)
            c.reps += 1
            c.due = now + Int64(c.ivl * Double(day))
        default:
            switch c.reps {
            case 0: c.ivl = 1
            case 1: c.ivl = 3
            default: c.ivl = max(c.ivl + 1, c.ivl * c.ease)
            }
            c.reps += 1
            c.due = now + Int64(c.ivl * Double(day))
        }
        update(c)
        // A plain history of grades, as Android; it never leaves the phone.
        let line = "\(now)\t\(c.lang)\t\(c.word)\t\(g)\n"
        if let h = try? FileHandle(forWritingTo: logFile) {
            h.seekToEndOfFile(); h.write(Data(line.utf8)); try? h.close()
        } else {
            try? Data(line.utf8).write(to: logFile)
        }
        return c
    }
}
