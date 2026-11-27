import SwiftUI

/// The keyboard's languages in the order of the app's lists, with their names
/// (moved here from the settings screen, which shows the same list).
enum AppLangs {
    static let all = [("isv", "Medžuslovjansky"), ("pl", "Polski"), ("cs", "češsky"),
                      ("sk", "slovačsky"), ("sl", "slovenečsky"),
                      ("hr", "hrvatsky"), ("sr", "srbsky"), ("bs", "bosnijsky"),
                      ("mk", "makedonsky"), ("ru", "russky"), ("uk", "ukrajinsky"), ("bg", "bulgarsky"),
                      ("be", "bělorussky"), ("sla", "praslovjansky"),
                      // names as the keyboard's own list (`Keyboard/Layout.swift`)
                      ("de", "němečsky"), ("it", "italijanjsky"),
                      ("es", "španjsky"), ("fr", "francuzsky"), ("nl", "holandsky"), ("ro", "rumunjsky"),
                      ("nb", "norvežsky"), ("en", "English")]
    static var codes: [String] { all.map(\.0) }
    static func name(_ code: String) -> String { all.first { $0.0 == code }?.1 ?? code.uppercased() }

    /// The language the keyboard types now (the keyboard writes it to the group).
    static var current: String { Shared.store.string(forKey: "language") ?? "isv" }
}

/// The 🎓 tab - the iOS twin of Android `LearnTab.kt` (3.10.38): one language
/// at a time (🌐 ▾), topics and ready sets as tiles, alphabets apart, then the
/// card with paced passes; the word lists stay folded until asked for.
struct LearnView: View {
    var active = true
    @State private var cards: [Learn.Card] = []
    /// Every card of every language, read in `reload()`.
    @State private var allCards: [Learn.Card] = []
    @State private var mine: [String] = []
    /// The language being learned; nil = the keyboard's current language.
    @State private var learnLang: String? = UserDefaults.standard.string(forKey: "learn_lang")
    /// The running pass: cards not shown yet (nil = no pass running).
    @State private var queue: [Learn.Card]?
    @State private var revealed = false
    @State private var justDone = false
    /*
     * Pacing inside one pass - Android `LearnTab.kt` (3.10.38). The cards not
     * shown yet wait in `queue`; the ones already met wait in `line`, each put
     * back a few places ahead after its grade, so a card not known comes back
     * soon and a known one later. A new card comes in while few are still being
     * learned, and now and then anyway (less often the more are in play), unless
     * the last answers were mostly "don't know". "Know" ends a card's pass.
     * Nothing blocks: the numbers only set the order.
     */
    @State private var line: [Learn.Card] = []
    @State private var current: Learn.Card?
    @State private var firstGrade: [String: Int] = [:]
    /// How often each card was not known in this pass: it comes back a little later each time.
    @State private var misses: [String: Int] = [:]
    @State private var recent: [Int] = []
    @State private var sinceNew = 0
    /// The running pass is "study" (every card, outside the schedule), not a review.
    @State private var study = false
    /// Cards in the running pass when it began, for the "3 / 12" over the card.
    @State private var passTotal = 0
    /// The script word cards are shown in (nil = the language's own), to practise reading.
    @State private var readIn: Script.Mode?
    /// nil = all, "" = cards without a category.
    @State private var filter: String?
    @State private var showWords = false
    @State private var showMine = false
    /// The two alphabet wheels: whose letters (a language) and in which script.
    @State private var alphaFrom: String?
    @State private var showLetters = false
    /// The tab shows words (false) or alphabets (true); kept between launches.
    @State private var alphaMode = UserDefaults.standard.bool(forKey: "learn_alpha_mode")
    @State private var alphaTo: String?
    @State private var newWord = ""
    @State private var editing: Learn.Card?
    @State private var toast: String?
    @Environment(\.scenePhase) private var phase
    /// Wiktionary glosses already looked up ("" = none), `lang\tword` -> gloss.
    @State private var glosses: [String: String] = [:]
    @State private var looking = false
    private static let paceLearning = 3, paceEvery = 8, paceSlower = 4
    private static let paceBack0 = 2, paceBack1 = 5, paceRest = 4
    /// Morse taught by families: dots, dashes, a dash added, their mirrors, the rest.
    private static let morseOrder = Array("eishtmoauvndbwgkrjpzqcylfx")

    private var lang: String { learnLang ?? AppLangs.current }
    private var langs: [String] { Gloss.enabled }

    /// The card's own translation, else the gloss looked up, else nil.
    private func trOf(_ lang: String, _ word: String, own: String = "") -> String? {
        if !own.isEmpty { return own }
        let g = glosses[Learn.key(lang, word)] ?? ""
        return g.isEmpty ? nil : g
    }

    /// Look up every gloss not looked up yet, once, off the main thread.
    private func lookUp() {
        let want = cards.filter { $0.tr.isEmpty }.map { ($0.lang, $0.word) } + mine.map { (lang, $0) }
        let missing = want.filter { glosses[Learn.key($0.0, $0.1)] == nil }
        guard !missing.isEmpty, !looking else { return }
        looking = true
        DispatchQueue.global(qos: .userInitiated).async {
            let found = missing.map { (Learn.key($0.0, $0.1), Gloss.lookup($0.0, $0.1) ?? "") }
            DispatchQueue.main.async {
                for (k, v) in found { glosses[k] = v }
                looking = false
            }
        }
    }

    private func reload() {
        // Alphabets do not depend on the language being learned: the alphabet
        // cards of every language are shown too, each kept under its own language.
        // Read once here, not on every redraw (owner 261121: the tab took long to come up).
        allCards = Learn.cards(order: AppLangs.codes)
        cards = allCards.filter { $0.lang == lang || Self.isAlphabet($0) }
        mine = Shared.userWords(lang).sorted()
        // A pair on the wheels stays chosen while it has no cards yet (＋ is right there).
        if let f = filter, !f.isEmpty, f != Self.allAlpha, !f.contains(" → "), !topics.contains(f) { filter = nil }
        lookUp()
    }

    /// Words and alphabets never mix: "all" is every word, the alphabet
    /// section has its own "all" (owner 261118: learning alphabets, the words
    /// barged in).
    private var shown: [Learn.Card] {
        guard let f = filter else { return cards.filter { !Self.isAlphabet($0) } }
        if f == Self.allAlpha { return cards.filter(Self.isAlphabet) }
        return cards.filter { topic($0) == f }
    }

    /// "Words": only word cards, never letters (owner 261118); with an
    /// alphabet chosen above, every word.
    private var wordList: [Learn.Card] {
        (isAlphaFilter(filter) ? cards : shown).filter { !Self.isAlphabet($0) }
    }

    /// A card answered "know" today does not come back today, not even in
    /// "study" (owner 261118: "h" known several times, it was to be once a day).
    private static let knownKey = "learn_known_day"
    private static var today: Int { Int(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 / 86_400) }
    private func knownToday(_ c: Learn.Card) -> Bool {
        (UserDefaults.standard.dictionary(forKey: Self.knownKey)?[c.id] as? Int) == Self.today
    }
    private func markKnown(_ c: Learn.Card) {
        var d = (UserDefaults.standard.dictionary(forKey: Self.knownKey) as? [String: Int] ?? [:])
            .filter { $0.value == Self.today }
        d[c.id] = Self.today
        UserDefaults.standard.set(d, forKey: Self.knownKey)
    }

    /// Due now and not known today, one per sign (see `twinKey`).
    private var dueNow: [Learn.Card] { Self.oneEach(Learn.due(shown).filter { !knownToday($0) }) }

    /// One sign with one answer is one card to the learner, whatever language
    /// it was taken under (Morse `··` = i in isv, pl, nb... - owner 261121:
    /// "i" and "h" known several times today, shown again). Android 3.11.13.
    private static func twinKey(_ c: Learn.Card) -> String {
        isAlphabet(c) ? "a\t\(c.word)\t\(c.tr)" : "w\t\(c.lang)\t\(c.word)"
    }
    private static func oneEach(_ l: [Learn.Card]) -> [Learn.Card] {
        var seen = Set<String>()
        return l.filter { seen.insert(twinKey($0)).inserted }
    }
    /// The same sign with the same answer under the other languages.
    private func twins(_ c: Learn.Card) -> [Learn.Card] {
        guard Self.isAlphabet(c) else { return [] }
        return allCards.filter {
            $0.id != c.id && Self.isAlphabet($0) && $0.word == c.word && $0.tr == c.tr
        }
    }

    private func isAlphaFilter(_ f: String?) -> Bool {
        guard let f else { return false }
        return f == Self.allAlpha || f.contains(" → ")
    }

    /// "My letters": the alphabet cards of the pair chosen, or all of them.
    private var letterList: [Learn.Card] {
        (isAlphaFilter(filter) ? shown : cards).filter(Self.isAlphabet)
            .sorted { (topic($0), $0.tr) < (topic($1), $1.tr) }
    }

    private func setMode(_ on: Bool) {
        alphaMode = on
        UserDefaults.standard.set(on, forKey: "learn_alpha_mode")
        if on { followWheels() } else { pick(nil) }
        readIn = nil
    }

    /// The filter value for every alphabet card at once.
    private static let allAlpha = "\u{1}alphabets"

    private static func isAlphabet(_ c: Learn.Card) -> Bool { c.cat.contains(" → ") }

    /// The topic a card is shown under: its category. Alphabets do not belong
    /// to a language (owner 261127, Android 3.11.33): one deck per pair of
    /// scripts, whatever language a card was once taken under.
    private func topic(_ c: Learn.Card) -> String { c.cat }

    /// The letters of the alphabets come from Interslavic, the one language
    /// written in Latin, Cyrillic and Glagolitic alike.
    private static let alphaSrc = "isv"

    /// The name of the deck being studied, as its tile says it.
    private var deckName: String {
        switch filter {
        case nil, Self.allAlpha: return Ui.t("learn_all")
        case "": return Ui.t("learn_nocat")
        default: return filter!
        }
    }

    /// "3 / 11": the cards due now in the count colour, then how many there are.
    private func counts(_ all: [Learn.Card]) -> Text {
        let c = Self.oneEach(all)
        return Text("\(Learn.due(c).count)").bold().foregroundColor(Look.count) + Text(" / \(c.count)")
    }

    /// The count says which deck it is about (it jumped 243 -> 23 between the
    /// modes with nothing saying why).
    private var dueLine: Text {
        let parts = Ui.t("learn_due").components(separatedBy: "{k}")
        var t = Text(deckName).bold() + Text(" · " + parts[0])
            + Text("\(dueNow.count)").bold().foregroundColor(Look.count)
        if parts.count > 1 { t = t + Text(parts[1]) }
        return t
    }

    /// A language's name for a wheel, in the screen's language where the
    /// system knows it (Interslavic and Proto-Slavic keep the app's own names).
    private func short(_ l: String) -> String {
        let ui = Ui.current
        var n: String?
        if ui != "isv", l != "isv", l != "sla" {
            n = Locale(identifier: ui == "sr" ? "sr-Latn" : ui).localizedString(forLanguageCode: l)
        }
        let s = n ?? AppLangs.name(l)
        return s.prefix(1).uppercased() + s.dropFirst()
    }

    private func stopPass() {
        queue = nil; current = nil; line = []; revealed = false
    }

    /// Every topic in use, the language's own first, alphabetically.
    private var topics: [String] {
        var seen = Set<String>()
        return cards.sorted { ($0.lang != lang ? 1 : 0, topic($0).lowercased()) < ($1.lang != lang ? 1 : 0, topic($1).lowercased()) }
            .map(topic).filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    private func say(_ s: String) {
        toast = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { if toast == s { toast = nil } }
    }

    private func small(_ s: String) -> some View {
        Text(s).font(.footnote).foregroundStyle(.secondary)
    }

    // MARK: language, topics, alphabets

    private func switchTo(_ l: String) {
        guard l != lang else { return }
        let running = queue != nil ? study : nil
        learnLang = l
        UserDefaults.standard.set(l, forKey: "learn_lang")
        // An alphabet filter stays: alphabets are the same in every language.
        if !isAlphaFilter(filter) { filter = nil }
        queue = nil; current = nil; justDone = false; readIn = nil
        reload()
        // Switched in the middle of a pass: it goes on in the new language.
        if let st = running {
            if st { start(Self.oneEach(shown.filter { !knownToday($0) }).shuffled(), study: true) }
            else { start(dueNow, study: false) }
        }
    }

    /// Two wheels, as for the alphabets: the language learned -> the language
    /// its words are translated into (owner 261127, Android 3.11.33: nothing
    /// said which language the translations came in). The keyboard's
    /// languages, Interslavic first; only Polish has more than one language to
    /// translate into, and the choice is the writing box's one.
    @ViewBuilder private var wordWheels: some View {
        let on = langs.sorted { ($0 != "isv" ? 1 : 0, $0) < ($1 != "isv" ? 1 : 0, $1) }
        let ls = on.contains(lang) ? on : on + [lang]
        let ts = lang == "pl" ? Gloss.targets("pl", enabled: langs) : ["pl"]
        let trs = ts.isEmpty ? [Sets.target(lang)] : ts
        let trNow = trs.first { $0 == Gloss.target(lang) } ?? trs[0]
        HStack(spacing: 0) {
            wheel(lang, ls, { l in
                let n = allCards.filter { $0.lang == l && !Self.isAlphabet($0) }.count
                return short(l) + (n > 0 ? " (\(n))" : "")
            }) { switchTo($0) }
            Text("→").foregroundStyle(.secondary)
            wheel(trNow, trs, short) { to in
                guard lang == "pl", to != trNow else { return }
                UserDefaults.standard.set(to, forKey: "gloss_target_pl")
                glosses = glosses.filter { !$0.key.hasPrefix("pl\t") }
                reload()
            }
        }
    }

    /// A topic as a tile: its name, under it the cards due and in all; a ready
    /// set not taken yet has ＋ and no count.
    private struct Tile: Identifiable {
        let id: String, name: String, cards: [Learn.Card]?, on: Bool, tap: () -> Void
    }

    private func pick(_ f: String?) {
        filter = f; queue = nil; current = nil; justDone = false
    }

    /// A ready set or an alphabet into the cards, under [from]'s language
    /// (an alphabet of another language stays that language's).
    private func take(_ name: String, _ pairs: [(String, String)], from: String? = nil) {
        let l = from ?? lang
        for (w, tr) in pairs {
            var c = Learn.card(l, w)
            c.deck = true
            if c.tr.isEmpty { c.tr = tr }
            if c.cat.isEmpty { c.cat = name }
            Learn.update(c)
        }
        say("\(Ui.t("learn_added")): \(name)")
        reload()
        pick(name)
    }

    /// Tiles: all words, every word topic in use, no topic, then the ready
    /// sets not taken.
    private var tiles: [Tile] {
        let cats = self.topics.filter { !$0.contains(" → ") }
        let words = cards.filter { !Self.isAlphabet($0) }
        var out: [Tile] = []
        if !words.isEmpty {
            out.append(Tile(id: "\u{1}all", name: Ui.t("learn_all"), cards: words, on: filter == nil) { pick(nil) })
        }
        for c in cats {
            out.append(Tile(id: c, name: c, cards: cards.filter { topic($0) == c }, on: filter == c) { pick(c) })
        }
        if !cats.isEmpty, words.contains(where: { $0.cat.isEmpty }) {
            out.append(Tile(id: "\u{1}nocat", name: Ui.t("learn_nocat"), cards: words.filter { $0.cat.isEmpty },
                            on: filter == "") { pick("") })
        }
        let l = lang, target = Sets.target(l)
        for set in Sets.names(l) {
            let name = Sets.title(set)
            if cats.contains(name) { continue }
            out.append(Tile(id: "\u{1}set " + set, name: "＋ \(name)", cards: nil, on: false) {
                take(name, Sets.words(set, l, target))
            })
        }
        return out
    }

    // MARK: the alphabet wheels

    private func taken(_ name: String) -> Int { cards.filter { topic($0) == name }.count }

    private func wheel<T: Hashable>(_ sel: T, _ items: [T], _ label: @escaping (T) -> String,
                                    _ set: @escaping (T) -> Void) -> some View {
        Picker("", selection: Binding(get: { sel }, set: set)) {
            ForEach(items, id: \.self) { Text(label($0)).lineLimit(1).minimumScaleFactor(0.6).tag($0) }
        }
        .pickerStyle(.wheel).frame(maxWidth: .infinity)
        // Tall enough to show the neighbours above and below: at 110 pt only
        // two names were in sight and Morse at the end looked missing (owner 261121).
        .frame(height: 180).clipped()
    }

    /// The pair the wheels show now. Both wheels hold every script (owner
    /// 261121, Android): a right wheel without the left one's script shifted
    /// under the finger when the left one turned, and runes -> Latin landed on Morse.
    private var wheelPair: (all: [Scripts.Choice], from: Scripts.Choice, to: Scripts.Choice) {
        let all = Alphabets.scripts(Self.alphaSrc)
        let from = all.first { $0.name == alphaFrom } ?? all[0]
        let to = all.first { $0.name == alphaTo } ?? all.first { $0 != from } ?? all[0]
        return (all, from, to)
    }

    /// The wheels stay on the screen; the pair on them is the pair studied.
    /// Under them ＋ for a pair not taken yet and "all" for every alphabet
    /// (no tiles, owner 261127), then the cheat sheet.
    @ViewBuilder private var alphabetWheels: some View {
        let w = wheelPair
        let name = Alphabets.title(w.from, w.to)
        let pairs = Alphabets.pair(Self.alphaSrc, w.from, w.to)
        let alpha = cards.filter(Self.isAlphabet)
        // With every alphabet chosen, the wheels are not what is studied.
        let everyOn = filter == Self.allAlpha && !alpha.isEmpty
        HStack(spacing: 0) {
            wheel(w.from, w.all, { $0.name }) { f in
                if alphaTo == nil { alphaTo = w.to.name }
                alphaFrom = f.name
                followWheels()
            }
            Text("→").foregroundStyle(.secondary)
            wheel(w.to, w.all, { t in t.name + (taken(Alphabets.title(w.from, t)) > 0 ? "  ✓" : "") }) { t in
                alphaFrom = w.from.name; alphaTo = t.name
                followWheels()
            }
        }
        .opacity(everyOn ? 0.45 : 1)
        HStack {
            if w.from == w.to {
                small("\(w.from.name) = \(w.to.name)").frame(maxWidth: .infinity, alignment: .leading)
            } else if taken(name) == 0, !pairs.isEmpty {
                Button("＋ \(name) (\(pairs.count))") { take(name, pairs, from: Self.alphaSrc) }
                    .buttonStyle(LookStyle(kind: .accent))
            }
            if !alpha.isEmpty {
                Button { pick(everyOn ? name : Self.allAlpha) } label: {
                    Text(Ui.t("learn_all") + " ") + counts(alpha)
                }
                .buttonStyle(LookStyle(kind: everyOn ? .chosen : .plain))
            }
        }
        sheet(w.from, w.to)
    }

    /// The cheat sheet's signs: the script translated INTO (the right wheel)
    /// with the Latin letter under each sign, whatever the left wheel says;
    /// with ABC on the right, the left wheel's script. Glagolitic: then the
    /// Polish letters Interslavic has not (ą ę ł ń ó ś ź ż), in the Polish
    /// spelling the keyboard writes (owner 261127).
    private func sheetPairs(_ from: Scripts.Choice, _ to: Scripts.Choice) -> (Scripts.Choice, [(String, String)])? {
        let latin = Scripts.Choice.script(.latin)
        let target = to == latin ? from : to
        guard target != latin else { return nil }
        var pairs = Alphabets.pair(Self.alphaSrc, latin, target)
        if target == .script(.glagolitic) {
            let known = Set(pairs.flatMap { $0.1.components(separatedBy: " / ") })
            pairs += Alphabets.pair("pl", latin, target).filter { p in
                !p.1.components(separatedBy: " / ").contains(where: known.contains)
            }
        }
        return (target, pairs)
    }

    /// Five columns for every script but Morse, so the sheets look alike;
    /// Morse codes are wider, four fit. Runes big: two of them can differ
    /// only by the slant of one twig (ᛅ a / ᚾ n).
    @ViewBuilder private func sheet(_ from: Scripts.Choice, _ to: Scripts.Choice) -> some View {
        if let (target, pairs) = sheetPairs(from, to) {
            let morse = target == .morse
            let runes: Bool = { if case .runes = target { return true }; return false }()
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: morse ? 4 : 5), spacing: 8) {
                ForEach(Array(pairs.enumerated()), id: \.offset) { _, p in
                    VStack(spacing: 2) {
                        Text(p.0).font(.system(size: morse ? 20 : runes ? 36 : 30, weight: morse ? .bold : .regular,
                                               design: morse ? .monospaced : .default))
                            .lineLimit(1).minimumScaleFactor(0.6)
                        Text(p.1).font(.system(size: 15)).foregroundStyle(Look.muted)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .accessibilityIdentifier("learn_sheet")
        }
    }

    /// The pair on the wheels is the pair studied, taken or not: "all
    /// alphabets" only when tapped. Before, a pair not taken (or the
    /// alphabets section just opened) fell back to all of them, so with
    /// ABC → ⰀⰁⰂ on the wheels a Morse card due today came up in the pass
    /// (owner 261122: "nagle wpierdolił się morse").
    private func followWheels() {
        let w = wheelPair
        pick(Alphabets.title(w.from, w.to))
    }

    private func grid(_ tiles: [Tile]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(tiles) { t in
                Button(action: t.tap) {
                    VStack(spacing: 2) {
                        Text(t.name).font(.subheadline.weight(t.on ? .bold : .regular))
                            .lineLimit(2).multilineTextAlignment(.center)
                        if let c = t.cards { counts(c).font(.subheadline) }
                    }
                    .frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(LookStyle(kind: t.on ? .chosen : t.cards == nil ? .add : .plain))
            }
        }
    }

    /// What to learn: words of a language, or alphabets. First on the screen:
    /// it says what the count under it is about.
    private var modeRow: some View {
        HStack(spacing: 8) {
            ForEach([false, true], id: \.self) { on in
                Button { if alphaMode != on { setMode(on) } } label: {
                    Text(on ? "ABC  А  Ⰰ  ᚠ  ·−" : Ui.t("learn_words"))
                        .lineLimit(1).minimumScaleFactor(0.7)
                }
                .buttonStyle(LookStyle(kind: alphaMode == on ? .chosen : .plain))
                .accessibilityIdentifier(on ? "learn_mode_alpha" : "learn_mode_words")
            }
        }
    }

    var body: some View {
        let t = tiles
        let words = cards.filter { !Self.isAlphabet($0) }
        return NavigationStack {
            List {
                // A pass has the screen to itself: what to learn waits under
                // it only while no pass runs (Android 3.11.33: it took 53 %
                // of the screen under the card).
                if queue != nil {
                    Section { cardBox }
                } else {
                Section {
                    modeRow
                    dueLine
                    cardBox
                }
                .listRowSeparator(.hidden)

                if alphaMode {
                    Section {
                        if !cards.contains(where: Self.isAlphabet) { small(Ui.body("learn_alpha_body")) }
                        // Only while 🎓 is shown: a wheel is a UIKit view that keeps
                        // taking touches under `opacity(0)` / `allowsHitTesting(false)`,
                        // so from the hidden tab it swallowed every tap in ✎ (owner 261118).
                        if active { alphabetWheels } else { Color.clear.frame(height: 180) }
                    }
                    .listRowSeparator(.hidden)
                    Section {
                        DisclosureGroup("\(Ui.t("learn_my_letters")) (\(letterList.count))", isExpanded: $showLetters) {
                            if letterList.isEmpty { small(Ui.t("learn_empty")) }
                            ForEach(letterList) { c in
                                row(c.word, c.tr, known: true, cat: filter == topic(c) ? "" : topic(c),
                                    morse: c.cat.hasSuffix(Script.morseLabel))
                                    .swipeActions {
                                        Button(Ui.t("learn_unlearn"), role: .destructive) { unlearn(c) }
                                    }
                            }
                        }
                    }
                } else {
                Section {
                    // The how-to only while there is nothing yet.
                    if words.isEmpty { small(Ui.body("learn_body")) }
                    if active { wordWheels } else { Color.clear.frame(height: 180) }
                }
                .listRowSeparator(.hidden)

                if !t.isEmpty {
                    Section(Ui.head("learn_topics")) { grid(t) }
                }

                Section {
                    DisclosureGroup("\(Ui.t("words_head")) (\(wordList.count))", isExpanded: $showWords) {
                        HStack {
                            TextField(Ui.t("learn_add_hint"), text: $newWord)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .onSubmit(addTyped)
                            Button(Ui.t("learn_add"), action: addTyped)
                                .disabled(newWord.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        if cards.isEmpty { small(Ui.t("learn_empty")) }
                        ForEach(wordList) { c in
                            let tr = trOf(c.lang, c.word, own: c.tr)
                            let looked = glosses[c.id] != nil
                            // The topic apart and small, not glued to the word: a Morse
                            // word "·" next to " · ABC → ·−" read as one string (owner 261118).
                            row(c.word, tr ?? (looked ? Ui.t("learn_notr") : "…"), known: tr != nil,
                                cat: filter == topic(c) ? "" : topic(c), morse: c.cat.hasSuffix(Script.morseLabel))
                                .contentShape(Rectangle())
                                .onTapGesture { editing = c }
                                .swipeActions {
                                    Button(Ui.t("learn_unlearn"), role: .destructive) { unlearn(c) }
                                    Button("✎") { editing = c }
                                }
                        }
                    }
                }

                Section {
                    DisclosureGroup("\(Ui.t("learn_kbd_words")) (\(mine.count))", isExpanded: $showMine) {
                        small(Ui.body("learn_my_body"))
                        if mine.isEmpty { small(Ui.t("words_none")) }
                        let inDeck = Set(cards.map(\.id))
                        ForEach(mine, id: \.self) { w in
                            HStack {
                                let tr = trOf(lang, w)
                                row(w, tr ?? (glosses[Learn.key(lang, w)] != nil ? "—" : "…"), known: tr != nil)
                                Spacer()
                                if !inDeck.contains(Learn.key(lang, w)) {
                                    Button(Ui.t("learn_add")) {
                                        Learn.add(lang, w)
                                        say("\(Ui.t("learn_added")): \(w)")
                                        reload()
                                    }.buttonStyle(.borderless)
                                }
                            }
                            .swipeActions {
                                Button(Ui.t("words_remove"), role: .destructive) {
                                    Shared.setUserWords(Shared.userWords(lang).filter { $0 != w }, lang)
                                    say("\(Ui.t("words_removed")): \(w)")
                                    reload()
                                }
                            }
                        }
                    }
                }
                }
                }
            }
            .brandTitle(Ui.t("learn_head"))
            .overlay(alignment: .bottom) {
                if let t = toast {
                    Text(t).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 12)
                }
            }
            .onAppear {
                reload()
                if alphaMode != isAlphaFilter(filter) { if alphaMode { followWheels() } else { pick(nil) } }
            }
            .onChange(of: active) { if $0 { reload() } }
            .onChange(of: phase) { if $0 == .active { reload() } }
            .sheet(item: $editing) { c in
                CardEditor(card: c, offered: c.tr.isEmpty ? trOf(c.lang, c.word) : nil,
                           categories: Learn.categories(cards.filter { $0.lang == lang })) { saved in
                    Learn.update(saved)
                    if var q = queue, let i = q.firstIndex(where: { $0.id == saved.id }) {
                        q[i] = saved; queue = q
                    }
                    if let i = line.firstIndex(where: { $0.id == saved.id }) { line[i] = saved }
                    if current?.id == saved.id { current = saved }
                    reload()
                }
            }
        }
    }

    private func unlearn(_ c: Learn.Card) {
        Learn.drop(c)
        queue?.removeAll { $0.id == c.id }
        line.removeAll { $0.id == c.id }
        if current?.id == c.id {
            revealed = false
            current = next()
            if current == nil { queue = nil }
        }
        say("\(Ui.t("learn_unlearn")): \(c.word)")
        reload()
    }

    /// Add a word to the chosen language - and to the chosen topic, which is
    /// how a ready set or one's own topic grows.
    private func addTyped() {
        let w = newWord.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !w.isEmpty else { return }
        Learn.add(lang, w)
        if let cat = filter, !cat.isEmpty, cat != Self.allAlpha, !cat.contains(" → ") {
            var c = Learn.card(lang, w)
            if c.cat.isEmpty { c.cat = cat; Learn.update(c) }
        }
        newWord = ""
        say("\(Ui.t("learn_added")): \(w)")
        reload()
    }

    // MARK: the card

    /// The review: word → "Show answer" → translation, note, example → three grades.
    @ViewBuilder private var cardBox: some View {
        if queue != nil, let card = current {
            let alphabet = card.cat.contains(" → ")
            let scripts = alphabet ? [] : Script.offered(card.lang)
            let mode = readIn.flatMap { scripts.contains($0) && $0 != scripts.first ? $0 : nil }
            let morse = card.cat.hasSuffix(Script.morseLabel)
            let left = (queue?.count ?? 0) + line.count + 1
            VStack(spacing: 10) {
                // Where the pass is, and the way out of it.
                HStack {
                    Text("\(max(1, passTotal - left + 1)) / \(passTotal) · \(deckName)")
                        .font(.subheadline).foregroundStyle(Look.muted).lineLimit(1)
                    Spacer()
                    Button("✕") { stopPass() }
                        .buttonStyle(LookStyle(kind: .plain, wide: false))
                        .accessibilityIdentifier("learn_stop")
                }
                // An alphabet has no language; its pair is named once (the line
                // above says it already, unless every alphabet is studied).
                if !alphabet {
                    small(AppLangs.name(card.lang) + (card.cat.isEmpty ? "" : " · \(card.cat)"))
                } else if card.cat != deckName {
                    small(card.cat)
                }
                // Reading practice: a word card can be shown in another script
                // of its language (Interslavic in Cyrillic or Glagolitic, Polish
                // in Glagolitic). Alphabet cards are already in their script.
                if scripts.count > 1 {
                    HStack {
                        ForEach(scripts, id: \.self) { m in
                            Button(Script.label(m)) { readIn = m == scripts.first ? nil : m }
                                .buttonStyle(.bordered)
                                .opacity((mode ?? scripts[0]) == m ? 1 : 0.45)
                        }
                    }
                }
                // A sign to learn is drawn big: a Morse "−−" at 32 pt was a few
                // pixels high. 56 is a step on a ladder (48/56/64).
                Text(mode.map { Scripts.convert(card.word, $0, card.lang) } ?? card.word)
                    .font(.system(size: alphabet ? 56 : 34, weight: .bold, design: morse ? .monospaced : .default))
                    .tracking(alphabet && morse ? 8 : 0)
                    .multilineTextAlignment(.center).minimumScaleFactor(0.5)
                    .padding(.vertical, alphabet ? 12 : 0)
                if !revealed {
                    Button(Ui.t("learn_show")) { revealed = true }.buttonStyle(LookStyle(kind: .accent))
                } else {
                    // The back: the translation first (what is checked), the word
                    // as normally written under it when it was read in another script.
                    if let tr = trOf(card.lang, card.word, own: card.tr) {
                        Text(tr).font(.title2).multilineTextAlignment(.center)
                        if card.tr.isEmpty {
                            Text("Wiktionary · CC BY-SA 4.0").font(.caption2).foregroundStyle(.secondary)
                        }
                    } else {
                        Button(Ui.t("learn_notr")) { editing = card }.foregroundStyle(.secondary)
                    }
                    if mode != nil { Text(card.word).font(.title3).foregroundStyle(.secondary) }
                    if !card.note.isEmpty { Text(card.note).frame(maxWidth: .infinity, alignment: .leading) }
                    if !card.ex.isEmpty { Text(card.ex).italic().frame(maxWidth: .infinity, alignment: .leading) }
                    // Side by side: don't know, so-so, know (as Android). One under
                    // the other, when the labels do not fit at a large text size:
                    // know first (owner 261127).
                    ViewThatFits(in: .horizontal) {
                        HStack { ForEach([0, 1, 2], id: \.self) { gradeButton(card, $0) } }
                        VStack { ForEach([2, 1, 0], id: \.self) { gradeButton(card, $0) } }
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .buttonStyle(.borderless)
        } else {
            if justDone { Text(Ui.t("learn_done")) }
            // Review takes the cards due now; study goes through every card of
            // the chosen topic in random order, so there is always something
            // to do after the review is over.
            // Both always answer a tap (owner 261127: "aktywne mają być"):
            // see `begin`.
            FitRow {
                Button { begin(study: false) } label: { Text(Ui.t("learn_start")).lineLimit(1) }
                    .buttonStyle(LookStyle(kind: .accent))
                    .accessibilityIdentifier("learn_start")
                Button { begin(study: true) } label: { Text(Ui.t("learn_study")).lineLimit(1) }
                    .buttonStyle(LookStyle(kind: .plain))
                    .accessibilityIdentifier("learn_study")
            }
            .buttonStyle(.borderless)
        }
    }

    /// Review takes the cards due now; study goes through every card of the
    /// chosen deck in random order. Neither button is ever greyed out:
    /// - an alphabet pair on the wheels not taken yet is taken by the tap
    ///   (the runes looked locked: nothing said ＋ had to come first);
    /// - study with every card known today goes through them all again;
    /// - review with nothing due says so.
    private func begin(study: Bool) {
        var pool = shown
        if pool.isEmpty, alphaMode, filter != Self.allAlpha {
            let w = wheelPair
            let name = Alphabets.title(w.from, w.to)
            let pairs = Alphabets.pair(Self.alphaSrc, w.from, w.to)
            if !pairs.isEmpty {
                take(name, pairs, from: Self.alphaSrc)
                pool = Learn.cards(order: AppLangs.codes).filter { $0.cat == name }
            }
        }
        if study {
            var l = Self.oneEach(pool.filter { !knownToday($0) })
            if l.isEmpty { l = Self.oneEach(pool) }
            guard !l.isEmpty else { say(Ui.t("learn_empty")); return }
            start(l.shuffled(), study: true)
        } else {
            let l = Self.oneEach(Learn.due(pool).filter { !knownToday($0) })
            guard !l.isEmpty else {
                say(pool.isEmpty ? Ui.t("learn_empty") : Ui.t("learn_done"))
                return
            }
            start(l, study: false)
        }
    }

    private func gradeButton(_ card: Learn.Card, _ g: Int) -> some View {
        Button { grade(card, g) } label: { Text(Ui.t("learn_g\(g)")).lineLimit(1) }
            .buttonStyle(LookStyle(kind: g == 0 ? .miss : g == 1 ? .plain : .accent))
            .accessibilityIdentifier("learn_g\(g)")
    }

    private func start(_ cards: [Learn.Card], study: Bool) {
        // Alphabet cards whose answer is a letter: Morse in families, not A to Z.
        func rank(_ c: Learn.Card) -> Int {
            guard c.cat.hasSuffix(Script.morseLabel) else { return 0 }
            if c.tr.count == 1, let ch = c.tr.lowercased().first,
               let i = Self.morseOrder.firstIndex(of: ch) { return i }
            return Self.morseOrder.count
        }
        // Learned cards due again first, then the ones missed before (never
        // learned yet), then the new ones in teaching order.
        func ordered(_ l: [Learn.Card]) -> [Learn.Card] {
            l.enumerated().sorted { (rank($0.1), $0.0) < (rank($1.1), $1.0) }.map(\.1)
        }
        let old = cards.filter { $0.reps > 0 }
        let rest = cards.filter { $0.reps == 0 }
        queue = old + ordered(rest.filter { !$0.isNew }) + ordered(rest.filter(\.isNew))
        passTotal = cards.count
        line = []; firstGrade = [:]; misses = [:]; recent = []; sinceNew = 0
        self.study = study
        revealed = false
        justDone = false
        current = next()
        if current == nil { queue = nil }
    }

    /// The next card of the pass, or nil when it is over.
    private func next() -> Learn.Card? {
        guard var pool = queue else { return nil }
        let struggling = recent.suffix(5).filter { $0 == 0 }.count >= 3
        let takeNew = !pool.isEmpty && (line.isEmpty || line.count <= Self.paceLearning ||
            (!struggling && sinceNew >= Self.paceEvery + Self.paceSlower * (line.count - Self.paceLearning)))
        if takeNew {
            sinceNew = 0
            let c = pool.removeFirst()
            queue = pool
            return c
        }
        sinceNew += 1
        return line.isEmpty ? nil : line.removeFirst()
    }

    private func grade(_ card: Learn.Card, _ g: Int) {
        let key = card.id
        let first = firstGrade[key]
        var after = card
        // Only the first answer of a pass sets the schedule; the repeats inside
        // the pass only bring a forgotten card back sooner. Studying ahead of the
        // schedule must not stretch the intervals, so there only "don't know" counts.
        if first == nil {
            firstGrade[key] = g
            // A card already due is not studied ahead, so its answer counts.
            if !study || g == 0 || card.due <= Learn.now() {
                after = Learn.grade(card, g)
                for t in twins(card) { Learn.grade(t, g) }
            }
        } else if g == 0 {
            after = Learn.grade(card, 0)
            for t in twins(card) { Learn.grade(t, 0) }
        }
        recent.append(g)
        if g == 2 {
            markKnown(card)
            for t in twins(card) { markKnown(t) }
            // "Know" ends the card's pass and the card does not come back
            // before tomorrow, in either mode (a card missed earlier in a
            // "study" pass stayed due in a minute and came back in review).
            // A card once forgotten starts again from one day, not a minute.
            let tomorrow = Int64((Calendar.current.startOfDay(for: Date()).timeIntervalSince1970 + 86_400) * 1000)
            if after.due < tomorrow {
                after = Learn.grade(after, 1)
                for t in twins(card) where t.due < tomorrow { Learn.grade(t, 1) }
            }
        } else {
            // Not to drill one card to boredom: every miss in this pass puts it
            // one place further back, and after paceRest misses it waits at the end.
            let m = (misses[key] ?? 0) + 1
            misses[key] = m
            let back = (g == 0 ? Self.paceBack0 : Self.paceBack1) + m - 1
            line.insert(after, at: m >= Self.paceRest ? line.count : min(back, line.count))
        }
        revealed = false
        current = next()
        if current == nil { queue = nil; justDone = true }
        reload()
    }

    private func row(_ word: String, _ tr: String, known: Bool, cat: String = "", morse: Bool = false) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(word).font(morse ? .body.monospaced().bold() : .body)
                Text(tr).font(.subheadline).foregroundStyle(known ? .primary : .secondary)
                    .opacity(known ? 0.8 : 1)
            }
            Spacer()
            if !cat.isEmpty { Text(cat).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
        }
    }
}

/// Colour only where there is something to press or a state to read (owner
/// 261127, Android 3.11.33): the icon's blue on the main action (white text
/// on it 5.1:1), its yellow on the cards due, a surface with an outline 3:1
/// to the page for the other buttons.
enum Look {
    private static func ui(_ rgb: UInt32) -> UIColor {
        UIColor(red: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255,
                blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }
    private static func dyn(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? ui(dark) : ui(light) })
    }
    static let accent = Color(ui(0x3A66D9))
    static let count = dyn(0x8A6D00, 0xF5C400)
    static let muted = dyn(0x5F6B73, 0x8A99A1)
    static let miss = dyn(0xB3261E, 0xFF8A80)
    static let edge = dyn(0x7D8C96, 0x5F6B73)
    static let surface = dyn(0xECEFF1, 0x262A2E)
    static let chosenFill = dyn(0xDCE5FB, 0x2A3550)
    static let chosenInk = dyn(0x2F57C4, 0xAFC4FF)
}

/// The tab's buttons: the main action, the chosen one, "don't know", a set
/// to add (＋, paler), the rest.
struct LookStyle: ButtonStyle {
    enum Kind { case plain, accent, chosen, miss, add }
    var kind: Kind = .plain
    /// Takes the row's whole width (the ✕ of a pass does not).
    var wide = true

    func makeBody(configuration: Configuration) -> some View {
        Face(kind: kind, wide: wide, pressed: configuration.isPressed, label: configuration.label)
    }

    private struct Face<L: View>: View {
        let kind: Kind, wide: Bool, pressed: Bool, label: L
        @Environment(\.isEnabled) private var enabled
        var body: some View {
            // A main action that cannot be pressed looks like the others.
            let k: Kind = kind == .accent && !enabled ? .plain : kind
            let fill: Color = k == .accent ? Look.accent : k == .chosen ? Look.chosenFill : Look.surface
            let edge: Color = k == .chosen ? Look.accent : k == .miss ? Look.miss : Look.edge
            let ink: Color = k == .accent ? .white : k == .chosen ? Look.chosenInk
                : k == .miss ? Look.miss : k == .add ? Look.muted : .primary
            let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
            label
                .foregroundStyle(ink)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .frame(maxWidth: wide ? .infinity : nil)
                .background(fill, in: shape)
                .overlay(shape.strokeBorder(edge, lineWidth: k == .accent ? 0 : k == .chosen ? 2 : 1))
                .contentShape(shape)
                .opacity(enabled ? (pressed ? 0.7 : 1) : 0.4)
        }
    }
}

/// Edit a card: translation, category (the ones in use as buttons), note, example.
private struct CardEditor: View {
    @State var card: Learn.Card
    /// The Wiktionary gloss put in the empty translation field; kept as the
    /// card's own only when the user changes it (Android `learnEdit`).
    let offered: String?
    let categories: [String]
    let onSave: (Learn.Card) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                TextField(Ui.t("learn_tr"), text: $card.tr, axis: .vertical)
                Section {
                    TextField(Ui.t("learn_cat"), text: $card.cat)
                    if !categories.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(categories, id: \.self) { c in
                                    Button(c) { card.cat = c }.buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                }
                TextField(Ui.t("learn_note"), text: $card.note, axis: .vertical)
                TextField(Ui.t("learn_ex"), text: $card.ex, axis: .vertical)
            }
            .navigationTitle(card.word)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { if card.tr.isEmpty, let o = offered { card.tr = o } }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(Ui.t("learn_cancel")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(Ui.t("learn_save")) {
                        var c = card
                        c.tr = c.tr.trimmingCharacters(in: .whitespacesAndNewlines)
                        if c.tr == offered { c.tr = "" }
                        c.cat = c.cat.trimmingCharacters(in: .whitespacesAndNewlines)
                        c.note = c.note.trimmingCharacters(in: .whitespacesAndNewlines)
                        c.ex = c.ex.trimmingCharacters(in: .whitespacesAndNewlines)
                        onSave(c)
                        dismiss()
                    }
                }
            }
        }
    }
}

/// Two wheel pickers side by side: without this each one takes touches across
/// the whole row, so turning the right wheel turns the left one.
extension UIPickerView {
    open override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: super.intrinsicContentSize.height)
    }
}
