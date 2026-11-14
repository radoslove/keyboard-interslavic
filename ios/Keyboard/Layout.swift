import Foundation

/// The canonical character table, same one as `build_keyman.py` and
/// `windows/src/KBDMSSTD.klc`. Standard orthography only (HOUSE_STYLE §1):
/// the extended alphabet is deliberately absent everywhere, not just here.
///
/// A phone has no AltGr, so the four letters live on LONGPRESS of their base
/// key - `c` held gives `č`. That is the same mnemonic as AltGr+C on desktop,
/// which is the point: one habit across platforms.
enum Layout {

    /// Every language of the keyboard, in the order of the list, with the name
    /// the space bar shows. ONE keyboard extension carries them all (owner
    /// 2026-11-11): the system's globe list had grown to a dozen entries and the
    /// last ones fell below its edge. The user ticks the languages in the
    /// keyboard's settings and 🌐 steps through them, as on Android.
    static let all: [(code: String, name: String)] = [
        ("isv", "Medžuslovjansky"), ("pl", "Polski"), ("cs", "češsky"),
        ("sk", "slovačsky"), ("sl", "slovenečsky"), ("hr", "hrvatsky"),
        ("sr", "srbsky"), ("bs", "bosnijsky"), ("mk", "makedonsky"),
        ("ru", "russky"), ("uk", "ukrajinsky"), ("bg", "bulgarsky"), ("be", "bělorussky"), ("sla", "praslovjansky"), ("nb", "norvežsky"),
        ("en", "English"),
    ]

    static func name(_ code: String) -> String { all.first { $0.code == code }?.name ?? code }

    /// The ticked languages, in list order; never empty. First run: Interslavic
    /// plus the phone's own language if the keyboard has it.
    static var enabled: [String] {
        get {
            if let saved = Shared.store.stringArray(forKey: "languages") {
                let known = all.map(\.code).filter { saved.contains($0) }
                if !known.isEmpty { return known }
            }
            var first = ["isv"]
            if let phone = Locale.preferredLanguages.first?.prefix(2).lowercased(),
               phone != "isv", all.contains(where: { $0.code == phone }) {
                first.append(String(phone))
            }
            return first
        }
        set {
            let ordered = all.map(\.code).filter { newValue.contains($0) }
            Shared.store.set(ordered.isEmpty ? ["isv"] : ordered, forKey: "languages")
        }
    }

    /// The language being typed now: `isv`, `pl`, `cs`, `sk`, `sl`, `hr`, `sr`
    /// (Latin + Cyrillic), `bs`, `mk` and `ru` (Cyrillic only), `sla`
    /// (Proto-Slavic), `nb` or `en`.
    /// Read on every letter of a glide, so it is kept in memory and only
    /// written through to the store; `forgetLanguage()` after the list changes.
    static var language: String {
        get {
            if let c = current { return c }
            let on = enabled
            let saved = Shared.store.string(forKey: "language") ?? ""
            let c = on.contains(saved) ? saved : on[0]
            current = c
            return c
        }
        set {
            current = newValue
            Shared.store.set(newValue, forKey: "language")
        }
    }
    private static var current: String?
    static func forgetLanguage() { current = nil }

    /// base letter -> the letters of THIS keyboard's language reached by
    /// holding it; they come first in the popup. The three Interslavic
    /// digraphs sit on `d` / `l` / `n`, as on Android (3.5.7) and in
    /// `android/isv_latin.xml`.
    /// English has none of its own; its popups hold only the foreign
    /// letters (Android `Lang.EN -> emptyMap()`).
    static var accents: [Character: [String]] { accents(for: language) }

    static func accents(for language: String) -> [Character: [String]] {
        switch language {
        case "pl":
            return ["a": ["ą"], "c": ["ć"], "e": ["ę"], "l": ["ł"], "n": ["ń"],
                    "o": ["ó"], "s": ["ś"], "z": ["ż", "ź"]]
        case "cs":
            // Android `Layout.csOwn` (3.5.31), same order: the order was
            // measured on the corpus, so it is copied, not re-thought.
            return ["a": ["á"], "c": ["č"], "d": ["ď"], "e": ["ě", "é"],
                    "i": ["í"], "n": ["ň"], "o": ["ó"], "r": ["ř"], "s": ["š"],
                    "t": ["ť"], "u": ["ů", "ú"], "y": ["ý"], "z": ["ž"]]
        case "sk":
            // Android `Layout.skOwn`, same order, measured on the corpus:
            // á before ä, ľ before ĺ, ô before ó.
            return ["a": ["á", "ä"], "c": ["č"], "d": ["ď"], "e": ["é"],
                    "i": ["í"], "l": ["ľ", "ĺ"], "n": ["ň"], "o": ["ô", "ó"],
                    "r": ["ŕ"], "s": ["š"], "t": ["ť"], "u": ["ú"], "y": ["ý"],
                    "z": ["ž"]]
        case "sl":
            // Android `Layout.slOwn`: č š ž on their base letters.
            return ["c": ["č"], "s": ["š"], "z": ["ž"]]
        case "hr", "sr", "bs":
            // Android `Layout.hrSrOwn`: č before ć, measured on both wordlists.
            return ["c": ["č", "ć"], "d": ["đ"], "s": ["š"], "z": ["ž"]]
        case "mk":
            // Android `Layout.mkOwn`: the internal Latin of the wordlist, held
            // on the key it folds to; `Script.out` writes ѓ ќ ж ѕ ч ш.
            return ["g": ["ǵ"], "k": ["ḱ"], "z": ["ž", "ẑ"], "c": ["č"], "s": ["š"]]
        case "ru":
            // Android `Layout.ruOwn`, ordered by frequency mass in the wordlist:
            // ё before э, ь before ъ.
            return ["e": ["ë", "ê"], "w": ["ŵ"], "z": ["ž"], "b": ["ḃ", "ḅ"], "u": ["ü"]]
        case "uk":
            // Ukrainian homophonic (xkeyboard-config `ua(homophonic)`, owner
            // 2026-11-11): the keys the phone lacks are held on their Latin
            // match. The apostrophe (`п'ять`) has no key - the glide skips it,
            // and a tap takes it from the strip under `.`.
            return ["c": ["č"], "z": ["ž"], "u": ["ü"], "w": ["ŵ"], "e": ["ê"],
                    "i": ["ï"], "b": ["ḃ"]]
        case "bg":
            // Bulgarian traditional phonetic ЯВЕРТЪ (xkeyboard-config
            // `bg(phonetic)`, owner 2026-11-11): the keys the phone lacks are
            // held on their Latin match, ш before щ (frequency in the list).
            return ["s": ["š", "ŝ"], "c": ["č"], "u": ["ü"], "i": ["ì"]]
        case "be":
            // Belarusian `by(phonetic)` (owner 2026-11-11, Android `beOwn`):
            // held on their Latin match, ordered by share of running words in
            // the Leipzig corpora - ў before ю on u, э before ё on e.
            return ["s": ["š"], "c": ["č"], "u": ["ŭ", "ü"], "e": ["ê", "ë"]]
        case "sla":
            // Proto-Slavic in the Wiktionary notation (tools/build_sla_wordlist.py):
            // `ъ` `ь` have keys of their own (`q` `f`), the rest is held,
            // ordered by frequency in the list: ě before ę.
            return ["e": ["ě", "ę"], "o": ["ǫ"], "c": ["č"], "s": ["š", "ś"],
                    "z": ["ž"], "l": ["ľ"], "n": ["ň"], "r": ["ř"], "t": ["ť"],
                    "d": ["ď"]]
        case "en":
            return [:]
        case "nb":
            // Android `Layout.nbOwn` (3.5.8): æ ø å have keys of their own;
            // held, they give the Swedish/Danish neighbours.
            return ["e": ["é", "è", "ê"], "o": ["ó", "ò", "ô"], "a": ["à"],
                    "æ": ["ä"], "ø": ["ö"], "å": ["ä", "ö"]]
        default:
            return ["c": ["č"], "s": ["š"], "z": ["ž"], "e": ["ě"],
                    "d": ["dž"], "l": ["lj"], "n": ["nj"]]
        }
    }

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

    /// Held on `.` of the LETTER layer, where 😀 took the comma's key
    /// (owner 2026-10-25): the comma comes first, right over the key, then
    /// `:` and `;` next to it (owner 2026-11-07: the comma must not sit at
    /// the far end of the strip), then `?` (not on the letters since 3.5.16)
    /// and the rest.
    /// Seven marks, the owner's order (2026-11-07): `,` first and over the key.
    /// Eleven did not fit the strip on the Android phone - 418dp asked of a
    /// 339dp row - and the first cells fell off the screen; the rest stay on
    /// the `123` page. ⚠ Code only: needs an Xcode build on the build Mac.
    static let periodLetterAccents: [String] =
        [",", "?", "!", "-", "'", "\"", ":", ";"]

    /// Held on `,`: the rest of the sentence punctuation.
    static let commaAccents: [String] = [":", ";", "-", "–", "(", ")"]

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

    /// Cyrillic and Glagolitic have no letter on `x`, so in those scripts that
    /// key is gone, and `q` `w` carry the digraphs `lj` `nj` - `љ` `њ` where the
    /// Serbian and Macedonian keyboards have them, `ⰾⱐ` `ⱀⱐ` in Glagolitic
    /// (Android 3.6.14, `Layout.nonLatinRows`). Inside, the keys stay `q` and
    /// `w`; `keyText` says what they write.
    static var letterRows: [[Character]] { rows(latin: Script.mode == .latin) }

    /// The rows in LATIN - the grid the swipe decoder is calibrated to, and the
    /// reference the shorter scripts are padded out to.
    static var latinLetterRows: [[Character]] { rows(latin: true) }

    static func rows(latin: Bool) -> [[Character]] {
        if language == "nb" {
            return [Array("qwertyuiopå"), Array("asdfghjkløæ"), Array("zxcvbnm")]  // Android `nbRows`
        }
        // Serbian Cyrillic (JUS I.K1.003) and Macedonian: the Latin QWERTZ grid
        // with `љ` `њ` on `q` `w` and `џ` on the `x` position; no `y`
        // (Android `srCyrillicRows`, `mkCyrillicRows`).
        // Russian, phonetic: its letters take all 26 Latin keys, QWERTY grid.
        // Proto-Slavic too: `ъ` on `q`, `ь` on `f`, every other key its letter.
        if ["ru", "uk", "bg", "be", "sla"].contains(language) {
            return [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")]
        }
        // Polish in Glagolitic keeps the Polish Latin grid: every key writes its
        // own letter and the script turns whole words (cz, rz, sia) into Glagolitic.
        if language == "pl" && !latin { return rows(latin: true) }
        if !latin && (language == "sr" || language == "mk") {
            return [Array("qwertzuiop"), Array("asdfghjkl"), Array("xcvbnm")]
        }
        if latin && qwertz {
            return [Array("qwertzuiop"), Array("asdfghjkl"), Array("yxcvbnm")]
        }
        return latin
            ? [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")]
            : [Array("qwertyuiop"), Array("asdfghjkl"), Array("zcvbnm")]
    }

    /// What a letter key WRITES, in Latin: its own letter, except `q` and `w`
    /// outside Latin, which write `lj` and `nj`. Everything downstream (the
    /// wordlist, the glide, `Script.out`) keeps seeing the two letters.
    static func keyText(_ ch: Character, latin: Bool) -> String {
        guard !latin else { return String(ch) }
        // Polish Glagolitic: the free keys write what Polish has no key for
        // (owner 2026-11-14, variant 1): q = ch (Ⱈ), x = dzelo (Ⰷ, marker ẑ).
        if language == "pl" {
            switch ch {
            // Layout v03 (owner 2026-11-14): q = ja Ⱑ, w = ę Ⱔ, v = w Ⰲ, x = ch Ⱈ.
            case "q": return "ja"
            case "Q": return "Ja"
            case "w": return "ę"
            case "W": return "Ę"
            case "v": return "w"
            case "V": return "W"
            // x = ch Ⱈ like Keyman's Glagolitic (Phonetic) and Cyrillic х
            // (owner 2026-11-14); dzelo stays on hold d.
            case "x": return "ch"
            case "X": return "Ch"
            default: return String(ch)
            }
        }
        // Russian, phonetic: every key writes its own letter (я on q, ш on w);
        // the Interslavic `lj`/`nj` keys are not part of this layout.
        if ["ru", "uk", "bg", "be", "sla"].contains(language) { return String(ch) }
        // Macedonian: ONE letter per key - the wordlist carries ǉ ǌ ǆ as
        // single characters (Android `keyText`, Lang.MK).
        if language == "mk" {
            switch ch {
            case "q": return "ǉ"
            case "Q": return "Ǉ"
            case "w": return "ǌ"
            case "W": return "Ǌ"
            case "x": return "ǆ"
            case "X": return "Ǆ"
            default: return String(ch)
            }
        }
        switch ch {
        case "q": return "lj"
        case "Q": return "Lj"
        case "w": return "nj"
        case "W": return "Nj"
        // Serbian only: `џ` is one letter with the `x` position in the national
        // layout; the key writes the pair `dž`, like `љ` and `њ`.
        case "x" where language == "sr": return "dž"
        case "X" where language == "sr": return "Dž"
        default: return String(ch)
        }
    }

    /// Key indices of the two digraph keys in `swipeKeys`.
    static let ljKey = 16   // q
    static let njKey = 22   // w
    static let dzhKey = 23  // x - Serbian Cyrillic `џ`

    /// QWERTY or QWERTZ, chosen per language in the keyboard's settings (Android
    /// `KeyRows`, owner 2026-11-10). The default is the layout of the language's
    /// own keyboards: QWERTZ for Czech, Slovak, Slovenian, Croatian and Serbian. Only `y` and `z` swap, so the
    /// grid stays the same; the decoder reads key centres from the buttons and
    /// the wordlist stores letters, not positions.
    static let qwertzByDefault: Set<String> = ["cs", "sk", "sl", "hr", "sr", "bs"]
    /// Not for Norwegian (rows of 11) nor Macedonian, which is Cyrillic only
    /// and has its own grid (Android `KeyRows.offered`).
    static var qwertzOffered: Bool { !["nb", "mk", "ru", "uk", "bg", "be", "sla"].contains(language) }

    /// Languages whose keys each write ONE letter, so the Interslavic digraph
    /// routes (`lj` `nj` over `q` `w`) must stay off.
    /// Languages that write an apostrophe INSIDE words (`п'ять`, `сям'я`);
    /// Android `Lang.APOSTROPHE_IN_WORD`.
    static var apostropheInWord: Bool { ["uk", "be"].contains(language) }

    static var singleLetterKeys: Bool { ["mk", "ru", "uk", "bg", "be", "sla", "pl"].contains(language) }
    static var qwertz: Bool {
        get {
            guard qwertzOffered else { return false }
            return Shared.store.object(forKey: "qwertz_" + language) as? Bool
                ?? qwertzByDefault.contains(language)
        }
        set { Shared.store.set(newValue, forKey: "qwertz_" + language) }
    }

    /// How many key widths a letter row spans. Fixed by the LATIN top row, so
    /// shift and backspace grow instead of the letters when a script drops keys.
    static var rowUnits: Int { language == "nb" ? 11 : 10 }

    /// The keys a swipe can cross, in the order of the wordlist's key indices:
    /// a-z, then the language's own letter keys (`build_swipe_dictionary.py`).
    static var swipeKeys: [Character] { language == "nb" ? nbKeys : latinKeys }
    private static let latinKeys = Array("abcdefghijklmnopqrstuvwxyz")
    private static let nbKeys = latinKeys + Array("åøæ")

    /// Index of a key letter in `swipeKeys`, nil for anything else.
    static func swipeIndex(_ ch: Character) -> Int? {
        (language == "nb" ? nbIndex : latinIndex)[ch]
    }
    private static let latinIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: latinKeys.enumerated().map { ($1, $0) })
    private static let nbIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: nbKeys.enumerated().map { ($1, $0) })

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
        // Polish Glagolitic, layout v02 (owner 2026-11-14,
        // vault_002/Projects/INTERSLOVE/glagolica_pl/uklad_klawiatury_pl_glagolica_v02.md):
        // every letter Polish writes with more than one key sits under the key
        // it starts with; the jotted family under q, j (ja…) and i (ia…).
        if !latin && language == "pl" && key.isLetter {
            let glagHolds: [Character: [String]] = [
                "q": ["ja", "ju", "ję", "ją"], "j": ["ja", "ju", "ję", "ją"],
                "i": ["ia", "iu", "ię", "ią"], "w": ["ę", "ię", "ą", "ią"],
                "a": ["ą", "ia"], "c": ["ć", "cz"], "d": ["dź", "ď", "dż", "ḍ"],
                "e": ["ę", "ię"], "h": ["ch"], "l": ["ł"], "n": ["ń"],
                "o": ["ó", "ō"], "r": ["rz"], "s": ["ś", "sz", "szcz"], "z": ["ż", "ź"],
            ]
            guard let g = glagHolds[lower] else { return nil }
            return g.map { cased($0, uppercase) }
        }
        if latin {
            let isvOnPl: [Character: String] = ["c": "č", "s": "š", "z": "ž", "e": "ě"]
            let foreign = (foreignAccents[lower] ?? [])
                + (language == "pl" ? isvOnPl[lower].map { [$0] } ?? [] : [])
            for f in foreign where !all.contains(f) { all.append(f) }
        }
        if !all.isEmpty { return all.map { cased($0, uppercase) } }
        if key == "." { return periodLetterAccents }
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
