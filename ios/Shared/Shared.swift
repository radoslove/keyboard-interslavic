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

    /// The `＋` words of one language, lowercase. In the group one list per
    /// language; in a keyboard's own defaults (no Full Access) the extension
    /// already is the language, and the key stays what it always was.
    static func userWordsKey(_ language: String) -> String {
        fullAccess ? "userWords." + language : "userWords"
    }

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
        guard fullAccess, let group = UserDefaults(suiteName: group) else { return }
        let mine = UserDefaults.standard
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
}
