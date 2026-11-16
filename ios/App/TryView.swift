import SwiftUI

/// The ✎ tab - Android `TryTab.kt`, with the word translator (`Gloss`,
/// Wiktionary data, app only). A box to try the
/// keyboard - it tells "the keyboard is broken" from "it is not switched on" -
/// and under it every word typed as a button: a tap puts it among the cards
/// (owner 2026-11-15: unknown words are met while writing, not in the `＋` chip).
struct TryView: View {
    var active = true
    @State private var text = ""
    @State private var known: Set<String> = []
    @State private var toast: String?
    @FocusState private var typing: Bool
    @Environment(\.scenePhase) private var phase

    private var lang: String { AppLangs.current }

    /// The last 12 distinct words of the box, longer than one letter.
    private var words: [String] {
        var seen = Set<String>(), out: [String] = []
        let parts = text.components(separatedBy: CharacterSet.letters.inverted)
        for w in parts where w.count > 1 && seen.insert(w.lowercased()).inserted { out.append(w) }
        return Array(out.suffix(12))
    }

    /// Cards already in the deck, as `lang\tword` lowercased.
    private func reloadKnown() {
        known = Set(Learn.cards(order: AppLangs.codes).map { "\($0.lang)\t\($0.word.lowercased())" })
    }

    private func inDeck(_ l: String, _ w: String) -> Bool { known.contains("\(l)\t\(w.lowercased())") }

    private func added(_ word: String) {
        reloadKnown()
        toast = "\(Ui.t("learn_added")): \(word)"
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { toast = nil }
    }

    /// The translated words, each with the word it came from: the text typed
    /// is often already known, so a card can be made from either side (owner
    /// 2026-11-16: "raz tak, raz tak, pół na pół").
    private var glossed: [(word: String, from: String)] {
        guard let t = table, let to else { return [] }
        var seen = Set<String>(), out: [(String, String)] = []
        let parts = text.components(separatedBy: CharacterSet.letters.inverted)
        for w in parts where w.count > 1 {
            guard var g = Gloss.word(t, w, from: lang, to: to) else { continue }
            if to != "de" { g = g.lowercased() }
            if seen.insert(g.lowercased()).inserted { out.append((g, lang == "de" ? w : w.lowercased())) }
        }
        return Array(out.suffix(12))
    }

    // MARK: translator (Android `addGlossMirror`)

    @State private var table: [String: String]?
    @State private var to: String?
    @State private var targets: [String] = []

    private func loadGloss() {
        let from = lang
        targets = Gloss.targets(from, enabled: Gloss.enabled)
        to = Gloss.target(from)
        guard !targets.isEmpty else { table = nil; return }
        DispatchQueue.global(qos: .userInitiated).async {
            let t = Gloss.table(from)
            DispatchQueue.main.async { table = t }
        }
    }

    /// The text glossed, unknown words greyed.
    private var mirror: Text {
        guard let t = table, let to else { return Text("…") }
        if text.isEmpty { return Text("…").foregroundColor(.secondary) }
        return Gloss.pieces(t, text, from: lang, to: to).reduce(Text("")) { acc, p in
            acc + (p.1 ? Text(p.0) : Text(p.0).foregroundColor(.secondary))
        }
    }

    private var mirrorPlain: String {
        guard let t = table, let to else { return "" }
        return Gloss.pieces(t, text, from: lang, to: to).map(\.0).joined()
    }

    @ViewBuilder private var translator: some View {
        if !targets.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                mirror.font(.title3).textSelection(.enabled)
                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .topLeading)
                HStack {
                    Text("\(lang.uppercased()) →").font(.subheadline).foregroundStyle(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(targets, id: \.self) { l in
                                Button(l.uppercased()) {
                                    to = l
                                    UserDefaults.standard.set(l, forKey: "gloss_target_\(lang)")
                                }
                                .buttonStyle(.bordered)
                                .opacity(l == to ? 1 : 0.45)
                            }
                        }
                    }
                    Button("⧉") {
                        UIPasteboard.general.string = mirrorPlain
                        toast = "✓ \(to?.uppercased() ?? "")"
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { toast = nil }
                    }
                    .buttonStyle(.bordered)
                    .disabled(text.isEmpty)
                }
                if !glossed.isEmpty, let to {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(glossed, id: \.word) { g in
                                let has = inDeck(to, g.word)
                                Button(has ? "✓ \(g.word)" : "＋ \(g.word)") {
                                    Learn.add(to, g.word, tr: g.from)
                                    added(g.word)
                                }
                                .buttonStyle(.bordered)
                                .disabled(has)
                                .opacity(has ? 0.5 : 1)
                            }
                        }
                    }
                }
                // Attribution the licence asks for.
                Text("Wiktionary · CC BY-SA 4.0").font(.caption2).foregroundStyle(.secondary)
            }
            .buttonStyle(.borderless)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                // What the text becomes comes FIRST, at the top: below the box
                // the keyboard covers it (owner 2026-11-15, Android).
                if !targets.isEmpty { Section { translator } }
                Section {
                    TextField(Ui.t("try_hint"), text: $text, axis: .vertical)
                        .focused($typing)
                        .font(.title3)
                        .lineLimit(4...10)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.sentences)
                    if !words.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(Ui.t("try_learn")).font(.footnote).foregroundStyle(.secondary)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack {
                                    ForEach(words, id: \.self) { w in
                                        let has = inDeck(lang, w)
                                        Button(has ? "✓ \(w)" : w) {
                                            // German nouns keep their capital, everything else is stored lowercase.
                                            let word = lang == "de" ? w : w.lowercased()
                                            // No `tr`: the card's back shows the gloss as the
                                            // dictionary's, not as the user's own translation.
                                            Learn.add(lang, word)
                                            added(word)
                                        }
                                        .buttonStyle(.bordered)
                                        .disabled(has)
                                        .opacity(has ? 0.5 : 1)
                                    }
                                }
                            }
                        }
                    }
                } header: {
                    Text(Ui.head("try_head"))
                }
            }
            .navigationTitle("Interslove")
            .overlay(alignment: .bottom) {
                if let t = toast {
                    Text(t).padding(.horizontal, 14).padding(.vertical, 8)
                        .background(.thinMaterial, in: Capsule()).padding(.bottom, 12)
                }
            }
            .onAppear { reloadKnown(); loadGloss() }
            .onChange(of: active) { if $0 { reloadKnown(); loadGloss() } }
            .onChange(of: phase) { if $0 == .active { reloadKnown(); loadGloss() } }
        }
    }
}
