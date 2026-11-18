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
        cards = Learn.cards(order: AppLangs.codes).filter { $0.lang == lang || Self.isAlphabet($0) }
        mine = Shared.userWords(lang).sorted()
        if let f = filter, !f.isEmpty, f != Self.allAlpha, !topics.contains(f) { filter = nil }
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
        pick(on ? Self.allAlpha : nil)
        readIn = nil
    }

    /// The filter value for every alphabet card at once.
    private static let allAlpha = "\u{1}alphabets"

    private static func isAlphabet(_ c: Learn.Card) -> Bool { c.cat.contains(" → ") }

    /// The topic a card is shown under: its category, and for an alphabet of
    /// another language that language's code in front ("NB · ABC → ᚠᚢᚦ").
    private func topic(_ c: Learn.Card) -> String {
        Self.isAlphabet(c) && c.lang != lang ? "\(c.lang.uppercased()) · \(c.cat)" : c.cat
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

    private func counted(_ l: String) -> String {
        let n = Learn.cards(order: AppLangs.codes).filter { $0.lang == l }.count
        return AppLangs.name(l) + (n > 0 ? " (\(n))" : "")
    }

    private func switchTo(_ l: String) {
        learnLang = l
        UserDefaults.standard.set(l, forKey: "learn_lang")
        filter = nil; queue = nil; current = nil; justDone = false; readIn = nil
        reload()
    }

    /// One button: the language being learned; the menu offers the keyboard's
    /// languages first, then ＋ the others (turned on in the keyboard when picked).
    private var langMenu: some View {
        Menu {
            ForEach(langs, id: \.self) { l in
                Button(counted(l)) { switchTo(l) }
            }
            Section {
                ForEach(AppLangs.codes.filter { !langs.contains($0) }, id: \.self) { l in
                    Button("＋ " + counted(l)) {
                        Shared.store.set(langs + [l], forKey: "languages")
                        switchTo(l)
                    }
                }
            }
        } label: {
            HStack {
                Text("🌐  " + counted(lang)).font(.title3)
                Spacer()
                Text("▾").foregroundStyle(.secondary)
            }
        }
    }

    private struct Tile: Identifiable {
        let label: String, on: Bool, tap: () -> Void
        var id: String { label }
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
        let shownAs = l == lang ? name : "\(l.uppercased()) · \(name)"
        say("\(Ui.t("learn_added")): \(shownAs)")
        reload()
        pick(shownAs)
    }

    /// Tiles: all, every topic in use, no topic, then the ready sets not taken;
    /// alphabet topics (with ` → ` in the name) go to their own grid.
    private var tiles: (topics: [Tile], alphabets: [Tile], addable: [Tile]) {
        let cats = self.topics
        var words: [Tile] = [], alpha: [Tile] = []
        if !cards.isEmpty {
            words.append(Tile(label: "\(Ui.t("learn_all")) (\(cards.filter { !Self.isAlphabet($0) }.count))", on: filter == nil) { pick(nil) })
        }
        for c in cats {
            let t = Tile(label: "\(c) (\(cards.filter { topic($0) == c }.count))", on: filter == c) { pick(c) }
            if c.contains(" → ") { alpha.append(t) } else { words.append(t) }
        }
        if !cats.isEmpty, cards.contains(where: { $0.cat.isEmpty }) {
            words.append(Tile(label: "\(Ui.t("learn_nocat")) (\(cards.filter { $0.cat.isEmpty }.count))",
                               on: filter == "") { pick("") })
        }
        let l = lang, target = Sets.target(l)
        for set in Sets.names(l) {
            let name = Sets.title(set)
            if cats.contains(name) { continue }
            words.append(Tile(label: "＋ \(name)", on: false) { take(name, Sets.words(set, l, target)) })
        }
        return (words, alpha, [])
    }

    // MARK: the alphabet wheels

    private func shownAs(_ src: String, _ name: String) -> String {
        src == lang ? name : "\(src.uppercased()) · \(name)"
    }

    private func taken(_ src: String, _ name: String) -> Int {
        let t = shownAs(src, name)
        return cards.filter { topic($0) == t }.count
    }

    private func wheel<T: Hashable>(_ sel: T, _ items: [T], _ label: @escaping (T) -> String,
                                    _ set: @escaping (T) -> Void) -> some View {
        Picker("", selection: Binding(get: { sel }, set: set)) {
            ForEach(items, id: \.self) { Text(label($0)).tag($0) }
        }
        .pickerStyle(.wheel).frame(maxWidth: .infinity).frame(height: 110).clipped()
    }

    /// The letters of the language being learned (🌐 at the top), Interslavic
    /// when that one has a single script. No language wheel: every script is
    /// there for every Latin-letter language anyway (owner 261118: "języki
    /// się wpierdalają"). Turning onto a pair already taken shows its cards;
    /// one not taken gets ＋.
    @ViewBuilder private var alphabetWheels: some View {
        let src = Alphabets.scripts(lang).count > 1 ? lang : "isv"
        let all = Alphabets.scripts(src)
        let from = all.first { $0.name == alphaFrom } ?? all[0]
        let tos = all.filter { $0 != from }
        let to = tos.first { $0.name == alphaTo } ?? tos[0]
        HStack(spacing: 0) {
            wheel(from, all, { $0.name }) { f in
                alphaFrom = f.name
                if alphaTo == f.name || alphaTo == nil { alphaTo = (all.first { $0 != f } ?? f).name }
                follow(src, alphaFrom, alphaTo)
            }
            Text("→").foregroundStyle(.secondary)
            wheel(to, tos, { t in t.name + (taken(src, Alphabets.title(from, t)) > 0 ? "  ✓" : "") }) { t in
                alphaFrom = from.name; alphaTo = t.name
                follow(src, alphaFrom, alphaTo)
            }
        }
        let name = Alphabets.title(from, to)
        let pairs = Alphabets.pair(src, from, to)
        let n = taken(src, name)
        let alpha = cards.filter(Self.isAlphabet).count
        HStack {
            if n > 0 {
                Button("\(shownAs(src, name)) (\(n))") { pick(shownAs(src, name)) }
                    .fontWeight(filter == shownAs(src, name) ? .bold : .regular)
            } else if !pairs.isEmpty {
                Button("＋ \(shownAs(src, name)) (\(pairs.count))") { take(name, pairs, from: src) }
            }
            Spacer()
            // Every alphabet taken, of whatever language: reviewed apart from words.
            if alpha > 0 {
                Button("\(Ui.t("learn_all")) (\(alpha))") { pick(Self.allAlpha) }
                    .fontWeight(filter == Self.allAlpha ? .bold : .regular)
            }
        }
        .buttonStyle(.borderless)
    }

    /// A turned wheel landing on a taken alphabet pair shows its cards.
    private func follow(_ src: String, _ from: String?, _ to: String?) {
        let all = Alphabets.scripts(src)
        guard let f = all.first(where: { $0.name == from }) ?? all.first,
              let t = all.first(where: { $0.name == to && $0 != f }) ?? all.first(where: { $0 != f }) else { return }
        let name = Alphabets.title(f, t)
        if taken(src, name) > 0 { pick(shownAs(src, name)) }
        else if cards.contains(where: Self.isAlphabet) { pick(Self.allAlpha) }
    }

    private func grid(_ tiles: [Tile]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(tiles) { t in
                Button(action: t.tap) {
                    Text(t.label).font(.subheadline.weight(t.on ? .bold : .regular))
                        .lineLimit(2).frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.bordered)
                .opacity(t.on ? 1 : 0.7)
            }
        }
        .buttonStyle(.borderless)
    }

    /// Every alphabet is offered in every language, so the heading names them all.
    private let alphabetHead = "ABC · АБВ · ⰀⰁⰂ · ᛆᛒᛍ · ᚠᚢᚦ · ·−"

    var body: some View {
        let t = tiles
        return NavigationStack {
            List {
                // The review first, high up: what the tab is for (owner 261118).
                Section {
                    Text(Ui.t("learn_due").replacingOccurrences(of: "{k}", with: "\(Learn.due(shown).count)"))
                    cardBox
                }

                // Then what to learn: words of a language, or new alphabets.
                Section {
                    Picker("", selection: Binding(get: { alphaMode }, set: setMode)) {
                        Text(Ui.t("learn_words")).tag(false)
                        Text("ABC · ⰀⰁⰂ · ᚠᚢᚦ · ·−").tag(true)
                    }
                    .pickerStyle(.segmented)
                }

                if alphaMode {
                    Section(alphabetHead) {
                        small(Ui.body("learn_alpha_body"))
                        alphabetWheels
                    }
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
                    small(Ui.body("learn_body"))
                    langMenu
                }

                if !t.topics.isEmpty {
                    Section(Ui.head("learn_topics")) { grid(t.topics) }
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
            .brandTitle(Ui.t("learn_head"))
            .overlay(alignment: .bottom) {
                if let t = toast {
                    Text(t).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 12)
                }
            }
            .onAppear {
                reload()
                if alphaMode != isAlphaFilter(filter) { pick(alphaMode ? Self.allAlpha : nil) }
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
            VStack(spacing: 10) {
                small(AppLangs.name(card.lang) + (card.cat.isEmpty ? "" : " · \(card.cat)"))
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
                Text(mode.map { Scripts.convert(card.word, $0, card.lang) } ?? card.word)
                    .font(.system(size: 34, weight: .bold, design: card.cat.hasSuffix("·−") ? .monospaced : .default))
                    .multilineTextAlignment(.center)
                if !revealed {
                    Button(Ui.t("learn_show")) { revealed = true }.buttonStyle(.borderedProminent)
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
                    HStack {
                        ForEach([(0, "learn_g0"), (1, "learn_g1"), (2, "learn_g2")], id: \.0) { g, key in
                            Button(Ui.t(key)) { grade(card, g) }
                                .buttonStyle(.bordered).frame(maxWidth: .infinity)
                        }
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
            HStack {
                Button(Ui.t("learn_start")) { start(Learn.due(shown), study: false) }
                    .disabled(Learn.due(shown).isEmpty)
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
                Button(Ui.t("learn_study")) { start(shown.filter { !knownToday($0) }.shuffled(), study: true) }
                    .disabled(shown.isEmpty)
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderless)
        }
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
            if !study || g == 0 || card.due <= Learn.now() { after = Learn.grade(card, g) }
        } else if g == 0 {
            after = Learn.grade(card, 0)
        }
        recent.append(g)
        if g == 2 {
            markKnown(card)
            // "Know" ends the card's pass. Learned in this pass: a card once
            // forgotten here starts again from one day, not from a minute.
            if !study, let f = first, f < 2, after.due - Learn.now() < 3_600_000 {
                after = Learn.grade(after, 1)
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
