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
    /// the space bar shows. ONE keyboard extension carries them all: the
    /// system's globe list had grown to a dozen entries and the
    /// last ones fell below its edge. The user ticks the languages in the
    /// keyboard's settings and 🌐 steps through them, as on Android.
    static let all: [(code: String, name: String)] = [
        ("isv", "Medžuslovjansky"), ("pl", "Polski"), ("cs", "češsky"),
        ("sk", "slovačsky"), ("sl", "slovenečsky"), ("hr", "hrvatsky"),
        ("sr", "srbsky"), ("bs", "bosnijsky"), ("mk", "makedonsky"),
        ("ru", "russky"), ("uk", "ukrajinsky"), ("bg", "bulgarsky"), ("be", "bělorussky"), ("sla", "praslovjansky"), ("nb", "norvežsky"),
        ("en", "English"), ("de", "němečsky"), ("it", "italijanjsky"),
        // Android `Ui.kt` names (owner 261118: holandsky; rumunjsky = the
        // canon's `rumuńsky` in standard spelling).
        ("es", "španjsky"), ("fr", "francuzsky"), ("nl", "holandsky"), ("ro", "rumunjsky"),
    ]

    static func name(_ code: String) -> String { all.first { $0.code == code }?.name ?? code }

    /// The space bar's name: every living language in ITSELF (Android
    /// `Lang.label`); the Interslavic names stay in the ⚙ list only.
    /// Proto-Slavic has no living speaker, so it keeps its Interslavic name.
    static func label(_ code: String) -> String {
        switch code {
        case "pl": return "Polski"
        case "nb": return "Norsk"
        case "en": return "English"
        case "de": return "Deutsch"
        case "it": return "Italiano"
        case "es": return "Español"
        case "fr": return "Français"
        case "nl": return "Nederlands"
        case "ro": return "Română"
        case "cs": return "Čeština"
        case "sk": return "Slovenčina"
        case "sl": return "Slovenščina"
        case "hr": return "Hrvatski"
        case "sr": return "Srpski"
        case "bs": return "Bosanski"
        case "mk": return "Македонски"
        case "ru": return "Русский"
        case "uk": return "Українська"
        case "bg": return "Български"
        case "be": return "Беларуская"
        case "sla": return "Praslovjansky"
        default: return "Medžuslovjansky"
        }
    }

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
            // Ukrainian homophonic (xkeyboard-config `ua(homophonic)`): the
            // keys the phone lacks are held on their Latin
            // match. The apostrophe (`п'ять`) has no key - the glide skips it,
            // and a tap takes it from the strip under `.`.
            return ["c": ["č"], "z": ["ž"], "u": ["ü"], "w": ["ŵ"], "e": ["ê"],
                    "i": ["ï"], "b": ["ḃ"]]
        case "bg":
            // Bulgarian traditional phonetic ЯВЕРТЪ (xkeyboard-config
            // `bg(phonetic)`): the keys the phone lacks are
            // held on their Latin match, ш before щ (frequency in the list).
            return ["s": ["š", "ŝ"], "c": ["č"], "u": ["ü"], "i": ["ì"]]
        case "be":
            // Belarusian `by(phonetic)` (Android `beOwn`):
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
        case "de":
            // Android `Layout.deOwn`: `ß` held on `s`; `ü ö ä` are keys.
            return ["s": ["ß"]]
        case "it":
            // Android `Layout.itOwn`, ordered by how often each is written
            // (subtitles: è 2.6 M, à 1.1 M, ì 1.0 M, ò 0.6 M, ù 0.5 M, é 0.5 M).
            return ["a": ["à"], "e": ["è", "é"], "i": ["ì"], "o": ["ò"], "u": ["ù"]]
        case "es":
            // Android `Layout.esOwn`: the acute vowels on their vowel, `ü`
            // (pingüino) after `ú`; `ñ` is a key, not a hold.
            return ["a": ["á"], "e": ["é"], "i": ["í"], "o": ["ó"], "u": ["ú", "ü"]]
        case "fr":
            // Android `Layout.frOwn`: the most written first (é before è ê ë).
            return ["e": ["é", "è", "ê", "ë"], "a": ["à", "â", "æ"], "c": ["ç"],
                    "u": ["ù", "û", "ü"], "i": ["î", "ï"], "o": ["ô", "œ"], "y": ["ÿ"]]
        case "nl":
            // Android `Layout.nlOwn`, by the weight of the forms carrying them.
            return ["e": ["ë", "é", "è", "ê"], "i": ["ï", "í", "î"], "o": ["ö", "ó", "ô"],
                    "u": ["ü", "ú", "û"], "a": ["á", "à"], "c": ["ç"]]
        case "ro":
            // Android `Layout.roOwn`: ă before â on a (505 028 vs 85 826 forms).
            return ["a": ["ă", "â"], "i": ["î"], "s": ["ș"], "t": ["ț"]]
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
    /// popup (`mañana` in a letter used to mean switching keyboards).
    /// Polish first, then other Slavic, then the rest. These are
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

    /// Held on `.` of the LETTER layer, where 😀 took the comma's key:
    /// the comma comes first, right over the key, then `:` and `;` next to
    /// it (the comma must not sit at the far end of the strip), then `?` (not on the letters since 3.5.16)
    /// and the rest.
    /// Seven marks: `,` first and over the key.
    /// Eleven did not fit the strip on the Android phone - 418dp asked of a
    /// 339dp row - and the first cells fell off the screen; the rest stay on
    /// the `123` page. ⚠ Code only: needs an Xcode build on the build Mac.
    static let periodLetterAccents: [String] =
        [",", "?", "!", "-", "'", "\"", ":", ";"]

    /// Spanish opens questions and exclamations with `¿` `¡`: right after
    /// their closing pair, and `:` `;` make room - eight cells, as everywhere
    /// (Android `periodLetterHoldEs`).
    static let periodLetterAccentsEs: [String] =
        [",", "?", "¿", "!", "¡", "-", "'", "\""]

    static var periodLetterHold: [String] {
        language == "es" ? periodLetterAccentsEs : periodLetterAccents
    }

    /// Held on `,`: the rest of the sentence punctuation.
    static let commaAccents: [String] = [":", ";", "-", "–", "(", ")"]

    /// Held on keys of the numeric layer; the layer itself keeps only what is
    /// used often enough to deserve a key.
    /// `?` and `!` left the letters (Android 3.5.16) and sit
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
        // German (Android `deQwertzRows` / `deQwertyRows`): `ü ö ä` are keys of
        // their own where every German keyboard has them, rows of 11.
        if language == "de" {
            return qwertz
                ? [Array("qwertzuiopü"), Array("asdfghjklöä"), Array("yxcvbnm")]
                : [Array("qwertyuiopü"), Array("asdfghjklöä"), Array("zxcvbnm")]
        }
        // Serbian Cyrillic (JUS I.K1.003) and Macedonian: the Latin QWERTZ grid
        // with `љ` `њ` on `q` `w` and `џ` on the `x` position; no `y`
        // (Android `srCyrillicRows`, `mkCyrillicRows`).
        // Russian, phonetic: its letters take all 26 Latin keys, QWERTY grid.
        // Proto-Slavic too: `ъ` on `q`, `ь` on `f`, every other key its letter.
        if ["ru", "uk", "bg", "be", "sla"].contains(language) {
            return [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")]
        }
        // Spanish: `ñ` is a key, at the end of the middle row (Android `esRows`).
        if language == "es" {
            return [Array("qwertyuiop"), Array("asdfghjklñ"), Array("zxcvbnm")]
        }
        // French AZERTY, as on French phone keyboards (Android `frAzertyRows`);
        // the national switch gives plain QWERTY.
        if language == "fr" {
            return qwertz
                ? [Array("azertyuiop"), Array("qsdfghjklm"), Array("wxcvbn")]
                : [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")]
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
        // Interslavic Glagolitic, layout 261124 (owner): the Polish grid with x.
        if isvGlagShift { return [Array("qwertyuiop"), Array("asdfghjkl"), Array("zxcvbnm")] }
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
        // (variant 1): q = ch (Ⱈ), x = dzelo (Ⰷ, marker ẑ).
        if language == "pl" {
            switch ch {
            // Layout v04: q = ja Ⱑ, w = w Ⰲ, x = ch Ⱈ, and
            // v = the soft yer Ⱐ, which works as the Polish kreska: s + v = ś,
            // z ź, c ć, n ń, dz dź, r rz, d ⰴⱐ; after any other letter a bare Ⱐ.
            // Inside it is the marker ʹ (`Script.softenPL`).
            case "q": return "ja"
            case "Q": return "Ja"
            case "v", "V": return Script.softYerMark
            // x = ch Ⱈ like Keyman's Glagolitic (Phonetic) and Cyrillic х;
            // dzelo stays on hold d.
            // 261124: x = dzelo ⰷ like Interslavic Glagolitic (was ch).
            case "x": return "ẑ"
            case "X": return "Ẑ"
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
        // Interslavic Glagolitic, layout 261124 (owner): as close to Polish
        // Glagolitic as it goes - q = ja, w = v, v = the soft yer like Polish,
        // x = dzelo, lj nj under holds l n, ž under z. Cyrillic keeps q = lj, w = nj.
        if isvGlagShift {
            switch ch {
            case "q": return "ja"
            case "Q": return "Ja"
            case "w": return "v"
            case "W": return "V"
            // v = the soft yer like Polish (l + v = lj, n + v = nj, else a
            // bare Ⱐ; the keyboard turns it into j after l and n).
            case "v", "V": return Script.softYerMark
            case "x": return "ẑ"
            case "X": return "Ẑ"
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

    /// Interslavic in Glagolitic: layout 261124 (q ja, w v, v soft yer, x dzelo; lj nj under l n, ž under z).
    static var isvGlagShift: Bool { language == "isv" && Script.mode == .glagolitic }

    /// The wordlist key index a BUTTON stands for. Normally its own letter;
    /// in `isvGlagShift` the letter v lies on button w, the ja route (index
    /// of x) on button q, and buttons x (dzelo) and v (yer) are crossed as
    /// nothing. The lj and nj routes get the
    /// centres of l and n (see `swipeKeyCentres`).
    static func swipeIndex(button ch: Character) -> Int? {
        guard isvGlagShift else { return swipeIndex(ch) }
        switch ch {
        case "q": return dzhKey
        case "w": return swipeIndex("v")
        case "x", "v": return nil
        default: return swipeIndex(ch)
        }
    }

    /// Key indices of the two digraph keys in `swipeKeys`.
    static let ljKey = 16   // q
    static let njKey = 22   // w
    static let dzhKey = 23  // x - Serbian Cyrillic `џ`

    /// QWERTY or QWERTZ, chosen per language in the keyboard's settings (Android
    /// `KeyRows`). The default is the layout of the language's
    /// own keyboards: QWERTZ for Czech, Slovak, Slovenian, Croatian and Serbian. Only `y` and `z` swap, so the
    /// grid stays the same; the decoder reads key centres from the buttons and
    /// the wordlist stores letters, not positions.
    static let qwertzByDefault: Set<String> = ["cs", "sk", "sl", "hr", "sr", "bs", "de", "fr"]
    /// The NATIONAL layout the switch turns on: QWERTZ, or AZERTY for French.
    /// The stored flag stays `qwertz_` - it means "national" (Android `KeyRows.nationalName`).
    static var nationalName: String { language == "fr" ? "AZERTY" : "QWERTZ" }
    /// Not for Norwegian (rows of 11) nor Macedonian, which is Cyrillic only
    /// and has its own grid (Android `KeyRows.offered`).
    static var qwertzOffered: Bool { !["nb", "mk", "ru", "uk", "bg", "be", "sla", "es"].contains(language) }

    /// Languages whose keys each write ONE letter, so the Interslavic digraph
    /// routes (`lj` `nj` over `q` `w`) must stay off.
    /// Languages that write an apostrophe INSIDE words (`п'ять`, `сям'я`);
    /// Android `Lang.APOSTROPHE_IN_WORD`.
    static var apostropheInWord: Bool { ["uk", "be", "nl"].contains(language) }

    /// Elision (Android `Lang.ELISION`): after `dell'` / `l'` the next word
    /// follows without a space.
    static var elision: Bool { ["it", "fr"].contains(language) }

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
    static var rowUnits: Int { ["nb", "de"].contains(language) ? 11 : 10 }

    /// The keys a swipe can cross, in the order of the wordlist's key indices:
    /// a-z, then the language's own letter keys (`build_swipe_dictionary.py`).
    static var swipeKeys: [Character] {
        language == "nb" ? nbKeys : language == "de" ? deKeys : language == "es" ? esKeys : latinKeys
    }
    private static let latinKeys = Array("abcdefghijklmnopqrstuvwxyz")
    private static let nbKeys = latinKeys + Array("åøæ")
    /// German: `ü ö ä` are key indices 26-28 (`build_swipe_dictionary.py de`).
    private static let deKeys = latinKeys + Array("üöä")
    /// Spanish: `ñ` is key index 26 (`build_swipe_dictionary.py es`).
    private static let esKeys = latinKeys + Array("ñ")

    /// Index of a key letter in `swipeKeys`, nil for anything else.
    static func swipeIndex(_ ch: Character) -> Int? {
        (language == "nb" ? nbIndex : language == "de" ? deIndex : language == "es" ? esIndex : latinIndex)[ch]
    }
    private static let latinIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: latinKeys.enumerated().map { ($1, $0) })
    private static let nbIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: nbKeys.enumerated().map { ($1, $0) })
    private static let deIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: deKeys.enumerated().map { ($1, $0) })
    private static let esIndex: [Character: Int] =
        Dictionary(uniqueKeysWithValues: esKeys.enumerated().map { ($1, $0) })

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
        // Polish Glagolitic, layout v02: every letter Polish writes with more than one key sits under the key
        // it starts with; the jotted family under q, j (ja…) and i (ia…).
        if !latin && language == "pl" && key.isLetter {
            let glagHolds: [Character: [String]] = [
                "q": ["ja", "ju", "ję", "ją"], "j": ["ja", "ju", "ję", "ją"],
                "i": ["ia", "iu", "ię", "ią"], "w": ["ę", "ię", "ą", "ią"],
                // v: the hard yer Ⱏ (l + Ⱏ = ł, otherwise a bare Ⱏ)
                "v": [Script.hardYerMark],
                // ę ię ą ią also under the keys they contain (0.6.30)
                "a": ["ą", "ią", "ia"],
                "c": ["ć", "cz"],
                "d": ["ẑ", "dź", "ď", "dż", "ḍ"],
                "e": ["ę", "ię"], "h": ["ch"], "l": ["ł"], "n": ["ń"],
                "o": ["ó", "ō"], "r": ["rz"], "s": ["ś", "sz", "szcz"], "z": ["ż", "ź"],
            ]
            let g = (glagHolds[lower] ?? []).map { cased($0, uppercase) } + titloItems(key, uppercase)
            return g.isEmpty ? nil : g
        }
        if !latin && Titlo.offered && key.isLetter {
            // Interslavic Glagolitic 261124: nj moved under n, dzelo first
            // under d like in Polish.
            let isvGlagHolds: [Character: [String]] = ["n": ["nj"], "l": ["lj"], "d": ["ẑ"], "q": ["ju", "jo", "ȧ"],
                                                     "v": [Script.hardYerMark]]
            let extra = isvGlagShift ? (isvGlagHolds[lower] ?? []) : []
            // ḍ: a d that stays apart from a following z (pod-zemny as ⰴⰸ)
            let tail = isvGlagShift && lower == "d" ? ["ḍ"] : []
            let g = (extra + own + tail).map { cased($0, uppercase) } + titloItems(key, uppercase)
            return g.isEmpty ? nil : g
        }
        if latin {
            let isvOnPl: [Character: String] = ["c": "č", "s": "š", "z": "ž", "e": "ě"]
            let foreign = (foreignAccents[lower] ?? [])
                + (language == "pl" ? isvOnPl[lower].map { [$0] } ?? [] : [])
            for f in foreign where !all.contains(f) { all.append(f) }
        }
        if !all.isEmpty { return all.map { cased($0, uppercase) } }
        if key == "." { return periodLetterHold }
        if key == "," { return commaAccents }
        return nil
    }

    /// The three titlo items of a letter key in Glagolitic (`Titlo`): the
    /// key's own letter with each mark. None on a key that writes no
    /// Glagolitic letter (isv q w x) or writes a yer marker (Polish v).
    private static func titloItems(_ key: Character, _ uppercase: Bool) -> [String] {
        guard Titlo.offered else { return [] }
        let base = keyText(uppercase ? Character(key.uppercased()) : Character(key.lowercased()), latin: false)
        guard base != Script.softYerMark, base != Script.hardYerMark,
              Script.out(base) != base else { return [] }
        return Titlo.marks.map { base + $0 }
    }

    /// `ß` has no one-letter capital; a digraph capitalises its first letter
    /// only (`Dž`, `Lj`).
    private static func cased(_ s: String, _ uppercase: Bool) -> String {
        guard uppercase, s != "ß" else { return s }
        return s.prefix(1).uppercased() + s.dropFirst()
    }
}
