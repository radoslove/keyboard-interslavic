import Foundation

/// Storage shared by the app and the keyboards (an App Group).
///
/// A keyboard reaches the group only with Full Access switched on. Without it
/// the keyboard keeps everything in its own defaults, exactly as before 0.4.0,
/// and the app simply sees nothing - the keyboard still works in full. With it,
/// the app lists and removes the words added with `＋` and picks the theme and
/// the script, as on Android. Nothing here touches the network: Full Access is
/// asked for this storage only.
enum Shared {

    static let group = "group.com.radoslove.interslavic"

    /// Set by the keyboard from `hasFullAccess` before anything is read; the
    /// app always owns its group.
    static var fullAccess = true

    static var store: UserDefaults {
        fullAccess ? (UserDefaults(suiteName: group) ?? .standard) : .standard
    }

    /// The `＋` words of one language, lowercase, one list per language - also
    /// in the keyboard's own defaults now that one extension types them all.
    /// The old Interslavic list without Full Access (`userWords`) is adopted by
    /// `adoptLocalWords`.
    static func userWordsKey(_ language: String) -> String { "userWords." + language }

    static func userWords(_ language: String) -> [String] {
        store.stringArray(forKey: userWordsKey(language)) ?? []
    }

    static func setUserWords(_ words: [String], _ language: String) {
        store.set(words, forKey: userWordsKey(language))
    }

    /// Words added before Full Access lived in the keyboard's own defaults
    /// under `userWords`; merged into the group the first time it is reachable.
    /// The old copy stays where it was; adopted once, so a word removed in the
    /// app does not come back from it.
    static func adoptLocalWords(_ language: String) {
        let mine = UserDefaults.standard
        guard fullAccess, let group = UserDefaults(suiteName: group) else {
            // No Full Access: the pre-0.6 key of this (the Interslavic)
            // extension becomes its per-language key, once.
            if !mine.bool(forKey: "userWordsRenamed"), let old = mine.stringArray(forKey: "userWords") {
                mine.set(true, forKey: "userWordsRenamed")
                var all = mine.stringArray(forKey: "userWords." + language) ?? []
                for w in old where !all.contains(w) { all.append(w) }
                mine.set(all, forKey: "userWords." + language)
            }
            return
        }
        guard !mine.bool(forKey: "userWordsAdopted") else { return }
        mine.set(true, forKey: "userWordsAdopted")
        let local = mine.stringArray(forKey: "userWords") ?? []
        guard !local.isEmpty else { return }
        var all = group.stringArray(forKey: "userWords." + language) ?? []
        for w in local where !all.contains(w) { all.append(w) }
        group.set(all, forKey: "userWords." + language)
    }

    /// The learned touch offset moves into the group the same way, once.
    /// 0.4.0 forgot it: with Full Access on, the decoder fell back to the
    /// default 0.35 while the owner's thumb had taught it 0.15, and swipes
    /// went wrong (`kuznja` -> `kyslina`, owner 2026-10-25: "as if the
    /// dictionary stopped working"). Overwrites what the group learned since,
    /// which started from that wrong default.
    static func adoptLocalLift() {
        guard fullAccess, let group = UserDefaults(suiteName: group) else { return }
        let mine = UserDefaults.standard
        guard !mine.bool(forKey: "touchLiftAdopted") else { return }
        mine.set(true, forKey: "touchLiftAdopted")
        guard mine.object(forKey: "touchLift") != nil else { return }
        group.set(mine.double(forKey: "touchLift"), forKey: "touchLift")
    }

    /// Keyboard theme: auto (follows the phone), light or dark.
    enum Theme: String, CaseIterable { case auto, light, dark }

    static var theme: Theme {
        get { Theme(rawValue: store.string(forKey: "theme") ?? "") ?? .auto }
        set { store.set(newValue.rawValue, forKey: "theme") }
    }

    // MARK: - Collecting words (Android `Collector` + `Popularity`)

    /// Two OPT-IN signals, both OFF by default and Interslavic only, kept apart
    /// as on Android: words the list does NOT know (`keyboard-new`, for review,
    /// never auto-corrected) and how often known words are CONFIRMED
    /// (`keyboard-usage`, the popularity ranking). Counted in the group, so the
    /// keyboard counts only with Full Access; they leave the phone only in a
    /// batch the user mails from the app (same JSON as Android 3.9.9).
    static let newWordsKey = "collected.new", usageKey = "collected.usage"

    static var collectNewOn: Bool {
        get { store.bool(forKey: "collectNew") }
        set { store.set(newValue, forKey: "collectNew") }
    }

    static var collectUsageOn: Bool {
        get { store.bool(forKey: "collectUsage") }
        set { store.set(newValue, forKey: "collectUsage") }
    }

    static func counts(_ key: String) -> [String: Int] {
        (store.dictionary(forKey: key) as? [String: Int]) ?? [:]
    }

    static func bump(_ key: String, _ word: String) {
        var c = counts(key)
        c[word, default: 0] += 1
        store.set(c, forKey: key)
    }

    static func clearCounts(_ key: String) { store.removeObject(forKey: key) }

    /// A stable, pseudonymous per-install handle (`kbd-` + 8 hex), as on
    /// Android: no personal data, only so two phones proposing the same word
    /// count as two people.
    static var contributorId: String {
        if let id = store.string(forKey: "contributor") { return id }
        let id = "kbd-" + UUID().uuidString.lowercased().replacingOccurrences(of: "-", with: "").prefix(8)
        store.set(id, forKey: "contributor")
        return id
    }

    /// The batch for the database, in Android's shape (`Collector.exportBatchJson`).
    static func batchJSON() -> Data? {
        var words: [[String: Any]] = []
        for (w, n) in counts(newWordsKey).sorted(by: { $0.value > $1.value }) {
            words.append(["word": w, "count": n, "source": "keyboard-new"])
        }
        for (w, n) in counts(usageKey).sorted(by: { $0.value > $1.value }) {
            words.append(["word": w, "count": n, "source": "keyboard-usage"])
        }
        guard !words.isEmpty else { return nil }
        let batch: [String: Any] = ["contributor": contributorId, "source": "keyboard", "words": words]
        return try? JSONSerialization.data(withJSONObject: batch, options: [.prettyPrinted, .sortedKeys])
    }

    // MARK: - Gesture log (error reports)

    /// Real glides kept for error reports (owner 2026-11-13): OFF until the
    /// user switches it on in the app. The file lives in the group container,
    /// so the keyboard writes it only with Full Access, and nothing leaves the
    /// phone unless the user mails it from the app - the keyboard still has no
    /// network code.
    static var gestureLogOn: Bool {
        get { fullAccess && (UserDefaults(suiteName: group)?.bool(forKey: "gestureLog") ?? false) }
        set { UserDefaults(suiteName: group)?.set(newValue, forKey: "gestureLog") }
    }

    static var gestureLogURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?
            .appendingPathComponent("gestures.jsonl")
    }

    /// Past this many bytes the older half of the log is dropped.
    static let gestureLogCap = 2_000_000

    private static let gestureQueue = DispatchQueue(label: "interslove.gesturelog")

    /// One gesture as one JSON line.
    static func appendGesture(_ row: [String: Any]) {
        guard gestureLogOn, let url = gestureLogURL,
              var line = try? JSONSerialization.data(withJSONObject: row) else { return }
        line.append(0x0A)
        gestureQueue.async {
            if let h = try? FileHandle(forWritingTo: url) {
                h.seekToEndOfFile(); h.write(line)
                let size = h.offsetInFile
                try? h.close()
                if size > UInt64(gestureLogCap), let all = try? Data(contentsOf: url) {
                    let half = all.suffix(from: all.count / 2)
                    if let nl = half.firstIndex(of: 0x0A) {
                        try? all.suffix(from: nl + 1).write(to: url, options: .atomic)
                    }
                }
            } else {
                try? line.write(to: url)
            }
        }
    }

    /// The log as lines (oldest first); empty when there is none.
    static func gestureLines() -> [Data] {
        guard let url = gestureLogURL, let all = try? Data(contentsOf: url) else { return [] }
        return all.split(separator: 0x0A).map { Data($0) }
    }

    static func clearGestures() {
        guard let url = gestureLogURL else { return }
        try? FileManager.default.removeItem(at: url)
    }
}
