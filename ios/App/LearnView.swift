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
                      ("nb", "norvežsky"), ("en", "English")]
    static var codes: [String] { all.map(\.0) }
    static func name(_ code: String) -> String { all.first { $0.0 == code }?.1 ?? code.uppercased() }

    /// The language the keyboard types now (the keyboard writes it to the group).
    static var current: String { Shared.store.string(forKey: "language") ?? "isv" }
}

/// The 🎓 tab - the iOS twin of Android `LearnTab.kt` (3.9.44): flashcards with
/// spaced repetition, each with the user's OWN translation to check oneself
/// against (owner 2026-11-15: writing it is part of learning), then the `＋`
/// words, which become cards only with "＋ Learn".
struct LearnView: View {
    var active = true
    @State private var cards: [Learn.Card] = []
    @State private var mine: [(String, String)] = []
    @State private var queue: [Learn.Card]?
    @State private var revealed = false
    @State private var justDone = false
    /// nil = all, "" = cards without a category.
    @State private var filter: String?
    @State private var newWord = ""
    @State private var editing: Learn.Card?
    @State private var toast: String?
    @Environment(\.scenePhase) private var phase
    /// Wiktionary glosses already looked up ("" = none), `lang\tword` -> gloss.
    @State private var glosses: [String: String] = [:]
    @State private var looking = false

    /// The card's own translation, else the gloss looked up, else nil.
    private func trOf(_ lang: String, _ word: String, own: String = "") -> String? {
        if !own.isEmpty { return own }
        let g = glosses[Learn.key(lang, word)] ?? ""
        return g.isEmpty ? nil : g
    }

    /// Look up every gloss not looked up yet, once, off the main thread.
    private func lookUp() {
        let want = cards.filter { $0.tr.isEmpty }.map { ($0.lang, $0.word) } + mine
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
        cards = Learn.cards(order: AppLangs.codes)
        mine = AppLangs.codes.flatMap { l in Shared.userWords(l).sorted().map { (l, $0) } }
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

    var body: some View {
        NavigationStack {
            List {
                Section {
                    small(Ui.body("learn_body"))
                    HStack {
                        TextField(Ui.t("learn_add_hint"), text: $newWord)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .onSubmit(addTyped)
                        Button(Ui.t("learn_add"), action: addTyped)
                            .disabled(newWord.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                } header: { Text(Ui.head("learn_head")) }

                Section {
                    Text(Ui.t("learn_due").replacingOccurrences(of: "{k}", with: "\(Learn.due(shown).count)"))
                    cardBox
                    if !Learn.categories(cards).isEmpty { categoryRow }
                }

                Section(Ui.head("learn_deck_head")) {
                    if cards.isEmpty { small(Ui.t("learn_empty")) }
                    ForEach(groups(shown), id: \.0) { lang, list in
                        small(AppLangs.name(lang))
                        ForEach(list) { c in
                            let tr = trOf(c.lang, c.word, own: c.tr)
                            let looked = glosses[c.id] != nil
                            line(c.word + (c.cat.isEmpty ? "" : "  · \(c.cat)"),
                                 tr ?? (looked ? Ui.t("learn_notr") : "…"), known: tr != nil)
                                .contentShape(Rectangle())
                                .onTapGesture { editing = c }
                                .swipeActions {
                                    Button(Ui.t("learn_unlearn"), role: .destructive) {
                                        Learn.drop(c)
                                        queue?.removeAll { $0.id == c.id }
                                        say("\(Ui.t("learn_unlearn")): \(c.word)")
                                        reload()
                                    }
                                    Button("✎") { editing = c }
                                }
                        }
                    }
                }

                Section {
                    small(Ui.body("learn_my_body"))
                    if mine.isEmpty { small(Ui.t("words_none")) }
                    let inDeck = Set(cards.map(\.id))
                    ForEach(groupsMine(), id: \.0) { lang, words in
                        small(AppLangs.name(lang))
                        ForEach(words, id: \.self) { w in
                            HStack {
                                let tr = trOf(lang, w)
                                line(w, tr ?? (glosses[Learn.key(lang, w)] != nil ? "—" : "…"), known: tr != nil)
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
                } header: { Text(Ui.head("words_head")) }
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
                    reload()
                }
            }
        }
    }

    private func addTyped() {
        let w = newWord.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !w.isEmpty else { return }
        Learn.add(AppLangs.current, w)
        newWord = ""
        say("\(Ui.t("learn_added")): \(w)")
        reload()
    }

    /// The review: word → "Show answer" → translation, note, example → three grades.
    @ViewBuilder private var cardBox: some View {
        if let q = queue, let card = q.first {
            VStack(spacing: 10) {
                small(AppLangs.name(card.lang) + (card.cat.isEmpty ? "" : " · \(card.cat)"))
                Text(card.word).font(.system(size: 32, weight: .bold))
                if !revealed {
                    Button(Ui.t("learn_show")) { revealed = true }.buttonStyle(.borderedProminent)
                } else {
                    if let tr = trOf(card.lang, card.word, own: card.tr) {
                        Text(tr).font(.title2)
                        if card.tr.isEmpty {
                            Text("Wiktionary · CC BY-SA 4.0").font(.caption2).foregroundStyle(.secondary)
                        }
                    } else {
                        Button(Ui.t("learn_notr")) { editing = card }.foregroundStyle(.secondary)
                    }
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
            Button(Ui.t("learn_start")) {
                queue = Learn.due(shown)
                revealed = false
                justDone = false
            }.disabled(Learn.due(shown).isEmpty)
        }
    }

    private func grade(_ card: Learn.Card, _ g: Int) {
        let after = Learn.grade(card, g)
        guard var q = queue else { return }
        q.removeFirst()
        // "Don't know" comes back at the end of the same review.
        if g == 0 { q.append(after) }
        revealed = false
        if q.isEmpty { queue = nil; justDone = true } else { queue = q }
        reload()
    }

    private var categoryRow: some View {
        let cats = Learn.categories(cards)
        var opts: [(String?, String)] = [(nil, Ui.t("learn_all"))] + cats.map { ($0, $0) }
        if cards.contains(where: { $0.cat.isEmpty }) { opts.append(("", Ui.t("learn_nocat"))) }
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack {
                ForEach(opts, id: \.1) { f, label in
                    Button(label) { filter = f; queue = nil }
                        .buttonStyle(.bordered)
                        .opacity(f == filter ? 1 : 0.5)
                }
            }
        }
    }

    private func line(_ word: String, _ tr: String, known: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(word)
            Text(tr).font(.subheadline).foregroundStyle(known ? .primary : .secondary)
                .opacity(known ? 0.8 : 1)
        }
    }

    private func groups(_ list: [Learn.Card]) -> [(String, [Learn.Card])] {
        var out: [(String, [Learn.Card])] = []
        for c in list {
            if out.last?.0 == c.lang { out[out.count - 1].1.append(c) } else { out.append((c.lang, [c])) }
        }
        return out
    }

    private func groupsMine() -> [(String, [String])] {
        var out: [(String, [String])] = []
        for (l, w) in mine {
            if out.last?.0 == l { out[out.count - 1].1.append(w) } else { out.append((l, [w])) }
        }
        return out
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
