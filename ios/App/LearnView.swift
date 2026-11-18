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
        cards = Learn.cards(order: AppLangs.codes).filter { $0.lang == lang }
        mine = Shared.userWords(lang).sorted()
        let cats = Learn.categories(cards)
        if let f = filter, !f.isEmpty, !cats.contains(f) { filter = nil }
        lookUp()
    }

    private var shown: [Learn.Card] {
        guard let f = filter else { return cards }
        return cards.filter { $0.cat == f }
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

    private func take(_ name: String, _ pairs: [(String, String)]) {
        for (w, tr) in pairs {
            var c = Learn.card(lang, w)
            c.deck = true
            if c.tr.isEmpty { c.tr = tr }
            if c.cat.isEmpty { c.cat = name }
            Learn.update(c)
        }
        say("\(Ui.t("learn_added")): \(name)")
        reload()
        pick(name)
    }

    /// Tiles: all, every topic in use, no topic, then the ready sets not taken;
    /// alphabet topics (with ` → ` in the name) go to their own grid.
    private var tiles: (topics: [Tile], alphabets: [Tile]) {
        let cats = Learn.categories(cards)
        var topics: [Tile] = [], alpha: [Tile] = []
        if !cards.isEmpty {
            topics.append(Tile(label: "\(Ui.t("learn_all")) (\(cards.count))", on: filter == nil) { pick(nil) })
        }
        for c in cats {
            let t = Tile(label: "\(c) (\(cards.filter { $0.cat == c }.count))", on: filter == c) { pick(c) }
            if c.contains(" → ") { alpha.append(t) } else { topics.append(t) }
        }
        if !cats.isEmpty, cards.contains(where: { $0.cat.isEmpty }) {
            topics.append(Tile(label: "\(Ui.t("learn_nocat")) (\(cards.filter { $0.cat.isEmpty }.count))",
                               on: filter == "") { pick("") })
        }
        let l = lang, target = Sets.target(l)
        for set in Sets.names(l) {
            let name = Sets.title(set)
            if cats.contains(name) { continue }
            topics.append(Tile(label: "＋ \(name)", on: false) { take(name, Sets.words(set, l, target)) })
        }
        for (name, pairs) in Alphabets.sets(l) where !cats.contains(name) {
            alpha.append(Tile(label: "＋ \(name)", on: false) { take(name, pairs) })
        }
        return (topics, alpha)
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

    /// Only the scripts this language has: Spanish is "ABC · ·−", not all four.
    private var alphabetHead: String {
        let have = Set(Alphabets.sets(lang).map(\.0))
        let native = Script.offered(lang)[0]
        return ([Script.label(native)] + Scripts.choices(lang).dropFirst()
            .filter { have.contains(Alphabets.title(lang, $0)) }.map(\.name)).joined(separator: " · ")
    }

    var body: some View {
        let t = tiles
        return NavigationStack {
            List {
                Section {
                    small(Ui.body("learn_body"))
                    langMenu
                } header: { Text(Ui.head("learn_head")) }

                if !t.topics.isEmpty {
                    Section(Ui.head("learn_topics")) { grid(t.topics) }
                }
                if !t.alphabets.isEmpty {
                    Section(alphabetHead) { grid(t.alphabets) }
                }

                Section {
                    Text(Ui.t("learn_due").replacingOccurrences(of: "{k}", with: "\(Learn.due(shown).count)"))
                    cardBox
                }

                Section {
                    DisclosureGroup("\(Ui.t("learn_words")) (\(shown.count))", isExpanded: $showWords) {
                        HStack {
                            TextField(Ui.t("learn_add_hint"), text: $newWord)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .onSubmit(addTyped)
                            Button(Ui.t("learn_add"), action: addTyped)
                                .disabled(newWord.trimmingCharacters(in: .whitespaces).isEmpty)
                        }
                        if cards.isEmpty { small(Ui.t("learn_empty")) }
                        ForEach(shown) { c in
                            let tr = trOf(c.lang, c.word, own: c.tr)
                            let looked = glosses[c.id] != nil
                            row(c.word + (c.cat.isEmpty ? "" : "  · \(c.cat)"),
                                 tr ?? (looked ? Ui.t("learn_notr") : "…"), known: tr != nil)
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
                    DisclosureGroup("\(Ui.t("words_head")) (\(mine.count))", isExpanded: $showMine) {
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
            .navigationTitle("🎓")
            .overlay(alignment: .bottom) {
                if let t = toast {
                    Text(t).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 12)
                }
            }
            .onAppear(perform: reload)
            .onChange(of: active) { if $0 { reload() } }
            .onChange(of: phase) { if $0 == .active { reload() } }
            .sheet(item: $editing) { c in
                CardEditor(card: c, offered: c.tr.isEmpty ? trOf(c.lang, c.word) : nil,
                           categories: Learn.categories(cards)) { saved in
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
        if let cat = filter, !cat.isEmpty {
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
                Button(Ui.t("learn_study")) { start(shown.shuffled(), study: true) }
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
            if !study || g == 0 { after = Learn.grade(card, g) }
        } else if g == 0 {
            after = Learn.grade(card, 0)
        }
        recent.append(g)
        if g == 2 {
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

    private func row(_ word: String, _ tr: String, known: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(word)
            Text(tr).font(.subheadline).foregroundStyle(known ? .primary : .secondary)
                .opacity(known ? 0.8 : 1)
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
